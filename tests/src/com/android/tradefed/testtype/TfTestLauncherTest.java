/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;

import org.easymock.EasyMock;
import org.mockito.Mockito;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link TfTestLauncher}
 */
public class TfTestLauncherTest {

    private static final String CONFIG_NAME = "FAKE_CONFIG";
    private static final String TEST_TAG = "FAKE_TAG";
    private static final String BUILD_BRANCH = "FAKE_BRANCH";
    private static final String BUILD_ID = "FAKE_BUILD_ID";
    private static final String BUILD_FLAVOR = "FAKE_FLAVOR";

    private TfTestLauncher mTfTestLauncher;
    private ITestInvocationListener mMockListener;
    private IRunUtil mMockRunUtil;
    private IFolderBuildInfo mMockBuildInfo;

    @Before
    public void setUp() throws Exception {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockBuildInfo = EasyMock.createMock(IFolderBuildInfo.class);

        mTfTestLauncher = new TfTestLauncher();
        mTfTestLauncher.setRunUtil(mMockRunUtil);
        mTfTestLauncher.setConfigName(CONFIG_NAME);
        mTfTestLauncher.setBuild(mMockBuildInfo);
        mTfTestLauncher.setEventStreaming(false);
    }

    /**
     * Test {@link TfTestLauncher#run(ITestInvocationListener)}
     */
    @Test
    public void testRun() {
        CommandResult cr = new CommandResult(CommandStatus.SUCCESS);
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                (FileOutputStream)EasyMock.anyObject(), (FileOutputStream)EasyMock.anyObject(),
                EasyMock.eq("java"), (String)EasyMock.anyObject(), EasyMock.eq("-cp"),
                (String)EasyMock.anyObject(),
                EasyMock.eq("com.android.tradefed.command.CommandRunner"),
                EasyMock.eq(CONFIG_NAME), EasyMock.eq("-n"), EasyMock.eq("--test-tag"),
                EasyMock.eq(TEST_TAG), EasyMock.eq("--build-id"), EasyMock.eq(BUILD_ID),
                EasyMock.eq("--branch"), EasyMock.eq(BUILD_BRANCH),
                EasyMock.eq("--build-flavor"), EasyMock.eq(BUILD_FLAVOR),
                EasyMock.eq("--subprocess-report-file"),
                (String)EasyMock.anyObject())).andReturn(cr);

        mMockRunUtil.unsetEnvVariable(TfTestLauncher.TF_GLOBAL_CONFIG);
        EasyMock.expect(mMockBuildInfo.getTestTag()).andReturn(TEST_TAG);
        EasyMock.expect(mMockBuildInfo.getBuildBranch()).andReturn(BUILD_BRANCH).times(2);
        EasyMock.expect(mMockBuildInfo.getBuildFlavor()).andReturn(BUILD_FLAVOR).times(2);

        EasyMock.expect(mMockBuildInfo.getRootDir()).andReturn(new File(""));
        EasyMock.expect(mMockBuildInfo.getBuildId()).andReturn(BUILD_ID).times(3);
        mMockListener.testLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (FileInputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);

        mMockListener.testRunStarted("temporaryFiles", 1);
        mMockListener.testRunStarted("StdErr", 1);
        for (int i = 0; i < 2; i++) {
            mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
            mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                    EasyMock.eq(Collections.<String, String>emptyMap()));
            mMockListener.testRunEnded(0, Collections.emptyMap());
        }
        mMockListener.testRunStarted("elapsed-time", 0);
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());

        EasyMock.replay(mMockBuildInfo, mMockRunUtil, mMockListener);
        mTfTestLauncher.run(mMockListener);
        EasyMock.verify(mMockBuildInfo, mMockRunUtil, mMockListener);
    }

    /**
     * Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)}
     */
    @Test
    public void testTestTmpDirClean_success() {
        mMockListener.testRunStarted("temporaryFiles", 1);
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(0, Collections.emptyMap());
        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list()).thenReturn(new String[] {"inv_123", "tradefed_global_log_123"});
        EasyMock.replay(mMockListener);
        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)}
     *
     * Test should fail if there are extra files do not match expected pattern.
     */
    @Test
    public void testTestTmpDirClean_failExtraFile() {
        mMockListener.testRunStarted("temporaryFiles", 1);
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mMockListener.testFailed((TestIdentifier)EasyMock.anyObject(),
                (String)EasyMock.anyObject());
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(0, Collections.emptyMap());
        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list()).thenReturn(new String[] {"extra_file"});
        EasyMock.replay(mMockListener);
        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Test {@link TfTestLauncher#testTmpDirClean(File, ITestInvocationListener)}
     *
     * Test should fail if there are multiple files matching an expected pattern.
     */
    @Test
    public void testTestTmpDirClean_failMultipleFiles() {
        mMockListener.testRunStarted("temporaryFiles", 1);
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());
        mMockListener.testFailed((TestIdentifier)EasyMock.anyObject(),
                (String)EasyMock.anyObject());
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(0, Collections.emptyMap());
        File tmpDir = Mockito.mock(File.class);
        Mockito.when(tmpDir.list()).thenReturn(new String[] {"inv_1", "inv_2"});
        EasyMock.replay(mMockListener);
        mTfTestLauncher.testTmpDirClean(tmpDir, mMockListener);
        EasyMock.verify(mMockListener);
    }
}
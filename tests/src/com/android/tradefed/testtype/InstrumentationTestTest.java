/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.IRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubTestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;

import org.easymock.EasyMock;

import java.io.InputStream;
import java.util.Collection;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InstrumentationTest}
 */
public class InstrumentationTestTest extends TestCase {

    private static final String TEST_PACKAGE_VALUE = "com.foo";
    private static final String TEST_RUNNER_VALUE = ".FooRunner";

    /** The {@link InstrumentationTest} under test, with all dependencies mocked out */
    private InstrumentationTest mInstrumentationTest;

    // The mock objects.
    private IDevice mMockIDevice;
    private ITestDevice mMockTestDevice;
    private IRemoteAndroidTestRunner mMockRemoteRunner;
    private ITestInvocationListener mMockListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockIDevice = EasyMock.createMock(IDevice.class);
        mMockTestDevice = EasyMock.createNiceMock(ITestDevice.class);
        EasyMock.expect(mMockTestDevice.getIDevice()).andReturn(mMockIDevice);
        mMockRemoteRunner = EasyMock.createMock(IRemoteAndroidTestRunner.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);

        mInstrumentationTest = new InstrumentationTest() {
            @Override
            IRemoteAndroidTestRunner createRemoteAndroidTestRunner(String packageName,
                    String runnerName, IDevice device) {
                return mMockRemoteRunner;
            }
        };
       mInstrumentationTest.setPackageName(TEST_PACKAGE_VALUE);
       mInstrumentationTest.setRunnerName(TEST_RUNNER_VALUE);
       mInstrumentationTest.setDevice(mMockTestDevice);
       // default to no rerun, for simplicity
       mInstrumentationTest.setRerunMode(false);
    }

    /**
     * Test normal run scenario.
     */
    @SuppressWarnings("unchecked")
    public void testRun() throws Exception {
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        // verify the mock listener is passed through to the runner
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                assertTrue(listeners.contains(mMockListener));
            }
        });
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class specified.
     */
    @SuppressWarnings("unchecked")
    public void testRun_class() throws Exception {
        final String className = "FooTest";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setClassName(className);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test normal run scenario with a test class and method specified.
     */
    @SuppressWarnings("unchecked")
    public void testRun_classMethod() throws Exception {
        final String className = "FooTest";
        final String methodName = "testFoo";
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockRemoteRunner.setMethodName(className, methodName);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.setClassName(className);
        mInstrumentationTest.setMethodName(methodName);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting package.
     */
    public void testRun_noPackage() throws Exception {
        mInstrumentationTest.setPackageName(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test that IllegalArgumentException is thrown when attempting run without setting device.
     */
    public void testRun_noDevice() throws Exception {
        mInstrumentationTest.setDevice(null);
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test a test run when a test times out.
     */
    @SuppressWarnings("unchecked")
    public void testRun_timeout() throws Exception {
        final long timeout = 1000;
        mInstrumentationTest.setTestTimeout(timeout);
        mMockRemoteRunner.setTestPackageName(TEST_PACKAGE_VALUE);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        // expect run to be cancelled
        mMockRemoteRunner.cancel();
        final TestIdentifier test = new TestIdentifier("FooTest", "testFoo");
        // test should be reported as failed
        mMockListener.testFailed(EasyMock.eq(TestFailure.ERROR), EasyMock.eq(test),
                (String)EasyMock.anyObject());
        // run should be reported as a failure
        mMockListener.testRunFailed(String.format(InstrumentationTest.TIMED_OUT_MSG, timeout));
        mMockListener.testRunLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (InputStream)EasyMock.anyObject());
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockListener);
        EasyMock.replay(mMockTestDevice);
        mInstrumentationTest.run(mMockListener);
        mInstrumentationTest.testTimeout(test);
    }

    /**
     * Test the rerun mode when test run has no tests.
     */
    @SuppressWarnings("unchecked")
    public void testRun_rerunEmpty() throws Exception {
        mInstrumentationTest.setRerunMode(true);
        // expect log only mode run first to collect tests
        mMockRemoteRunner.setLogOnly(true);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                // perform call back on listeners to show empty run
                for (ITestRunListener listener : listeners) {
                    listener.testRunStarted(0);
                    listener.testRunEnded(1);
                }
            }
        });
        mMockListener.testRunStarted(0);
        mMockListener.testRunEnded(1);
        mMockListener.testRunLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (InputStream)EasyMock.anyObject());
        // expect normal mode to be turned off
        mMockRemoteRunner.setLogOnly(false);
        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test the rerun mode when first test run fails.
     */
    @SuppressWarnings("unchecked")
    public void testRun_rerun() throws Exception {
        final TestIdentifier test1 = new TestIdentifier("Test", "test1");
        final TestIdentifier test2 = new TestIdentifier("Test", "test2");
        final String runErrorMsg = "error";

        mInstrumentationTest.setRerunMode(true);
        // expect log only mode run first to collect tests
        mMockRemoteRunner.setLogOnly(true);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                // perform call back on listeners to show run of two tests
                for (ITestRunListener listener : listeners) {
                    listener.testRunStarted(2);
                    listener.testStarted(test1);
                    listener.testEnded(test1);
                    listener.testStarted(test2);
                    listener.testEnded(test2);
                    listener.testRunEnded(1);
                }
            }
        });
        // now expect second run with log only mode off
        mMockRemoteRunner.setLogOnly(false);
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                // perform call back on listeners to show run failed - only one test
                for (ITestRunListener listener : listeners) {
                    listener.testRunStarted(2);
                    listener.testStarted(test1);
                    listener.testEnded(test1);
                    listener.testRunFailed(runErrorMsg);
                }
            }
        });
        // now expect third run to run remaining test
        mMockRemoteRunner.setMethodName(test2.getClassName(), test2.getTestName());
        mMockTestDevice.runInstrumentationTests(EasyMock.eq(mMockRemoteRunner),
                (Collection<ITestRunListener>)EasyMock.anyObject());
        EasyMock.expectLastCall().andDelegateTo(new StubTestDevice() {
            @Override
            public void runInstrumentationTests(IRemoteAndroidTestRunner runner,
                    Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {
                // perform call back on listeners to show run failed - only one test
                for (ITestRunListener listener : listeners) {
                    listener.testRunStarted(1);
                    listener.testStarted(test2);
                    listener.testEnded(test2);
                    listener.testRunEnded(1);
                }
            }
        });

        mMockListener.testRunStarted(2);
        mMockListener.testStarted(test1);
        mMockListener.testEnded(test1);
        mMockListener.testRunFailed(runErrorMsg);
        mMockListener.testRunStarted(1);
        mMockListener.testStarted(test2);
        mMockListener.testEnded(test2);
        mMockListener.testRunEnded(1);
        // expect only one "testRunLog" call
        mMockListener.testRunLog((String)EasyMock.anyObject(), (LogDataType)EasyMock.anyObject(),
                (InputStream)EasyMock.anyObject());

        EasyMock.replay(mMockRemoteRunner);
        EasyMock.replay(mMockTestDevice);
        EasyMock.replay(mMockListener);
        mInstrumentationTest.run(mMockListener);
    }

    /**
     * Test that IllegalArgumentException is thrown if an invalid test size is provided.
     */
    public void testRun_badTestSize() throws Exception {
        mInstrumentationTest.setTestSize("foo");
        EasyMock.replay(mMockRemoteRunner);
        try {
            mInstrumentationTest.run(mMockListener);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}

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

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A result parser for gtest dry run mode with "--gtest_list_tests" parameter.
 *
 */
public class GTestListTestParser extends MultiLineReceiver {

    private String mLastTestClassName = null;
    private String mTestRunName = null;
    private ITestRunListener mTestRunListener = null;
    /** Whether or not to prepend filename to classname. */
    private boolean mPrependFileName = false;

    // test class name should start without leading spaces, and end with a "."
    // example: <line start>RecordingCanvas.<line end>
    private static Pattern TEST_CLASS = Pattern.compile("^[a-zA-Z]+.*\\.$");
    // test method name should start with leading spaces, named as however valid as a C function
    // example: <line start>  emptyPlayback<line end>
    private static Pattern TEST_METHOD = Pattern.compile("\\s+\\w+$");

    private List<TestIdentifier> mTests = new ArrayList<>();

    /**
     * Creates the GTestListTestParser for a single listener.
     *
     * @param testRunName the test run name to provide to
     *            {@link ITestRunListener#testRunStarted(String, int)}
     * @param listener informed of test results as the tests are executing
     */
    public GTestListTestParser(String testRunName, ITestRunListener listener) {
        mTestRunName = testRunName;
        mTestRunListener = listener;
        // don't trim, since we need the leading whitespace
        setTrimLine(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNewLines(String[] lines) {
        for (String line : lines) {
            parse(line);
        }
    }

    private String getTestClass(String name) {
        if (mPrependFileName) {
            StringBuilder sb = new StringBuilder();
            sb.append(mTestRunName);
            sb.append(".");
            sb.append(name);
            return sb.toString();
        }
        return name;
    }

    private void parse(String line) {
        // parsing a new test class
        if (TEST_CLASS.matcher(line).matches()) {
            mLastTestClassName = line;
        } else if (TEST_METHOD.matcher(line).matches()) {
            if (mLastTestClassName == null) {
                throw new IllegalStateException(String.format(
                        "parsed new test case name %s but no test class name has been set", line));
            }
            mTests.add(new TestIdentifier(getTestClass(mLastTestClassName), line));
        } else {
            CLog.v("line ignored: %s", line);
        }
    }

    public void setPrependFileName(boolean prepend) {
        mPrependFileName = prepend;
    }

    public boolean getPrependFileName() {
        return mPrependFileName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void done() {
        // now we send out all the test callbacks
        final Map<String, String> empty = Collections.<String, String>emptyMap();
        mTestRunListener.testRunStarted(mTestRunName, mTests.size());
        for (TestIdentifier id : mTests) {
            mTestRunListener.testStarted(id);
            mTestRunListener.testEnded(id, empty);
        }
        mTestRunListener.testRunEnded(0, empty);
        super.done();
    }
}
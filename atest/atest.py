#!/usr/bin/env python
#
# Copyright 2017, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Command line utility for running Android tests through TradeFederation.

atest helps automate the flow of building test modules across the Android
code base and executing the tests via the TradeFederation test harness.

atest is designed to support any test types that can be ran by TradeFederation.
"""

import logging
import os
import subprocess
import sys
import tempfile
import time

import atest_utils
import cli_translator

EXPECTED_VARS = frozenset([
    atest_utils.ANDROID_BUILD_TOP,
    'ANDROID_TARGET_OUT_TESTCASES',
    'OUT'])
EXIT_CODE_ENV_NOT_SETUP = 1
EXIT_CODE_BUILD_FAILURE = 2
BUILD_STEP = 'build'
INSTALL_STEP = 'install'
TEST_STEP = 'test'
ALL_STEPS = [BUILD_STEP, INSTALL_STEP, TEST_STEP]
TEST_RUN_DIR_PREFIX = 'atest_run_%s_'
HELP_DESC = '''Build, install and run Android tests locally.'''

EPILOG_TEXT = '''


- - - - - - - - -
IDENTIFYING TESTS
- - - - - - - - -

    The positional argument <tests> should be a reference to one or more of the
    tests you'd like to run. Multiple tests can be run in one command by
    separating test references with spaces.

    Usage Template: atest <reference_to_test_1> <reference_to_test_2>

    A <reference_to_test> can be satisfied by the test's MODULE NAME,
    MODULE:CLASS, CLASS NAME, TF INTEGRATION TEST or FILE PATH. Explanations
    and examples of each follow.


    < MODULE NAME >

        Identifying a test by its module name will run the entire module. Input
        the name as it appears in the LOCAL_MODULE or LOCAL_PACKAGE_NAME
        variables in that test's Android.mk or Android.bp file.

        Note: Use < TF INTEGRATION TEST > to run non-module tests integrated
        directly into TradeFed.

        Examples:
            atest FrameworksServicesTests
            atest CtsJankDeviceTestCases


    < MODULE:CLASS >

        Identifying a test by its class name will run just the tests in that
        class and not the whole module. MODULE:CLASS is the preferred way to run
        a single class. MODULE is the same as described above. CLASS is the
        name of the test class in the .java file. It can either be the fully
        qualified class name or just the basic name.

        Examples:
            atest PtsBatteryTestCases:BatteryTest
            atest PtsBatteryTestCases:com.google.android.battery.pts.BatteryTest
            atest CtsJankDeviceTestCases:CtsDeviceJankUi


    < CLASS NAME >

        A single class can also be run by referencing the class name without
        the module name. However, this will take more time than the equivalent
        MODULE:CLASS reference, so we suggest using a MODULE:CLASS reference
        whenever possible.

        Examples:
            atest ScreenDecorWindowTests
            atest com.google.android.battery.pts.BatteryTest
            atest CtsDeviceJankUi


    < TF INTEGRATION TEST >

        To run tests that are integrated directly into TradeFed (non-modules),
        input the name as it appears in the output of the "tradefed.sh list
        configs" cmd.

        Examples:
           atest example/reboot
           atest native-benchmark


    < FILE PATH >

        Both module-based tests and integration-based tests can be run by
        inputting the path to their test file or dir as appropriate. A single
        class can also be run by inputting the path to the class's java file.
        Both relative and absolute paths are supported.

        Example - run module from android repo root:
            atest cts/tests/jank/jank

        Example - same module but from <repo root>/cts/tests/jank:
            atest .

        Example - run just class from android repo root:
            atest cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java

        Example - run tf integration test from android repo root:
            atest tools/tradefederation/contrib/res/config/example/reboot.xml


- - - - - - - - - - - - - - - - - - - - - - - - - -
SPECIFYING INDIVIDUAL STEPS: BUILD, INSTALL OR RUN
- - - - - - - - - - - - - - - - - - - - - - - - - -

    The -b, -i and -t options allow you to specify which steps you want to run.
    If none of those options are given, then all steps are run. If any of these
    options are provided then only the listed steps are run.

    Note: -i alone is not currently support and can only be included with -t.
    Both -b and -t can be run alone.

    Examples:
        atest -b <test>     (just build targets)
        atest -bt <test>    (build targets, run tests, but skip installing apk)
        atest -t <test>     (just run test, skip build/install)
        atest -it <test>    (install and run tests, skip building)


- - - - - - - - - - - - -
RUNNING SPECIFIC METHODS
- - - - - - - - - - - - -

    It is possible to run only specific methods within a test class. To run
    only specific methods, identify the class in any of the ways supported
    for identifying a class (MODULE:CLASS, FILE PATH, etc) and then append the
    name of the method or method using the following template:

    <reference_to_class>#<method1>,<method2>,<method3>...

    Examples:
        FrameworksServicesTests:ScreenDecorWindowTests#testFlagChange,testRemoval
        com.google.android.battery.pts.BatteryTest#testDischarge


- - - - - - - - - - - - -
RUNNING MULTIPLE CLASSES
- - - - - - - - - - - - -

    To run multiple classes, deliminate them with spaces just like you would
    if running multiple tests.  Atest will automatically build and run
    multiple classes in the most efficient way possible.


    Example - two classes in same module:
        atest FrameworksServicesTests:ScreenDecorWindowTests FrameworksServicesTest:DimmerTests

    Example - two classes, different modules:
        atest FrameworksServicesTests:ScreenDecorWindowTests CtsJankDeviceTestCases:CtsDeviceJankUi

'''


def _parse_args(argv):
    """Parse command line arguments.

    Args:
        argv: A list of arguments.

    Returns:
        An argspace.Namespace class instance holding parsed args.
    """
    import argparse
    parser = argparse.ArgumentParser(
        description=HELP_DESC,
        epilog=EPILOG_TEXT,
        formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument('tests', nargs='*', help='Tests to build and/or run.')
    parser.add_argument('-b', '--build', action='append_const', dest='steps',
                        const=BUILD_STEP, help='Run a build.')
    parser.add_argument('-i', '--install', action='append_const', dest='steps',
                        const=INSTALL_STEP, help='Install an APK.')
    parser.add_argument('-t', '--test', action='append_const', dest='steps',
                        const=TEST_STEP, help='Run the tests.')
    parser.add_argument('-w', '--wait-for-debugger', action='store_true',
                        help='Only for instrumentation tests. Waits for '
                             'debugger prior to execution.')
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='Display DEBUG level logging.')
    return parser.parse_args(argv)


def _configure_logging(verbose):
    """Configure the logger.

    Args:
        verbose: A boolean. If true display DEBUG level logs.
    """
    if verbose:
        logging.basicConfig(level=logging.DEBUG)
    else:
        logging.basicConfig(level=logging.INFO)


def _missing_environment_variables():
    """Verify the local environment has been set up to run atest.

    Returns:
        List of strings of any missing environment variables.
    """
    missing = filter(None, [x for x in EXPECTED_VARS if not os.environ.get(x)])
    if missing:
        logging.error('Local environment doesn\'t appear to have been '
                      'initialized. Did you remember to run lunch? Expected '
                      'Environment Variables: %s.', missing)
    return missing


def _is_missing_adb(root_dir=''):
    """Check if system built adb is available.

    TF requires adb and we want to make sure we use the latest built adb (vs.
    system adb that might be too old).

    Args:
        root_dir: A String. Path to the root dir that adb should live in.

    Returns:
        True if adb is missing, False otherwise.
    """
    try:
        output = subprocess.check_output(['which', 'adb'])
    except subprocess.CalledProcessError:
        return True
    # TODO: Check if there is a clever way to determine if system adb is good
    # enough.
    return os.path.commonprefix([output, root_dir]) != root_dir


def make_test_run_dir():
    """Make the test run dir in tmp.

    Returns:
        A string of the dir path.
    """
    utc_epoch_time = int(time.time())
    prefix = TEST_RUN_DIR_PREFIX % utc_epoch_time
    return tempfile.mkdtemp(prefix=prefix)


def run_tests(run_commands):
    """Shell out and execute tradefed run commands.

    Args:
        run_commands: A list of strings of Tradefed run commands.
    """
    logging.info('Running tests')
    # TODO: Build result parser for run command. Until then display raw stdout.
    for run_command in run_commands:
        logging.debug('Executing command: %s', run_command)
        subprocess.check_call(run_command, shell=True, stderr=subprocess.STDOUT)


def main(argv):
    """Entry point of atest script.

    Args:
        argv: A list of arguments.
    """
    args = _parse_args(argv)
    _configure_logging(args.verbose)
    if _missing_environment_variables():
        return EXIT_CODE_ENV_NOT_SETUP
    repo_root = os.environ.get(atest_utils.ANDROID_BUILD_TOP)
    results_dir = make_test_run_dir()
    translator = cli_translator.CLITranslator(results_dir=results_dir,
                                              root_dir=repo_root)
    build_targets, run_commands = translator.translate(args.tests)
    if args.wait_for_debugger:
        run_commands = [cmd + ' --wait-for-debugger' for cmd in run_commands]
    if _is_missing_adb(root_dir=repo_root):
        build_targets.add('adb')
    # args.steps will be None if none of -bit set, else list of params set.
    steps = args.steps if args.steps else ALL_STEPS
    if BUILD_STEP in steps:
        success = atest_utils.build(build_targets, args.verbose)
        if not success:
            return EXIT_CODE_BUILD_FAILURE
    if INSTALL_STEP not in steps:
        run_commands = [cmd + ' --disable-target-preparers'
                        for cmd in run_commands]
    elif TEST_STEP not in steps:
        logging.warn('Install step without test step currently not '
                     'supported, installing AND testing instead.')
        steps.append(TEST_STEP)
    if TEST_STEP in steps:
        run_tests(run_commands)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))

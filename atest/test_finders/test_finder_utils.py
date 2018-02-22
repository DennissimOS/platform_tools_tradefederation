# Copyright 2018, The Android Open Source Project
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
Utils for finder classes.
"""

import logging
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

# pylint: disable=import-error
import atest_error
import atest_enum
import constants

# Helps find apk files listed in a test config (AndroidTest.xml) file.
# Matches "filename.apk" in <option name="foo", value="bar/filename.apk" />
_APK_RE = re.compile(r'^[^/]+\.apk$', re.I)
# Parse package name from the package declaration line of a java file.
# Group matches "foo.bar" of line "package foo.bar;"
_PACKAGE_RE = re.compile(r'\s*package\s+(?P<package>[^;]+)\s*;\s*', re.I)

# Explanation of FIND_REFERENCE_TYPEs:
# ----------------------------------
# 0. CLASS: Name of a java class, usually file is named the same (HostTest lives
#           in HostTest.java)
# 1. QUALIFIED_CLASS: Like CLASS but also contains the package in front like
#.                    com.android.tradefed.testtype.HostTest.
# 2. INTEGRATION: XML file name in one of the 4 integration config directories.
FIND_REFERENCE_TYPE = atest_enum.AtestEnum(['CLASS', 'QUALIFIED_CLASS',
                                            'INTEGRATION'])

# Unix find commands for searching for test files based on test type input.
# Note: Find (unlike grep) exits with status 0 if nothing found.
FIND_CMDS = {
    FIND_REFERENCE_TYPE.CLASS : r"find %s -type d -name \".*\" -prune -o -type "
                                r"f -name '%s.java' -print",
    FIND_REFERENCE_TYPE.QUALIFIED_CLASS: r"find %s -type d -name \".*\" -prune "
                                         r"-o -wholename '*%s.java' -print",
    FIND_REFERENCE_TYPE.INTEGRATION: r"find %s -type d -name \".*\" -prune -o "
                                     r"-wholename '*%s.xml' -print"
}

# XML parsing related constants.
_COMPATIBILITY_PACKAGE_PREFIX = "com.android.compatibility"
_CTS_JAR = "cts-tradefed"
# Setup script for device perf tests.
_PERF_SETUP_LABEL = 'perf-setup.sh'

# XML tags.
_XML_NAME = 'name'
_XML_VALUE = 'value'

# VTS xml parsing constants.
_VTS_TEST_MODULE = 'test-module-name'
_VTS_BINARY_SRC = 'binary-test-source'
_VTS_PUSH_GROUP = 'push-group'
_VTS_PUSH = 'push'
_VTS_BINARY_SRC_DELIM = '::'
_VTS_PUSH_DELIM = '->'
_VTS_PUSH_DIR = os.path.join(os.environ.get(constants.ANDROID_BUILD_TOP, ''),
                             'test', 'vts', 'tools', 'vts-tradefed', 'res',
                             'push_groups')
_VTS_PUSH_SUFFIX = '.push'
_VTS_BITNESS = 'append-bitness'
_VTS_BITNESS_TRUE = 'true'
_VTS_BITNESS_32 = '32'
_VTS_BITNESS_64 = '64'
# Matches 'DATA/target' in '_32bit::DATA/target'
_VTS_BINARY_SRC_DELIM_RE = re.compile(r'.*::(?P<target>.*)$')

# pylint: disable=inconsistent-return-statements
def split_methods(user_input):
    """Split user input string into test reference and list of methods.

    Args:
        user_input: A string of the user's input.
                    Examples:
                        class_name
                        class_name#method1,method2
                        path
                        path#method1,method2
    Returns:
        A tuple. First element is String of test ref and second element is
        a set of method name strings or empty list if no methods included.
    Exception:
        atest_error.TooManyMethodsError raised when input string is trying to
        specify too many methods in a single positional argument.

        Examples of unsupported input strings:
            module:class#method,class#method
            class1#method,class2#method
            path1#method,path2#method
    """
    parts = user_input.split('#')
    if len(parts) == 1:
        return parts[0], frozenset()
    elif len(parts) == 2:
        return parts[0], frozenset(parts[1].split(','))
    raise atest_error.TooManyMethodsError(
        'Too many methods specified with # character in user input: %s.'
        '\n\nOnly one class#method combination supported per positional'
        ' argument. Multiple classes should be separated by spaces: '
        'class#method class#method')


# pylint: disable=inconsistent-return-statements
def get_fully_qualified_class_name(test_path):
    """Parse the fully qualified name from the class java file.

    Args:
        test_path: A string of absolute path to the java class file.

    Returns:
        A string of the fully qualified class name.

    Raises:
        atest_error.MissingPackageName if no class name can be found.
    """
    with open(test_path) as class_file:
        for line in class_file:
            match = _PACKAGE_RE.match(line)
            if match:
                package = match.group('package')
                cls = os.path.splitext(os.path.split(test_path)[1])[0]
                return '%s.%s' % (package, cls)
    raise atest_error.MissingPackageNameError(test_path)


def extract_test_path(output):
    """Extract the test path from the output of a unix 'find' command.

    Example of find output for CLASS find cmd:
    /<some_root>/cts/tests/jank/src/android/jank/cts/ui/CtsDeviceJankUi.java

    Args:
        output: A string output of a unix 'find' command.

    Returns:
        A string of the test path or None if output is '' or None.
    """
    if not output:
        return None
    tests = output.strip('\n').split('\n')
    count = len(tests)
    test_index = 0
    if count > 1:
        numbered_list = ['%s: %s' % (i, t) for i, t in enumerate(tests)]
        print 'Multiple tests found:\n%s' % '\n'.join(numbered_list)
        test_index = int(raw_input('Please enter number of test to use:'))
    return tests[test_index]


def find_class_file(class_name, search_dir):
    """Find a java class file given a class name and search dir.

    Args:
        class_name: A string of the test's class name.
        search_dir: A string of the dirpath to search in.

    Return:
        A string of the path to the java file.
    """
    if '.' in class_name:
        find_cmd = FIND_CMDS[FIND_REFERENCE_TYPE.QUALIFIED_CLASS] % (
            search_dir, class_name.replace('.', '/'))
    else:
        find_cmd = FIND_CMDS[FIND_REFERENCE_TYPE.CLASS] % (
            search_dir, class_name)
    # TODO: Pull out common find cmd and timing code.
    start = time.time()
    logging.debug('Executing: %s', find_cmd)
    out = subprocess.check_output(find_cmd, shell=True)
    logging.debug('Find completed in %ss', time.time() - start)
    logging.debug('Class - Find Cmd Out: %s', out)
    return extract_test_path(out)


def is_equal_or_sub_dir(sub_dir, parent_dir):
    """Return True sub_dir is sub dir or equal to parent_dir.

    Args:
      sub_dir: A string of the sub directory path.
      parent_dir: A string of the parent directory path.

    Returns:
        A boolean of whether both are dirs and sub_dir is sub of parent_dir
        or is equal to parent_dir.
    """
    # avoid symlink issues with real path
    parent_dir = os.path.realpath(parent_dir)
    sub_dir = os.path.realpath(sub_dir)
    if not os.path.isdir(sub_dir) or not os.path.isdir(parent_dir):
        return False
    return os.path.commonprefix([sub_dir, parent_dir]) == parent_dir


def find_parent_module_dir(root_dir, start_dir,
                           file_to_locate=constants.MODULE_CONFIG):
    """From current dir search up file tree until root dir for module dir.

    Args:
      start_dir: A string of the dir to start searching up from.
      root_dir: A string  of the dir that is the parent of the start dir.
      file_to_locate: Name of the file to locate in parent module dir,
              default is set to AndroidTest.xml

    Returns:
        A string of the module dir relative to root.

    Exceptions:
        ValueError: Raised if cur_dir not dir or not subdir of root dir.
        atest_error.TestWithNoModuleError: Raised if no Module Dir found.
    """
    if not is_equal_or_sub_dir(start_dir, root_dir):
        raise ValueError('%s not in repo %s' % (start_dir, root_dir))
    current_dir = start_dir
    while current_dir != root_dir:
        if os.path.isfile(os.path.join(current_dir, file_to_locate)):
            return os.path.relpath(current_dir, root_dir)
        current_dir = os.path.dirname(current_dir)
    raise atest_error.TestWithNoModuleError('No Parent Module Dir for: %s' %
                                            start_dir)


def get_targets_from_xml(xml_file, module_info):
    """Retrieve build targets from the given xml.

    Just a helper func on top of get_targets_from_xml_root.

    Args:
        xml_file: abs path to xml file.
        module_info: ModuleInfo class used to verify targets are valid modules.

    Returns:
        A set of build targets based on the signals found in the xml file.
    """
    xml_root = ET.parse(xml_file).getroot()
    return get_targets_from_xml_root(xml_root, module_info)


def get_targets_from_xml_root(xml_root, module_info):
    """Retrieve build targets from the given xml root.

    We're going to pull the following bits of info:
      - Parse any .apk files listed in the config file.
      - Parse option value for "test-module-name" (for vts tests).
      - Look for the perf script.

    Args:
        module_info: ModuleInfo class used to verify targets are valid modules.
        xml_root: ElementTree xml_root for us to look through.

    Returns:
        A set of build targets based on the signals found in the xml file.
    """
    targets = set()
    option_tags = xml_root.findall('.//option')
    for tag in option_tags:
        target_to_add = None
        value = tag.attrib[_XML_VALUE].strip()
        if _APK_RE.match(value):
            target_to_add = value[:-len('.apk')]
        elif _PERF_SETUP_LABEL in value:
            targets.add(_PERF_SETUP_LABEL)
            continue

        # Let's make sure we can actually build the target.
        if target_to_add and module_info.is_module(target_to_add):
            targets.add(target_to_add)
        elif target_to_add:
            logging.warning('Build target (%s) not present in module info, '
                            'skipping build', target_to_add)

    # TODO (b/70813166): Remove this lookup once all runtime dependencies
    # can be listed as a build dependencies or are in the base test harness.
    nodes_with_class = xml_root.findall(".//*[@class]")
    for class_attr in nodes_with_class:
        fqcn = class_attr.attrib['class'].strip()
        if fqcn.startswith(_COMPATIBILITY_PACKAGE_PREFIX):
            targets.add(_CTS_JAR)
    logging.debug('Targets found in config file: %s', targets)
    return targets

def _get_vts_push_group_targets(push_file, rel_out_dir):
    """Retrieve vts push group build targets.

    A push group file is a file that list out test dependencies and other push
    group files. Go through the push file and gather all the test deps we need.

    Args:
        push_file: Name of the push file in the VTS
        rel_out_dir: Abs path to the out dir to help create vts build targets.

    Returns:
        Set of string which represent build targets.
    """
    targets = set()
    full_push_file_path = os.path.join(_VTS_PUSH_DIR, push_file)
    # pylint: disable=invalid-name
    with open(full_push_file_path) as f:
        for line in f:
            target = line.strip()
            # Skip empty lines.
            if not target:
                continue

            # This is a push file, get the targets from it.
            if target.endswith(_VTS_PUSH_SUFFIX):
                targets |= _get_vts_push_group_targets(line.strip(),
                                                       rel_out_dir)
                continue
            sanitized_target = target.split(_VTS_PUSH_DELIM)[0]
            targets.add(os.path.join(rel_out_dir, sanitized_target))
    return targets

def _specified_bitness(xml_root):
    """Check if the xml file contains the option append-bitness.

    Args:
        xml_file: abs path to xml file.

    Returns:
        True if xml specifies to append-bitness, False otherwise.
    """
    option_tags = xml_root.findall('.//option')
    for tag in option_tags:
        value = tag.attrib[_XML_VALUE].strip()
        name = tag.attrib[_XML_NAME].strip()
        if name == _VTS_BITNESS and value == _VTS_BITNESS_TRUE:
            return True
    return False

def _get_vts_binary_src_target(value, rel_out_dir):
    """Parse out the vts binary src target.

    The value can be in the following pattern:
      - {_32bit,_64bit,_IPC32_32bit}::DATA/target (DATA/target)
      - DATA/target->/data/target (DATA/target)
      - out/host/linx-x86/bin/VtsSecuritySelinuxPolicyHostTest (the string as
        is)

    Args:
        value: String of the XML option value to parse.
        rel_out_dir: String path of out dir to prepend to target when required.

    Returns:
        String of the target to build.
    """
    # We'll assume right off the bat we can use the value as is and modify it if
    # necessary, e.g. out/host/linux-x86/bin...
    target = value
    # _32bit::DATA/target
    match = _VTS_BINARY_SRC_DELIM_RE.match(value)
    if match:
        target = os.path.join(rel_out_dir, match.group('target'))
    # DATA/target->/data/target
    elif _VTS_PUSH_DELIM in value:
        target = value.split(_VTS_PUSH_DELIM, 1)[0].strip()
        target = os.path.join(rel_out_dir, target)
    return target

def get_targets_from_vts_xml(xml_file, rel_out_dir, module_info):
    """Parse a vts xml for test dependencies we need to build.

    We have a separate vts parsing function because we make a big assumption
    on the targets (the way they're formatted and what they represent) and we
    also create these build targets in a very special manner as well.
    The 4 options we're looking for are:
      - binary-test-source
      - push-group
      - push
      - test-module-name

    Args:
        module_info: ModuleInfo class used to verify targets are valid modules.
        rel_out_dir: Abs path to the out dir to help create vts build targets.
        xml_file: abs path to xml file.

    Returns:
        A set of build targets based on the signals found in the xml file.
    """
    xml_root = ET.parse(xml_file).getroot()
    targets = set()
    option_tags = xml_root.findall('.//option')
    for tag in option_tags:
        value = tag.attrib[_XML_VALUE].strip()
        name = tag.attrib[_XML_NAME].strip()
        if name == _VTS_TEST_MODULE:
            if module_info.is_module(value):
                targets.add(value)
            else:
                logging.warning('vts test module (%s) not present in module '
                                'info, skipping build', value)
        elif name == _VTS_BINARY_SRC:
            targets.add(_get_vts_binary_src_target(value, rel_out_dir))
        elif name == _VTS_PUSH_GROUP:
            # Look up the push file and parse out build artifacts (as well as
            # other push group files to parse).
            targets |= _get_vts_push_group_targets(value, rel_out_dir)
        elif name == _VTS_PUSH:
            # Parse out the build artifact directly.
            push_target = value.split(_VTS_PUSH_DELIM)[0]
            # If the config specified append-bitness, append the bits suffixes
            # to the target.
            if _specified_bitness(xml_root):
                targets.add(os.path.join(rel_out_dir, push_target + _VTS_BITNESS_32))
                targets.add(os.path.join(rel_out_dir, push_target + _VTS_BITNESS_64))
            else:
                targets.add(os.path.join(rel_out_dir, push_target))
    logging.debug('Targets found in config file: %s', targets)
    return targets

def get_dir_path_and_filename(path):
    """Return tuple of dir and file name from given path.

    Args:
        path: String of path to break up.

    Returns:
        Tuple of (dir, file) paths.
    """
    if os.path.isfile(path):
        dir_path, file_path = os.path.split(path)
    else:
        dir_path, file_path = path, None
    return dir_path, file_path

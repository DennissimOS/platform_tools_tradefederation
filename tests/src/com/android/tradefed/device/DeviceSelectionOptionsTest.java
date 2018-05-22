/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.tradefed.device;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceManager.FastbootDevice;

import com.google.common.util.concurrent.SettableFuture;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link DeviceSelectionOptions}
 */
public class DeviceSelectionOptionsTest extends TestCase {

    // DEVICE_SERIAL and DEVICE_ENV_SERIAL need to be different.
    private static final String DEVICE_SERIAL = "12345";
    private static final String DEVICE_ENV_SERIAL = "6789";

    private IDevice mMockDevice;
    private IDevice mMockEmulatorDevice;

    // DEVICE_TYPE and OTHER_DEVICE_TYPE should be different
    private static final String DEVICE_TYPE = "charm";
    private static final String OTHER_DEVICE_TYPE = "strange";

    // For mockBatteryTemperatureCheck
    private static final String DUMPSYS_BATTERY_OUTPUT_TEMPLATE =
            "Current Battery Service state:\n"
                    + "  AC powered: true\n"
                    + "  USB powered: false\n"
                    + "  Wireless powered: false\n"
                    + "  Max charging current: 1500000\n"
                    + "  Max charging voltage: 5000000\n"
                    + "  Charge counter: 6418283\n"
                    + "  status: 5\n"
                    + "  health: 2\n"
                    + "  present: true\n"
                    + "  level: 100\n"
                    + "  scale: 100\n"
                    + "  voltage: 4279\n"
                    + "  temperature: %s\n"
                    + "  technology: Li-ion\n";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn(DEVICE_SERIAL);
        EasyMock.expect(mMockDevice.isEmulator()).andStubReturn(Boolean.FALSE);
        mMockEmulatorDevice = EasyMock.createMock(IDevice.class);
        EasyMock.expect(mMockEmulatorDevice.getSerialNumber()).andStubReturn("emulator");
        EasyMock.expect(mMockEmulatorDevice.isEmulator()).andStubReturn(Boolean.TRUE);
    }

    /**
     * Test for {@link DeviceSelectionOptions#getSerials()}
     */
    public void testGetSerials() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        // If no serial is available, the environment variable will be used instead.
        assertEquals(1, options.getSerials().size());
        assertTrue(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertFalse(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Test that {@link DeviceSelectionOptions#getSerials()} does not override the values.
     */
    public void testGetSerialsDoesNotOverride() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(DEVICE_ENV_SERIAL);
        options.addSerial(DEVICE_SERIAL);

        // Check that now we do not override the serial with the environment variable.
        assertEquals(1, options.getSerials().size());
        assertFalse(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Test for {@link DeviceSelectionOptions#getSerials()} without the environment variable set.
     */
    public void testGetSerialsWithNoEnvValue() {
        DeviceSelectionOptions options = getDeviceSelectionOptionsWithEnvVar(null);
        // An empty list will cause it to fetch the
        assertTrue(options.getSerials().isEmpty());
        // If no serial is available and the environment variable is not set, nothing happens.
        assertEquals(0, options.getSerials().size());

        options.addSerial(DEVICE_SERIAL);
        // Check that now we do not override the serial.
        assertEquals(1, options.getSerials().size());
        assertFalse(options.getSerials().contains(DEVICE_ENV_SERIAL));
        assertTrue(options.getSerials().contains(DEVICE_SERIAL));
    }

    /**
     * Helper method to return an anonymous subclass of DeviceSelectionOptions with a given
     * environmental variable.
     *
     * @param value {@link String} of the environment variable ANDROID_SERIAL
     * @return {@link DeviceSelectionOptions} subclass with a given environmental variable.
     */
    private DeviceSelectionOptions getDeviceSelectionOptionsWithEnvVar(final String value) {
        return new DeviceSelectionOptions() {
            // We don't have the environment variable set, return null.
            @Override
            String fetchEnvironmentVariable(String name) {
                return value;
            }
        };
    }

    public void testGetProductVariant_legacy() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();

        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY))
                .andReturn("legacy");
        EasyMock.replay(mMockDevice);

        assertEquals("legacy", options.getDeviceProductVariant(mMockDevice));
    }

    public void testGetProductVariant_vendor() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();

        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT)).andReturn("variant");
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY))
                .andReturn("legacy");
        EasyMock.replay(mMockDevice);

        assertEquals("variant", options.getDeviceProductVariant(mMockDevice));
    }

    public void testGetProductType_mismatch() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(OTHER_DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.BOARD)).andReturn(DEVICE_TYPE);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT)).andReturn(null);
        EasyMock.replay(mMockDevice);

        assertFalse(options.matches(mMockDevice));
    }

    public void testGetProductType_match() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.BOARD)).andReturn(DEVICE_TYPE);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY)).andReturn(null);
        EasyMock.replay(mMockDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test scenario where device does not return a valid product type. For now, this will result
     * in device not being matched.
     */
    public void testGetProductType_missingProduct() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProductType(DEVICE_TYPE);

        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.BOARD)).andReturn(DEVICE_TYPE);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT)).andReturn(null);
        EasyMock.expect(mMockDevice.getProperty(DeviceProperties.VARIANT_LEGACY)).andReturn(null);
        EasyMock.replay(mMockDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test matching by property
     */
    public void testMatches_property() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1", "propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.replay(mMockDevice);

        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test negative case for matching by property
     */
    public void testMatches_propertyNotMatch() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1", "propvalue");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("wrongvalue");
        EasyMock.replay(mMockDevice);
        assertFalse(options.matches(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching by multiple properties
     */
    public void testMatches_multipleProperty() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1", "propvalue");
        options.addProperty("prop2", "propvalue2");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.expect(mMockDevice.getProperty("prop2")).andReturn("propvalue2");
        EasyMock.replay(mMockDevice);
        assertTrue(options.matches(mMockDevice));
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching by multiple properties, when one property does not match
     */
    public void testMatches_notMultipleProperty() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.addProperty("prop1", "propvalue");
        options.addProperty("prop2", "propvalue2");

        EasyMock.expect(mMockDevice.getProperty("prop1")).andReturn("propvalue");
        EasyMock.expect(mMockDevice.getProperty("prop2")).andReturn("wrongpropvalue");
        EasyMock.replay(mMockDevice);
        assertFalse(options.matches(mMockDevice));
        // don't verify in this case, because order of property checks is not deterministic
        // EasyMock.verify(mMockDevice);
    }

    /**
     * Test for matching with an srtub emulator
     */
    public void testMatches_stubEmulator() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setStubEmulatorRequested(true);
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertTrue(options.matches(emulatorDevice));
    }

    /**
     * Test that an stub emulator is not matched by default
     */
    public void testMatches_stubEmulatorNotDefault() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        IDevice emulatorDevice = new StubDevice("emulator", true);
        assertFalse(options.matches(emulatorDevice));
    }

    /**
     * Test for matching with null device requested flag
     */
    public void testMatches_nullDevice() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        IDevice stubDevice = new NullDevice("null device");
        assertTrue(options.matches(stubDevice));
    }


    /**
     * Test for matching with tcp device requested flag
     */
    public void testMatches_tcpDevice() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setTcpDeviceRequested(true);
        IDevice stubDevice = new TcpDevice("tcp device");
        assertTrue(options.matches(stubDevice));
    }

    /**
     * Test that a real device is not matched if the 'null device requested' flag is set
     */
    public void testMatches_notNullDevice() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setNullDeviceRequested(true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that a real device is matched when requested
     */
    public void testMatches_device() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setDeviceRequested(true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
        assertFalse(options.matches(mMockEmulatorDevice));
    }

    /**
     * Test that a emulator is matched when requested
     */
    public void testMatches_emulator() {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setEmulatorRequested(true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
        assertTrue(options.matches(mMockEmulatorDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_minBatteryPass() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setMinBatteryLevel(25);
        mockBatteryCheck(50);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_minBatteryFail() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setMinBatteryLevel(75);
        mockBatteryCheck(50);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_maxBatteryPass() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setMaxBatteryLevel(75);
        mockBatteryCheck(50);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_maxBatteryFail() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setMaxBatteryLevel(25);
        mockBatteryCheck(50);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_forceBatteryCheckTrue() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setRequireBatteryCheck(true);
        mockBatteryCheck(null);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
        options.setMinBatteryLevel(25);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that battery checking works
     */
    public void testMatches_forceBatteryCheckFalse() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setRequireBatteryCheck(false);
        mockBatteryCheck(null);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
        options.setMinBatteryLevel(25);
        assertTrue(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    public void testMatches_maxBatteryTempPass() throws Exception {
        // 50 < 100, test should pass
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(50, 100, true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    public void testMatches_maxBatteryTempFail() throws Exception {
        // 150 > 100, test should fail
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(150, 100, true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    public void testMatches_forceBatteryTempCheckTrue() throws Exception {
        // temperature unavailable, should fail
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(0, 100, true);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /** Test that battery temperature checking works */
    public void testMatches_forceBatteryTempCheckFalse() throws Exception {
        // temperature unavailable, should pass
        DeviceSelectionOptions options = mockBatteryTemperatureCheck(0, 100, false);
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test that min sdk checking works for negative case
     */
    public void testMatches_minSdkFail() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--min-sdk-level", "15");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("10");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that min sdk checking works for positive case
     */
    public void testMatches_minSdkPass() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--min-sdk-level", "10");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("10");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test that device is not matched if device api cannot be determined
     */
    public void testMatches_minSdkNull() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--min-sdk-level", "10");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("blargh");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that max sdk checking works for negative case
     */
    public void testMatches_maxSdkFail() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--max-sdk-level", "15");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("25");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that max sdk checking works for positive case
     */
    public void testMatches_maxSdkPass() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--max-sdk-level", "15");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("10");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertTrue(options.matches(mMockDevice));
    }

    /**
     * Test that device is not matched if device api cannot be determined
     */
    public void testMatches_maxSdkNull() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        ArgsOptionParser p = new ArgsOptionParser(options);
        p.parse("--max-sdk-level", "15");
        EasyMock.expect(
                mMockDevice.getProperty(DeviceProperties.SDK_VERSION))
                .andStubReturn("blargh");
        EasyMock.replay(mMockDevice, mMockEmulatorDevice);
        assertFalse(options.matches(mMockDevice));
    }

    /**
     * Test that min-battery is not used to check non physical devices otherwise they will never
     * match.
     */
    public void testStubDevice_minBattery() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("min-battery", "20");
        setter.setOptionValue("null-device", "true");
        assertTrue(options.matches(new NullDevice("test")));
    }

    /**
     * A FastbootDevice does not expose a battery level so if a battery is specified we cannot match
     * it.
     */
    public void testFastbootDevice_minBattery() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        OptionSetter setter = new OptionSetter(options);
        setter.setOptionValue("min-battery", "20");
        assertFalse(options.matches(new FastbootDevice("serial")));
    }

    /**
     * Ensure that a fastboot device without any special condition can be matched for allocation.
     */
    public void testFastbootDevice() throws Exception {
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        assertTrue(options.matches(new FastbootDevice("serial")));
    }

    private void mockBatteryCheck(Integer battery) {
        SettableFuture<Integer> batteryFuture = SettableFuture.create();
        batteryFuture.set(battery);
        EasyMock.expect(mMockDevice.getBattery()).andStubReturn(batteryFuture);
    }

    private DeviceSelectionOptions mockBatteryTemperatureCheck(
            Integer batteryTemp, Integer maxBatteryTemp, Boolean required) throws Exception {

        // Mock out the execution of executeShellCommand
        String dumpsysOutput = "";

        if (batteryTemp != 0) {
            dumpsysOutput = String.format(DUMPSYS_BATTERY_OUTPUT_TEMPLATE, batteryTemp * 10);
        }

        MockDeviceHelper.injectShellResponse(mMockDevice, "dumpsys battery", dumpsysOutput, false);

        // Create the actual selection options, and set the parameters
        DeviceSelectionOptions options = new DeviceSelectionOptions();
        options.setMaxBatteryTemperature(maxBatteryTemp);
        options.setRequireBatteryTempratureCheck(required);
        return options;
    }
}

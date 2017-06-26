package com.goodix.gestures.jni;

public class GesturesJni {
	
	static{
		try {
			System.loadLibrary("GesturesTool");
		} catch (UnsatisfiedLinkError e) {

		}
	}
	
	public native static int readFirmwareVersion(byte[] V);
	public native static int readSensorId();
	public native static int upgradeFirmware(String binFilePath);
	public native static float pollingUpgradeProgress();
	public native static int detectMinMax();
	public native static int i2cReadRegister(int addr, byte[] data, int len);
	public native static int i2cWriteRegister(int addr, byte[] data, int len);
}

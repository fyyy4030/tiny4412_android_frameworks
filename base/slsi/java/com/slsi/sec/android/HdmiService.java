package com.slsi.sec.android;

public class HdmiService
{
    static {
        System.loadLibrary("hdmiservice_jni");
    }

    public native void setHdmiCableStatus(int status);
    public native void setHdmiMode(int mode);
    public native void setHdmiResolution(int resolution);
    public native void setHdmiHdcp(int enHdcp);
    public native int setHdmiOverScan(int width, int height, int left, int top);
    public native int getHdmiXOverScan();
    public native int getHdmiYOverScan();
    public native void initHdmiService();
}
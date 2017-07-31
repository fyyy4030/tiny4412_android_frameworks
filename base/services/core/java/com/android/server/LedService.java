
package com.android.server;

import android.os.ILedService;

public class LedService extends ILedService.Stub{
    private static final String TAG = "LedService";

	/*调用本地方法c函数来实现访问硬件*/

	public int ledCtrl(int which, int status) throws android.os.RemoteException {
		return native_ledCtrl(which, status);
	}
	public LedService() {//构造方法
		native_ledOpen();
	}

	/*声明*/
	public static native int native_ledOpen();
	public static native void native_ledClose();
	public static native int native_ledCtrl(int which, int status);
}

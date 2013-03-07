package com.daohoangson.android.uinput;

public class NativeMethods {
	public static native boolean open();
	public static native boolean close();
	public static native boolean keyPress(int key, boolean shift, boolean alt);
	public static native boolean pointerTap(int x, int y);
	
	static {
		System.loadLibrary("uinputdemo");
    }
}

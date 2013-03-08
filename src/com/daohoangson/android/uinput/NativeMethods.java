package com.daohoangson.android.uinput;

public class NativeMethods {
	public static int BTN_LEFT = 0x110;
	public static int BTN_TOUCH = 0x14a;
	
	public static native boolean open(boolean absolute, int screenWidth, int screenHeight);

	public static native boolean close();
	
	public static native boolean keyDown(int key);
	
	public static native boolean keyUp(int key);

	public static native boolean keyPress(int key, boolean shift, boolean alt);

	public static native boolean pointerMove(int x, int y);

	public static native boolean pointerSet(int x, int y);

	static {
		System.loadLibrary("uinputdemo");
	}
}

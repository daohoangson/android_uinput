#include <jni.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdint.h>

#include "log.h"
#include "suinput.h"
#include "util.h"

int uinput_fd = -1;
char* device_name = "uinputdemo";

jboolean Java_com_daohoangson_android_uinput_NativeMethods_open(JNIEnv* env,
		jobject thiz, jboolean absolute, jint screen_width, jint screen_height) {
	struct input_id id = { BUS_VIRTUAL, 1, 1, 1 };

	// write the idc file (input device configuration)
	LOGV("open -> write_idc(%s)", device_name);
	bool write_idc_result = write_idc(device_name);

	if (write_idc_result == true) {
		// chmod the /dev/uinput node so we cant open it
		// by default, the permission is 0660
		system("su -c \"chmod 0666 /dev/uinput\"");

		uinput_fd = suinput_open(device_name, &id, absolute ? true : false,
				screen_width, screen_height);
		LOGV("suinput_open(%s,&id,%d,%d,%d) = %d", device_name, absolute, screen_width, screen_height, uinput_fd);
	}

	return uinput_fd > 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_close(JNIEnv* env,
		jobject thiz) {
	int retval = suinput_close(uinput_fd);

	// restore the node permission for security reason
	system("su -c \"chmod 0660 /dev/uinput\"");

	LOGV("suinput_close(%d) = %d", uinput_fd, retval);
	uinput_fd = -1;

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyDown(JNIEnv* env,
		jobject thiz, jint key) {
	int retval = suinput_press(uinput_fd, key);
	LOGV("suinput_press(%d,%d) = %d", uinput_fd, key, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyUp(JNIEnv* env,
		jobject thiz, jint key) {
	int retval = suinput_release(uinput_fd, key);
	LOGV("suinput_release(%d,%d) = %d", uinput_fd, key, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyPress(
		JNIEnv* env, jobject thiz, jint key, jboolean shift, jboolean alt) {
	int retval = 0;

	if (shift) {
		retval = suinput_press(uinput_fd, KEY_LEFTSHIFT);
		LOGV("suinput_press(%d,%d) = %d", uinput_fd, KEY_LEFTSHIFT, retval);
	}
	if (alt && retval == 0) {
		retval = suinput_press(uinput_fd, KEY_LEFTALT);
		LOGV("suinput_press(%d,%d) = %d", uinput_fd, KEY_LEFTALT, retval);
	}

	if (retval == 0) {
		retval = suinput_click(uinput_fd, key);
		LOGV("suinput_click(%d,%d) = %d", uinput_fd, key, retval);
	}

	if (alt && retval == 0) {
		retval = suinput_release(uinput_fd, KEY_LEFTALT);
		LOGV("suinput_release(%d,%d) = %d", uinput_fd, KEY_LEFTALT, retval);
	}
	if (shift && retval == 0) {
		retval = suinput_release(uinput_fd, KEY_LEFTSHIFT);
		LOGV("suinput_release(%d,%d) = %d", uinput_fd, KEY_LEFTSHIFT, retval);
	}

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_pointerMove(
		JNIEnv* env, jobject thiz, jint x, jint y) {
	int retval = suinput_move_pointer(uinput_fd, x, y);
	LOGV("suinput_move_pointer(%d,%d,%d) = %d", uinput_fd, x, y, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_pointerSet(
		JNIEnv* env, jobject thiz, jint x, jint y) {
	int retval = suinput_set_pointer(uinput_fd, x, y);
	LOGV("suinput_set_pointer(%d,%d,%d) = %d", uinput_fd, x, y, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jlong Java_com_daohoangson_android_uinput_NativeMethods_grabScreenShot(
		JNIEnv* env, jobject thiz, jobject bb) {
	jbyte* data = (*env)->GetDirectBufferAddress(env, bb);
	jlong size = (*env)->GetDirectBufferCapacity(env, bb);

	return fb2png(data, size);
}

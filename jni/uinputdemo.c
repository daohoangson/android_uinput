#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdint.h>

#include "suinput.h"

#define LOGTAG "jni"

int uinput_fd = -1;

jboolean Java_com_daohoangson_android_uinput_NativeMethods_open(JNIEnv* env,
		jobject thiz, jboolean absolute, jint screen_width, jint screen_height) {
	struct input_id id = { BUS_VIRTUAL, 1, 1, 1 };

	// chmod the /dev/uinput node so we cant open it
	// by default, the permission is 0660
	system("su -c \"chmod 0666 /dev/uinput\"");

	uinput_fd = suinput_open("uinputdemo", &id, absolute ? true : false
	, screen_width, screen_height);

	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
			"suinput_open(&id,%d,%d,%d) = %d", absolute, screen_width,
			screen_height, uinput_fd);

	return uinput_fd > 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_close(JNIEnv* env,
		jobject thiz) {
	int retval = suinput_close(uinput_fd);

	// restore the node permission for security reason
	system("su -c \"chmod 0660 /dev/uinput\"");

	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG, "suinput_close(%d) = %d",
			uinput_fd, retval);
	uinput_fd = -1;

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyDown(JNIEnv* env,
		jobject thiz, jint key) {
	int retval = suinput_press(uinput_fd, key);
	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
			"suinput_press(%d,%d) = %d", uinput_fd, key, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyUp(JNIEnv* env,
		jobject thiz, jint key) {
	int retval = suinput_release(uinput_fd, key);
	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
			"suinput_release(%d,%d) = %d", uinput_fd, key, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_keyPress(JNIEnv* env,
		jobject thiz, jint key, jboolean shift, jboolean alt) {
	int retval = 0;

	if (shift) {
		retval = suinput_press(uinput_fd, KEY_LEFTSHIFT);
		__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
				"suinput_press(%d,%d) = %d", uinput_fd, KEY_LEFTSHIFT, retval);
	}
	if (alt && retval == 0) {
		retval = suinput_press(uinput_fd, KEY_LEFTALT);
		__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
				"suinput_press(%d,%d) = %d", uinput_fd, KEY_LEFTALT, retval);
	}

	if (retval == 0) {
		retval = suinput_click(uinput_fd, key);
		__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
				"suinput_click(%d,%d) = %d", uinput_fd, key, retval);
	}

	if (alt && retval == 0) {
		retval = suinput_release(uinput_fd, KEY_LEFTALT);
		__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
				"suinput_release(%d,%d) = %d", uinput_fd, KEY_LEFTALT, retval);
	}
	if (shift && retval == 0) {
		retval = suinput_release(uinput_fd, KEY_LEFTSHIFT);
		__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
				"suinput_release(%d,%d) = %d", uinput_fd, KEY_LEFTSHIFT,
				retval);
	}

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_pointerMove(
		JNIEnv* env, jobject thiz, jint x, jint y) {
	int retval = suinput_move_pointer(uinput_fd, x, y);
	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
			"suinput_move_pointer(%d,%d,%d) = %d", uinput_fd, x, y, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

jboolean Java_com_daohoangson_android_uinput_NativeMethods_pointerSet(
		JNIEnv* env, jobject thiz, jint x, jint y) {
	int retval = suinput_set_pointer(uinput_fd, x, y);
	__android_log_print(ANDROID_LOG_VERBOSE, LOGTAG,
			"suinput_set_pointer(%d,%d,%d) = %d", uinput_fd, x, y, retval);

	return retval == 0 ? JNI_TRUE : JNI_FALSE;
}

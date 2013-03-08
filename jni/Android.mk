LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := uinputdemo
LOCAL_SRC_FILES := \
	uinputdemo.c \
	suinput.c

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)

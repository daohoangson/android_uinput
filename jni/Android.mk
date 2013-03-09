LOCAL_PATH := $(call my-dir)

# <-- Build libpng
include $(CLEAR_VARS)

LOCAL_MODULE := libpng
LOCAL_SRC_FILES := libpng/lib/libpng.a

include $(PREBUILT_STATIC_LIBRARY)
# -->

include $(CLEAR_VARS)

LOCAL_MODULE    := uinputdemo
LOCAL_SRC_FILES := \
	uinputdemo.c \
	util.c \
	suinput.c

LOCAL_C_INCLUDES += $(LOCAL_PATH)/libpng/include
LOCAL_STATIC_LIBRARIES := libpng
LOCAL_LDLIBS := -lz -llog

include $(BUILD_SHARED_LIBRARY)

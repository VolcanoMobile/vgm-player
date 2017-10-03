LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := VGMScanner_JNI
LOCAL_SRC_FILES := vgmscanner_jni.cpp
LOCAL_SHARED_LIBRARIES := vgmplay
LOCAL_LDLIBS    := -llog
LOCAL_ARM_MODE := arm

include $(BUILD_SHARED_LIBRARY)

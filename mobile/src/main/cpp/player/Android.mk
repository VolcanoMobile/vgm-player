LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := VGMPlayer_JNI
LOCAL_SRC_FILES := vgmplayer_jni.cpp castplayer_jni.cpp
LOCAL_SHARED_LIBRARIES := vgmplay
LOCAL_LDLIBS    := -llog
LOCAL_ARM_MODE := arm

include $(BUILD_SHARED_LIBRARY)

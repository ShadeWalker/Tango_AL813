ifeq ($(strip $(MTK_SUBTITLE_SUPPORT)), yes)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	android_media_VobSubTrack.cpp\
	SUBParser.cpp\
	VOBSubtitleParser.cpp

LOCAL_SHARED_LIBRARIES := \
	liblog \
	libcutils \
	libutils \
	libandroid_runtime \
	libnativehelper\

LOCAL_MODULE:= libvobsub_jni

include $(BUILD_SHARED_LIBRARY)

endif

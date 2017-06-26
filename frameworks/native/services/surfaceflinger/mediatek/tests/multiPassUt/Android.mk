LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES := \
    $(TOP)/$(MTK_ROOT)/hardware/gralloc_extra/include \
    $(TOP)/$(MTK_ROOT)/hardware/include

LOCAL_SRC_FILES:= \
	multipassUt.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
    libui \
    libgui \
	libgralloc_extra

LOCAL_MODULE:= test-multipassUt

LOCAL_MODULE_TAGS := tests

include $(BUILD_EXECUTABLE)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_SRC_FILES := libdumpstate_default.c
LOCAL_MODULE := libdumpstate.default
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

ifdef BOARD_WLAN_DEVICE
LOCAL_CFLAGS := -DFWDUMP_$(BOARD_WLAN_DEVICE)
endif

LOCAL_SRC_FILES := dumpstate.c utils.c

LOCAL_MODULE := dumpstate

LOCAL_SHARED_LIBRARIES := libcutils liblog libselinux
LOCAL_HAL_STATIC_LIBRARIES := libdumpstate
LOCAL_CFLAGS += -Wall -Wno-unused-parameter -std=gnu99

#Added by guofeiyao for redline
ifeq ($(strip $(TARGET_BUILD_VARIANT)), eng)
LOCAL_CFLAGS += -DMTK_ENG_BUILD
endif
#End

include $(BUILD_EXECUTABLE)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

$(info  "  build com.android.nfcgsma_extras  -- "$(LOCAL_PATH))

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_MODULE:= com.android.nfcgsma_extras

include $(BUILD_JAVA_LIBRARY)

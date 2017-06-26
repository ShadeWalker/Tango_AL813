LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-java-files-under, ../../annotation)
LOCAL_MODULE := test-annotation
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := $(call all-subdir-java-files) \
                   $(call all-java-files-under, ../../annotation)       
LOCAL_PACKAGE_NAME := TelephonyRadioTests
LOCAL_CERTIFICATE := platform
LOCAL_JAVA_LIBRARIES := android.test.runner telephony-common test-annotation

include $(BUILD_PACKAGE)

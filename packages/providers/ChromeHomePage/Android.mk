LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

#ChromeHomePage.apk install to /vendor/app
#LOCAL_MODULE_PATH := $(TARGET_OUT)/vendor/app

LOCAL_PACKAGE_NAME := ChromeHomePage
#LOCAL_PACKAGE_NAME := homepage_provider_tmobile
LOCAL_CERTIFICATE := platform
#LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))


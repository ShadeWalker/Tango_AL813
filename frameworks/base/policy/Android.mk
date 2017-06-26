LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# DTS2015012402503 hanjinxiao/00186861 20141231 begin
policy_extra_dirs := \
    ../../../vendor/huawei/custFwk/frameworks/base/policy \
    ../../../vendor/huawei/custFwk/frameworks/hwCommInterface/base/policy
LOCAL_SRC_FILES += $(call find-other-java-files, $(policy_extra_dirs))
# DTS2015012402503 hanjinxiao/00186861 20141231 end

# DTS2014102105031 litao/185177 20141103 begin
-include $(TOP)/vendor/huawei/Emui/frameworks/hwCommInterface/base/policy/hwCommInterface.mk
# DTS2014102105031 litao/185177 20141103 end
# Decoupling
-include $(TOP)/vendor/huawei/Emui/frameworks/hwcust/base/policy/hwcustInterface.mk
# Decoupling
         
LOCAL_MODULE := android.policy

include $(BUILD_JAVA_LIBRARY)

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# android.policy API table.
# ============================================================
LOCAL_MODULE += android.policy-api

LOCAL_STATIC_JAVA_LIBRARIES := 
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/android.policy-api.txt \
		-nodocs \
		-hidden

include $(BUILD_DROIDDOC)
endif

# additionally, build unit tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))






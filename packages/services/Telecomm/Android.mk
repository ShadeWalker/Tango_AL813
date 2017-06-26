LOCAL_PATH:= $(call my-dir)

# Build the Telecom service.
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := telephony-common\
                         ims-common
# Add for MMI, include the account widget framework
LOCAL_JAVA_LIBRARIES += mediatek-framework
LOCAL_STATIC_JAVA_LIBRARIES := \
        guava \
        com.mediatek.telecom.ext \

phone_common_dir := ../../apps/PhoneCommon

res_dirs := res \
            res_ext \
            $(phone_common_dir)/res

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.phone.common

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
        src/com/mediatek/telecom/recording/IPhoneRecorder.aidl\
        src/com/mediatek/telecom/recording/IPhoneRecordStateListener.aidl
                        
LOCAL_PACKAGE_NAME := Telecom

LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAGS := $(proguard.flags)

# Workaround for "local variable type mismatch" error.
LOCAL_DX_FLAGS += --no-locals

include $(BUILD_PACKAGE)

# Build the test package.
#include $(call all-makefiles-under,$(LOCAL_PATH))

# Build Plug in jar 
include $(LOCAL_PATH)/ext/Android.mk

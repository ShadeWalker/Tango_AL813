LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

incallui_dir := ../InCallUI
contacts_common_dir := ../ContactsCommon
phone_common_dir := ../PhoneCommon
contacts_ext_dir := ../ContactsCommon/ext
dialer_dir := ../Dialer
mms_dir := ../Mms

# M: add mtk-ex
chips_dir := ../../../frameworks/ex/chips

src_dirs := src \
    $(incallui_dir)/src \
    $(dialer_dir)/src \
    $(contacts_common_dir)/src \
    $(phone_common_dir)/src \
    $(contacts_ext_dir)/src \
    $(mms_dir)/src

res_dirs := res \
    res_ext \
    $(incallui_dir)/res \
    $(incallui_dir)/res_ext \
    $(dialer_dir)/res \
    $(dialer_dir)/res_ext \
    $(dialer_dir)/res_speed_dial \
    $(contacts_common_dir)/res \
    $(contacts_common_dir)/res_ext \
    $(phone_common_dir)/res \
    $(chips_dir)/res \
    $(mms_dir)/res

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_SRC_FILES += $(call all-java-files-under, ext/src)
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    frameworks/support/v7/cardview/res
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res_ext

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages com.android.incallui \
    --extra-packages com.android.contacts.common \
    --extra-packages com.android.phone.common \
    --extra-packages android.support.v7.cardview
# M: add mtk-ex
LOCAL_AAPT_FLAGS += --extra-packages com.android.mtkex.chips
LOCAL_AAPT_FLAGS += --extra-packages com.android.dialer 
LOCAL_AAPT_FLAGS += --extra-packages com.android.mms


# M: add ims-common for WFC feature
LOCAL_JAVA_LIBRARIES := telephony-common mediatek-framework voip-common ims-common

LOCAL_JAVA_LIBRARIES += mediatek-common
LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.services.telephony.common \
    com.android.vcard \
    android-common \
    guava \
    android-support-v13 \
    android-support-v7-cardview \
    android-support-v7-palette \
    android-support-v4 \
    android-ex-variablespeed \
    libphonenumber \
    libgeocoding   \
    TouchPalOemModule47 \
    com.mediatek.dialer.ext \
    com.mediatek.incallui.ext
    
LOCAL_STATIC_JAVA_LIBRARIES += jsr305
LOCAL_STATIC_JAVA_LIBRARIES += libchips
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.mms.ext
#HQ_zhangjing add for al812 mms ui
LOCAL_STATIC_JAVA_LIBRARIES += mms_hwframework mms_hwEmui


# M: add mtk-ex
LOCAL_STATIC_JAVA_LIBRARIES += android-common-chips
LOCAL_STATIC_JAVA_LIBRARIES += dialer_hwframework dialer_hwEmui
LOCAL_STATIC_JAVA_LIBRARIES += com.huawei.util.FSJ
LOCAL_REQUIRED_MODULES := libvariablespeed
LOCAL_REQUIRED_MODULES += SoundRecorder

LOCAL_PACKAGE_NAME := Contacts
LOCAL_OVERRIDES_PACKAGES := Dialer
LOCAL_OVERRIDES_PACKAGES += Mms
LOCAL_CERTIFICATE := shared
#LOCAL_CERTIFICATE := shared zhangjing the difference here
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags $(incallui_dir)/proguard.flags $(dialer_dir)/proguard.flags $(mms_dir)/proguard.flags

# Uncomment the following line to build against the current SDK
# This is required for building an unbundled app.
# LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)


include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES :=TouchPalOemModule47:libs/TouchPalOemModule47.jar
include $(BUILD_MULTI_PREBUILT)


include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := dialer_hwframework:$(dialer_dir)/libs/hwframework.jar \
dialer_hwEmui:$(dialer_dir)/libs/hwEmui.jar \
com.huawei.util.FSJ:$(dialer_dir)/libs/com.huawei.util.FSJ.jar
include $(BUILD_MULTI_PREBUILT)

#HQ_zhangjing add for al812 mms ui begin
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := mms_hwframework:$(mms_dir)/libs/hwframework.jar \
mms_hwEmui:$(mms_dir)/libs/hwEmui.jar
include $(BUILD_MULTI_PREBUILT)
#HQ_zhangjing add for al812 mms ui end


# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))

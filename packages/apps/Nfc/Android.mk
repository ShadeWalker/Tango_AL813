ifeq ($(MTK_NFC_SUPPORT), yes)
LOCAL_PATH:= $(call my-dir)

NFC_LOCAL_PATH:= $(LOCAL_PATH)

########################################
# MTK Single-Load NFC Configuration
########################################
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

ifneq ($(MTK_BSP_PACKAGE), yes)
LOCAL_JAVA_LIBRARIES := mediatek-framework
endif

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_SRC_FILES += \
        $(call all-java-files-under, mtk-nfc/src)
        
ifneq ($(MTK_BSP_PACKAGE), yes)

LOCAL_SRC_FILES += \
      src/org/simalliance/openmobileapi/service/ISmartcardServiceCallback.aidl \
      src/org/simalliance/openmobileapi/service/ISmartcardService.aidl \
      src/org/simalliance/openmobileapi/service/ISmartcardServiceReader.aidl \
      src/org/simalliance/openmobileapi/service/ISmartcardServiceSession.aidl \
      src/org/simalliance/openmobileapi/service/ISmartcardServiceChannel.aidl \
      
endif


LOCAL_PACKAGE_NAME := Nfc
LOCAL_CERTIFICATE := platform


LOCAL_JNI_SHARED_LIBRARIES := libnfc_mt6605_jni libmtknfc_dynamic_load_jni

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

########################################
# NXP Configuration
########################################
#include $(CLEAR_VARS)
#
#LOCAL_MODULE_TAGS := optional
#
#LOCAL_SRC_FILES := \
#        $(call all-java-files-under, src)
#
#LOCAL_SRC_FILES += \
#        $(call all-java-files-under, nxp)
#
#LOCAL_PACKAGE_NAME := Nfc
#LOCAL_CERTIFICATE := platform
#
#
#LOCAL_JNI_SHARED_LIBRARIES  := libnfc_jni
#
#LOCAL_PROGUARD_ENABLED := disabled
#
#include $(BUILD_PACKAGE)

########################################
# NCI Configuration
########################################
#include $(CLEAR_VARS)
#
#LOCAL_MODULE_TAGS := optional
#
#LOCAL_SRC_FILES := \
#        $(call all-java-files-under, src)
#
#LOCAL_SRC_FILES += \
#        $(call all-java-files-under, nci)
#
#LOCAL_PACKAGE_NAME := NfcNci
#LOCAL_OVERRIDES_PACKAGES := Nfc
#LOCAL_CERTIFICATE := platform
#
#LOCAL_JNI_SHARED_LIBRARIES := libnfc_nci_jni
#
#LOCAL_PROGUARD_ENABLED := disabled
#
#include $(BUILD_PACKAGE)

########################################

include $(call all-makefiles-under,$(LOCAL_PATH)/mtk-nfc)


LOCAL_PATH := $(NFC_LOCAL_PATH)
$(info  "NFC GSMA LOCAL_PATH  -- "$(LOCAL_PATH))
include $(call all-makefiles-under,$(LOCAL_PATH)/gsma)


#include $(call all-makefiles-under,$(LOCAL_PATH))

endif

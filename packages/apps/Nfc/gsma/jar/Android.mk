LOCAL_PATH:= $(call my-dir)

#$(info  "Building  gsma 1  ...")

include $(CLEAR_VARS)

LOCAL_MODULE := com.gsma.services.nfc.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)

include $(BUILD_PREBUILT)



include $(CLEAR_VARS)

$(info  "Building  com.gsma.services.nfc.jar  ...")

LOCAL_SRC_FILES := $(call all-java-files-under, src)

#LOCAL_SRC_FILES += \
#      src/com/gsma/services/INfcGsma.aidl \

LOCAL_MODULE:= com.gsma.services.nfc
LOCAL_MODULE_TAGS := optional

include $(BUILD_JAVA_LIBRARY)

$(info  "  full_classes_jar -- "$(full_classes_jar))

# put the classes.jar, with full class files instead of classes.dex inside, into the dist directory
$(call dist-for-goals, droidcore, $(full_classes_jar):com.gsma.services.nfc.jar)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := mediatek-framework
LOCAL_JAVA_LIBRARIES += telephony-common

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
                   ../../ContactsCommon/src/com/android/contacts/common/util/UriUtils.java \
                   ../../ContactsCommon/src/com/android/contacts/common/util/Constants.java \
                   ../src/com/android/dialer/PhoneCallDetails.java \
                   ../src/com/android/dialer/calllog/ContactInfo.java \
                   ../src/com/android/dialer/calllog/CallLogQuery.java \
                   ../src/com/mediatek/dialer/util/DialerFeatureOptions.java


LOCAL_MODULE := com.mediatek.dialer.ext

LOCAL_STATIC_JAVA_LIBRARIES := guava
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v13
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_STATIC_JAVA_LIBRARY)


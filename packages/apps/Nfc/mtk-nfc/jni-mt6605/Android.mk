LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)



LOCAL_SRC_FILES:= \
    com_android_nfc_NativeLlcpConnectionlessSocket.cpp \
    com_android_nfc_NativeLlcpServiceSocket.cpp \
    com_android_nfc_NativeLlcpSocket.cpp \
    com_android_nfc_NativeNfcManager.cpp \
    com_android_nfc_NativeNfcTag.cpp \
    com_android_nfc_NativeP2pDevice.cpp \
    com_android_nfc_NativeNfcSecureElement.cpp \
    RoutingManager.cpp \
    com_android_nfc_list.cpp \
    com_android_nfc.cpp

LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    vendor/mediatek/proprietary/external/mtknfc/inc \
    libcore/include

LOCAL_SHARED_LIBRARIES := \
    libnativehelper \
    libcutils \
    libutils \
    liblog \
    libhardware

#LOCAL_CFLAGS += -O0 -g

LOCAL_MODULE := libnfc_mt6605_jni
LOCAL_MODULE_TAGS := optional eng

include $(BUILD_SHARED_LIBRARY)

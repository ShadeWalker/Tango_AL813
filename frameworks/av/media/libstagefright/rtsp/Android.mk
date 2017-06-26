LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=       \
        AAMRAssembler.cpp           \
        AAVCAssembler.cpp           \
        AH263Assembler.cpp          \
        AMPEG2TSAssembler.cpp       \
        AMPEG4AudioAssembler.cpp    \
        AMPEG4ElementaryAssembler.cpp \
        APacketSource.cpp           \
        ARawAudioAssembler.cpp      \
        ARTPAssembler.cpp           \
        ARTPConnection.cpp          \
        ARTPSource.cpp              \
        ARTPWriter.cpp              \
        ARTSPConnection.cpp         \
        ASessionDescription.cpp     \
        SDPLoader.cpp               \

LOCAL_C_INCLUDES:= \
	$(TOP)/frameworks/av/media/libstagefright \
	$(TOP)/frameworks/native/include/media/openmax \
	$(TOP)/external/openssl/include    \
	$(TOP)/frameworks/av/media/libstagefright/mpeg2ts    
	
ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
else

ifeq ($(strip $(MTK_RTP_OVER_RTSP_SUPPORT)), yes)
	LOCAL_CFLAGS += -DMTK_RTP_OVER_RTSP_SUPPORT
endif

ifeq ($(strip $(MTK_RTSP_BITRATE_ADAPTATION_SUPPORT)),yes)
  LOCAL_CFLAGS += -DMTK_RTSP_BITRATE_ADAPTATION_SUPPORT
endif

ifeq ($(strip $(MTK_BSP_PACKAGE)),yes)
	LOCAL_CFLAGS += -DMTK_BSP_PACKAGE
endif

ifeq ($(strip $(HAVE_XLOG_FEATURE)),yes)
	LOCAL_CFLAGS += -DMTK_RTSP_USE_XLOG
endif

ifeq ($(strip $(MTK_AUDIO_DDPLUS_SUPPORT)),yes)
	LOCAL_SRC_FILES += ADDPAssembler.cpp
endif


MTK_CUSTOM_UASTRING_FROM_PROPERTY := yes
ifeq ($(strip $(MTK_BSP_PACKAGE)),yes)
	MTK_CUSTOM_UASTRING_FROM_PROPERTY := no
endif
ifeq ($(strip $(MTK_CUSTOM_UASTRING_FROM_PROPERTY)), yes)
LOCAL_CFLAGS += -DCUSTOM_UASTRING_FROM_PROPERTY
LOCAL_C_INCLUDES += $(MTK_PATH_SOURCE)/frameworks/base/custom/inc
endif

#LOCAL_CFLAGS += -DMTK_RTSP_ERROR_TEST_PLAY_NORANGE
#LOCAL_CFLAGS += -DMTK_RTSP_ERROR_TEST_PLAY_NORTPTIME
#LOCAL_CFLAGS += -DMTK_RTSP_ERROR_TEST_PLAY_SRTIMEOUT

endif

LOCAL_MODULE:= libstagefright_rtsp

ifeq ($(TARGET_ARCH),arm)
    LOCAL_CFLAGS += -Wno-psabi
endif

LOCAL_CFLAGS += -Werror

LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

include $(BUILD_STATIC_LIBRARY)

################################################################################

include $(CLEAR_VARS)

LOCAL_SRC_FILES:=         \
        rtp_test.cpp

LOCAL_SHARED_LIBRARIES := \
	libstagefright liblog libutils libbinder libstagefright_foundation

LOCAL_STATIC_LIBRARIES := \
        libstagefright_rtsp

LOCAL_C_INCLUDES:= \
	frameworks/av/media/libstagefright \
	$(TOP)/frameworks/native/include/media/openmax

LOCAL_CFLAGS += -Wno-multichar

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE:= rtp_test

LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

# include $(BUILD_EXECUTABLE)

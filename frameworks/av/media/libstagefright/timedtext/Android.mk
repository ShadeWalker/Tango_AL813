LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=                 \
        TextDescriptions.cpp      \
        TimedTextDriver.cpp       \
        TimedText3GPPSource.cpp \
        TimedTextSource.cpp       \
        TimedTextSRTSource.cpp    \
        TimedTextPlayer.cpp    \
        TimedTextVOBSUBSource.cpp    \
        TimedTextASSSource.cpp    \
        TimedTextSSASource.cpp    \
        TimedTextTXTSource.cpp    \
        TimedTextVOBSubtitleParser.cpp    \
        TimedTextUtil.cpp    \
        MagicString.cpp    \
        FileCacheManager.cpp  \
        StructTime.cpp    \
        DvbPage.cpp               \
        DVbPageMgr.cpp            \
        DvbClut.cpp               \
        DvbClutMgr.cpp            \
        DvbObject.cpp             \
        DvbObjectMgr.cpp          \
        DvbRegion.cpp             \
        DvbRegionMgr.cpp          \
        DvbDds.cpp                \
        DVBDdsMgr.cpp             \
        dvbparser.cpp             \
        TimedTextDVBSource.cpp	  

LOCAL_CFLAGS += -Wno-multichar


LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/include/media/stagefright/timedtext \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/av/include/media/stagefright


LOCAL_MODULE:= libstagefright_timedtext

include $(BUILD_STATIC_LIBRARY)

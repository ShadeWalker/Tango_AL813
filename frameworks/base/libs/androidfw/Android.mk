#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

# libandroidfw is partially built for the host (used by obbtool, aapt, and others)
# These files are common to host and target builds.

commonSources := \
    Asset.cpp \
    AssetDir.cpp \
    AssetManager.cpp \
    misc.cpp \
    ObbFile.cpp \
    ResourceTypes.cpp \
    StreamingZipInflater.cpp \
    TypeWrappers.cpp \
    ZipFileRO.cpp \
    ZipUtils.cpp

deviceSources := \
    $(commonSources) \
    BackupData.cpp \
    BackupHelpers.cpp \
    CursorWindow.cpp

hostSources := $(commonSources)

# For the host
# =====================================================
include $(CLEAR_VARS)

LOCAL_MODULE:= libandroidfw
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS += -DSTATIC_ANDROIDFW_FOR_TOOLS
LOCAL_SRC_FILES:= $(hostSources)
LOCAL_C_INCLUDES := external/zlib

ifeq ($(strip $(MTK_DFO_RESOLUTION_SUPPORT)),yes)
LOCAL_CFLAGS += -DMTK_FOR_HOST_BUILD_AAPT
endif
include $(BUILD_HOST_STATIC_LIBRARY)


# For the device
# =====================================================

include $(CLEAR_VARS)

LOCAL_MODULE:= libandroidfw
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES:= $(deviceSources)
LOCAL_C_INCLUDES := \
    external/zlib \
    system/core/include
LOCAL_STATIC_LIBRARIES := \
		libziparchive \
  		libandroidfw.huawei
#LOCAL_STATIC_LIBRARIES += libandroidfw.huawei
LOCAL_SHARED_LIBRARIES := \
	libbinder \
	liblog \
	libcutils \
	libutils \
	libz

#LOCAL_STATIC_LIBRARIES := libziparchive
# DTS2014102105499 yinwenshuai/00211458 20141105 begin
#LOCAL_STATIC_LIBRARIES := libandroidfw.huawei
# DTS2014102105499 yinwenshuai/00211458 20141105 end

#LOCAL_C_INCLUDES := \
#    external/zlib \
#    system/core/include
LOCAL_CFLAGS += -DHQ_COUNTRYCODE_CUST

#LOCAL_MODULE:= libandroidfw

#LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)


# Include subdirectory makefiles
# ============================================================

# If we're building with ONE_SHOT_MAKEFILE (mm, mmm), then what the framework
# team really wants is to build the stuff defined by this makefile.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call first-makefiles-under,$(LOCAL_PATH))
endif

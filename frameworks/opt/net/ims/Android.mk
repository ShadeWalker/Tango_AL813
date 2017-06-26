#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
# Copyright 2013 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/src

    
LOCAL_SRC_FILES += \
    $(call all-java-files-under, src/java)\
    $(call all-java-files-under, src/org)
    
LOCAL_SRC_FILES += \
    src/org/gsma/joyn/IJoynServiceRegistrationListener.aidl\
    src/org/gsma/joyn/capability/ICapabilitiesListener.aidl\
    src/org/gsma/joyn/capability/ICapabilityService.aidl\
    src/org/gsma/joyn/chat/IChat.aidl\
    src/org/gsma/joyn/chat/IChatListener.aidl\
    src/org/gsma/joyn/chat/IChatService.aidl\
    src/org/gsma/joyn/chat/IGroupChatListener.aidl\
    src/org/gsma/joyn/chat/INewChatListener.aidl\
    src/org/gsma/joyn/chat/IGroupChat.aidl\
    src/org/gsma/joyn/gsh/IGeolocSharingListener.aidl\
    src/org/gsma/joyn/gsh/INewGeolocSharingListener.aidl\
    src/org/gsma/joyn/gsh/IGeolocSharing.aidl\
    src/org/gsma/joyn/gsh/IGeolocSharingService.aidl\
    src/org/gsma/joyn/ipcall/IIPCall.aidl\
    src/org/gsma/joyn/ipcall/IIPCallPlayer.aidl\
    src/org/gsma/joyn/ipcall/IIPCallRenderer.aidl\
    src/org/gsma/joyn/ipcall/IIPCallListener.aidl\
    src/org/gsma/joyn/ipcall/IIPCallPlayerListener.aidl\
    src/org/gsma/joyn/ipcall/IIPCallRendererListener.aidl\
    src/org/gsma/joyn/ipcall/IIPCallService.aidl\
    src/org/gsma/joyn/ipcall/INewIPCallListener.aidl\
    src/org/gsma/joyn/ish/IImageSharing.aidl\
    src/org/gsma/joyn/ish/IImageSharingListener.aidl\
    src/org/gsma/joyn/ish/IImageSharingService.aidl\
    src/org/gsma/joyn/ish/INewImageSharingListener.aidl\
    src/org/gsma/joyn/vsh/INewVideoSharingListener.aidl\
    src/org/gsma/joyn/vsh/IVideoSharingListener.aidl\
    src/org/gsma/joyn/vsh/IVideoPlayer.aidl\
    src/org/gsma/joyn/vsh/IVideoPlayerListener.aidl\
    src/org/gsma/joyn/vsh/IVideoRenderer.aidl\
    src/org/gsma/joyn/vsh/IVideoRendererListener.aidl\
    src/org/gsma/joyn/vsh/IVideoSharing.aidl\
    src/org/gsma/joyn/vsh/IVideoSharingService.aidl\
    src/org/gsma/joyn/session/IMultimediaSession.aidl\
    src/org/gsma/joyn/session/IMultimediaSessionListener.aidl\
    src/org/gsma/joyn/session/IMultimediaSessionService.aidl\
    src/org/gsma/joyn/ft/IFileTransfer.aidl\
    src/org/gsma/joyn/ft/IFileTransferService.aidl\
    src/org/gsma/joyn/ft/IFileTransferListener.aidl\
    src/org/gsma/joyn/ft/INewFileTransferListener.aidl\
    src/org/gsma/joyn/contacts/IContactsService.aidl\
    src/org/gsma/joyn/ICoreServiceWrapper.aidl \
    

#LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := ims-common

include $(BUILD_JAVA_LIBRARY)

ifeq ($(strip $(BUILD_MTK_API_DEP)), yes)
# ims-common API table.
# ============================================================
LOCAL_MODULE := ims-common-api

LOCAL_JAVA_LIBRARIES += $(LOCAL_STATIC_JAVA_LIBRARIES) okhttp
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

LOCAL_DROIDDOC_OPTIONS:= \
		-api $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/ims-common-api.txt \
		-nodocs \
		-hidden

include $(BUILD_DROIDDOC)
endif

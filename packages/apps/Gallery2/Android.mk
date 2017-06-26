LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := platform

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += com.android.gallery3d.common2
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.camera.ext
LOCAL_STATIC_JAVA_LIBRARIES += xmp_toolkit
LOCAL_STATIC_JAVA_LIBRARIES += mp4parser
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v8-renderscript
LOCAL_STATIC_JAVA_LIBRARIES += android-ex-camera2

LOCAL_JAVA_LIBRARIES += mediatek-framework
LOCAL_JAVA_LIBRARIES += telephony-common

LOCAL_JAVA_LIBRARIES += com.mediatek.effect
LOCAL_RENDERSCRIPT_TARGET_API := 18
LOCAL_RENDERSCRIPT_COMPATIBILITY := 18
LOCAL_RENDERSCRIPT_FLAGS := -rs-package-name=android.support.v8.renderscript

# Keep track of previously compiled RS files too (from bundled GalleryGoogle).
prev_compiled_rs_files := $(call all-renderscript-files-under, src)

# We already have these files from GalleryGoogle, so don't install them.
LOCAL_RENDERSCRIPT_SKIP_INSTALL := $(prev_compiled_rs_files)

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(prev_compiled_rs_files)
LOCAL_SRC_FILES += $(call all-java-files-under, src_pd)
#make plugin
LOCAL_SRC_FILES += $(call all-java-files-under, ext/src)
LOCAL_SRC_FILES += $(call all-java-files-under, ../Camera/src)
LOCAL_SRC_FILES += ../Camera/src/com/mediatek/camera/addition/remotecamera/service/ICameraClientCallback.aidl
LOCAL_SRC_FILES += ../Camera/src/com/mediatek/camera/addition/remotecamera/service/IMtkCameraService.aidl
LOCAL_AIDL_INCLUDES += $(LOCAL_PATH)/../Camera/src

#LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res
LOCAL_ASSET_DIR := packages/apps/Camera/assets
ifeq ($(MTK_GMO_ROM_OPTIMIZE),yes)
    LOCAL_ASSET_DIR:=packages/apps/Camera/slim_assets
endif
ifeq ($(strip $(MTK_CAM_IMAGE_REFOCUS_SUPPORT)),yes)
    LOCAL_ASSET_DIR += packages/apps/Gallery2/assets
endif

ifeq ($(MTK_EMULATOR_SUPPORT),yes)
LOCAL_RESOURCE_DIR += packages/apps/Camera/res_emulator
endif
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res packages/apps/Camera/res packages/apps/Camera/res_ext packages/apps/Camera/res_v2

#LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages com.android.camera

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

#LOCAL_SDK_VERSION := current

# If this is an unbundled build (to install seprately) then include
# the libraries in the APK, otherwise just put them in /system/lib and
# leave them out of the APK
ifneq (,$(TARGET_BUILD_APPS))
  LOCAL_JNI_SHARED_LIBRARIES := libjni_eglfence libjni_filtershow_filters librsjni libjni_jpegstream libjni_motion_track
  ifeq ($(strip $(MTK_SUBTITLE_SUPPORT)),yes)
      LOCAL_JNI_SHARED_LIBRARIES += libjni_subtitle_bitmap
  endif
  ifeq ($(strip $(MTK_CAM_IMAGE_REFOCUS_SUPPORT)),yes)
      LOCAL_JNI_SHARED_LIBRARIES += libjni_image_refocus
  endif
else
  LOCAL_REQUIRED_MODULES := libjni_eglfence libjni_filtershow_filters libjni_jpegstream libjni_motion_track
  ifeq ($(strip $(MTK_SUBTITLE_SUPPORT)),yes)
      LOCAL_REQUIRED_MODULES += libjni_subtitle_bitmap
  endif
  ifeq ($(strip $(MTK_CAM_IMAGE_REFOCUS_SUPPORT)),yes)
      LOCAL_JNI_SHARED_LIBRARIES += libjni_image_refocus
  endif
endif

#REOFUCIMAGE @{
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.ngin3d-static
ifneq ($(strip $(MTK_PLATFORM)),)
LOCAL_JNI_SHARED_LIBRARIES += libja3m liba3m
endif
# @}
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# mtkgallery @{
LOCAL_SRC_FILES += $(call all-java-files-under, mtkgallery/src)
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/mtkgallery/res
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.transcode
LOCAL_JAVA_LIBRARIES += com.mediatek.effect
# @}

LOCAL_MULTILIB := 32
include $(BUILD_PACKAGE)

include $(call all-makefiles-under, jni)

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)

# Use the following include to make gallery test apk
include $(call all-makefiles-under, $(LOCAL_PATH))

endif

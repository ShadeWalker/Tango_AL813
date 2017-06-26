LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Only build program binaries when USE_OPENGL_RENDERER is
# defined in the current device/board configuration
ifeq ($(USE_OPENGL_RENDERER),true)

  LOCAL_SRC_FILES := \
    ProgramBinaryClient.cpp    

  intermediates := $(call intermediates-dir-for,STATIC_LIBRARIES,libRS,TARGET,)

  LOCAL_C_INCLUDES += \
    $(LOCAL_PATH) \
    frameworks/base/libs/hwui \
    external/skia/include/core \
    external/skia/src/core \
    system/core/include
    
  LOCAL_CFLAGS := -DUSE_OPENGL_RENDERER -DEGL_EGLEXT_PROTOTYPES -DGL_GLEXT_PROTOTYPES
  LOCAL_SHARED_LIBRARIES := libbinder libhwui libEGL libGLESv2 libskia libcutils libutils liblog libui libgui
  LOCAL_MODULE := libprogrambinary
  LOCAL_MODULE_TAGS := optional
  
  # Defaults for ATRACE_TAG and LOG_TAG for programbinary
  LOCAL_CFLAGS += -DLOG_TAG=\"ProgramBinary\/Service\"
	  
  include $(BUILD_STATIC_LIBRARY)
  
  include $(call all-makefiles-under,$(LOCAL_PATH))
endif	
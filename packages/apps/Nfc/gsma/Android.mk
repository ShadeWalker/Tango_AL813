LOCAL_PATH:= $(call my-dir)

$(info  "  in gsma  -- "$(LOCAL_PATH))

include $(call all-makefiles-under,$(LOCAL_PATH))

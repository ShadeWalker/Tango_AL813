package com.mediatek.contacts.list;

public class DropDownItem {

    private String mTitle;
    private int mType;

    private long mGroupId;
    private int mSlotId = -1;

    public int getSlotId() {
        return mSlotId;
    }

    public void setSlotId(int slotId) {
        this.mSlotId = slotId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public void setGroupId(long groupId) {
        this.mGroupId = groupId;
    }

}

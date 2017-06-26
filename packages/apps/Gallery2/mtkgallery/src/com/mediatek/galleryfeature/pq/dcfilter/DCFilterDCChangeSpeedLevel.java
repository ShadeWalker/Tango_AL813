package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterDCChangeSpeedLevel extends DCFilter {
    //36
    public DCFilterDCChangeSpeedLevel(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetDCChangeSpeedLevelRange();
        mDefaultIndex = nativeGetDCChangeSpeedLevelIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetDCChangeSpeedLevelIndex(index);
    }

}

package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterDCChangeSpeedLevel2 extends DCFilter {
    //38
    public DCFilterDCChangeSpeedLevel2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetDCChangeSpeedLevel2Range();
        mDefaultIndex = nativeGetDCChangeSpeedLevel2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetDCChangeSpeedLevel2Index(index);
    }

}

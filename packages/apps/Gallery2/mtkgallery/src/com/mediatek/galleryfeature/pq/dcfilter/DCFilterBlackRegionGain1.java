package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackRegionGain1 extends DCFilter {
    //18
    public DCFilterBlackRegionGain1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackRegionGain1Range();
        mDefaultIndex = nativeGetBlackRegionGain1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackRegionGain1Index(index);
    }

}

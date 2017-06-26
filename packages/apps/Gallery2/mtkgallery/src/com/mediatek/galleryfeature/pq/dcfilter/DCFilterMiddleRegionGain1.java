package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterMiddleRegionGain1 extends DCFilter {
//16
    public DCFilterMiddleRegionGain1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetMiddleRegionGain1Range();
        mDefaultIndex = nativeGetMiddleRegionGain1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetMiddleRegionGain1Index(index);
    }

}

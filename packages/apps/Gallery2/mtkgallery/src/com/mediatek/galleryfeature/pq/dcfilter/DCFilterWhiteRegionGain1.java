package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteRegionGain1 extends DCFilter {
    //26
    public DCFilterWhiteRegionGain1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteRegionGain1Range();
        mDefaultIndex = nativeGetWhiteRegionGain1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteRegionGain1Index(index);
    }

}

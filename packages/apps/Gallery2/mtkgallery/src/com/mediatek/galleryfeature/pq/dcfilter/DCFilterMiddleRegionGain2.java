package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterMiddleRegionGain2 extends DCFilter {
//17
    public DCFilterMiddleRegionGain2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetMiddleRegionGain2Range();
        mDefaultIndex = nativeGetMiddleRegionGain2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetMiddleRegionGain2Index(index);
    }

}

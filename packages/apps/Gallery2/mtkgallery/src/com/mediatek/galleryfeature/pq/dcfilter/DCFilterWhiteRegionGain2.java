package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteRegionGain2 extends DCFilter {
    //27
    public DCFilterWhiteRegionGain2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteRegionGain2Range();
        mDefaultIndex = nativeGetWhiteRegionGain2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteRegionGain2Index(index);
    }

}

package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackRegionGain2 extends DCFilter {
    //19
    public DCFilterBlackRegionGain2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackRegionGain2Range();
        mDefaultIndex = nativeGetBlackRegionGain2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackRegionGain2Index(index);
    }

}

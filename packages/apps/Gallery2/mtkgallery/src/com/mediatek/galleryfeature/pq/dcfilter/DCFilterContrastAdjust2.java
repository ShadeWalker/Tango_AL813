package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterContrastAdjust2 extends DCFilter {
    //35
    public DCFilterContrastAdjust2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetContrastAdjust2Range();
        mDefaultIndex = nativeGetContrastAdjust2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContrastAdjust2Index(index);
    }

}

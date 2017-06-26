package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterContrastAdjust1 extends DCFilter {
    //34
    public DCFilterContrastAdjust1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetContrastAdjust1Range();
        mDefaultIndex = nativeGetContrastAdjust1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContrastAdjust1Index(index);
    }

}

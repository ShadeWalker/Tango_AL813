package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterContentSmooth2 extends DCFilter {
//14
    public DCFilterContentSmooth2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetContentSmooth2Range();
        mDefaultIndex = nativeGetContentSmooth2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContentSmooth2Index(index);
    }

}

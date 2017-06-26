package com.mediatek.galleryfeature.pq.dcfilter;


public class DCFilterContentSmooth1 extends DCFilter {
//13
    public DCFilterContentSmooth1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetContentSmooth1Range();
        mDefaultIndex = nativeGetContentSmooth1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContentSmooth1Index(index);
    }

}

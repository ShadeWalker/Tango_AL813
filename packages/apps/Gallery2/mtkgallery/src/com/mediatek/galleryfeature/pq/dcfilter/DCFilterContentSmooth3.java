package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterContentSmooth3 extends DCFilter {
//15
    public DCFilterContentSmooth3(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetContentSmooth3Range();
        mDefaultIndex = nativeGetContentSmooth3Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContentSmooth3Index(index);
    }

}

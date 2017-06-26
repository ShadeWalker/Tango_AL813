package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeTh3 extends DCFilter {
//12
    public DCFilterScenceChangeTh3(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeTh3Range();
        mDefaultIndex = nativeGetScenceChangeTh3Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeTh3Index(index);
    }

}

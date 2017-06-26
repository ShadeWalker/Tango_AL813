package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeTh1 extends DCFilter {
    //10
    public DCFilterScenceChangeTh1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeTh1Range();
        mDefaultIndex = nativeGetScenceChangeTh1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeTh1Index(index);
    }

}

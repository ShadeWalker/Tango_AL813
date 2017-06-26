package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeTh2 extends DCFilter {
//11
    public DCFilterScenceChangeTh2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeTh2Range();
        mDefaultIndex = nativeGetScenceChangeTh2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeTh2Index(index);
    }

}

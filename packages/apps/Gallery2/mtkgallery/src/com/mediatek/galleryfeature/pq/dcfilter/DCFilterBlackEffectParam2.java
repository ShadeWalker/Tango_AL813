package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackEffectParam2 extends DCFilter {
    //23
    public DCFilterBlackEffectParam2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectParam2Range();
        mDefaultIndex = nativeGetBlackEffectParam2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectParam2Index(index);
    }

}

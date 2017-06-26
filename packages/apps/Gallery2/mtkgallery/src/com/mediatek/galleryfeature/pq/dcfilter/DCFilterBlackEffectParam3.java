package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackEffectParam3 extends DCFilter {
    //24
    public DCFilterBlackEffectParam3(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectParam3Range();
        mDefaultIndex = nativeGetBlackEffectParam3Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectParam3Index(index);
    }

}

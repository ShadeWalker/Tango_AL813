package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackEffectParam1 extends DCFilter {
    //22

    public DCFilterBlackEffectParam1(String name) {
        super(name);
    }


    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectParam1Range();
        mDefaultIndex = nativeGetBlackEffectParam1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectParam1Index(index);
    }

}

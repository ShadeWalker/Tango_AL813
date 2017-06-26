package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackEffectParam4 extends DCFilter {
    //25
    public DCFilterBlackEffectParam4(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectParam4Range();
        mDefaultIndex = nativeGetBlackEffectParam4Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectParam4Index(index);
    }

}

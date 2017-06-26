package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackEffectEnable extends DCFilter {
    ///1

    public DCFilterBlackEffectEnable(String name) {
        super(name);
    }


    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectEnableRange();
        mDefaultIndex = nativeGetBlackEffectEnableIndex();
        mCurrentIndex = mDefaultIndex;

    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectEnableIndex(index);
    }

}

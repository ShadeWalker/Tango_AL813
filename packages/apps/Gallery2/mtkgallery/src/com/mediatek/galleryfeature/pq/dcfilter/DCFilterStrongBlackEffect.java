package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterStrongBlackEffect extends DCFilter {
    ///3
    public DCFilterStrongBlackEffect(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetStrongBlackEffectRange();
        mDefaultIndex = nativeGetStrongBlackEffectIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetStrongBlackEffectIndex(index);
    }

}

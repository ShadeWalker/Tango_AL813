package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterAdaptiveBlackEffect extends DCFilter {
    //5
    public DCFilterAdaptiveBlackEffect(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetAdaptiveBlackEffectRange();
        mDefaultIndex = nativeGetAdaptiveBlackEffectIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setDefaultIndex() {
        // TODO Auto-generated method stub
        nativeSetAdaptiveBlackEffectIndex(mDefaultIndex);
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetAdaptiveBlackEffectIndex(index);
    }

}

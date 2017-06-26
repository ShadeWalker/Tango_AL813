package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterAdaptiveWhiteEffect extends DCFilter {
    //6
    public DCFilterAdaptiveWhiteEffect(String name) {
        super(name);
    }


    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetAdaptiveWhiteEffectRange();
        mDefaultIndex = nativeGetAdaptiveWhiteEffectIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetAdaptiveWhiteEffectIndex(index);
    }

}

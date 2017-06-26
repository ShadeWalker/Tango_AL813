package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterStrongWhiteEffect extends DCFilter {
    ///4
    public DCFilterStrongWhiteEffect(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetStrongWhiteEffectRange();
        mDefaultIndex = nativeGetStrongWhiteEffectIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetStrongWhiteEffectIndex(index);
    }

}

package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteEffectParam2 extends DCFilter {
    //31

    public DCFilterWhiteEffectParam2(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectParam2Range();
        mDefaultIndex = nativeGetWhiteEffectParam2Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectParam2Index(index);
    }

}

package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteEffectParam1 extends DCFilter {
    //30
    public DCFilterWhiteEffectParam1(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectParam1Range();
        mDefaultIndex = nativeGetWhiteEffectParam1Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectParam1Index(index);
    }

}

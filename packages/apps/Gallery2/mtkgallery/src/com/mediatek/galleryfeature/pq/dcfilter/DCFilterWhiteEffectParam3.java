package com.mediatek.galleryfeature.pq.dcfilter;
    //32
public class DCFilterWhiteEffectParam3 extends DCFilter {

    public DCFilterWhiteEffectParam3(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectParam3Range();
        mDefaultIndex = nativeGetWhiteEffectParam3Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectParam3Index(index);
    }

}

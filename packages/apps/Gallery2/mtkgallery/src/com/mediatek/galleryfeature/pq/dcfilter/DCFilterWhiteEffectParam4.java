package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteEffectParam4 extends DCFilter {
    //33
    public DCFilterWhiteEffectParam4(String name) {
        super(name);
    }


    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectParam4Range();
        mDefaultIndex = nativeGetWhiteEffectParam4Index();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectParam4Index(index);
    }

}

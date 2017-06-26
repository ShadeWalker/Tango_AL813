package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteEffectEnable extends DCFilter {
    ///2
    public DCFilterWhiteEffectEnable(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectEnableRange();
        mDefaultIndex = nativeGetWhiteEffectEnableIndex();
        mCurrentIndex = mDefaultIndex;

    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectEnableIndex(index);
    }

}

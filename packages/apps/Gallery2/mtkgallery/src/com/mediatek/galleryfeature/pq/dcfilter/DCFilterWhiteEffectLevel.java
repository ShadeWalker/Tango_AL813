package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteEffectLevel extends DCFilter {
    //29
    public DCFilterWhiteEffectLevel(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteEffectLevelRange();
        mDefaultIndex = nativeGetWhiteEffectLevelIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteEffectLevelIndex(index);
    }

}

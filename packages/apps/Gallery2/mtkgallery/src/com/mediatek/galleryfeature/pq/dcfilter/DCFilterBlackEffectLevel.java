package com.mediatek.galleryfeature.pq.dcfilter;
//21
public class DCFilterBlackEffectLevel extends DCFilter {


    public DCFilterBlackEffectLevel(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackEffectLevelRange();
        mDefaultIndex = nativeGetBlackEffectLevelIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackEffectLevelIndex(index);
    }

}

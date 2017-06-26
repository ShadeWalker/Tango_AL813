package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeOnceEn extends DCFilter {
    //7
    public DCFilterScenceChangeOnceEn(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeOnceEnRange();
        mDefaultIndex = nativeGetScenceChangeOnceEnIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeOnceEnIndex(index);
    }

}

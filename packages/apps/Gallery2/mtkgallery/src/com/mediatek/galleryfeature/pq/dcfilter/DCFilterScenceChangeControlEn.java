package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeControlEn extends DCFilter {
//8
    public DCFilterScenceChangeControlEn(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeControlEnRange();
        mDefaultIndex = nativeGetScenceChangeControlEnIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeControlEnIndex(index);
    }

}

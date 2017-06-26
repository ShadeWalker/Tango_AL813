package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterScenceChangeControl extends DCFilter {
//9
    public DCFilterScenceChangeControl(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetScenceChangeControlRange();
        mDefaultIndex = nativeGetScenceChangeControlIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetScenceChangeControlIndex(index);
    }

}

package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterProtectRegionWeight extends DCFilter {
    //39
    public DCFilterProtectRegionWeight(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetProtectRegionWeightRange();
        mDefaultIndex = nativeGetProtectRegionWeightIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetProtectRegionWeightIndex(index);
    }

}

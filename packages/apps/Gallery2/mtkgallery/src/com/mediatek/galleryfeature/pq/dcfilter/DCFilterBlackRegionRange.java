package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterBlackRegionRange extends DCFilter {
    //20

    public DCFilterBlackRegionRange(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetBlackRegionRangeRange();
        mDefaultIndex = nativeGetBlackRegionRangeIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetBlackRegionRangeIndex(index);
    }

}

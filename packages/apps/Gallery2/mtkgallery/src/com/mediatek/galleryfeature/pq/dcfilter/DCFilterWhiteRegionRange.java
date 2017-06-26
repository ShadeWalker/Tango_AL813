package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterWhiteRegionRange extends DCFilter {
    //28
    public DCFilterWhiteRegionRange(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetWhiteRegionRangeRange();
        mDefaultIndex = nativeGetWhiteRegionRangeIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetWhiteRegionRangeIndex(index);
    }

}

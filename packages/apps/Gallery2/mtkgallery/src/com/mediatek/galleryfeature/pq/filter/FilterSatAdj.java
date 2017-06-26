package com.mediatek.galleryfeature.pq.filter;


public class FilterSatAdj extends Filter {

    public FilterSatAdj() {
        super();
    }

    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "GlobalSat:  " + super.getCurrentValue();
    }


    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetSatAdjIndex(index);
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetSatAdjIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetSatAdjRange();
    }


}

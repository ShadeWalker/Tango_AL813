package com.mediatek.galleryfeature.pq.filter;

public class FilterContrastAdj extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Contrast:  " + super.getCurrentValue();
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetContrastAdjIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetContrastAdjRange();
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetContrastAdjIndex(index);
    }

}

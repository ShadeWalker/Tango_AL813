package com.mediatek.galleryfeature.pq.filter;


public class FilterSharpAdj extends Filter {


    public FilterSharpAdj() {
        super();
    }

    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Sharpness:  " + super.getCurrentValue();
    }


    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetSharpAdjIndex(index);
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetSharpAdjIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetSharpAdjRange();
    }

}

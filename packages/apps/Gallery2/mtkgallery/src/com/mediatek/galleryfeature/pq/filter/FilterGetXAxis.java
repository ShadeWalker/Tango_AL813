package com.mediatek.galleryfeature.pq.filter;


public class FilterGetXAxis extends Filter {


    public FilterGetXAxis() {
        super();
    }

    public String getMaxValue() {
        // TODO Auto-generated method stub
        return "-1";
    }


    public String getMinValue() {
        // TODO Auto-generated method stub
        return "0";
    }


    public String getSeekbarProgressValue() {
        // TODO Auto-generated method stub
        return "0";
    }


    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetXAxisRange();
        mDefaultIndex = nativeGetXAxisIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetXAxisIndex(index);
    }


}

package com.mediatek.galleryfeature.pq.filter;

public class FilterGetYAxis extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "0";
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

    }


    public void setIndex() {
        // TODO Auto-generated method stub
        mRange = nativeGetYAxisRange();
        mDefaultIndex = nativeGetYAxisIndex();
        mCurrentIndex = mDefaultIndex;
    }

}

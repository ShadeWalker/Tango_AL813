package com.mediatek.galleryfeature.pq.filter;


public class FilterHueAdj extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "0";
    }


    public String getMaxValue() {
        // TODO Auto-generated method stub
        return "0";
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
        mRange = nativeGetHueAdjRange();
        mDefaultIndex = nativeGetHueAdjIndex();
        mCurrentIndex = mDefaultIndex;
    }


    public void setCurrentIndex(int progress) {
        // TODO Auto-generated method stub

    }


    public void setIndex() {
        // TODO Auto-generated method stub
        nativeSetHueAdjIndex(0);
    }


    public void setDefaultIndex() {
        // TODO Auto-generated method stub

    }

}

package com.mediatek.galleryfeature.pq.filter;


public class FilterGrassToneH extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Grass tone(Hue):  " + (mCurrentIndex + mRange / 2 + 1 - mRange);
    }


    public String getMaxValue() {
        // TODO Auto-generated method stub
        return Integer.toString((mRange - 1) / 2);
    }


    public String getMinValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mRange / 2 + 1 - mRange);
    }
    //

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetGrassToneHIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetGrassToneHRange();
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetGrassToneHIndex(index);
    }

}

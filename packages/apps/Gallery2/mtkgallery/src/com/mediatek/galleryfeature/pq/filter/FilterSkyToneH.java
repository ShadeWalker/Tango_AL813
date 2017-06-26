package com.mediatek.galleryfeature.pq.filter;

public class FilterSkyToneH extends Filter {

    public FilterSkyToneH() {
        super();
    }


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Sky tone(Hue):  " + Integer.toString(mRange / 2 + 1 - mRange + mCurrentIndex);
    }


    public String getMaxValue() {
        // TODO Auto-generated method stub
        return Integer.toString((mRange - 1) / 2);
    }

    public String getMinValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mRange / 2 + 1 - mRange);
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetSkyToneHIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetSkyToneHRange();
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetSkyToneHIndex(index);
    }

}

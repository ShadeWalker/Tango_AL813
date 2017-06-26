package com.mediatek.galleryfeature.pq.filter;

public class FilterSkinToneH extends Filter {

    public FilterSkinToneH() {
        super();
    }

    public String getCurrentValue() {
        return "Skin tone(Hue):  " + (mRange / 2 + 1 - mRange + mCurrentIndex);
    }


    public String getMaxValue() {
        return Integer.toString((mRange - 1) / 2);
    }


    public String getMinValue() {
        return Integer.toString(mRange / 2 + 1 - mRange);
    }


    public void setIndex(int index) {
        nativeSetSkinToneHIndex(index);
    }

    public void init() {
        mDefaultIndex = nativeGetSkinToneHIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetSkinToneHRange();

    }

}

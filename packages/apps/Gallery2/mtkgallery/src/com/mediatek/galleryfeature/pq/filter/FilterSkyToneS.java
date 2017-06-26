package com.mediatek.galleryfeature.pq.filter;
public class FilterSkyToneS extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Sky tone(Sat):  " +  super.getCurrentValue();
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetSkyToneSRange();
        mDefaultIndex = nativeGetSkyToneSIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetSkyToneSIndex(index);
    }

}

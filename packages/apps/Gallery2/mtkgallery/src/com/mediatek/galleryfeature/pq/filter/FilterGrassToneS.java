package com.mediatek.galleryfeature.pq.filter;

public class FilterGrassToneS extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Grass tone(Sat):  " + super.getCurrentValue();
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetGrassToneSIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetGrassToneSRange();
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetGrassToneSIndex(index);
    }
}

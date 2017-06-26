package com.mediatek.galleryfeature.pq.filter;

public class FilterSkinToneS extends Filter {


    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return "Skin tone(Sat):  " +  super.getCurrentValue();
    }

    public void init() {
        // TODO Auto-generated method stub
        mDefaultIndex = nativeGetSkinToneSIndex();
        mCurrentIndex = mDefaultIndex;
        mRange = nativeGetSkinToneSRange();
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetSkinToneSIndex(index);
    }


    public String getSeekbarProgressValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mCurrentIndex);
    }

}

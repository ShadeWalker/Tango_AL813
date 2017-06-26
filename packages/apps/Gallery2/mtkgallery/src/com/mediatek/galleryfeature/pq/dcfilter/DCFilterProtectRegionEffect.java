package com.mediatek.galleryfeature.pq.dcfilter;

public class DCFilterProtectRegionEffect extends DCFilter {
    //37
    public DCFilterProtectRegionEffect(String name) {
        super(name);
    }

    public void init() {
        // TODO Auto-generated method stub
        mRange = nativeGetProtectRegionEffectRange();
        mDefaultIndex = nativeGetProtectRegionEffectIndex();
        mCurrentIndex = mDefaultIndex;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub
        nativeSetProtectRegionEffectIndex(index);
    }

}

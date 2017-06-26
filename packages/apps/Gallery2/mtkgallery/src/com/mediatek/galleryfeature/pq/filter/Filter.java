package com.mediatek.galleryfeature.pq.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.mediatek.galleryframework.util.MtkLog;

public class Filter implements FilterInterface {

    public static final String TAG = "MtkGallery2/Filter";
    public static final String  MIN_VALUE = "textViewMinValue";
    public static final String RANGE = "textViewMaxValue";
    public static final String CURRRENT_INDEX = "textViewCurrentIndex";
    public static final String SEEKBAR_PROGRESS = "seekbarProgress";
    protected int mDefaultIndex ;
    protected int mRange;
    protected int mCurrentIndex;

    static {
        System.loadLibrary("PQjni");
    }

    public Map<String, String> map = new HashMap<String, String>();

    public Map<String, String> getDate() {
        return map;
    }
    public Filter() {
        init();
        map.put(MIN_VALUE, getMinValue());
        map.put(RANGE, getMaxValue());
        map.put(CURRRENT_INDEX, getCurrentValue());
        map.put(SEEKBAR_PROGRESS, getSeekbarProgressValue());
        MtkLog.d(TAG, "<Filter> Create [" + this.getClass().getName() + " ]: MIN_VALUE=" + getMinValue()
                + " RANGE=" + getMaxValue()
                + " CURRRENT_INDEX=" + getCurrentValue()
                + "  SEEKBAR_PROGRESS=" + getSeekbarProgressValue());
    }

    public boolean addToList(ArrayList<FilterInterface> list) {
        if (Integer.parseInt(getMaxValue()) > 0) {
            list.add((FilterInterface) this);
            MtkLog.d(TAG, "<addToList>:::" + this.getClass().getName() + " has alread addToList! ");
            return true;
        }
        return false;
    }

    public ArrayList<FilterInterface> getFilterList() {
        ArrayList<FilterInterface> list = new ArrayList<FilterInterface>();
        (new FilterSharpAdj()).addToList(list);
        (new FilterSatAdj()).addToList(list);
        (new FilterHueAdj()).addToList(list);
        (new FilterSkinToneH()).addToList(list);
        (new FilterSkinToneS()).addToList(list);
        (new FilterSkyToneH()).addToList(list);
        (new FilterSkyToneS()).addToList(list);
        (new FilterGetXAxis()).addToList(list);
        (new FilterGetYAxis()).addToList(list);
        (new FilterGrassToneH()).addToList(list);
        (new FilterGrassToneS()).addToList(list);
        (new FilterContrastAdj()).addToList(list);
        return list;
    }

    public native int nativeGetContrastAdjRange();
    public native int nativeGetContrastAdjIndex();
    public native boolean nativeSetContrastAdjIndex(int index);

    public native int nativeGetXAxisRange();
    public native int nativeGetXAxisIndex();
    public native boolean nativeSetXAxisIndex(int index);

    public native int nativeGetYAxisRange();
    public native int nativeGetYAxisIndex();
    public native boolean nativeSetYAxisIndex(int index);

    public native int nativeGetGrassToneHRange();
    public native int nativeGetGrassToneHIndex();
    public native boolean nativeSetGrassToneHIndex(int index);

    public native int nativeGetGrassToneSRange();
    public native int nativeGetGrassToneSIndex();
    public native boolean nativeSetGrassToneSIndex(int index);

    public native int nativeGetHueAdjRange();
    public native int nativeGetHueAdjIndex();
    public native boolean nativeSetHueAdjIndex(int index);

    public native int nativeGetSatAdjRange();
    public native int nativeGetSatAdjIndex();
    public native boolean nativeSetSatAdjIndex(int index);

    public native int nativeGetSharpAdjRange();
    public native int nativeGetSharpAdjIndex();
    public native boolean nativeSetSharpAdjIndex(int index);

    public native int nativeGetSkinToneHRange();
    public native int nativeGetSkinToneHIndex();
    public native boolean nativeSetSkinToneHIndex(int index);

    public native int nativeGetSkinToneSRange();
    public native int nativeGetSkinToneSIndex();
    public native boolean nativeSetSkinToneSIndex(int index);

    public native int nativeGetSkyToneHRange();
    public native int nativeGetSkyToneHIndex();
    public native boolean nativeSetSkyToneHIndex(int index);

    public native int nativeGetSkyToneSRange();
    public native int nativeGetSkyToneSIndex();
    public native boolean nativeSetSkyToneSIndex(int index);

    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mCurrentIndex);
    }
    public String getMaxValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mRange - 1);
    }
    public String getMinValue() {
        // TODO Auto-generated method stub
        return "0";
    }
    public String getSeekbarProgressValue() {
        // TODO Auto-generated method stub
        return Integer.toString(mCurrentIndex);
    }
    public void init() {
        // TODO Auto-generated method stub

    }
    public void setCurrentIndex(int progress) {
        // TODO Auto-generated method stub
        mCurrentIndex = progress;
    }

    public void onDestroy() {
        // TODO Auto-generated method stub

    }
    public void onResume() {
        // TODO Auto-generated method stub

    }
    public int getCurrentIndex() {
        // TODO Auto-generated method stub
        return mCurrentIndex;
    }

    public int getRange() {
        return mRange;
    }
    public int getDefaultIndex() {
        // TODO Auto-generated method stub
        return mDefaultIndex;
    }
    public void setIndex(int index) {
        // TODO Auto-generated method stub

    }


}

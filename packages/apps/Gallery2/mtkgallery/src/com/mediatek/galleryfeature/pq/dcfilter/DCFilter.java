package com.mediatek.galleryfeature.pq.dcfilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.mediatek.galleryframework.util.MtkLog;

import com.mediatek.galleryfeature.pq.filter.FilterInterface;

public class DCFilter implements FilterInterface {
    public static final String TAG = "MtkGallery2/DCFilter";
    public static final String  MIN_VALUE = "textViewMinValue";
    public static final String RANGE = "textViewMaxValue";
    public static final String CURRRENT_INDEX = "textViewCurrentIndex";
    public static final String SEEKBAR_PROGRESS = "seekbarProgress";
    protected int mDefaultIndex ;
    protected int mRange;
    protected int mCurrentIndex;

    protected String mName;
    static {
        System.loadLibrary("PQDCjni");
    }

    public Map<String, String> map = new HashMap<String, String>();

    public Map<String, String> getDate() {
        return map;
    }

    public DCFilter(String name) {
        mName = name;
        initFilter();
    }

    public DCFilter() {
        //initFilter();
    }

    protected void initFilter() {
        init();
        map.put(MIN_VALUE, getMinValue());
        map.put(RANGE, getMaxValue());
        map.put(CURRRENT_INDEX, getCurrentValue());
        map.put(SEEKBAR_PROGRESS, getSeekbarProgressValue() + "");
        MtkLog.d(TAG, "<initFilter>Create [" + this.getClass().getName() + " ]: MIN_VALUE=" + getMinValue()
                + " RANGE=" + getMaxValue()
                + " CURRRENT_INDEX=" + getCurrentValue()
                + "  SEEKBAR_PROGRESS=" + getSeekbarProgressValue());
    }

    public boolean addToList(ArrayList<FilterInterface> list) {
        MtkLog.d(TAG, "<addToList>this " + this.getClass().getName() + "  Integer.parseInt(getMaxValue()=" + Integer.parseInt(getMaxValue()));
        if (Integer.parseInt(getMaxValue()) > 0) {
            list.add((FilterInterface) this);
            MtkLog.d(TAG, "<addToList>:::" + this.getClass().getName() + " has alread addToList! ");
            return true;
        }
    return false;
    }
    public void onResume() {
        MtkLog.d(TAG, "<onResume>: nativeSetTuningMode(1)");
        nativeSetTuningMode(1);
    }

    public void onDestroy() {
    }

    public ArrayList<FilterInterface> getFilterList() {
        ArrayList<FilterInterface> list = new ArrayList<FilterInterface>();
        (new DCFilterBlackEffectEnable("BlackEffectEnable")).addToList(list); //1
        (new DCFilterWhiteEffectEnable("WhiteEffectEnable")).addToList(list); //2
        (new DCFilterStrongBlackEffect("StrongBlackEffect")).addToList(list); //3
        (new DCFilterStrongWhiteEffect("StrongWhiteEffect")).addToList(list); //4
        (new DCFilterAdaptiveBlackEffect("AdaptiveBlackEffect")).addToList(list); //5
        (new DCFilterAdaptiveWhiteEffect("AdaptiveWhiteEffect")).addToList(list); //6
        (new DCFilterScenceChangeOnceEn("ScenceChangeOnceEn")).addToList(list); //7
        (new DCFilterScenceChangeControlEn("ScenceChangeControlEn")).addToList(list); // 8
        (new DCFilterScenceChangeControl("ScenceChangeControl")).addToList(list); //9
        (new DCFilterScenceChangeTh1("ScenceChangeTh1")).addToList(list); //10
        (new DCFilterScenceChangeTh2("ScenceChangeTh2")).addToList(list); //11
        (new DCFilterScenceChangeTh3("ScenceChangeTh3")).addToList(list);  //12
        (new DCFilterContentSmooth1("ContentSmooth1")).addToList(list); // 13
        (new DCFilterContentSmooth2("ContentSmooth2")).addToList(list); //14
        (new DCFilterContentSmooth3("ContentSmooth3")).addToList(list); //15
        (new DCFilterMiddleRegionGain1("MiddleRegionGain1")).addToList(list); // 16
        (new DCFilterMiddleRegionGain2("MiddleRegionGain")).addToList(list); //17
        (new DCFilterBlackRegionGain1("BlackRegionGain1")).addToList(list); //18
        (new DCFilterBlackRegionGain2("BlackRegionGain")).addToList(list); //19
        (new DCFilterBlackRegionRange("BlackRegionRange")).addToList(list); //20
        (new DCFilterBlackEffectLevel("BlackEffectLevel")).addToList(list); //21
        (new DCFilterBlackEffectParam1("BlackEffectParam1")).addToList(list); //22
        (new DCFilterBlackEffectParam2("BlackEffectParam2")).addToList(list); //23
        (new DCFilterBlackEffectParam3("BlackEffectParam3")).addToList(list); //24
        (new DCFilterBlackEffectParam4("BlackEffectParam4")).addToList(list); //25
        (new DCFilterWhiteRegionGain1("WhiteRegionGain1")).addToList(list); //26
        (new DCFilterWhiteRegionGain2("WhiteRegionGain")).addToList(list); //27
        (new DCFilterWhiteRegionRange("WhiteRegionRange")).addToList(list); //28
        (new DCFilterWhiteEffectLevel("WhiteEffectLevel")).addToList(list); //29
        (new DCFilterWhiteEffectParam1("WhiteEffectParam1")).addToList(list); //30
        (new DCFilterWhiteEffectParam2("WhiteEffectParam2")).addToList(list); //31
        (new DCFilterWhiteEffectParam3("WhiteEffectParam3")).addToList(list); //32
        (new DCFilterWhiteEffectParam4("WhiteEffectParam4")).addToList(list); //33
        (new DCFilterContrastAdjust1("ContrastAdjust1")).addToList(list); //34
        (new DCFilterContrastAdjust2("ContrastAdjust2")).addToList(list); //35
        (new DCFilterDCChangeSpeedLevel("DCChangeSpeedLevel")).addToList(list); //36
        (new DCFilterProtectRegionEffect("ProtectRegionEffect")).addToList(list); //37
        (new DCFilterDCChangeSpeedLevel2("DCChangeSpeedLevel2")).addToList(list); //38
        (new DCFilterProtectRegionWeight("ProtectRegionWeight")).addToList(list); // 39


        return list;
    }



    public static native boolean nativeSetTuningMode(int mode);
    ///1
    public native int nativeGetBlackEffectEnableRange();
    public native int nativeGetBlackEffectEnableIndex();
    public native boolean nativeSetBlackEffectEnableIndex(int index);
    ///2
    public native int nativeGetWhiteEffectEnableRange();
    public native int nativeGetWhiteEffectEnableIndex();
    public native boolean nativeSetWhiteEffectEnableIndex(int index);
    ///3
    public native int nativeGetStrongBlackEffectRange();
    public native int nativeGetStrongBlackEffectIndex();
    public native boolean nativeSetStrongBlackEffectIndex(int index);

    ///4
    public native int nativeGetStrongWhiteEffectRange();
    public native int nativeGetStrongWhiteEffectIndex();
    public native boolean nativeSetStrongWhiteEffectIndex(int index);

    //5
    public native int nativeGetAdaptiveBlackEffectRange();
    public native int nativeGetAdaptiveBlackEffectIndex();
    public native boolean nativeSetAdaptiveBlackEffectIndex(int index);

  //6
    public native int nativeGetAdaptiveWhiteEffectRange();
    public native int nativeGetAdaptiveWhiteEffectIndex();
    public native boolean nativeSetAdaptiveWhiteEffectIndex(int index);
  //7
    public native int nativeGetScenceChangeOnceEnRange();
    public native int nativeGetScenceChangeOnceEnIndex();
    public native boolean nativeSetScenceChangeOnceEnIndex(int index);
  //8
    public native int nativeGetScenceChangeControlEnRange();
    public native int nativeGetScenceChangeControlEnIndex();
    public native boolean nativeSetScenceChangeControlEnIndex(int index);
  //9
    public native int nativeGetScenceChangeControlRange();
    public native int nativeGetScenceChangeControlIndex();
    public native boolean nativeSetScenceChangeControlIndex(int index);
  //10
    public native int nativeGetScenceChangeTh1Range();
    public native int nativeGetScenceChangeTh1Index();
    public native boolean nativeSetScenceChangeTh1Index(int index);
  //11
    public native int nativeGetScenceChangeTh2Range();
    public native int nativeGetScenceChangeTh2Index();
    public native boolean nativeSetScenceChangeTh2Index(int index);
  //12
    public native int nativeGetScenceChangeTh3Range();
    public native int nativeGetScenceChangeTh3Index();
    public native boolean nativeSetScenceChangeTh3Index(int index);
  //13
    public native int nativeGetContentSmooth1Range();
    public native int nativeGetContentSmooth1Index();
    public native boolean nativeSetContentSmooth1Index(int index);
  //14
    public native int nativeGetContentSmooth2Range();
    public native int nativeGetContentSmooth2Index();
    public native boolean nativeSetContentSmooth2Index(int index);
  //15
    public native int nativeGetContentSmooth3Range();
    public native int nativeGetContentSmooth3Index();
    public native boolean nativeSetContentSmooth3Index(int index);
  //16
    public native int nativeGetMiddleRegionGain1Range();
    public native int nativeGetMiddleRegionGain1Index();
    public native boolean nativeSetMiddleRegionGain1Index(int index);
  //17
    public native int nativeGetMiddleRegionGain2Range();
    public native int nativeGetMiddleRegionGain2Index();
    public native boolean nativeSetMiddleRegionGain2Index(int index);
  //18
    public native int nativeGetBlackRegionGain1Range();
    public native int nativeGetBlackRegionGain1Index();
    public native boolean nativeSetBlackRegionGain1Index(int index);
  //19
    public native int nativeGetBlackRegionGain2Range();
    public native int nativeGetBlackRegionGain2Index();
    public native boolean nativeSetBlackRegionGain2Index(int index);
  //20
    public native int nativeGetBlackRegionRangeRange();
    public native int nativeGetBlackRegionRangeIndex();
    public native boolean nativeSetBlackRegionRangeIndex(int index);
  //21
    public native int nativeGetBlackEffectLevelRange();
    public native int nativeGetBlackEffectLevelIndex();
    public native boolean nativeSetBlackEffectLevelIndex(int index);
  //22
    public native int nativeGetBlackEffectParam1Range();
    public native int nativeGetBlackEffectParam1Index();
    public native boolean nativeSetBlackEffectParam1Index(int index);
  //23
    public native int nativeGetBlackEffectParam2Range();
    public native int nativeGetBlackEffectParam2Index();
    public native boolean nativeSetBlackEffectParam2Index(int index);
  //24
    public native int nativeGetBlackEffectParam3Range();
    public native int nativeGetBlackEffectParam3Index();
    public native boolean nativeSetBlackEffectParam3Index(int index);
  //25
    public native int nativeGetBlackEffectParam4Range();
    public native int nativeGetBlackEffectParam4Index();
    public native boolean nativeSetBlackEffectParam4Index(int index);
  //26
    public native int nativeGetWhiteRegionGain1Range();
    public native int nativeGetWhiteRegionGain1Index();
    public native boolean nativeSetWhiteRegionGain1Index(int index);
  //27
    public native int nativeGetWhiteRegionGain2Range();
    public native int nativeGetWhiteRegionGain2Index();
    public native boolean nativeSetWhiteRegionGain2Index(int index);
  //28
    public native int nativeGetWhiteRegionRangeRange();
    public native int nativeGetWhiteRegionRangeIndex();
    public native boolean nativeSetWhiteRegionRangeIndex(int index);
  //29
    public native int nativeGetWhiteEffectLevelRange();
    public native int nativeGetWhiteEffectLevelIndex();
    public native boolean nativeSetWhiteEffectLevelIndex(int index);
  //30
    public native int nativeGetWhiteEffectParam1Range();
    public native int nativeGetWhiteEffectParam1Index();
    public native boolean nativeSetWhiteEffectParam1Index(int index);
  //31
    public native int nativeGetWhiteEffectParam2Range();
    public native int nativeGetWhiteEffectParam2Index();
    public native boolean nativeSetWhiteEffectParam2Index(int index);
  //32
    public native int nativeGetWhiteEffectParam3Range();
    public native int nativeGetWhiteEffectParam3Index();
    public native boolean nativeSetWhiteEffectParam3Index(int index);
  //33
    public native int nativeGetWhiteEffectParam4Range();
    public native int nativeGetWhiteEffectParam4Index();
    public native boolean nativeSetWhiteEffectParam4Index(int index);
  //34
    public native int nativeGetContrastAdjust1Range();
    public native int nativeGetContrastAdjust1Index();
    public native boolean nativeSetContrastAdjust1Index(int index);
  //35
    public native int nativeGetContrastAdjust2Range();
    public native int nativeGetContrastAdjust2Index();
    public native boolean nativeSetContrastAdjust2Index(int index);
  //36
    public native int nativeGetDCChangeSpeedLevelRange();
    public native int nativeGetDCChangeSpeedLevelIndex();
    public native boolean nativeSetDCChangeSpeedLevelIndex(int index);
  //37
    public native int nativeGetProtectRegionEffectRange();
    public native int nativeGetProtectRegionEffectIndex();
    public native boolean nativeSetProtectRegionEffectIndex(int index);
  //38
    public native int nativeGetDCChangeSpeedLevel2Range();
    public native int nativeGetDCChangeSpeedLevel2Index();
    public native boolean nativeSetDCChangeSpeedLevel2Index(int index);
  //39
    public native int nativeGetProtectRegionWeightRange();
    public native int nativeGetProtectRegionWeightIndex();
    public native boolean nativeSetProtectRegionWeightIndex(int index);
    public String getCurrentValue() {
        // TODO Auto-generated method stub
        return getName() + Integer.toString(mCurrentIndex);
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
    public int getDefaultIndex() {
        // TODO Auto-generated method stub
        return mDefaultIndex ;
    }

    public void setIndex(int index) {
        // TODO Auto-generated method stub

    }

    public int getCurrentIndex() {
        // TODO Auto-generated method stub
        return mCurrentIndex;
    }

    public int getRange() {
        return mRange;
    }

    protected String getName() {
        return mName + ":  ";
    }
}

package com.mediatek.galleryfeature.pq;

import java.nio.ByteBuffer;
import java.util.HashMap;

import com.android.gallery3d.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemProperties;
import android.view.MenuItem;

import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.pq.PictureQuality;
import com.mediatek.pq.PictureQuality.Hist;

public class ImageDC {
    private static final String TAG = "MtkGallery2/ImageDC";
    private String mFilePath;
    private int mOrientation;
    private String mMimeType = "";
    private int[] mHistogram;
    public static HashMap<String, int[]> sHistogramHashMap = new HashMap<String, int[]>();
    
    public static final String DC = "com.android.gallery3d.ImageDC";
    public static final String DCNAME = "ImageDC";
    private static boolean sAvailable = (SystemProperties.get("DC").equals("1"));
    
    
    public boolean isNeedHistogram () {
        return ("image/jpeg".equals(mMimeType) && sAvailable);
    }
    
    public boolean isNeedHistogram (String mimeType) {
        return ("image/jpeg".equals(mimeType) && sAvailable);
    }
    
    public boolean isNeedToGetThumbFromCache() {
        return true && !("image/jpeg".equals(mMimeType) && sAvailable);
    }
    
    public ImageDC (String filePath ,int orientation, String mimeType) {
        mFilePath = filePath;
        mOrientation = orientation;
        mMimeType = mimeType;
    }
    
    public boolean hasHistorgram () {
        int[] hist = sHistogramHashMap.get(mFilePath);
        if (hist != null) {
            return true;
        } else {
            return false;
        }
    }
    public int [] getHist () {
        MtkLog.d(TAG, "<getHist> mFilePath="+mFilePath);
        return sHistogramHashMap.get(mFilePath);
    }
    
    public boolean generateHistogram (Bitmap bitmap) {
        if (bitmap == null ) return false;
        MtkLog.d(TAG, " <generateHistogram (bitmap)> FeatureConfig.supportImageDCEnhance="+FeatureConfig.supportImageDCEnhance +" bitmap w="+bitmap.getWidth() 
                +" height="+bitmap.getHeight());
        int length = bitmap.getWidth()*bitmap.getHeight()*4;
        if (!FeatureConfig.supportImageDCEnhance) {
            return false;
        } else {
            int[] histogram= sHistogramHashMap.get(mFilePath);
            if (histogram == null) {
                byte[] array = null;
                ByteBuffer buffer = ByteBuffer.allocate(length);
                bitmap.copyPixelsToBuffer(buffer);
                array = buffer.array();
                boolean result = generateHistogram(array, bitmap.getWidth(), bitmap.getHeight(), mFilePath);
                if (buffer != null) buffer.clear();
                return result;
            } else {
                return true;
            }
        }
    }
    
    private boolean generateHistogram(byte[] array, int width, int height , String filePath) {
        long begin = System.currentTimeMillis();
        MtkLog.d(TAG, " <generateHistogram (, , )> get Histogram :mMediaData.filePath="+filePath);

        Hist mHist = PictureQuality.getDynamicContrastHistogram(array, width, height);
        if (mHist != null) {
            int[] histogram = mHist.info;
            sHistogramHashMap.put(filePath, histogram);
            int lenght = histogram.length;
            for (int i = 0; i < lenght; i++) {
                MtkLog.d(TAG, "<generateHistogram> histogram["+i+"]="+ histogram[i]);
            }
            MtkLog.d(TAG, " <generateHistogram> get Histogram use Time = "+(System.currentTimeMillis() - begin));
            return true;
        } else {
            return false;
        }
    }
    
    public Bitmap getImageDC (Bitmap bitmap, String filePath) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPostProc = true;
        opts.inDynamicCon = sHistogramHashMap.get(filePath);
        if (opts.inDynamicCon == null) {
            return bitmap;
        }
        int size = opts.inDynamicCon.length;
        MtkLog.d(TAG, "<getImageDC>  opts.inPostProc ="+opts.inPostProc);
        for (int i = 0; i < size; i++) {
            MtkLog.d(TAG, "<getImageDC> histogram["+i+"]="+ opts.inDynamicCon[i] );
        }
        int length = bitmap.getWidth()*bitmap.getHeight()*4;
        byte[] array = null;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        bitmap.copyPixelsToBuffer(buffer);
        array = buffer.array();
        long beginDecode = System.currentTimeMillis();
        Bitmap dcBitmap = BitmapFactory.decodeByteArray(array, 0, length, opts);
        buffer.clear();
        if (dcBitmap != null) {
            //DebugUtils.dumpBitmap(bitmap, mMediaData.filePath);
            bitmap.recycle();
            bitmap = null;
            MtkLog.d(TAG, " <getImageDC> Decode DC image use Time = "+(System.currentTimeMillis() - beginDecode));
            //DebugUtils.dumpBitmap(dcBitmap, mMediaData.filePath+"DCImage");
            return dcBitmap;
        } else {
            return bitmap;
        }
    }
    
    public void addFlag (BitmapFactory.Options option) {
        option.inDynmicConFlag = true;
        option.inDynamicCon = getHist();
    }
    
    public void clearFlag (BitmapFactory.Options option) {
        option.inDynmicConFlag = false;
    }
    
    public static int [] getHist (String filePath) {
        return sHistogramHashMap.get(filePath);
    }
    public String getFilePath () {
        return mFilePath;
    }
    
    public static void resetImageDC(Context context) {
        if (FeatureConfig.supportImageDCEnhance) {
            SharedPreferences sp = context.getSharedPreferences(ImageDC.DC,
                    Context.MODE_PRIVATE);
            if (null != sp) {
                sAvailable = sp.getBoolean(ImageDC.DCNAME, false);
            }
            MtkLog.d(TAG, " <resetImageDC> get imageDC config from sharePreference sAvailable = "+sAvailable);
        }
    }
    
    public static void setStatus(boolean avaliable) {
        sAvailable = avaliable;
    }
    public static boolean getStatus() {
        return sAvailable;
    }

    public static void setMenuItemTile(Context context, MenuItem dcItem) {
        String text ;
        if (sAvailable) {
            dcItem.setTitle(R.string.m_dc_open);
        } else {
            dcItem.setTitle(R.string.m_dc_close);
        }
    }
    
    public static void resetStatus(Context context) {
        SharedPreferences sp = context.getSharedPreferences(ImageDC.DC,
                Context.MODE_PRIVATE);
        final Editor editor = sp.edit();
        editor.putBoolean(ImageDC.DCNAME, !sAvailable);
        editor.commit();
        sAvailable = !sAvailable;
    }
}

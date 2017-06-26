package com.mediatek.galleryfeature.pq;

import com.mediatek.galleryframework.base.ThumbType;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import com.mediatek.galleryframework.util.MtkLog;

public class LoadPQBitmapTask extends AsyncTask<Void, Void, Bitmap> {

    private static final String TAG = "MtkGallery2/LoadPQBitmapTask";
    private static Bitmap mScreenNailBitmap;
    private static Bitmap mTileBitmap;

    private static String mPQMineType;
    private static String mPqUri;
    private static Context mContext;
    private DecoderScreenNailBitmap mScreenNailDecoder;
    private DecoderTiledBitmap mDecoderTiledBitmap;
    private boolean isFrist;
    private static PresentImage mPresent;
    private int mRotation = 0;
    private String mCurrentUri;
    public static void init(Context context, PresentImage present) {
        mContext = context;
        Bundle bundle = ((Activity) context).getIntent().getExtras();
        if (bundle != null) {
            mPQMineType =  bundle.getString("PQMineType");
            mPqUri = bundle.getString("PQUri");
        }
        mPresent = present;
        MtkLog.d(TAG, " <init>mPqUri=" + mPqUri);
    }
    public LoadPQBitmapTask(String uri) {
        super();
        mCurrentUri = uri;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        isFrist = mScreenNailBitmap == null && mTileBitmap == null;
        mRotation = PQUtils.getRotation(mContext, mPqUri);
        if (mPQMineType != null && mPqUri != null) {
            if (isFrist || !PQUtils.isSupportedByRegionDecoder(mPQMineType)) {
                mScreenNailDecoder = new DecoderScreenNailBitmap(mContext, mPqUri, ThumbType.MIDDLE.getTargetSize());
                mScreenNailBitmap = mScreenNailDecoder.screenNailBitmapDecoder();
                if (mScreenNailBitmap != null) MtkLog.d(TAG, "<doInBackground> mScreenNailBitmap=" + mScreenNailBitmap.getWidth() + " " + mScreenNailBitmap.getHeight());
                return mScreenNailBitmap;
            } else {
                mDecoderTiledBitmap = new DecoderTiledBitmap(mContext, mPqUri, ThumbType.MIDDLE.getTargetSize());
                if (mDecoderTiledBitmap != null) {
                    mTileBitmap = mDecoderTiledBitmap.decodeBitmap();
                }
                if (mTileBitmap != null) MtkLog.d(TAG, " <doInBackground> mTileBitmap=" + mTileBitmap.getWidth() + " " + mTileBitmap.getHeight());
                return mTileBitmap;
            }

        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (result != null) {
            if (mRotation != 0) {
                result = PQUtils.rotateBitmap(result, mRotation, true);
            }
            mPresent.setBitmap(result, mCurrentUri);
        }

    }

    public static boolean startLoadBitmap() {
        return (PQUtils.isSupportedByRegionDecoder(mPQMineType) && null == mTileBitmap);
    }

    public static void free() {
        if (mScreenNailBitmap != null) {
            mScreenNailBitmap.recycle();
            mScreenNailBitmap = null;
        }
        if (mTileBitmap != null) {
            mTileBitmap.recycle();
            mTileBitmap = null;
        }
    }
}

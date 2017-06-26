package com.mediatek.galleryfeature.pq;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.mediatek.galleryframework.util.MtkLog;

public class DecoderScreenNailBitmap {
    private String mPqUri;
    private Context mContext;
    private int targetSize;
    private BitmapFactory.Options options = new BitmapFactory.Options();
    private int mSampleSize;
    private static final String TAG = "MtkGallery2/DecoderScreenNailBitmap";

    public DecoderScreenNailBitmap(Context context, String uri, int targetSize) {
        mPqUri = uri;
        mContext = context;
        this.targetSize = targetSize;
        mSampleSize = PQUtils.caculateInSampleSize(context, uri, targetSize);
    }

    public Bitmap screenNailBitmapDecoder() {
        FileDescriptor fd = null;
        FileInputStream fis = null;
        Bitmap mBitmap = null;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = mSampleSize;
        options.inPostProc = true;
        try {
            fis = PQUtils.getFileInputStream(mContext, mPqUri);
            if (fis != null) {
                fd = fis.getFD();
                if (fd != null) {
                    mBitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
                }
            }
        } catch (IOException e) {
            MtkLog.w(TAG, "<screenNailBitmapDecoder> exception occur, "
                    + e.getMessage());
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (mBitmap != null) {
                float scale = (float) targetSize / Math.max(mBitmap.getWidth(), mBitmap.getHeight());
                if (scale <= 0.5) {
                    return PQUtils.resizeBitmapByScale(mBitmap, scale, true);
                }
            }
        }
        return mBitmap;

    }
}

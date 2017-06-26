package com.mediatek.galleryfeature.pq;

import android.content.Context;
import android.graphics.Bitmap;

public class PresentImage {

    private static PresentImage mPresentImage;
    private Context mContext;
    private RenderingRequestListener mListener;
    private  LoadPQBitmapTask mLoadBitmapTask;

    public interface RenderingRequestListener {
        public boolean available(Bitmap bitmap, String uri);
    }

    public static PresentImage getPresentImage() {
        if (null == mPresentImage) {
            mPresentImage = new PresentImage();
        }
        return mPresentImage;
    }

    public PresentImage() {

    }


    public void setListener(Context context, RenderingRequestListener listener) {
        mContext = context;
        LoadPQBitmapTask.init(mContext, this);
        mListener = listener;
    }

    public void setBitmap(Bitmap bitmap, String uri) {
        boolean finished = mListener.available(bitmap, uri);
        if (LoadPQBitmapTask.startLoadBitmap() && finished) {
            loadBitmap(uri);
        }
    }

    public void loadBitmap(String uri) {
        if (uri == null) return;
        stopLoadBitmap();
        mLoadBitmapTask = new LoadPQBitmapTask(uri);
        mLoadBitmapTask.execute();
    }
    public void free() {
        LoadPQBitmapTask.free();
    }
    public void stopLoadBitmap() {
        if (mLoadBitmapTask == null) return;
        mLoadBitmapTask.cancel(true);
    }
}

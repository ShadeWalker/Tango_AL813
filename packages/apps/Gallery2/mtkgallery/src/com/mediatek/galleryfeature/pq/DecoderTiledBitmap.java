package com.mediatek.galleryfeature.pq;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import com.mediatek.galleryframework.util.MtkLog;

public class DecoderTiledBitmap {
    private static final String TAG = "MtkGallery2/DecoderTiledBitmap";
    int mScreenWidth;
    int mScreenHeight;
    int mOriginalImageWidth;
    int mOriginalImageHeight;
    int mGLviewWidth;
    int mGLviewHeight;
    public String mUri = null;
    int targetSize ;
    Context mContext;
    BitmapFactory.Options options = new BitmapFactory.Options();;
    Bitmap mScreenNail = null;
    Runnable mApply = null;
    int mLevelCount;
    BitmapRegionDecoder decoder = null;
    Rect mDesRect = null;
    Handler mHandler = null;
    int mLevel;
    int TILE_SIZE;
    final static int SCALE_LIMIT = 4;
    private final int TILE_BORDER = 1;

    public DecoderTiledBitmap(Context context, String mPqUri , int targetSize) {
        mContext = context;
        mUri = mPqUri;
        this.targetSize = targetSize;
        DisplayMetrics outMetrics = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
        mScreenHeight = outMetrics.heightPixels;
        Bundle bundle = ((Activity) context).getIntent().getExtras();
        if (bundle != null) {
            mPqUri = bundle.getString("PQUri");
            mGLviewWidth = bundle.getInt("PQViewWidth");
            mGLviewHeight = bundle.getInt("PQViewHeight");
        }
        if (PQUtils.isHighResolution(mContext)) {
            TILE_SIZE = 511;
        } else {
            TILE_SIZE = 255;
        }
        MtkLog.d(TAG, "<DecoderTiledBitmap> TILE_SIZE====" + TILE_SIZE);
        init();
        mLevelCount = PQUtils.calculateLevelCount(mOriginalImageWidth, mScreenWidth);
        mLevel = PQUtils.clamp(PQUtils.floorLog2(1f / getScaleMin()), 0, mLevelCount);
        MtkLog.d(TAG, "<DecoderTiledBitmap> mLevel=" + mLevel + " mLevelCount=" + mLevelCount);
        decoder = getBitmapRegionDecoder(mUri);
    }

    private void init() {
        FileDescriptor fd = null;
        FileInputStream fis = null;
        options.inJustDecodeBounds = true;
        try {
            fis = PQUtils.getFileInputStream(mContext, mUri);
            if (fis != null) {
                fd = fis.getFD();
                if (fd != null) {
                    BitmapFactory.decodeFileDescriptor(fd, null, options);
                }
            }
            } catch (FileNotFoundException e) {
                MtkLog.e(TAG, "<init>bitmapfactory decodestream fail");
            } catch (IOException e) {
                MtkLog.e(TAG, "<init>bitmapfactory decodestream fail");
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            float scale = 1;
            if (options.outWidth > 0 && options.outHeight > 0) {
                mOriginalImageWidth = options.outWidth;
                mOriginalImageHeight = options.outHeight;
                scale = (float) targetSize / Math.max(options.outWidth, options.outHeight);
            }
        options.inSampleSize = PQUtils.computeSampleSizeLarger(scale);
        MtkLog.d(TAG, " <init>  options.inSampleSize==" + options.inSampleSize + " width==" + options.outWidth + " height==" + options.outHeight + "targetSize==" + targetSize);
    }

    public float getScaleMin() {
        float s = Math.min(((float) mGLviewWidth) / mOriginalImageWidth,
                ((float) mGLviewHeight) / mOriginalImageHeight);
        MtkLog.d(TAG, " <getScaleMin>viewW==" + mGLviewWidth + "  viewH==" + mGLviewHeight + "  mOriginalImageWidth==" + mOriginalImageWidth +
                "  mOriginalImageHeight==" + mOriginalImageHeight);
        return Math.min(SCALE_LIMIT, s);
    }

    private BitmapRegionDecoder getBitmapRegionDecoder(String mUri) {
        InputStream inputstream = null;
        BitmapRegionDecoder  decoder;
        try {
            inputstream = mContext.getContentResolver().openInputStream(Uri.parse(mUri));
            decoder = BitmapRegionDecoder.newInstance(inputstream, false);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            decoder = null;
            MtkLog.d(TAG, "<getBitmapRegionDecoder>FileNotFoundException!!!!!!!!!!!!!!!!!!!!" + e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            decoder = null;
            MtkLog.d(TAG, "<getBitmapRegionDecoder>IOException!!!!!!!!!!!!!!!!!!!!" + e.toString());
            e.printStackTrace();
        } finally {
            if (inputstream != null) {
                try {
                    inputstream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return decoder;

    }


    public Bitmap decodeBitmap() {
        return decodeTileImage(1, mLevel);
    }

    private Bitmap decodeTileImage(float scale, int sample) {
        if (decoder == null) return null;
        int imagewidth = decoder.getWidth();
        int imageheight = decoder.getHeight();
        MtkLog.d(TAG, "<decodeTileImage>scale===" + scale);
        imagewidth = (int) (imagewidth * scale);
        imageheight = (int) (imageheight * scale);
        Bitmap result = Bitmap.createBitmap(
                imagewidth >> sample, imageheight >> sample, Config.ARGB_8888); //
        Canvas canvas = new Canvas(result);
        Rect desRect = new Rect(0, 0, result.getWidth(), result.getHeight());
        Rect rect = new Rect(0, 0, decoder.getWidth(), decoder.getHeight());

        drawInTiles(canvas, decoder, rect, desRect, sample);
        return result;
    }

    public class Tile {
        public Tile(int x, int y, Bitmap mBitmap) {
            this.x = x;
            this.y = y;
            this.bitmap = mBitmap;
        }
        public int x;
        public int y;
        Bitmap bitmap = null;
    }

    private void drawInTiles(Canvas canvas,
            BitmapRegionDecoder decoder, Rect rect, Rect dest, int sample) {
        int tileSize = (TILE_SIZE << sample);
        int borderSize = (TILE_BORDER << sample);
        Rect tileRect = new Rect();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Config.ARGB_8888;
        options.inPreferQualityOverSpeed = true;
        //options.inBitmap = bitmap;
        options.inPostProc = true;
        options.inSampleSize = (1 << sample);
        MtkLog.v(TAG, "<drawInTiles>sample====" + sample);
        ArrayList<Tile> mTileList = new ArrayList<Tile>();
        boolean complate = true;
        for (int tx = rect.left, x = 0;
                tx < rect.right; tx += tileSize, x += TILE_SIZE) {
            for (int ty = rect.top, y = 0;
                    ty < rect.bottom; ty += tileSize, y += TILE_SIZE) {
                tileRect.set(tx, ty, tx + tileSize + borderSize, ty + tileSize + borderSize);
                if (tileRect.intersect(rect)) {
                    Bitmap bitmap = null; //Bitmap.createBitmap(tileRect.width(), tileRect.height(), Config.ARGB_8888);
                    try {
                        synchronized (decoder) {
                            if (decoder != null && !decoder.isRecycled()) {
                                bitmap = decoder.decodeRegion(tileRect, options);
                                mTileList.add(new Tile(x, y, bitmap));
                            } else {
                                complate = false;
                                break;
                            }
                        }
                        //canvas.drawBitmap(bitmap, x, y, paint);
                        //bitmap.recycle();
                    } catch (IllegalArgumentException e) {
                        MtkLog.w(TAG, " <drawInTiles>drawInTiles:got exception:" + e);
                    }
                }
            }
        }
        if (complate == true) {
            Paint paint = new Paint();
            int size  = mTileList.size();
            for (int i = size - 1; i >= 0; i--) {
                Bitmap bitmap = mTileList.get(i).bitmap;
                canvas.drawBitmap(mTileList.get(i).bitmap, mTileList.get(i).x, mTileList.get(i).y, paint);
                MtkLog.d(TAG, "<drawInTiles>pixelX=" + mTileList.get(i).x + " pixelY=" + mTileList.get(i).y);
                mTileList.get(i).bitmap.recycle();
            }
        }
    }

}

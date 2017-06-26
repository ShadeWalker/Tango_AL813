package com.mediatek.galleryfeature.mav;

import junit.framework.Assert;
import android.graphics.Bitmap;
import com.mediatek.galleryframework.gl.MGLCanvas;
import com.mediatek.galleryframework.gl.MUploadedTexture;
import com.mediatek.galleryframework.util.MtkLog;

//MavTexture is a texture whose content is specified by a fixed Bitmap.
//
// The texture own the Bitmap. While the texture is uploaded, it
// should free the Bitmap.
public class MavTexture extends MUploadedTexture {
    private static final String TAG = "MtkGallery2/MavTexture";
    protected Bitmap mContentBitmap;
    public MavTexture(Bitmap bitmap) {
        this(bitmap, false);
    }

    public MavTexture(Bitmap bitmap, boolean hasBorder) {
        super(hasBorder);
        Assert.assertTrue(bitmap != null && !bitmap.isRecycled());
        mContentBitmap = bitmap;
    }
    
    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
       if (!inFinalizer() && bitmap != null) {
           MtkLog.d(TAG, "<onFreeBitmap> bitmap = "+bitmap+ "this = "+this);
           bitmap.recycle();
           bitmap = null;
       }
    }

    @Override
    protected Bitmap onGetBitmap() {
        return mContentBitmap;
    }

    public Bitmap getBitmap() {
        return mContentBitmap;
    }
    
    @Override
    protected boolean onBind(MGLCanvas canvas) {
        return super.onBind(canvas);
    }
    
}

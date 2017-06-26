package com.mediatek.galleryfeature.mav;


import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;

import com.mediatek.mpodecoder.MpoDecoder;

public class MpoDecoderWrapper {

    private static final String TAG = "MtkGallery2/MpoDecoderWrapper";
    public static final int INVALID_VALUE = 0;

    private MpoDecoder mMpoDecoder;

    private MpoDecoderWrapper(MpoDecoder mpoDecoder) {
        mMpoDecoder = mpoDecoder;
    }

    public static MpoDecoderWrapper createMpoDecoderWrapper(String filePath) {
        MpoDecoder mpoDecoder = MpoHelper.createMpoDecoder(filePath);
        if (null == mpoDecoder)
            return null;
        return new MpoDecoderWrapper(mpoDecoder);
    }

    public static MpoDecoderWrapper createMpoDecoderWrapper(ContentResolver cr,
            Uri uri) {
        MpoDecoder mpoDecoder = MpoHelper.createMpoDecoder(cr, uri);
        if (null == mpoDecoder)
            return null;
        return new MpoDecoderWrapper(mpoDecoder);
    }

    public static MpoDecoderWrapper createMpoDecoderWrapper(byte[] buffer) {
        MpoDecoder mpoDecoder = MpoHelper.createMpoDecoder(buffer);
        if (null == mpoDecoder)
            return null;
        return new MpoDecoderWrapper(mpoDecoder);
    }

    public int width() {
        if (null == mMpoDecoder)
            return INVALID_VALUE;
        return mMpoDecoder.getWidth();
    }

    public int height() {
        if (null == mMpoDecoder)
            return INVALID_VALUE;
        return mMpoDecoder.getHeight();
    }

    public int frameCount() {
        if (null == mMpoDecoder)
            return INVALID_VALUE;
        return mMpoDecoder.getFrameCount();
    }

    public int getMtkMpoType() {
        if (null == mMpoDecoder)
            return INVALID_VALUE;
        return mMpoDecoder.getMpoType();
    }

    public int suggestMtkMpoType() {
        if (null == mMpoDecoder)
            return INVALID_VALUE;
        return mMpoDecoder.getMpoSubType();
    }

    public Bitmap frameBitmap(int frameIndex, Options options) {
        if (null == mMpoDecoder)
            return null;
        return mMpoDecoder.getFrameBitmap(frameIndex, options);
    }

    public void close() {
        if (null == mMpoDecoder)
            return;
        mMpoDecoder.close();
    }
}

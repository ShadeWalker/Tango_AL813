/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediatek.galleryfeature.gif;

import java.io.FileDescriptor;
import java.io.InputStream;

import android.graphics.Bitmap;

import com.mediatek.gifdecoder.GifDecoder;

public class GifDecoderWrapper {

    private static final String TAG = "MtkGallery2/GifDecoderWrapper";
    public static final int INVALID_VALUE =
                                GifDecoder.INVALID_VALUE;

    private GifDecoder mGifDecoder;

    private GifDecoderWrapper(GifDecoder gifDecoder) {
        mGifDecoder = gifDecoder;
    }

    public static GifDecoderWrapper
        createGifDecoderWrapper(String filePath) {
        GifDecoder gifDecoder = GifHelper.createGifDecoder(filePath);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper
        createGifDecoderWrapper(byte[] data, int offset, int length) {
        GifDecoder gifDecoder =
            GifHelper.createGifDecoder(data, offset, length);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper
        createGifDecoderWrapper(InputStream is) {
        GifDecoder gifDecoder = GifHelper.createGifDecoder(is);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public static GifDecoderWrapper
        createGifDecoderWrapper(FileDescriptor fd) {
        GifDecoder gifDecoder = GifHelper.createGifDecoder(fd);
        if (null == gifDecoder) return null;
        return new GifDecoderWrapper(gifDecoder);
    }

    public void close() {
        //if (null == mGifDecoder) return;
        //mGifDecoder.close();
    }

    public int getWidth() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getWidth();
    }

    public int getHeight() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getHeight();
    }

    public int getTotalDuration() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getTotalDuration();
    }

    public int getTotalFrameCount() {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getTotalFrameCount();
    }

    public int getFrameDuration(int frameIndex) {
        if (null == mGifDecoder) return INVALID_VALUE;
        return mGifDecoder.getFrameDuration(frameIndex);
    }

    public Bitmap getFrameBitmap(int frameIndex) {
        if (null == mGifDecoder) return null;
        return mGifDecoder.getFrameBitmap(frameIndex);
    }
}

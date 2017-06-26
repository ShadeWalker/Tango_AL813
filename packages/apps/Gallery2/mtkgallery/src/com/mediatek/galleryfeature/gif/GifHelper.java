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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;

import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.gifdecoder.GifDecoder;

public class GifHelper {

    private static final String TAG = "MtkGallery2/GifHelper";

    public static final String FILE_EXTENSION = "gif";

    public static final String MIME_TYPE = "image/gif";


    public static GifDecoder createGifDecoder(String filePath) {
        try {
            InputStream is = new FileInputStream(filePath);
            GifDecoder gifDecoder = createGifDecoderInner(is);
            is.close();
            return gifDecoder;
        } catch (FileNotFoundException e) {
            MtkLog.w(TAG, "<createGifDecoder> filePath, FileNotFoundException", e);
            return null;
        } catch (IOException e) {
            MtkLog.w(TAG, "<createGifDecoder> filePath, IOException", e);
            return null;
        }
    }

    public static GifDecoder createGifDecoder(byte[] data,
                              int offset, int length) {
        if (null == data) {
            MtkLog.e(TAG, "createGifDecoder:find null buffer!");
            return null;
        }
        GifDecoder gifDecoder = new GifDecoder(data, offset, length);
        if (gifDecoder.getTotalFrameCount() == GifDecoder.INVALID_VALUE) {
            MtkLog.e(TAG, "<createGifDecoder> got invalid TotalFrameCount, then createGifDecoder fail");
            gifDecoder = null;
        }
        return gifDecoder;
    }

    public static GifDecoder createGifDecoder(InputStream is) {
        return createGifDecoderInner(is);
    }

    public static GifDecoder createGifDecoder(FileDescriptor fd) {
        try {
            InputStream is = new FileInputStream(fd);
            GifDecoder gifDecoder = createGifDecoderInner(is);
            is.close();
            return gifDecoder;
        } catch (FileNotFoundException e) {
            MtkLog.w(TAG, "<createGifDecoder> fd, FileNotFoundException", e);
            return null;
        } catch (IOException e) {
            MtkLog.w(TAG, "<createGifDecoder> fd, IOException", e);
            return null;
        }
    }

    private static GifDecoder createGifDecoderInner(InputStream is) {
        if (null == is) {
            MtkLog.e(TAG, "createGifDecoder:find null InputStream!");
            return null;
        }
        GifDecoder gifDecoder = new GifDecoder(is);
        if (gifDecoder.getTotalFrameCount() == GifDecoder.INVALID_VALUE) {
            MtkLog.e(TAG, "<createGifDecoder> got invalid TotalFrameCount, then createGifDecoder fail");
            gifDecoder = null;
        }
        return gifDecoder;
    }
}

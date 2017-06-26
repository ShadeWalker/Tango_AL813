/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.gallery3d.gadget;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.graphics.drawable.Drawable;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.util.ThreadPool;
import com.mediatek.galleryfeature.config.FeatureConfig;

import java.io.File;

public class WidgetUtils {

    private static final String TAG = "Gallery2/WidgetUtils";

    private static int sStackPhotoWidth = 220;
    private static int sStackPhotoHeight = 170;

    /// M: [BUG.ADD] @{
    private static Drawable sMavOverlay = null;
    private static final String MIMETYPE_MPO = "image/mpo";
    /// @}
    /// M: [BUG.ADD] get context in widget.@{
    public static Context sContext;
    /// @}

    private WidgetUtils() {
    }

    public static void initialize(Context context) {
        /// M: [BUG.ADD] get context in widget.@{
        sContext = context;
        /// @}
        Resources r = context.getResources();
        sStackPhotoWidth = r.getDimensionPixelSize(R.dimen.stack_photo_width);
        sStackPhotoHeight = r.getDimensionPixelSize(R.dimen.stack_photo_height);
    }

    public static Bitmap createWidgetBitmap(MediaItem image) {
        /// M: [DEBUG.ADD] @{
        Log.i(TAG, "<createWidgetBitmap> decode image path: " + image.getFilePath());
        /// @}
        Bitmap bitmap = image.requestImage(MediaItem.TYPE_THUMBNAIL)
               .run(ThreadPool.JOB_CONTEXT_STUB);
        if (bitmap == null) {
            Log.w(TAG, "fail to get image of " + image.toString());
            return null;
        }
        return createWidgetBitmap(bitmap, image.getRotation());
    }

    public static Bitmap createWidgetBitmap(Bitmap bitmap, int rotation) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        float scale;
        if (((rotation / 90) & 1) == 0) {
            scale = Math.max((float) sStackPhotoWidth / w,
                    (float) sStackPhotoHeight / h);
        } else {
            scale = Math.max((float) sStackPhotoWidth / h,
                    (float) sStackPhotoHeight / w);
        }

        Bitmap target = Bitmap.createBitmap(
                sStackPhotoWidth, sStackPhotoHeight, Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        canvas.translate(sStackPhotoWidth / 2, sStackPhotoHeight / 2);
        canvas.rotate(rotation);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, -w / 2, -h / 2, paint);
        return target;
    }

    /// M: [BUG.ADD] @{
    public static void drawWidgetImageTypeOverlay(Context context, Uri uri, Bitmap bitmap) {
        if (uri == null || bitmap == null) {
            return;
        }
        // M: query media DB for mime type and other info
        String[] columns = new String[] { MediaStore.Images.ImageColumns.MIME_TYPE };
        String mimeType = "";
        Cursor c = null;
        // for URI same as content://media/external/images/media/id
        if ("content".equals(uri.getScheme())) {
            c = context.getContentResolver().query(uri, columns, null, null, null);
        // for URI same as /storage/sdcard1/xxx/xxx
        } else if (new File(uri.toString()).exists()) {
            c = context.getContentResolver().query(Images.Media.EXTERNAL_CONTENT_URI, columns,
                    "_data = ?", new String[] { uri.toString() }, null);
        }
        if (c != null) {
            if (c.moveToFirst()) {
                mimeType = c.getString(0);
            }
            c.close();
        }
        boolean isMAV = MIMETYPE_MPO.equalsIgnoreCase(mimeType);
        if (isMAV && FeatureConfig.supportThumbnailMAV) {
            WidgetUtils.drawImageTypeOverlay(context, bitmap);
        }
    }

    public static void drawImageTypeOverlay(Context context, Bitmap bitmap) {
        if (null == sMavOverlay) {
            sMavOverlay = context.getResources().getDrawable(
                    R.drawable.m_mav_overlay);
        }
        int width = sMavOverlay.getIntrinsicWidth();
        int height = sMavOverlay.getIntrinsicHeight();
        float aspectRatio = (float) width / (float) height;
        int bmpWidth = bitmap.getWidth();
        int bmpHeight = bitmap.getHeight();
        boolean heightSmaller = (bmpHeight < bmpWidth);
        int scaleResult = (heightSmaller ? bmpHeight : bmpWidth) / 5;
        if (heightSmaller) {
            height = scaleResult;
            width = (int) (scaleResult * aspectRatio);
        } else {
            width = scaleResult;
            height = (int) (width / aspectRatio);
        }
        int left = (bmpWidth - width) / 2;
        int top = (bmpHeight - height) / 2;
        sMavOverlay.setBounds(left, top, left + width, top + height);
        Canvas tmpCanvas = new Canvas(bitmap);
        sMavOverlay.draw(tmpCanvas);
    }
    /// @}
}

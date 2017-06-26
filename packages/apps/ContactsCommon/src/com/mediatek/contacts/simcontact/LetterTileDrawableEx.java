package com.mediatek.contacts.simcontact;

import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.util.SimContactPhotoUtils;

import com.android.contacts.common.R;

public class LetterTileDrawableEx extends LetterTileDrawable {
    private static final String TAG = LetterTileDrawableEx.class.getSimpleName();

    /**
     * This height ratio is just a experience value.
     * Avatar icon will take up the ratio height of View.
     */
    private static float SIM_AVATAR_HEIGHT_RATIO = 0.32f;
    private static float SIM_AVATAR_WIDTH_RATIO = 0.32f;
    private static float SDN_LOCKED_RATIO = 0.3f;
    /**
     * This width ratio is just a experience value.
     * Avatar icon will take up the ratio width of View.
     */
    private static int SIM_ALPHA = 240;

    private static Bitmap DEFAULT_SIM_AVATAR;
    private static Bitmap DEFAULT_SIM_YELLOW_AVATAR;
    private static Bitmap DEFAULT_SIM_ORANGE_AVATAR;
    private static Bitmap DEFAULT_SIM_GREEN_AVATAR;
    private static Bitmap DEFAULT_SIM_PURPLE_AVATAR;
    private static Bitmap DEFAULT_SIM_SDN_AVATAR;
    private static Bitmap DEFAULT_SIM_YELLOW_SDN_AVATAR;
    private static Bitmap DEFAULT_SIM_ORANGE_SDN_AVATAR;
    private static Bitmap DEFAULT_SIM_GREEN_SDN_AVATAR;
    private static Bitmap DEFAULT_SIM_PURPLE_SDN_AVATAR;  
    private static Bitmap DEFAULT_SIM_SDN_AVATAR_LOCKED;


    private long mSdnPhotoId = 0;
    private Paint mSimPaint;
    private static final Paint sPaint = new Paint();
    private int mBackgroundColor;
    private Context mContext;
    private int mSubId = 0;


    public LetterTileDrawableEx(Resources res) {
        super(res);
        mSimPaint = new Paint();
        mSimPaint.setAntiAlias(true);
        // mSimPaint.setAlpha(SIM_ALPHA);
        mSimPaint.setDither(true);
        mBackgroundColor = res.getColor(R.color.background_primary);
        if (DEFAULT_SIM_AVATAR == null) {
            DEFAULT_SIM_AVATAR =  BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_default_small);
            DEFAULT_SIM_YELLOW_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_yellow_small);
            DEFAULT_SIM_ORANGE_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_orange_small);
            DEFAULT_SIM_GREEN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_green_small);
            DEFAULT_SIM_PURPLE_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_purple_small);
            // SDN avatar
            DEFAULT_SIM_SDN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_default_small_locked);
            DEFAULT_SIM_YELLOW_SDN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_yellow_small_locked);
            DEFAULT_SIM_ORANGE_SDN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_orange_small_locked);
            DEFAULT_SIM_GREEN_SDN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_green_small_locked);
            DEFAULT_SIM_PURPLE_SDN_AVATAR = BitmapFactory.decodeResource(res,
                    R.drawable.sim_indicator_purple_small_locked);
            DEFAULT_SIM_SDN_AVATAR_LOCKED = BitmapFactory.decodeResource(res, 
                    R.drawable.sim_indicator_sim_locked);
            /// M: add show sim card icon feature @{
            ExtensionManager.getInstance().getCtExtension().loadSimCardIconBitmap(res);
            /// @}
        }
    }

    public void setSIMProperty(DefaultImageRequest request) {
        if (request.subId > 0 ) {
            mSubId = request.subId;
            mSdnPhotoId = request.photoId;
        }
        Log.d(TAG, "request subId = " + request.subId + " request photoId: " + request.photoId);
    }

    class IconEntry {
        public int iconTint;
        public Bitmap iconBitmap;

    }

    private static HashMap<Integer, IconEntry> BITMAP_ICONS = new HashMap<Integer, IconEntry>();

    public void initSimIconBitmaps() {
        BITMAP_ICONS.clear();
        int[] subIds = SubInfoUtils.getActiveSubscriptionIdList();
        int size = subIds.length;
        for (int i = 0; i < size; i++) {
            IconEntry icon = new IconEntry();
            icon.iconBitmap = SubInfoUtils.getIconBitmap(subIds[i]);
            icon.iconTint = SubInfoUtils.getColorUsingSubId(subIds[i]);
            BITMAP_ICONS.put(subIds[i], icon);
        }
    }
    private Bitmap getIconBitmapUsingSubId(int subId) {
        IconEntry iconEntry = BITMAP_ICONS.get(subId);
        Bitmap bitmap = null;
        if (iconEntry != null) {
            bitmap = iconEntry.iconBitmap;
        }
        return bitmap;
    }

    public  Bitmap getIconBitmapCache(int subId) {
        // Icon color change by setting, we refresh bitmaps icon cache.
        Bitmap bitmap = null;
        IconEntry iconEntry = BITMAP_ICONS.get(subId);
        if (iconEntry == null ||
            SubInfoUtils.iconTintChange(iconEntry.iconTint, subId)) {
            Log.d(TAG, "icon tint changed need to re-get sim icons bitmap");
            initSimIconBitmaps();
        }
        bitmap = getIconBitmapUsingSubId(subId);
        return bitmap;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (SubInfoUtils.checkSubscriber(mSubId)) {
            Bitmap bitmap = getIconBitmapCache(mSubId);
            Log.d(TAG, "bitmap: " + bitmap);
            if (bitmap != null) {
                //drawSimAvatar(bitmap, bitmap.getWidth(), bitmap.getHeight(), canvas);
            }
            //For SDN icon.
            if (mSdnPhotoId == SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED){
                bitmap = DEFAULT_SIM_SDN_AVATAR_LOCKED;
                //drawSdnAvatar(bitmap, bitmap.getWidth(), bitmap.getHeight(), canvas);
            }
        }
    }

    private Bitmap getBitmapForPhotoId(long photoId) {
        Bitmap resultBitmap;
        switch ((int) mSdnPhotoId) {
        case SimContactPhotoUtils.SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID:
            resultBitmap = DEFAULT_SIM_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW:
            resultBitmap = DEFAULT_SIM_YELLOW_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE:
            resultBitmap = DEFAULT_SIM_ORANGE_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN:
            resultBitmap = DEFAULT_SIM_GREEN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE:
            resultBitmap = DEFAULT_SIM_PURPLE_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.DEFAULT_SIM_PHOTO_ID_SDN:
            resultBitmap = DEFAULT_SIM_SDN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_YELLOW_SDN:
            resultBitmap = DEFAULT_SIM_YELLOW_SDN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_ORANGE_SDN:
            resultBitmap = DEFAULT_SIM_ORANGE_SDN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_GREEN_SDN:
            resultBitmap = DEFAULT_SIM_GREEN_SDN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_PURPLE_SDN:
            resultBitmap = DEFAULT_SIM_PURPLE_SDN_AVATAR;
            break;
        case SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED:
            resultBitmap = DEFAULT_SIM_SDN_AVATAR_LOCKED;
            break;
        default:
            resultBitmap = DEFAULT_SIM_AVATAR;
        }
        /// M: add show sim card icon feature @{
        resultBitmap = ExtensionManager.getInstance().getCtExtension()
                .getOperatorIconBitmapForPhotoId(photoId, resultBitmap);
        /// @}
        return resultBitmap;
    }

    private void drawSimAvatar(final Bitmap bitmap, final int width,
            final int height, final Canvas canvas) {
        // rect for sim avatar
        final Rect destRect = copyBounds();
        destRect.set((int) (destRect.right - mScale * SIM_AVATAR_WIDTH_RATIO * destRect.width()),
                (int) (destRect.bottom - mScale * SIM_AVATAR_HEIGHT_RATIO * destRect.height()),
                destRect.right,
                destRect.bottom);
        sPaint.setColor(mBackgroundColor);
        sPaint.setAntiAlias(true);
        float radius = destRect.width() / 2 * 1.2f;
        Log.d(TAG, "width: " + width);
        Log.d(TAG, "radius: " + radius);
        canvas.drawCircle(destRect.centerX(),
            destRect.centerY(),
            radius, sPaint);
        canvas.drawBitmap(bitmap, null, destRect, mSimPaint);
    }
    
    
    private void drawSdnAvatar(final Bitmap bitmap, final int width, 
            final int height, final Canvas canvas){
        // rect for sim avatar
        final Rect destRect = copyBounds();
        
        destRect.set((int)(destRect.left), 
                (int)(destRect.top + mScale * SDN_LOCKED_RATIO * destRect.height()),
                (int)(destRect.left + mScale * SDN_LOCKED_RATIO * destRect.width()),
                (int)(destRect.top  + 2.0f *mScale * SDN_LOCKED_RATIO * destRect.height()));
        
        canvas.drawBitmap(bitmap, null, destRect, mSimPaint);
    }
}

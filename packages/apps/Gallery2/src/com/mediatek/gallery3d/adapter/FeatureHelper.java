package com.mediatek.gallery3d.adapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.PhotoPage;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.Texture;

import com.mediatek.storage.StorageManagerEx;

import com.mediatek.galleryframework.base.ExtItem;
import com.mediatek.galleryframework.base.ExtItem.SupportOperation;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.ThumbType;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryfeature.drm.DrmHelper;

public class FeatureHelper {
    private static final String TAG = "MtkGallery2/FeatureHelper";

    public static final String EXTRA_ENABLE_VIDEO_LIST = "mediatek.intent.extra.ENABLE_VIDEO_LIST";

    // added for rendering sub-type overlay on micro-thumbnail
    private static ResourceTexture sMavOverlay = null;
    private static ResourceTexture sRefocusOverlay = null;
    private static ResourceTexture sConShotsPlay = null;
    private static ResourceTexture sMotionPlay = null;
    private static ResourceTexture sLivePhotoOverlay = null;
    private static ResourceTexture sSlowMotionOverlay = null;
    private static ResourceTexture sPanoramaOverlay = null;
    private static ResourceTexture sDrmOverlay_lock = null;
    private static ResourceTexture sDrmOverlay_unlock = null;
    private static ResourceTexture sBestShotTexture = null;
    private static ResourceTexture sDisableTexture = null;
    private static StorageManager sStorageManager = null;

    private static final HashMap<SupportOperation, Integer> sSpMap = new HashMap<SupportOperation, Integer>();

    static {
        sSpMap.put(SupportOperation.DELETE, MediaObject.SUPPORT_DELETE);
        sSpMap.put(SupportOperation.ROTATE, MediaObject.SUPPORT_ROTATE);
        sSpMap.put(SupportOperation.SHARE, MediaObject.SUPPORT_SHARE);
        sSpMap.put(SupportOperation.CROP, MediaObject.SUPPORT_CROP);
        sSpMap.put(SupportOperation.SHOW_ON_MAP,
                MediaObject.SUPPORT_SHOW_ON_MAP);
        sSpMap.put(SupportOperation.SETAS, MediaObject.SUPPORT_SETAS);
        sSpMap.put(SupportOperation.FULL_IMAGE, MediaObject.SUPPORT_FULL_IMAGE);
        sSpMap.put(SupportOperation.PLAY, MediaObject.SUPPORT_PLAY);
        sSpMap.put(SupportOperation.CACHE, MediaObject.SUPPORT_CACHE);
        sSpMap.put(SupportOperation.EDIT, MediaObject.SUPPORT_EDIT);
        sSpMap.put(SupportOperation.INFO, MediaObject.SUPPORT_INFO);
        sSpMap.put(SupportOperation.TRIM, MediaObject.SUPPORT_TRIM);
        sSpMap.put(SupportOperation.UNLOCK, MediaObject.SUPPORT_UNLOCK);
        sSpMap.put(SupportOperation.BACK, MediaObject.SUPPORT_BACK);
        sSpMap.put(SupportOperation.ACTION, MediaObject.SUPPORT_ACTION);
        sSpMap.put(SupportOperation.CAMERA_SHORTCUT,
                MediaObject.SUPPORT_CAMERA_SHORTCUT);
        sSpMap.put(SupportOperation.MUTE, MediaObject.SUPPORT_MUTE);
        sSpMap.put(SupportOperation.PRINT, MediaObject.SUPPORT_PRINT);
        sSpMap.put(SupportOperation.EXPORT, MediaObject.SUPPORT_EXPORT);
        sSpMap.put(SupportOperation.PROTECTION_INFO, MediaObject.SUPPORT_PROTECTION_INFO);
    }

    public static int mergeSupportOperations(int originSp,
            ArrayList<SupportOperation> exSp,
            ArrayList<SupportOperation> exNotSp) {
        if (exSp != null && exSp.size() != 0) {
            int size = exSp.size();
            for (int i = 0; i < size; i++) {
                originSp |= sSpMap.get(exSp.get(i));
            }
        }
        if (exNotSp != null && exNotSp.size() != 0) {
            int size = exNotSp.size();
            for (int i = 0; i < size; i++) {
                originSp &= ~sSpMap.get(exNotSp.get(i));
            }
        }
        return originSp;
    }

    public static void setDRMLevelFLToIntent(Intent intent) {
        if (null == intent)
            return;
        intent.putExtra(com.mediatek.drm.OmaDrmStore.DrmExtra.EXTRA_DRM_LEVEL,
                com.mediatek.drm.OmaDrmStore.DrmExtra.LEVEL_FL);
    }

    public static ThumbType convertToThumbType(int type) {
        switch (type) {
        case MediaItem.TYPE_THUMBNAIL:
            return ThumbType.MIDDLE;
        case MediaItem.TYPE_MICROTHUMBNAIL:
            return ThumbType.MICRO;
        case MediaItem.TYPE_FANCYTHUMBNAIL:
            return ThumbType.FANCY;
        case MediaItem.TYPE_HIGHQUALITYTHUMBNAIL:
            return ThumbType.HIGHQUALITY;
        default:
            MtkLog.e(TAG, "<covertToThumbType> not support type");
            assert(false);
            return null;
        }
    }

    public static void drawMicroThumbOverLay(Context context, GLCanvas canvas,
            int width, int height, MediaItem item) {
        if (item == null)
            return;

        drawContainerOverLay(context, canvas, width, height, item);
        renderMediaTypeOverlay(context, canvas, width, height, item
                .getMediaData());
    }

    public static void renderMediaTypeOverlay(Context context, GLCanvas canvas,
            int width, int height, MediaData data) {
        ResourceTexture overlay = null;

        // for Refocus
        if (data.mediaType == MediaData.MediaType.REFOCUS) {
            if (sRefocusOverlay == null) {
                sRefocusOverlay = new ResourceTexture(context,
                        R.drawable.m_refocus_tile);
            }
            overlay = sRefocusOverlay;
        }
        
        // for Mav
        if (data.mediaType == MediaData.MediaType.MAV) {
            if (sMavOverlay == null) {
                sMavOverlay = new ResourceTexture(context,
                        R.drawable.m_mav_overlay);
            }
            overlay = sMavOverlay;
        }

        // for ConShots
        if (data.mediaType == MediaData.MediaType.CONTAINER) {
            if (data.subType == MediaData.SubType.CONSHOT) {
                if (sConShotsPlay == null) {
                    sConShotsPlay = new ResourceTexture(context,
                            R.drawable.m_ic_conshots_play);
                }
                overlay = sConShotsPlay;
            } else if (data.subType == MediaData.SubType.MOTRACK) {
                if (sMotionPlay == null) {
                    sMotionPlay = new ResourceTexture(context,
                            R.drawable.m_ic_motion_play);
                }
                overlay = sMotionPlay;
            }
        }

        // for livephoto
        if (data.mediaType == MediaData.MediaType.LIVEPHOTO) {
            if (sLivePhotoOverlay == null) {
                sLivePhotoOverlay = new ResourceTexture(context,
                        R.drawable.m_ic_livephoto);
            }
            overlay = sLivePhotoOverlay;
        }

        // for slowmotion
        if (data.isSlowMotion) {
            if (sSlowMotionOverlay == null) {
                sSlowMotionOverlay = new ResourceTexture(context,
                        R.drawable.m_ic_slowmotion_albumview);
            }
            overlay = sSlowMotionOverlay;
        }

        // for panorama
        if (data.mediaType == MediaData.MediaType.PANORAMA) {
            if (sPanoramaOverlay == null) {
                sPanoramaOverlay = new ResourceTexture(context,
                        R.drawable.m_ic_panorama);
            }
            overlay = sPanoramaOverlay;
        }

        if (overlay != null) {
            int side = Math.min(width, height) / 5;
            overlay.draw(canvas, side / 2, height - side * 3 / 2, side, side);
        }

        // <DRM>
        if (data.mediaType == MediaData.MediaType.DRM) {
            int id = DrmHelper.getDrmIconResourceID(context, data);
            if (id == 0) {
                overlay = null;
            } else {
                overlay = new ResourceTexture(context, id);
            }
            if (overlay != null) {
                drawRightBottom(canvas, overlay, 0, 0, width, height, 1.0f);
            }
        }
    }

    public static void setExtBundle(AbstractGalleryActivity activity,
            Intent intent, Bundle data, Path path) {
        MediaObject object = activity.getDataManager().getMediaObject(path);
        if (object instanceof MediaItem) {
            MediaItem item = (MediaItem) object;
            MediaData md = item.getMediaData();
            if (md.mediaType == MediaData.MediaType.CONTAINER
                    && md.subType == MediaData.SubType.CONSHOT) {
                MediaSet mediaset = getContainerSet(activity, md);
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH, mediaset.getPath()
                        .toString());
                data.putInt(PhotoPage.KEY_INDEX_HINT, getConShotIndex(mediaset,
                        md.id));
                if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0)
                    data.putBoolean(PhotoPage.KEY_TREAT_BACK_AS_UP, false);
            }
        }
    }

    public static Uri tryContentMediaUri(Context context, Uri uri) {
        if (null == uri)
            return null;

        String scheme = uri.getScheme();
        if (!ContentResolver.SCHEME_FILE.equals(scheme)) {
            return uri;
        } else {
            String path = uri.getPath();
            MtkLog.d(TAG, "<tryContentMediaUri> for " + path);
            if (!new File(path).exists()) {
                return null;
            }
        }

        Cursor cursor = null;
        try {
            // for file kinds of uri, query media database
            cursor = Media.query(context.getContentResolver(), Media
                    .getContentUri("external"), new String[] { Media._ID,
                    Media.BUCKET_ID }, "_data=(?)", new String[] { uri
                    .getPath() }, null); // " bucket_id ASC, _id ASC");
            if (null != cursor && cursor.moveToNext()) {
                long id = cursor.getLong(0);
                final String imagesUri = Media.getContentUri("external")
                        .toString();
                uri = Uri.parse(imagesUri + "/" + id);
                MtkLog.i(TAG, "<tryContentMediaUri> got " + uri);
            } else {
                MtkLog.w(TAG, "<tryContentMediaUri> fail to convert " + uri);
            }
        } finally {
            if (null != cursor) {
                cursor.close();
                cursor = null;
            }
        }
        return uri;
    }

    public static MediaSet getContainerSet(AbstractGalleryActivity activity, MediaData data) {
        if (data == null) {
            MtkLog.i(TAG, "<getContainerSet> data is null, return null");
            return null;
        } else if (data.subType == MediaData.SubType.CONSHOT) {
            return getConshotSet(activity, data.bucketId, data.groupID);
        } else if (data.subType == MediaData.SubType.MOTRACK) {
            return getMotrackSet(activity, data.id);
        } else {
            MtkLog.i(TAG, "<getContainerSet> subType = " + data.subType + ", return null");
            return null;
        }
    }

    private static MediaSet getConshotSet(AbstractGalleryActivity activity, int bucketId,
            long groupId) {
        Path tmpPath = Path.fromString(ContainerSource.CONTAINER_CONSHOT_SET).getChild(bucketId)
                .getChild(groupId);
        return (MediaSet) activity.getDataManager().getMediaObject(tmpPath);
    }

    private static MediaSet getMotrackSet(AbstractGalleryActivity activity, long id) {
        Path tmpPath = Path.fromString(ContainerSource.CONTAINER_MOTRACK_SET).getChild(id);
        return (MediaSet) activity.getDataManager().getMediaObject(tmpPath);
    }

    public static File getExternalCacheDir(Context context) {
        if (context == null) {
            return null;
        }
        File ret = StorageManagerEx.getExternalCacheDir(context
                .getPackageName());
        if (ret == null)
            return null;
        String internalStoragePath = StorageManagerEx.getInternalStoragePath();
        if (internalStoragePath == null)
            return null;
        String cachePath = ret.getAbsolutePath();
        cachePath = internalStoragePath
                + cachePath.substring(internalStoragePath.length(), cachePath
                        .length());
        ret = new File(cachePath);
        if (ret.exists())
            return ret;
        if (ret.mkdirs())
            return ret;
        MtkLog
                .v(TAG,
                        "<getExternalCacheDir> can not create external cache dir");
        return null;
    }

    public static String getDefaultPath() {
        String path = StorageManagerEx.getDefaultPath();
        return path;
    }

    public static String getDefaultStorageState(Context context) {
        if (sStorageManager == null && context == null) {
            return null;
        }
        if (sStorageManager == null) {
            sStorageManager = (StorageManager) context
                    .getSystemService(Context.STORAGE_SERVICE);
        }
        String path = StorageManagerEx.getDefaultPath();
        if (path == null) {
            return null;
        }
        String volumeState = sStorageManager.getVolumeState(path);
        MtkLog.v(TAG, "<getDefaultStorageState> default path = " + path
                + ", state = " + volumeState);
        return volumeState;
    }

    private static void drawContainerOverLay(Context context, GLCanvas canvas,
            int width, int height, MediaItem item) {
        if (item == null)
            return;
        MediaData data = item.getMediaData();
        ExtItem extItem = item.getExtItem();
        // When mediaType ==  MediaData.MediaType.CONTAINER, 
        // it means this item is in LocalAlbum, not in ContainerSet,
        // it's a group continuous shot images, no need to render best shot icon
        if (data.mediaType == MediaData.MediaType.CONTAINER)
            return;
        if (data.subType == MediaData.SubType.CONSHOT
                && MediaData.BEST_SHOT_MARK_TRUE == data.bestShotMark) {
            if (sBestShotTexture == null) {
                sBestShotTexture = new ResourceTexture(context,
                        R.drawable.m_ic_best_shot);
            }
            int side = Math.min(width, height) / 5;
            int texWidth = sBestShotTexture.getWidth();
            int texHeight = sBestShotTexture.getHeight();
            sBestShotTexture.draw(canvas, 0, height - texHeight, texWidth,
                    texHeight);
        }
        if (data.subType == MediaData.SubType.MOTRACK && !extItem.isEnable()) {
            if (sDisableTexture == null) {
                sDisableTexture = new ResourceTexture(context,
                        R.drawable.m_ic_motrack_disable);
            }
            sDisableTexture.draw(canvas, 0, 0, width, height);
        }
    }

    private static int getConShotIndex(MediaSet mediaset, long id) {
        ArrayList<MediaItem> items;

        items = mediaset.getMediaItem(0, mediaset.getMediaItemCount());
        for (int i = 0; i < items.size(); i++) {
            LocalImage item = (LocalImage) items.get(i);
            if (item.id == id)
                return i;
        }
        return 0;
    }

    private static void drawRightBottom(GLCanvas canvas, Texture tex, int x,
            int y, int width, int height, float scale) {
        if (null == tex)
            return;
        int texWidth = (int) ((float) tex.getWidth() * scale);
        int texHeight = (int) ((float) tex.getHeight() * scale);
        tex.draw(canvas, x + width - texWidth, y + height - texHeight,
                texWidth, texHeight);
    }

    public static boolean isLocalUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        boolean isLocal = ContentResolver.SCHEME_FILE.equals(uri.getScheme());
        isLocal |= ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && MediaStore.AUTHORITY.equals(uri.getAuthority());
        return isLocal;
    }

    public static MediaDetails convertStringArrayToDetails(String[] array) {
        if (array == null || array.length < 1) return null;
        MediaDetails res = new MediaDetails();
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null)
                res.addDetail(i + 1, array[i]);
        }
        return res;
    }

    // HW limitation @{
    private static final int JPEG_DECODE_LENGTH_MAX = 8192;

    public static boolean isJpegOutOfLimit(String mimeType, int width,
            int height) {
        if (mimeType.equals("image/jpeg")
                && (width > JPEG_DECODE_LENGTH_MAX || height > JPEG_DECODE_LENGTH_MAX)) {
            return true;
        }
        return false;
    }
    // @}

    // <CTA Data Protection> @{
    public static void clearToken(Context context, String tokenKey, String token) {
        DrmHelper.clearToken(context, tokenKey, token);
    }

    public static boolean isTokenValid(Context context, String tokenKey,
            String token) {
        return DrmHelper.isTokenValid(context, tokenKey, token);
    }
    // @}

    public static boolean isDefaultStorageMounted(Context context) {
        String defaultStorageState = getDefaultStorageState(context);
        if (defaultStorageState == null) {
            defaultStorageState = Environment.getExternalStorageState();
        }
        return Environment.MEDIA_MOUNTED.equalsIgnoreCase(defaultStorageState);
    }
    
    /// M: add for Drm icon show in sildeshow page.
    public static void drawSliderShowDrmIcon (Context context, GLCanvas canvas, int x, int y,
            int width, int height, MediaData data, float scale) {
        if (data == null) return ;
        ResourceTexture overlay = null;
        if (data.mediaType == MediaData.MediaType.DRM) {
            int id = DrmHelper.getDrmIconResourceID(context, data);
            if (id == 0) {
                overlay = null;
            } else {
                overlay = new ResourceTexture(context, id);
            }
            if (overlay != null) {
                drawRightBottom(canvas, overlay, x, y, width, height, scale);
            }
        }
    }

    public static void refreshResource(Context context) {
        if (sBestShotTexture != null) {
            sBestShotTexture.recycle();
        }
        if (sDisableTexture != null) {
            sDisableTexture.recycle();
        }
        if (sRefocusOverlay != null) {
            sRefocusOverlay.recycle();
        }
        if (sMavOverlay != null) {
            sMavOverlay.recycle();
        }
        if (sConShotsPlay != null) {
            sConShotsPlay.recycle();
        }
        if (sMotionPlay != null) {
            sMotionPlay.recycle();
        }
        if (sLivePhotoOverlay != null) {
            sLivePhotoOverlay.recycle();
        }
        if (sSlowMotionOverlay != null) {
            sSlowMotionOverlay.recycle();
        }
        if (sPanoramaOverlay != null) {
            sPanoramaOverlay.recycle();
        }
        sBestShotTexture = new ResourceTexture(context, R.drawable.m_ic_best_shot);
        sDisableTexture = new ResourceTexture(context, R.drawable.m_ic_motrack_disable);
        sRefocusOverlay = new ResourceTexture(context, R.drawable.m_refocus_tile);
        sMavOverlay = new ResourceTexture(context, R.drawable.m_mav_overlay);
        sConShotsPlay = new ResourceTexture(context, R.drawable.m_ic_conshots_play);
        sMotionPlay = new ResourceTexture(context, R.drawable.m_ic_motion_play);
        sLivePhotoOverlay = new ResourceTexture(context, R.drawable.m_ic_livephoto);
        sSlowMotionOverlay = new ResourceTexture(context, R.drawable.m_ic_slowmotion_albumview);
        sPanoramaOverlay = new ResourceTexture(context, R.drawable.m_ic_panorama);
    }
}

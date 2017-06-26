package com.mediatek.gallery3d.ext;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.os.Bundle;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Locale;


/**
 * Util class for Movie functions. *
 */
public class MovieUtils {
    private static final String TAG = "Gallery2/MovieUtils";
    private static final boolean LOG = true;
    private static final String HTTP_LIVE_SUFFIX = ".m3u8";

    // video type
    public static final int VIDEO_TYPE_LOCAL = 0;
    public static final int VIDEO_TYPE_HTTP = 1;
    public static final int VIDEO_TYPE_RTSP = 2;
    public static final int VIDEO_TYPE_SDP = 3;

    private MovieUtils() {
    }

    /**
     * Judge the video type
     *
     * @param uri The video uri.
     * @param mimeType The mimeType of the video.
     */
    public static int judgeStreamingType(Uri uri, String mimeType) {
        int videoType = VIDEO_TYPE_LOCAL;
        if (LOG) {
            Log.v(TAG, "judgeStreamingType entry with uri is: " + uri + " and mimeType is: "
                    + mimeType);
        }
        if (uri == null) {
            return -1;
        }
        if (isSdpStreaming(uri, mimeType)) {
            videoType = VIDEO_TYPE_SDP;
        } else if (isRtspStreaming(uri, mimeType)) {
            videoType = VIDEO_TYPE_RTSP;
        } else if (isHttpStreaming(uri, mimeType) || isHttpLiveStreaming(uri, mimeType)) {
            videoType = VIDEO_TYPE_HTTP;
        } else {
            videoType = VIDEO_TYPE_LOCAL;
        }
        if (LOG) {
            Log.v(TAG, "videoType is " + videoType);
        }
        return videoType;
    }

    /**
     * Whether current video(Uri) is RTSP streaming or not.
     *
     * @param uri
     * @param mimeType
     * @return
     */
    public static boolean isRtspStreaming(Uri uri, String mimeType) {
        boolean rtsp = false;
        if (uri != null) {
            if ("rtsp".equalsIgnoreCase(uri.getScheme())) {
                rtsp = true;
            }
        }
        if (LOG) {
            Log.v(TAG, "isRtspStreaming(" + uri + ", " + mimeType + ") return " + rtsp);
        }
        return rtsp;
    }

    /**
     * Whether current video(Uri) is HTTP streaming or not.
     *
     * @param uri
     * @param mimeType
     * @return
     */
    public static boolean isHttpStreaming(Uri uri, String mimeType) {
        boolean http = false;
        if (uri != null) {
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri
                    .getScheme()))
                    && !(uri.toString().toLowerCase(Locale.ENGLISH).contains(HTTP_LIVE_SUFFIX))
                    && !(uri.toString().toLowerCase(Locale.ENGLISH).contains(".sdp"))
                    && !(uri.toString().toLowerCase(Locale.ENGLISH).contains(".smil"))) {
                http = true;
            }
        }
        if (LOG) {
            Log.v(TAG, "isHttpStreaming(" + uri + ", " + mimeType + ") return " + http);
        }
        return http;
    }

    /**
     * Whether current video(Uri) is http live streaming or not.
     *
     * @param uri The video Uri.
     * @param mimeType The mimeType of the video.
     * @return True if the video is a http live streaming,false otherwise.
     */
    public static boolean isHttpLiveStreaming(Uri uri, String mimeType) {
        boolean isHttpLive = false;
        if (uri != null) {
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri
                    .getScheme()))
                    && (uri.toString().toLowerCase(Locale.ENGLISH).contains(HTTP_LIVE_SUFFIX))) {
                isHttpLive = true;
            }
        }
        if (LOG) {
            Log.v(TAG, "isHttpLiveStreaming(" + uri + ", " + mimeType + ") return "
                            + isHttpLive);
        }
        return isHttpLive;
    }

    /**
     * Whether current video(Uri) is live streaming or not.
     *
     * @param uri
     * @param mimeType
     * @return
     */
    public static boolean isSdpStreaming(Uri uri, String mimeType) {
        boolean sdp = false;
        if (uri != null) {
            if ("application/sdp".equals(mimeType)) {
                sdp = true;
            } else if (uri.toString().toLowerCase(Locale.ENGLISH).endsWith(".sdp")) {
                sdp = true;
            }
        }
        if (LOG) {
            Log.v(TAG, "isSdpStreaming(" + uri + ", " + mimeType + ") return " + sdp);
        }
        return sdp;
    }

    /**
     * Whether current video(Uri) is local file or not.
     *
     * @param uri
     * @param mimeType
     * @return
     */
    public static boolean isLocalFile(Uri uri, String mimeType) {
        boolean local =
                (!isSdpStreaming(uri, mimeType) && !isRtspStreaming(uri, mimeType)
                        && !isHttpStreaming(uri, mimeType) && !isHttpLiveStreaming(uri, mimeType));
        if (LOG) {
            Log.v(TAG, "isLocalFile(" + uri + ", " + mimeType + ") return " + local);
        }
        return local;
    }

    /**
     * Check whether the video is a rtsp streaming video or not.
     *
     * @return True if the video is a rtsp streaming video,false otherwise.
     */
    public static boolean isRTSP(int videoType) {
        if (videoType == VIDEO_TYPE_RTSP) {
            Log.v(TAG, "isRTSP() is RTSP");
            return true;
        }
        Log.v(TAG, "isRTSP() is not RTSP videoType: " + videoType);
        return false;
    }
    public static boolean isRtspOrSdp(int videoType) {
        if (videoType == VIDEO_TYPE_RTSP || videoType == VIDEO_TYPE_SDP) {
            Log.v(TAG, "isRtspOrSdp() is not RTSP or SDP ");
            return true;
        }
        Log.v(TAG, "isRtspOrSdp() is not RTSP or SDP videoType: " + videoType);
        return false;
    }
    /**
     * Check whether the video is a live streaming video.
     *
     * @return True if the video is a live streaming video,false otherwise.
     */
    public static boolean isLiveStreaming(int videoType) {
        if (videoType == VIDEO_TYPE_SDP) {
            Log.v(TAG, "isLiveStreaming() is live streaming");
            return true;
        }
        Log.v(TAG, "isLiveStreaming() is not live streaming");
        return false;
    }

    /**
     * Whether current video is support trim and mute?
     */
    public static boolean isSupportTrim(String mimeType) {
       Log.v(TAG, "isSupportTrim(" + mimeType + ")");
       if (mimeType != null) {
           return mimeType.toLowerCase().equals("video/mp4")
                  || mimeType.toLowerCase().equals("video/3gpp")
                  || mimeType.toLowerCase().equals("video/quicktime");
       }
       return false;
    }

    /**
     * Whether current video is support trim and mute?
     */
    public static boolean isSupportTrim(final Context context, Uri uri) {
       String mimeType = null;
       Cursor cursor = null;
       Log.v(TAG, "isSupportTrim(" + uri + ")");
 /*      if (isLivePhoto(context, uri)) {
           return false;
       } */
       try {
              cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Video.Media.MIME_TYPE}, null, null, null);

              if (cursor == null) {
                  String data = Uri.decode(uri.toString());
                  if (data == null) {
                      return false;
                  }
                  data = data.replaceAll("'", "''");
                  final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";

                  cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                      new String[]{MediaStore.Video.Media.MIME_TYPE}, where, null, null);
                              }

              if (cursor != null && cursor.moveToFirst()) {
                    mimeType = cursor.getString(0);
            }
            } catch (final SQLiteException ex) {
                ex.printStackTrace();
            } catch (IllegalArgumentException e) {
                //if this exception happen, return false.
                Log.v(TAG, "ContentResolver query IllegalArgumentException");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
       return isSupportTrim(mimeType);
    }
    public static boolean isLivePhoto(final Context context, final Uri uri) {
        int title = 0;
        Cursor cursor = null;
        try {
            String data = Uri.decode(uri.toString());
            if (data == null) {
                return false;
            }
            data = data.replaceAll("'", "''");
            final String where = "_data LIKE '%" + data.replaceFirst("file:///", "") + "'";
            cursor = context.getContentResolver().query(uri,
                                       new String[]{Video.Media.IS_LIVE_PHOTO}, null, null, null);

            if (cursor == null) {
                cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{Video.Media.IS_LIVE_PHOTO}, where, null, null);
                            }
            if (LOG) {
                Log.v(TAG, "setInfoFromMediaData() cursor=" + (cursor == null ? "null" : cursor.getCount()));
            }
            if (cursor != null && cursor.moveToFirst()) {
                title = cursor.getInt(0);
           }
        } catch (final IllegalStateException ex) {
            //cursor.getInt(0) may cause exception, should catch it here.
            ex.printStackTrace();
        } catch (final SQLiteException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException e) {
            //if this exception happen, return false.
            Log.v(TAG, "ContentResolver query IllegalArgumentException");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (LOG) {
            Log.v(TAG, "setInfoFromMediaData() return " + title);
        }
        return (1 == title);
    }

       /*
        * Judge WFD is connected or not?
        */
       public static boolean isWfdEnabled(Context mContext) {
            boolean enabled = false;
            int activityDisplayState = -1;
            DisplayManager mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            WifiDisplayStatus mWfdStatus = mDisplayManager.getWifiDisplayStatus();
            activityDisplayState = mWfdStatus.getActiveDisplayState();
            enabled = activityDisplayState == WifiDisplayStatus.DISPLAY_STATE_CONNECTED;
            if (LOG) {
                Log.d(TAG, "isWfdEnabled() mWfdStatus=" + mWfdStatus + ", return " + enabled);
            }
            return enabled;
        }


     private static final String EXTRA_CAN_SHARE = "CanShare";
     public static boolean canShare(Bundle extra) {
         boolean canshare = true;
         if (extra != null) {
             canshare = extra.getBoolean(EXTRA_CAN_SHARE, true);
         }
         if (LOG) {
             Log.v(TAG, "canShare(" + extra + ") return " + canshare);
         }
         return canshare;
     }

       /**
        * Convert byteArry to Drawable
        *
        * @param byteArry The picture in byte array.
        * @return A Drawable.
        */
       public static Drawable bytesToDrawable(byte[] byteArray) {
           Bitmap bitmap = null;
           Drawable drawable = null;
           int length = byteArray.length;
           if (length != 0) {
               bitmap = BitmapFactory.decodeByteArray(byteArray, 0, length);
               drawable = new BitmapDrawable(bitmap);
           }
           Log.v(TAG, "bytesToDrawable() exit with the drawable is " + drawable);
           return drawable;
       }



       // //FOR MTK_SUBTITLE_SUPPORT
       // @{
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_SRT = ".srt";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_MPL = ".mpl";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_SMI = ".smi";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_SUB = ".sub";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_IDX = ".idx";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_TXT = ".txt";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_SSA = ".ssa";
       public static final String SUBTITLE_SUPPORT_WITH_SUFFIX_ASS = ".ass";

       public static final String MEDIA_MIMETYPE_TEXT_SUBASS = "application/x-subtitle-ass";
       public static final String MEDIA_MIMETYPE_TEXT_SUBSSA = "application/x-subtitle-ssa";
       public static final String MEDIA_MIMETYPE_TEXT_SUBTXT = "application/x-subtitle-txt";
       public static final String MEDIA_MIMETYPE_TEXT_SUBMPL = "application/x-subtitle-mpl";
       public static final String MEDIA_MIMETYPE_TEXT_SUBSMI = "application/x-subtitle-smi";
       public static final String MEDIA_MIMETYPE_TEXT_SUB = "application/x-subtitle-sub";
       public static final String MEDIA_MIMETYPE_TEXT_IDX = "application/x-subtitle-idx";

       static private class ScriptFileNameFilter implements FilenameFilter {
           private String mNameToFilter;

           /**
            * construct
            * @param nameGetToFilter
            */
           public ScriptFileNameFilter(String nameGetToFilter) {
           this.mNameToFilter = nameGetToFilter;
           }

           /**
            * Override
            * @param dir
            * @param name
            * @return
            */
           public boolean accept(File dir, String name) {
               if (name.indexOf(mNameToFilter) != -1) {
                   return true;
               } else {
                   return false;
               }
           }
       }

       /**
        * list external subtitle files with the same name
        *
        * @param VideoPath the path of a video
        * @return files array
        */
       public static File[] listTheSameNameOfVideo(String VideoPath) {
           if (null == VideoPath) {
               return null;
           }
           File dir = new File(VideoPath);
           if (!dir.exists()) {
               return null;
           }
           File parentFileDir = dir.getParentFile();
           if (parentFileDir == null) {
               return null;
           }
           String VideoName = dir.getName();
           Log.i(TAG, "AudioAndSubtitle getExtSubTitleFileName parentFileDir ="
                    + parentFileDir.getName() + "  VideoName=" + VideoName);
           int index = VideoName.lastIndexOf(".");
           if (index == -1) {
               return null;
           }
           String nameToFiliter = VideoName.substring(0, index) + ".";
           File[] sameNameFiles = parentFileDir.listFiles(new ScriptFileNameFilter(nameToFiliter));
           if (sameNameFiles != null) {
               for (File file : sameNameFiles) {
                   Log.i(TAG, "AudioAndSubtitle getExtSubTitleFileName sameNameFiles.path ="                         + file.getPath());
               }
           }
           return sameNameFiles;
       }

       ///@}


}

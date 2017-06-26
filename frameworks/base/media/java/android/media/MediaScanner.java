/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.media;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import android.app.ActivityManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.database.Cursor;
import android.database.SQLException;
import android.drm.DrmManagerClient;
import android.graphics.BitmapFactory;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.net.TrafficStats;
import android.os.Environment;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.RootElement;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.Xml;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import com.mediatek.dcfdecoder.DcfDecoder;

/*<DTS2015060215541 lichen/00348693 20150602 begin */
import android.hwtheme.HwThemeManager;
import android.common.HwFrameworkFactory;
/*DTS2015060215541 lichen/00348693 20150602 end > */
/**
 * Internal service helper that no-one should use directly.
 *
 * The way the scan currently works is:
 * - The Java MediaScannerService creates a MediaScanner (this class), and calls
 *   MediaScanner.scanDirectories on it.
 * - scanDirectories() calls the native processDirectory() for each of the specified directories.
 * - the processDirectory() JNI method wraps the provided mediascanner client in a native
 *   'MyMediaScannerClient' class, then calls processDirectory() on the native MediaScanner
 *   object (which got created when the Java MediaScanner was created).
 * - native MediaScanner.processDirectory() calls
 *   doProcessDirectory(), which recurses over the folder, and calls
 *   native MyMediaScannerClient.scanFile() for every file whose extension matches.
 * - native MyMediaScannerClient.scanFile() calls back on Java MediaScannerClient.scanFile,
 *   which calls doScanFile, which after some setup calls back down to native code, calling
 *   MediaScanner.processFile().
 * - MediaScanner.processFile() calls one of several methods, depending on the type of the
 *   file: parseMP3, parseMP4, parseMidi, parseOgg or parseWMA.
 * - each of these methods gets metadata key/value pairs from the file, and repeatedly
 *   calls native MyMediaScannerClient.handleStringTag, which calls back up to its Java
 *   counterparts in this file.
 * - Java handleStringTag() gathers the key/value pairs that it's interested in.
 * - once processFile returns and we're back in Java code in doScanFile(), it calls
 *   Java MyMediaScannerClient.endFile(), which takes all the data that's been
 *   gathered and inserts an entry in to the database.
 *
 * In summary:
 * Java MediaScannerService calls
 * Java MediaScanner scanDirectories, which calls
 * Java MediaScanner processDirectory (native method), which calls
 * native MediaScanner processDirectory, which calls
 * native MyMediaScannerClient scanFile, which calls
 * Java MyMediaScannerClient scanFile, which calls
 * Java MediaScannerClient doScanFile, which calls
 * Java MediaScanner processFile (native method), which calls
 * native MediaScanner processFile, which calls
 * native parseMP3, parseMP4, parseMidi, parseOgg or parseWMA, which calls
 * native MyMediaScanner handleStringTag, which calls
 * Java MyMediaScanner handleStringTag.
 * Once MediaScanner processFile returns, an entry is inserted in to the database.
 *
 * The MediaScanner class is not thread-safe, so it should only be used in a single threaded manner.
 *
 * {@hide}
 */
public class MediaScanner
{
    static {
        System.loadLibrary("media_jni");
        native_init();
    }

    private final static String TAG = "MediaScanner";

    private static final String[] FILES_PRESCAN_PROJECTION = new String[] {
            Files.FileColumns._ID, // 0
            Files.FileColumns.DATA, // 1
            Files.FileColumns.FORMAT, // 2
            Files.FileColumns.DATE_MODIFIED, // 3
    };

    private static final String[] ID_PROJECTION = new String[] {
            Files.FileColumns._ID,
    };

    private static final int FILES_PRESCAN_ID_COLUMN_INDEX = 0;
    private static final int FILES_PRESCAN_PATH_COLUMN_INDEX = 1;
    private static final int FILES_PRESCAN_FORMAT_COLUMN_INDEX = 2;
    private static final int FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX = 3;

    private static final String[] PLAYLIST_MEMBERS_PROJECTION = new String[] {
            Audio.Playlists.Members.PLAYLIST_ID, // 0
     };

    private static final int ID_PLAYLISTS_COLUMN_INDEX = 0;
    private static final int PATH_PLAYLISTS_COLUMN_INDEX = 1;
    private static final int DATE_MODIFIED_PLAYLISTS_COLUMN_INDEX = 2;

    private static final String RINGTONES_DIR = "/ringtones/";
    private static final String NOTIFICATIONS_DIR = "/notifications/";
    private static final String ALARMS_DIR = "/alarms/";
    private static final String MUSIC_DIR = "/music/";
    private static final String PODCAST_DIR = "/podcasts/";

    private static final String[] ID3_GENRES = {
        // ID3v1 Genres
        "Blues",
        "Classic Rock",
        "Country",
        "Dance",
        "Disco",
        "Funk",
        "Grunge",
        "Hip-Hop",
        "Jazz",
        "Metal",
        "New Age",
        "Oldies",
        "Other",
        "Pop",
        "R&B",
        "Rap",
        "Reggae",
        "Rock",
        "Techno",
        "Industrial",
        "Alternative",
        "Ska",
        "Death Metal",
        "Pranks",
        "Soundtrack",
        "Euro-Techno",
        "Ambient",
        "Trip-Hop",
        "Vocal",
        "Jazz+Funk",
        "Fusion",
        "Trance",
        "Classical",
        "Instrumental",
        "Acid",
        "House",
        "Game",
        "Sound Clip",
        "Gospel",
        "Noise",
        "AlternRock",
        "Bass",
        "Soul",
        "Punk",
        "Space",
        "Meditative",
        "Instrumental Pop",
        "Instrumental Rock",
        "Ethnic",
        "Gothic",
        "Darkwave",
        "Techno-Industrial",
        "Electronic",
        "Pop-Folk",
        "Eurodance",
        "Dream",
        "Southern Rock",
        "Comedy",
        "Cult",
        "Gangsta",
        "Top 40",
        "Christian Rap",
        "Pop/Funk",
        "Jungle",
        "Native American",
        "Cabaret",
        "New Wave",
        "Psychadelic",
        "Rave",
        "Showtunes",
        "Trailer",
        "Lo-Fi",
        "Tribal",
        "Acid Punk",
        "Acid Jazz",
        "Polka",
        "Retro",
        "Musical",
        "Rock & Roll",
        "Hard Rock",
        // The following genres are Winamp extensions
        "Folk",
        "Folk-Rock",
        "National Folk",
        "Swing",
        "Fast Fusion",
        "Bebob",
        "Latin",
        "Revival",
        "Celtic",
        "Bluegrass",
        "Avantgarde",
        "Gothic Rock",
        "Progressive Rock",
        "Psychedelic Rock",
        "Symphonic Rock",
        "Slow Rock",
        "Big Band",
        "Chorus",
        "Easy Listening",
        "Acoustic",
        "Humour",
        "Speech",
        "Chanson",
        "Opera",
        "Chamber Music",
        "Sonata",
        "Symphony",
        "Booty Bass",
        "Primus",
        "Porn Groove",
        "Satire",
        "Slow Jam",
        "Club",
        "Tango",
        "Samba",
        "Folklore",
        "Ballad",
        "Power Ballad",
        "Rhythmic Soul",
        "Freestyle",
        "Duet",
        "Punk Rock",
        "Drum Solo",
        "A capella",
        "Euro-House",
        "Dance Hall",
        // The following ones seem to be fairly widely supported as well
        "Goa",
        "Drum & Bass",
        "Club-House",
        "Hardcore",
        "Terror",
        "Indie",
        "Britpop",
        null,
        "Polsk Punk",
        "Beat",
        "Christian Gangsta",
        "Heavy Metal",
        "Black Metal",
        "Crossover",
        "Contemporary Christian",
        "Christian Rock",
        "Merengue",
        "Salsa",
        "Thrash Metal",
        "Anime",
        "JPop",
        "Synthpop",
        // 148 and up don't seem to have been defined yet.
    };

    private long mNativeContext;
    private Context mContext;
    private String mPackageName;
    private int mContext1;
    private IContentProvider mMediaProvider;
    private Uri mAudioUri;
    private Uri mVideoUri;
    private Uri mImagesUri;
    private Uri mThumbsUri;
    private Uri mVideoThumbsUri;
    private Uri mPlaylistsUri;
    private Uri mFilesUri;
    private Uri mFilesUriNoNotify;
    private boolean mProcessPlaylists, mProcessGenres;
    private int mMtpObjectHandle;

    private final String mExternalStoragePath;
    private final boolean mExternalIsEmulated;

    /** whether to use bulk inserts or individual inserts for each item */
    private static final boolean ENABLE_BULK_INSERTS = true;

    // used when scanning the image database so we know whether we have to prune
    // old thumbnail files
    private int mOriginalCount;
    /// M: old video thumbnail files
    private int mOriginalVideoCount;
    /** Whether the database had any entries in it before the scan started */
    private boolean mWasEmptyPriorToScan = false;
    /** Whether the scanner has set a default sound for the ringer ringtone. */
    private boolean mDefaultRingtoneSet;
    /** Whether the scanner has set a default sound for the notification ringtone. */
    private boolean mDefaultNotificationSet;
    /** Whether the scanner has set a default sound for the alarm ringtone. */
    private boolean mDefaultAlarmSet;
    /** The filename for the default sound for the ringer ringtone. */
    private String mDefaultRingtoneFilename;
    /** The filename for the default sound for the notification ringtone. */
    private String mDefaultNotificationFilename;
    /** The filename for the default sound for the alarm ringtone. */
    private String mDefaultAlarmAlertFilename;

    /** HQ_wuhuihui_20150609 add for multisim ringtone support start*/
	private String mDefaultRingtoneSim2Filename;
    private boolean mDefaultRingtoneSim2Set;
    private static final String RINGTONE_SIM2_SET = "ringtone_sim2_set";
	/** HQ_wuhuihui_20150609 add for multisim ringtone support end*/
	
    /**
     * The prefix for system properties that define the default sound for
     * ringtones. Concatenate the name of the setting from Settings
     * to get the full system property.
     */
    private static final String DEFAULT_RINGTONE_PROPERTY_PREFIX = "ro.config.";

    // set to true if file path comparisons should be case insensitive.
    // this should be set when scanning files on a case insensitive file system.
    private boolean mCaseInsensitivePaths;

    private final BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

    private static class FileEntry {
        long mRowId;
        String mPath;
        long mLastModified;
        int mFormat;
        boolean mLastModifiedChanged;

        FileEntry(long rowId, String path, long lastModified, int format) {
            mRowId = rowId;
            mPath = path;
            mLastModified = lastModified;
            mFormat = format;
            mLastModifiedChanged = false;
        }

        @Override
        public String toString() {
            return mPath + " mRowId: " + mRowId;
        }
    }

    private static class PlaylistEntry {
        String path;
        long bestmatchid;
        int bestmatchlevel;
    }

    private ArrayList<PlaylistEntry> mPlaylistEntries = new ArrayList<PlaylistEntry>();

    private MediaInserter mMediaInserter;

    private ArrayList<FileEntry> mPlayLists;

    private DrmManagerClient mDrmManagerClient = null;
    public static final boolean IS_SUPPORT_DRM = SystemProperties.getBoolean("ro.mtk_oma_drm_support", true);
    /// M: Add 3 flags to identify if alarm, notification and ringtone is set or not. @{
    private static final String ALARM_SET = "alarm_set";
    private static final String NOTIFICATION_SET = "notification_set";
    private static final String RINGTONE_SET = "ringtone_set";
    /// @}
    /// M: limit bmp and gif file size, don't decode images if big over limit to avoid memory issue
    private long mLimitBmpFileSize = Long.MAX_VALUE;
    private long mLimitGifFileSize = Long.MAX_VALUE;

    public MediaScanner(Context c) {
        native_setup();
        mContext = c;
        mPackageName = c.getPackageName();
        mBitmapOptions.inSampleSize = 1;
        mBitmapOptions.inJustDecodeBounds = true;

        setDefaultRingtoneFileNames();

        mExternalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        mExternalIsEmulated = Environment.isExternalStorageEmulated();
        //mClient.testGenreNameConverter();

        /// M: set bmp and gif decode limit size same as Gallery
        final ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am.isLowRamDevice()) {
            mLimitBmpFileSize = 6 * TrafficStats.MB_IN_BYTES;
            mLimitGifFileSize = 10 * TrafficStats.MB_IN_BYTES;
        } else {
            mLimitBmpFileSize = 52 * TrafficStats.MB_IN_BYTES;
            mLimitGifFileSize = 20 * TrafficStats.MB_IN_BYTES;
        }
    }

    private void setDefaultRingtoneFileNames() {
        mDefaultRingtoneFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.RINGTONE);
		/*<DTS2014101007092 zhuxuefu/00290029 20141011 begin*/
        HwFrameworkFactory.getHwMediaScannerManager().setHwDefaultRingtoneFileNames();
        /*<DTS2014101007092 zhuxuefu/00290029 20141011 end > */
		//HQ_wuhuihui_20150609 add for multisim ringtone start
		mDefaultRingtoneSim2Filename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.RINGTONE_SIM2);
		//HQ_wuhuihui_20150609 add for multisim ringtone end
        mDefaultNotificationFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.NOTIFICATION_SOUND);
        mDefaultAlarmAlertFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.ALARM_ALERT);
        /// M: Adds log to debug setting ringtones.
        Log.v(TAG, "setDefaultRingtoneFileNames: ringtone=" + mDefaultRingtoneFilename
                + ", ringtone_sim2=" + mDefaultRingtoneSim2Filename
                + ",notification=" + mDefaultNotificationFilename + ",alarm=" + mDefaultAlarmAlertFilename);
    }

    private final MyMediaScannerClient mClient = new MyMediaScannerClient();

    private boolean isDrmEnabled() {
        String prop = SystemProperties.get("drm.service.enabled");
        return prop != null && prop.equals("true");
    }

    /* <DTS2014103001999 litao/185177 20141106 begin */
    public class MyMediaScannerClient implements MediaScannerClient {
    /* DTS2014103001999 litao/185177 20141106 end> */

        private String mArtist;
        private String mAlbumArtist;    // use this if mArtist is missing
        private String mAlbum;
        private String mTitle;
        private String mComposer;
        private String mGenre;
        private String mMimeType;
        private int mFileType;
        private int mTrack;
        private int mYear;
        private int mDuration;
        private String mPath;
        private long mLastModified;
        private long mFileSize;
        private String mWriter;
        private int mCompilation;
        private boolean mIsDrm;
        private boolean mNoMedia;   // flag to suppress file from appearing in media tables
        private int mWidth;
        private int mHeight;
        /// M: add for mtk drm @{
        private String mDrmContentUr;
        private long mDrmOffset;
        private long mDrmDataLen;
        private String mDrmRightsIssuer;
        private String mDrmContentName;
        private String mDrmContentDescriptioin;
        private String mDrmContentVendor;
        private String mDrmIconUri;
        private long mDrmMethod;
        /// @}
        /// M: add for Live photo
        private boolean mIsLivePhoto;
        /// M: add for slow motion
        private String mSlowMotionSpeed;
        /// M: Add for fancy gallery homepage(Video get it's rotation)
        private int mOrientation;

        public FileEntry beginFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean noMedia) {
            /// M: Directory or folder's mimeType must be null, because some special name folder(such as
            /// test.mp3) may give a wrong mimeType by MediaFile.getMimeTypeForFile(path).
            mMimeType = isDirectory ? null : mimeType;
            mFileType = 0;
            mFileSize = fileSize;
            mIsDrm = false;

            if (!isDirectory) {
                if (!noMedia && isNoMediaFile(path)) {
                    noMedia = true;
                }
                mNoMedia = noMedia;

                // try mimeType first, if it is specified
                if (mimeType != null) {
                    mFileType = MediaFile.getFileTypeForMimeType(mimeType);
                }

                /// M: OMA DRM v1: however, for those DCF file, normally when scanning it,
                // the input mime type should be "application/vnd.oma.drm.content";
                // however, there's case that the input mimetype is, for example, "image/*"
                // in these cases it will not call processFile() but processImageFile() instead.
                // for these cases, we change the {mFileType} back to ZERO,
                // and let Media.getFileType(path) to determine the type,
                // so that it can call processFile() as normal. @{
                if (MediaFile.isImageFileType(mFileType)) {
                    int lastDot = path.lastIndexOf(".");
                    if (lastDot > 0 && path.substring(lastDot + 1).toUpperCase().equals("DCF")) {
                        Log.v(TAG, "detect a *.DCF file with input mime type:" + mimeType);
                        mFileType = 0; // work around: change to ZERO
                    }
                }
                /// @}

                // if mimeType was not specified, compute file type based on file extension.
                if (mFileType == 0) {
                    MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                    if (mediaFileType != null) {
                        mFileType = mediaFileType.fileType;
                        if (mMimeType == null || isValueslessMimeType(mMimeType)) {
                            mMimeType = mediaFileType.mimeType;
                        }
                    }
                }

                if (isDrmEnabled() && MediaFile.isDrmFileType(mFileType)) {
                    mFileType = getFileTypeFromDrm(path);
                }
                /// M: Add to get original mimetype from file for cta
                if (isDrmEnabled() && path.endsWith(".mudp")) {
                    if (mDrmManagerClient == null) {
                        mDrmManagerClient = new DrmManagerClient(mContext);
                    }
                    if (mDrmManagerClient.canHandle(path, null)) {
                        mMimeType = mDrmManagerClient.getOriginalMimeType(path);
                        mIsDrm = true;
                        Log.d(TAG, "get cta file " + path + " with original mimetype " + mMimeType);
                    }
                }
            }

            FileEntry entry = makeEntryFor(path);
            // add some slack to avoid a rounding error
            long delta = (entry != null) ? (lastModified - entry.mLastModified) : 0;
            boolean wasModified = delta > 1 || delta < -1;
            if (entry == null || wasModified) {
                if (wasModified) {
                    entry.mLastModified = lastModified;
                } else {
                    entry = new FileEntry(0, path, lastModified,
                            (isDirectory ? MtpConstants.FORMAT_ASSOCIATION : 0));
                }
                entry.mLastModifiedChanged = true;
            }

            if (mProcessPlaylists && MediaFile.isPlayListFileType(mFileType)) {
                mPlayLists.add(entry);
                /// M: MediaScanner Performance turning {@
                /// Store playlist path and insert them in postScanAll
                mPlaylistFilePathList.add(path);
                /// @}
                // we don't process playlists in the main scan, so return null
                return null;
            }

            // clear all the metadata
            mArtist = null;
            mAlbumArtist = null;
            mAlbum = null;
            mTitle = null;
            mComposer = null;
            mGenre = null;
            mTrack = 0;
            mYear = 0;
            mDuration = 0;
            mPath = path;
            mLastModified = lastModified;
            mWriter = null;
            mCompilation = 0;
            mWidth = 0;
            mHeight = 0;
            /// M: add for mtk drm @{
            mDrmContentDescriptioin = null;
            mDrmContentName = null;
            mDrmContentUr = null;
            mDrmContentVendor = null;
            mDrmIconUri = null;
            mDrmRightsIssuer = null;
            mDrmDataLen = -1;
            mDrmOffset = -1;
            mDrmMethod = -1;
            /// @}
            /// M: add for live photo
            mIsLivePhoto = false;
            /// M: add for slow motion
            mSlowMotionSpeed = "(0,0)x0";
            /// M: add for fancy gallery homepage
            mOrientation = 0;

            return entry;
        }

        @Override
        public void scanFile(String path, long lastModified, long fileSize,
                boolean isDirectory, boolean noMedia) {
            // This is the callback funtion from native codes.
            // Log.v(TAG, "scanFile: "+path);
            doScanFile(path, null, lastModified, fileSize, isDirectory, false, noMedia);
        }

        public Uri doScanFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean scanAlways, boolean noMedia) {
            Uri result = null;
//            long t1 = System.currentTimeMillis();
            try {
                FileEntry entry = beginFile(path, mimeType, lastModified,
                        fileSize, isDirectory, noMedia);

                // if this file was just inserted via mtp, set the rowid to zero
                // (even though it already exists in the database), to trigger
                // the correct code path for updating its entry
                if (mMtpObjectHandle != 0) {
                    entry.mRowId = 0;
                }
                // rescan for metadata if file was modified since last scan
                if (entry != null && (entry.mLastModifiedChanged || scanAlways)) {
                    if (noMedia) {
                        result = endFile(entry, false, false, false, false, false);
                    } else {
                        String lowpath = path.toLowerCase(Locale.ROOT);
                        boolean ringtones = (lowpath.indexOf(RINGTONES_DIR) > 0);
                        boolean notifications = (lowpath.indexOf(NOTIFICATIONS_DIR) > 0);
                        boolean alarms = (lowpath.indexOf(ALARMS_DIR) > 0);
                        boolean podcasts = (lowpath.indexOf(PODCAST_DIR) > 0);
                        boolean music = (lowpath.indexOf(MUSIC_DIR) > 0) ||
                            (!ringtones && !notifications && !alarms && !podcasts);
                        /* < DTS2014102001272 zhangxinming/00181848 20140926 begin */
                        ringtones = ringtones | HwThemeManager.isTRingtones(lowpath);
                        notifications = notifications | HwThemeManager.isTNotifications(lowpath);
                        alarms = alarms | HwThemeManager.isTAlarms(lowpath);
                        /*   DTS2014102001272 zhangxinming/00181848 20140926 end > */
                        boolean isaudio = MediaFile.isAudioFileType(mFileType);
                        boolean isvideo = MediaFile.isVideoFileType(mFileType);

                        /**
                         * M: because fork process can access external storage
                         * path since ALPS00453547.@{
                         */
                        // if (isaudio || isvideo || isimage) {
                        // if (mExternalIsEmulated &&
                        // path.startsWith(mExternalStoragePath)) {
                        // // try to rewrite the path to bypass the sd card fuse
                        // layer
                        // String directPath =
                        // Environment.getMediaStorageDirectory() +
                        // path.substring(mExternalStoragePath.length());
                        // File f = new File(directPath);
                        // if (f.exists()) {
                        // path = directPath;
                        // }
                        // }
                        // }
                        /** @} */

                        // we only extract metadata for audio and video files
                        if (isaudio || isvideo) {
                            processFile(path, mimeType, this);
                        }
                        /// M: process DRM image if needed @{
                        boolean isimage = MediaFile.isImageFileType(mFileType);
                        /// @}
                        if (isimage) {
                            /// M: Decodes OMA DRM 1.0 FL image's width and height. ALPS00335107
                            if (IS_SUPPORT_DRM && lowpath.endsWith(".dcf")) {
                                processDcfImageFile(path);
                            } else {
                                processImageFile(path);
                            }
                        }

                        result = endFile(entry, ringtones, notifications, alarms, music, podcasts);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            }
//            long t2 = System.currentTimeMillis();
//            Log.v(TAG, "scanFile: " + path + " took " + (t2-t1));
            return result;
        }

        private int parseSubstring(String s, int start, int defaultValue) {
            int length = s.length();
            if (start == length) return defaultValue;

            char ch = s.charAt(start++);
            // return defaultValue if we have no integer at all
            if (ch < '0' || ch > '9') return defaultValue;

            int result = ch - '0';
            while (start < length) {
                ch = s.charAt(start++);
                if (ch < '0' || ch > '9') return result;
                result = result * 10 + (ch - '0');
            }

            return result;
        }

        public void handleStringTag(String name, String value) {
            // Log.v(TAG, "handleStringTag: name=" + name + ",value=" + value);
            if (name.equalsIgnoreCase("title") || name.startsWith("title;")) {
                // Don't trim() here, to preserve the special \001 character
                // used to force sorting. The media provider will trim() before
                // inserting the title in to the database.
                mTitle = value;
            } else if (name.equalsIgnoreCase("artist") || name.startsWith("artist;")) {
                mArtist = value.trim();
            } else if (name.equalsIgnoreCase("albumartist") || name.startsWith("albumartist;")
                    || name.equalsIgnoreCase("band") || name.startsWith("band;")) {
                mAlbumArtist = value.trim();
            } else if (name.equalsIgnoreCase("album") || name.startsWith("album;")) {
                mAlbum = value.trim();
            } else if (name.equalsIgnoreCase("composer") || name.startsWith("composer;")) {
                mComposer = value.trim();
            } else if (mProcessGenres &&
                    (name.equalsIgnoreCase("genre") || name.startsWith("genre;"))) {
                mGenre = getGenreName(value);
            } else if (name.equalsIgnoreCase("year") || name.startsWith("year;")) {
                mYear = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("tracknumber") || name.startsWith("tracknumber;")) {
                // track number might be of the form "2/12"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (mTrack / 1000) * 1000 + num;
            } else if (name.equalsIgnoreCase("discnumber") ||
                    name.equals("set") || name.startsWith("set;")) {
                // set number might be of the form "1/3"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (num * 1000) + (mTrack % 1000);
            } else if (name.equalsIgnoreCase("duration")) {
                mDuration = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("writer") || name.startsWith("writer;")) {
                mWriter = value.trim();
            } else if (name.equalsIgnoreCase("compilation")) {
                mCompilation = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("isdrm")) {
                mIsDrm = (parseSubstring(value, 0, 0) == 1);
            } else if (name.equalsIgnoreCase("width")) {
                mWidth = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("height")) {
                mHeight = parseSubstring(value, 0, 0);
            /// M: add for MTK added feature. {@
            /// 1. DRM
            } else if (name.equalsIgnoreCase("drm_content_uri")) {
                mDrmContentUr = value.trim();
            } else if (name.equalsIgnoreCase("drm_offset")) {
                mDrmOffset = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("drm_dataLen")) {
                mDrmDataLen = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("drm_rights_issuer")) {
                mDrmRightsIssuer = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_name")) {
                mDrmContentName = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_description")) {
                mDrmContentDescriptioin = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_vendor")) {
                mDrmContentVendor = value.trim();
            } else if (name.equalsIgnoreCase("drm_icon_uri")) {
                mDrmIconUri = value.trim();
            } else if (name.equalsIgnoreCase("drm_method")) {
                mDrmMethod = parseSubstring(value, 0, 0);
            /// 2. live photo
            } else if (name.equalsIgnoreCase("is_live_photo")) {
                mIsLivePhoto = (parseSubstring(value, 0, 0) == 1);
            /// 3. slow motion
            } else if (name.equalsIgnoreCase("SlowMotion_Speed_Value")) {
                mSlowMotionSpeed = "(0,0)x" + value;
            /// 4. fancy gallery homepage
            } else if (name.equalsIgnoreCase("rotation")) {
                mOrientation = parseSubstring(value, 0, 0);
            } else {
                //Log.v(TAG, "unknown tag: " + name + " (" + mProcessGenres + ")");
            }
            /// @}
        }

        private boolean convertGenreCode(String input, String expected) {
            String output = getGenreName(input);
            if (output.equals(expected)) {
                return true;
            } else {
                Log.d(TAG, "'" + input + "' -> '" + output + "', expected '" + expected + "'");
                return false;
            }
        }

        private void testGenreNameConverter() {
            convertGenreCode("2", "Country");
            convertGenreCode("(2)", "Country");
            convertGenreCode("(2", "(2");
            convertGenreCode("2 Foo", "Country");
            convertGenreCode("(2) Foo", "Country");
            convertGenreCode("(2 Foo", "(2 Foo");
            convertGenreCode("2Foo", "2Foo");
            convertGenreCode("(2)Foo", "Country");
            convertGenreCode("200 Foo", "Foo");
            convertGenreCode("(200) Foo", "Foo");
            convertGenreCode("200Foo", "200Foo");
            convertGenreCode("(200)Foo", "Foo");
            convertGenreCode("200)Foo", "200)Foo");
            convertGenreCode("200) Foo", "200) Foo");
        }

        public String getGenreName(String genreTagValue) {

            if (genreTagValue == null) {
                Log.e(TAG, "getGenreName: Null genreTag!");
                return null;
            }
            final int length = genreTagValue.length();

            if (length > 0) {
                boolean parenthesized = false;
                StringBuffer number = new StringBuffer();
                int i = 0;
                for (; i < length; ++i) {
                    char c = genreTagValue.charAt(i);
                    if (i == 0 && c == '(') {
                        parenthesized = true;
                    } else if (Character.isDigit(c)) {
                        number.append(c);
                    } else {
                        break;
                    }
                }
                char charAfterNumber = i < length ? genreTagValue.charAt(i) : ' ';
                if ((parenthesized && charAfterNumber == ')')
                        || !parenthesized && Character.isWhitespace(charAfterNumber)) {
                    try {
                        short genreIndex = Short.parseShort(number.toString());
                        if (genreIndex >= 0) {
                            if (genreIndex < ID3_GENRES.length && ID3_GENRES[genreIndex] != null) {
                                return ID3_GENRES[genreIndex];
                            } else if (genreIndex == 0xFF) {
                                Log.e(TAG, "getGenreName: genreIndex = 0xFF!");
                                return null;
                            } else if (genreIndex < 0xFF && (i + 1) < length) {
                                // genre is valid but unknown,
                                // if there is a string after the value we take it
                                if (parenthesized && charAfterNumber == ')') {
                                    i++;
                                }
                                String ret = genreTagValue.substring(i).trim();
                                if (ret.length() != 0) {
                                    return ret;
                                }
                            } else {
                                // else return the number, without parentheses
                                return number.toString();
                            }
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "getGenreName: invalidNum=" + number.toString(), e);
                    }
                }
            }

            return genreTagValue;
        }

        private void processImageFile(String path) {
            /// M: If decode bmp and gif over limit size, just return.
            long limitFileSize = Long.MAX_VALUE;
            if (MediaFile.FILE_TYPE_BMP == mFileType) {
                limitFileSize = mLimitBmpFileSize;
            } else if (MediaFile.FILE_TYPE_GIF == mFileType) {
                limitFileSize = mLimitGifFileSize;
            }
            if (mFileSize > limitFileSize) {
                Log.w(TAG, "processImageFile " + path + " over limit size " + limitFileSize);
                return;
            }
            /// @}
            try {
                mBitmapOptions.outWidth = 0;
                mBitmapOptions.outHeight = 0;
                /*<DTS2014101007092 zhuxuefu/00290029 20141011 begin*/
                //DTS2014101007092 decoupling
                if (HwFrameworkFactory.getHwMediaScannerManager()
                        .isBitmapSizeTooLarge(path)) {
                    mWidth = -1;
                    mHeight = -1;
                } else {
                	BitmapFactory.decodeFile(path, mBitmapOptions);
                	mWidth = mBitmapOptions.outWidth;
                	mHeight = mBitmapOptions.outHeight;
				}
				 /*<DTS2014101007092 zhuxuefu/00290029 20141011 end > */
            } catch (Throwable th) {
                Log.e(TAG, "processImageFile: path=" + path, th);
            }
            Log.v(TAG, "processImageFile: path = " + path + ", width = " + mWidth + ", height = "
                    + mHeight + ", limitFileSize = " + limitFileSize);
        }

        /// M: Decodes OMA DRM 1.0 FL image's width and height. ALPS00335107 @{
        private DcfDecoder mDcfDecoder;
        private void processDcfImageFile(String path) {
            try {
                mBitmapOptions.outWidth = 0;
                mBitmapOptions.outHeight = 0;
                if (mDcfDecoder == null) {
                    mDcfDecoder = new DcfDecoder();
                }
                mDcfDecoder.decodeFile(path, mBitmapOptions, false);
                mWidth = mBitmapOptions.outWidth;
                mHeight = mBitmapOptions.outHeight;
                Log.v(TAG, "processDcfImageFile: path=" + path + ",width=" + mWidth + ",height=" + mHeight);
            } catch (Throwable th) {
                Log.e(TAG, "processDcfImageFile: Error! path=" + path, th);
            }
        }
        /// M: @}

        public void setMimeType(String mimeType) {
            if ("audio/mp4".equals(mMimeType) &&
                    mimeType.startsWith("video")) {
                // for feature parity with Donut, we force m4a files to keep the
                // audio/mp4 mimetype, even if they are really "enhanced podcasts"
                // with a video track
                return;
            }
            mMimeType = mimeType;
            mFileType = MediaFile.getFileTypeForMimeType(mimeType);
            Log.v(TAG, "setMimeType: mMimeType = " + mMimeType);
        }

        /**
         * Formats the data into a values array suitable for use with the Media
         * Content Provider.
         *
         * @return a map of values
         */
        private ContentValues toValues() {
            ContentValues map = new ContentValues();

            map.put(MediaStore.MediaColumns.DATA, mPath);
            map.put(MediaStore.MediaColumns.TITLE, mTitle);
            map.put(MediaStore.MediaColumns.DATE_MODIFIED, mLastModified);
            map.put(MediaStore.MediaColumns.SIZE, mFileSize);
            map.put(MediaStore.MediaColumns.MIME_TYPE, mMimeType);
            map.put(MediaStore.MediaColumns.IS_DRM, mIsDrm);

            String resolution = null;
            if (mWidth > 0 && mHeight > 0) {
                map.put(MediaStore.MediaColumns.WIDTH, mWidth);
                map.put(MediaStore.MediaColumns.HEIGHT, mHeight);
                resolution = mWidth + "x" + mHeight;
            }

            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    map.put(Video.Media.ARTIST, (mArtist != null && mArtist.length() > 0
                            ? mArtist : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0
                            ? mAlbum : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.DURATION, mDuration);
                    if (resolution != null) {
                        map.put(Video.Media.RESOLUTION, resolution);
                    }
                    /// M: Add for live photo
                    map.put(Video.Media.IS_LIVE_PHOTO, mIsLivePhoto);
                    /// M: Add for slow motion
                    map.put(Video.Media.SLOW_MOTION_SPEED, mSlowMotionSpeed);
                    /// M: Add for fancy gallery homepage
                    map.put(Video.Media.ORIENTATION, mOrientation);
                } else if (MediaFile.isImageFileType(mFileType)) {
                    // FIXME - add DESCRIPTION
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    map.put(Audio.Media.ARTIST, (mArtist != null && mArtist.length() > 0) ?
                            mArtist : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.ALBUM_ARTIST, (mAlbumArtist != null &&
                            mAlbumArtist.length() > 0) ? mAlbumArtist : null);
                    map.put(Audio.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0) ?
                            mAlbum : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.COMPOSER, mComposer);
                    map.put(Audio.Media.GENRE, mGenre);
                    if (mYear != 0) {
                        map.put(Audio.Media.YEAR, mYear);
                    }
                    map.put(Audio.Media.TRACK, mTrack);
                    map.put(Audio.Media.DURATION, mDuration);
                    map.put(Audio.Media.COMPILATION, mCompilation);
                }
            }

            /// M: drm media file, add new column values.
            if (mIsDrm) {
                map.put(MediaStore.MediaColumns.DRM_CONTENT_DESCRIPTION, mDrmContentDescriptioin);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_NAME, mDrmContentName);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_URI, mDrmContentUr);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_VENDOR, mDrmContentVendor);
                map.put(MediaStore.MediaColumns.DRM_DATA_LEN, mDrmDataLen);
                map.put(MediaStore.MediaColumns.DRM_ICON_URI, mDrmIconUri);
                map.put(MediaStore.MediaColumns.DRM_OFFSET, mDrmOffset);
                map.put(MediaStore.MediaColumns.DRM_RIGHTS_ISSUER, mDrmRightsIssuer);
                map.put(MediaStore.MediaColumns.DRM_METHOD, mDrmMethod);
            }
            /// @}
            return map;
        }

        private Uri endFile(FileEntry entry, boolean ringtones, boolean notifications,
                boolean alarms, boolean music, boolean podcasts)
                throws RemoteException {
            // update database
            boolean isSim2Ringtone = false; //wuhuihui add for multisim ringtone

            // use album artist if artist is missing
            if (mArtist == null || mArtist.length() == 0) {
                mArtist = mAlbumArtist;
            }

            ContentValues values = toValues();
            String title = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (title == null || TextUtils.isEmpty(title.trim())) {
                title = MediaFile.getFileTitle(values.getAsString(MediaStore.MediaColumns.DATA));
                values.put(MediaStore.MediaColumns.TITLE, title);
            }
            String album = values.getAsString(Audio.Media.ALBUM);
            if (MediaStore.UNKNOWN_STRING.equals(album)) {
                album = values.getAsString(MediaStore.MediaColumns.DATA);
                // extract last path segment before file name
                int lastSlash = album.lastIndexOf('/');
                if (lastSlash >= 0) {
                    int previousSlash = 0;
                    while (true) {
                        int idx = album.indexOf('/', previousSlash + 1);
                        if (idx < 0 || idx >= lastSlash) {
                            break;
                        }
                        previousSlash = idx;
                    }
                    if (previousSlash != 0) {
                        album = album.substring(previousSlash + 1, lastSlash);
                        values.put(Audio.Media.ALBUM, album);
                    }
                }
            }
            long rowId = entry.mRowId;
            if (MediaFile.isAudioFileType(mFileType) && (rowId == 0 || mMtpObjectHandle != 0)) {
                // Only set these for new entries. For existing entries, they
                // may have been modified later, and we want to keep the current
                // values so that custom ringtones still show up in the ringtone
                // picker.
                values.put(Audio.Media.IS_RINGTONE, ringtones);
                values.put(Audio.Media.IS_NOTIFICATION, notifications);
                values.put(Audio.Media.IS_ALARM, alarms);
                values.put(Audio.Media.IS_MUSIC, music);
                values.put(Audio.Media.IS_PODCAST, podcasts);
            /// M: MAV type MPO file need parse some info from exif
            } else if ((mFileType == MediaFile.FILE_TYPE_JPEG || mFileType == MediaFile.FILE_TYPE_MPO) && !mNoMedia) {
                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(entry.mPath);
                } catch (IOException ex) {
                    // exif is null
                    Log.e(TAG, "endFile: Null ExifInterface!", ex);
                }
                if (exif != null) {
                    float[] latlng = new float[2];
                    if (exif.getLatLong(latlng)) {
                        values.put(Images.Media.LATITUDE, latlng[0]);
                        values.put(Images.Media.LONGITUDE, latlng[1]);
                    }

                    long time = exif.getGpsDateTime();
                    if (time != -1) {
                        values.put(Images.Media.DATE_TAKEN, time);
                    } else {
                        // If no time zone information is available, we should consider using
                        // EXIF local time as taken time if the difference between file time
                        // and EXIF local time is not less than 1 Day, otherwise MediaProvider
                        // will use file time as taken time.
                        time = exif.getDateTime();
                        if (time != -1 && Math.abs(mLastModified * 1000 - time) >= 86400000) {
                            values.put(Images.Media.DATE_TAKEN, time);
                        }
                    }

                    int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, -1);
                    if (orientation != -1) {
                        // We only recognize a subset of orientation tag values.
                        int degree;
                        switch(orientation) {
                            case ExifInterface.ORIENTATION_ROTATE_90:
                                degree = 90;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_180:
                                degree = 180;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_270:
                                degree = 270;
                                break;
                            default:
                                degree = 0;
                                break;
                        }
                        values.put(Images.Media.ORIENTATION, degree);
                    }

                    /// M: Gets groupId and groupIndex for continuous shots images,
                    /// gets focus value for best shots images. @{
                    long groupId = 0;
                    int groupIndex = 0;
                    long focusValueHigh = 0;
                    long focusValueLow = 0;
                    try {
                        InputStream in = new FileInputStream(entry.mPath);
                        ConshotInfo conshotInfo = ConshotExif.getConshotInfo(in);
                        groupId = conshotInfo.groupId;
                        groupIndex = conshotInfo.groupIndex;
                        focusValueHigh = conshotInfo.focusValueHigh;
                        focusValueLow = conshotInfo.focusValueLow;
                    } catch (IOException e) {
                        Log.w(TAG, "Invalid image.", e);
                    } finally {
                        values.put(Images.Media.FOCUS_VALUE_HIGH, focusValueHigh);
                        values.put(Images.Media.FOCUS_VALUE_LOW, focusValueLow);
                        values.put(Images.Media.GROUP_ID, groupId);
                        values.put(Images.Media.GROUP_INDEX, groupIndex);
                        int refocus = isStereoPhoto(entry.mPath) ? 1 : 0;
                        Log.d(TAG, "endFile() refocus = " + refocus);
                        values.put(Images.Media.CAMERA_REFOCUS, refocus);
                    }
                    /// @}
                    /*< DTS2015060215541 lichen/00348693 20150602 begin */
                    values.put(Images.Media.IS_HDR,
                            "hdr".equals(exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)));
                    /*  DTS2015060215541 lichen/00348693 20150602 end >*/
                    ///@}
                }
                /*<DTS2015060215541 lichen/00348693 20150602 begin*/
                //DTS2014101007092 decoupling
                HwFrameworkFactory.getHwMediaScannerManager()
                                           .initializeHwVoiceAndFocus(entry.mPath, values);
                /*<DTS2015060215541 lichen/00348693 20150602 end > */
            }

            Uri tableUri = mFilesUri;
            MediaInserter inserter = mMediaInserter;
            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    tableUri = mVideoUri;
                } else if (MediaFile.isImageFileType(mFileType)) {
                    tableUri = mImagesUri;
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    tableUri = mAudioUri;
                }
            }
            Uri result = null;
            boolean needToSetSettings = false;
            if (rowId == 0) {
                if (mMtpObjectHandle != 0) {
                    values.put(MediaStore.MediaColumns.MEDIA_SCANNER_NEW_OBJECT_ID, mMtpObjectHandle);
                }
                if (tableUri == mFilesUri) {
                    int format = entry.mFormat;
                    if (format == 0) {
                        format = MediaFile.getFormatCode(entry.mPath, mMimeType);
                    }
                    values.put(Files.FileColumns.FORMAT, format);
                }
                // Setting a flag in order not to use bulk insert for the file related with
                // notifications, ringtones, and alarms, because the rowId of the inserted file is
                // needed.

                /// M: Set notification, ringtone and alarm if it wasn't set before. @{
                if (notifications && ((mWasEmptyPriorToScan && !mDefaultNotificationSet) ||
                        doesSettingEmpty(NOTIFICATION_SET))) {
                    if (TextUtils.isEmpty(mDefaultNotificationFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultNotificationFilename)) {
                        needToSetSettings = true;
                        /// M: Adds log to debug setting ringtones.
                        Log.v(TAG, "endFile: needToSetNotification=true.");
                    }
				//HQ_wuhuihui_20150609 modified for multisim ringtone start
                } else if (ringtones && ((mWasEmptyPriorToScan && (!mDefaultRingtoneSet||!mDefaultRingtoneSim2Set)) ||
                        doesSettingEmpty(RINGTONE_SET)||doesSettingEmpty(RINGTONE_SIM2_SET))) {
                        if (TextUtils.isEmpty(mDefaultRingtoneFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultRingtoneFilename)) {
                            needToSetSettings = true;
                            /// M: Adds log to debug setting ringtones.
                            Log.v(TAG, "endFile: needToSetRingtone=true.");
                        } else if (TextUtils.isEmpty(mDefaultRingtoneSim2Filename) ||
                              doesPathHaveFilename(entry.mPath, mDefaultRingtoneSim2Filename))
                        {
                            needToSetSettings = true;
                            isSim2Ringtone = true;
                            Log.v(TAG, "endFile: needToSetRingtone=true.sim2 default ringtone");
                        }
                 //HQ_wuhuihui_20150609 modified for multisim ringtone end      
                } else if (alarms && ((mWasEmptyPriorToScan && !mDefaultAlarmSet) ||
                        doesSettingEmpty(ALARM_SET))) {
                    if (TextUtils.isEmpty(mDefaultAlarmAlertFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultAlarmAlertFilename)) {
                        needToSetSettings = true;
                        /// M: Adds log to debug setting ringtones.
                        Log.v(TAG, "endFile: needToSetAlarm=true.");
                    }
                }
                /// @}

                // New file, insert it.
                // Directories need to be inserted before the files they contain, so they
                // get priority when bulk inserting.
                // If the rowId of the inserted file is needed, it gets inserted immediately,
                // bypassing the bulk inserter.
                if (inserter == null || needToSetSettings) {
                    if (inserter != null) {
                        inserter.flushAll();
                    }
                    result = mMediaProvider.insert(mPackageName, tableUri, values);
                } else if (entry.mFormat == MtpConstants.FORMAT_ASSOCIATION) {
                    inserter.insertwithPriority(tableUri, values);
                } else {
                    inserter.insert(tableUri, values);
                }

                if (result != null) {
                    rowId = ContentUris.parseId(result);
                    entry.mRowId = rowId;
                }
            } else {
                // updated file
                result = ContentUris.withAppendedId(tableUri, rowId);
                // path should never change, and we want to avoid replacing mixed cased paths
                // with squashed lower case paths
                values.remove(MediaStore.MediaColumns.DATA);

                int mediaType = 0;
                if (!MediaScanner.isNoMediaPath(entry.mPath)) {
                    int fileType = MediaFile.getFileTypeForMimeType(mMimeType);
                    if (MediaFile.isAudioFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                    } else if (MediaFile.isVideoFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                    } else if (MediaFile.isImageFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_IMAGE;
                    } else if (MediaFile.isPlayListFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_PLAYLIST;
                    }
                    values.put(FileColumns.MEDIA_TYPE, mediaType);
                }

                mMediaProvider.update(mPackageName, result, values, null, null);
            }
            ///M: Set ringtone if it wasn't set before. @{
            if(needToSetSettings) {
                if (notifications && doesSettingEmpty(NOTIFICATION_SET)) {
                    setSettingIfNotSet(Settings.System.NOTIFICATION_SOUND, tableUri, rowId);
                    setProfileSettings(RingtoneManager.TYPE_NOTIFICATION, tableUri, rowId);
                    mDefaultNotificationSet = true;
                    setSettingFlag(NOTIFICATION_SET);
                    Log.v(TAG, "endFile: set notification. uri=" + tableUri + ", rowId=" + rowId);
                } else if (ringtones && (doesSettingEmpty(RINGTONE_SET) || doesSettingEmpty(RINGTONE_SIM2_SET))) {
                    if (!isSim2Ringtone) {
						setSettingIfNotSet(Settings.System.RINGTONE, tableUri, rowId);
						setSettingIfNotSet(Settings.System.VIDEO_CALL, tableUri, rowId);
						setSettingIfNotSet(Settings.System.SIP_CALL, tableUri, rowId);
						setProfileSettings(RingtoneManager.TYPE_RINGTONE, tableUri, rowId);
						setProfileSettings(RingtoneManager.TYPE_VIDEO_CALL, tableUri, rowId);
						setProfileSettings(RingtoneManager.TYPE_SIP_CALL, tableUri, rowId);
						mDefaultRingtoneSet = true;
						setSettingFlag(RINGTONE_SET);
						Log.v(TAG, "endFile: set ringtone. uri=" + tableUri + ", rowId=" + rowId);

					} else {
						 setSettingIfNotSet(Settings.System.RINGTONE_SIM2, tableUri, rowId);
						 setProfileSettings(RingtoneManager.TYPE_RINGTONE_SIM2, tableUri, rowId);
						 mDefaultRingtoneSim2Set = true;
						 setSettingFlag(RINGTONE_SIM2_SET);
						 Log.v(TAG, "endFile: set sim2 ringtone. uri=" + tableUri + ", rowId=" + rowId);
					}

                } else if (alarms && doesSettingEmpty(ALARM_SET)) {
                    setSettingIfNotSet(Settings.System.ALARM_ALERT, tableUri, rowId);
                    setProfileSettings(RingtoneManager.TYPE_ALARM, tableUri, rowId);
                    mDefaultAlarmSet = true;
                    setSettingFlag(ALARM_SET);
                }
            }
            /// @}
            return result;
        }

        private boolean doesPathHaveFilename(String path, String filename) {
            int pathFilenameStart = path.lastIndexOf(File.separatorChar) + 1;
            int filenameLength = filename.length();
            return path.regionMatches(pathFilenameStart, filename, 0, filenameLength) &&
                    pathFilenameStart + filenameLength == path.length();
        }

        /// M: Modify for ringtone list is empty @{
        private boolean doesSettingEmpty(String settingName) {
            String existingSettingValue = Settings.System.getString(mContext.getContentResolver(), settingName);
            if (TextUtils.isEmpty(existingSettingValue)) {
                return true;
            }
            return false;
        }

        private void setSettingFlag(String settingName) {
            final String VALUE = "yes";
            Log.d(TAG, "setSettingFlag set:" + settingName);
            Settings.System.putString(mContext.getContentResolver(), settingName, VALUE);
        }
        /// @}

        private void setSettingIfNotSet(String settingName, Uri uri, long rowId) {

            String existingSettingValue = Settings.System.getString(mContext.getContentResolver(),
                    settingName);

            if (TextUtils.isEmpty(existingSettingValue)) {
                // Set the setting to the given URI
                Settings.System.putString(mContext.getContentResolver(), settingName,
                        ContentUris.withAppendedId(uri, rowId).toString());
                /// M: Adds log to debug setting ringtones.
                Log.v(TAG, "setSettingIfNotSet: name=" + settingName + ",value=" + rowId);
            } else {
                /// M: Adds log to debug setting ringtones.
                Log.e(TAG, "setSettingIfNotSet: name=" + settingName + " with value=" + existingSettingValue);
            }
        }

        private void setProfileSettings(int type, Uri uri, long rowId) {
            if (type == RingtoneManager.TYPE_NOTIFICATION) {
                setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_NOTIFICATION, uri, rowId);
            } else if (type == RingtoneManager.TYPE_RINGTONE) {
                setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_RINGTONE, uri, rowId);
            } else if (type ==RingtoneManager.TYPE_RINGTONE_SIM2) {//add by wuhuihui for multisim ringtone start
				setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_RINGTONE_SIM2, uri, rowId);
				//add by wuhuihui for multisim ringtone end
			} else if (type == RingtoneManager.TYPE_VIDEO_CALL) {
                setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_VIDEO_CALL, uri, rowId);
            } else if (type == RingtoneManager.TYPE_SIP_CALL) {
                setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_SIP_CALL, uri, rowId);
            } else if (type == RingtoneManager.TYPE_ALARM) {
                setSettingIfNotSet(RingtoneManager.KEY_DEFAULT_ALARM, uri, rowId);
            }
        }

        private int getFileTypeFromDrm(String path) {
            if (!isDrmEnabled()) {
                return 0;
            }

            int resultFileType = 0;

            if (mDrmManagerClient == null) {
                mDrmManagerClient = new DrmManagerClient(mContext);
            }

            if (mDrmManagerClient.canHandle(path, null)) {
                mIsDrm = true;
                String drmMimetype = mDrmManagerClient.getOriginalMimeType(path);
                if (drmMimetype != null) {
                    mMimeType = drmMimetype;
                    resultFileType = MediaFile.getFileTypeForMimeType(drmMimetype);
                }
            }
            return resultFileType;
        }

    }; // end of anonymous MediaScannerClient instance

    private void prescan(String filePath, boolean prescanFiles) throws RemoteException {
        /// M: Adds log for debug.
        Log.v(TAG, "prescan>>> filePath=" + filePath + ",prescanFiles=" + prescanFiles);
        Cursor c = null;
        String where = null;
        String[] selectionArgs = null;

        if (mPlayLists == null) {
            mPlayLists = new ArrayList<FileEntry>();
        } else {
            mPlayLists.clear();
        }

        if (filePath != null) {
            // query for only one file
            where = MediaStore.Files.FileColumns._ID + ">?" +
                " AND " + Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { "", filePath };
        } else {
            where = MediaStore.Files.FileColumns._ID + ">?";
            selectionArgs = new String[] { "" };
        }

        // Tell the provider to not delete the file.
        // If the file is truly gone the delete is unnecessary, and we want to avoid
        // accidentally deleting files that are really there (this may happen if the
        // filesystem is mounted and unmounted while the scanner is running).
        Uri.Builder builder = mFilesUri.buildUpon();
        builder.appendQueryParameter(MediaStore.PARAM_DELETE_DATA, "false");
        MediaBulkDeleter deleter = new MediaBulkDeleter(mMediaProvider, mPackageName,
                builder.build());

        /// M: Add to debug how many file scanned
        int audioCount = 0;
        long lastId = Long.MIN_VALUE;

        // Build the list of files from the content provider
        try {
            if (prescanFiles) {
                // First read existing files from the files table.
                // Because we'll be deleting entries for missing files as we go,
                // we need to query the database in small batches, to avoid problems
                // with CursorWindow positioning.
                Uri limitUri = mFilesUri.buildUpon().appendQueryParameter("limit", "1000").build();
                mWasEmptyPriorToScan = true;

                while (true) {
                    selectionArgs[0] = "" + lastId;
                    if (c != null) {
                        c.close();
                        c = null;
                    }
                    c = mMediaProvider.query(mPackageName, limitUri, FILES_PRESCAN_PROJECTION,
                            where, selectionArgs, MediaStore.Files.FileColumns._ID, null);
                    if (c == null) {
                        break;
                    }

                    int num = c.getCount();

                    if (num == 0) {
                        break;
                    }
                    mWasEmptyPriorToScan = false;
                    while (c.moveToNext()) {
                        long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                        String path = c.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
                        int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                        long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                        lastId = rowId;

                        // Only consider entries with absolute path names.
                        // This allows storing URIs in the database without the
                        // media scanner removing them.
                        if (path != null && path.startsWith("/")) {
                            boolean exists = false;
                            try {
                                exists = Os.access(path, android.system.OsConstants.F_OK);
                            } catch (ErrnoException e1) {
                                /// M: Adds log for debug.
                                Log.e(TAG, "prescan: ErrnoException! path=" + path);
                            }
                            if (!exists && !MtpConstants.isAbstractObject(format)) {
                                // do not delete missing playlists, since they may have been
                                // modified by the user.
                                // The user can delete them in the media player instead.
                                // instead, clear the path and lastModified fields in the row
                                MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                                int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

                                if (!MediaFile.isPlayListFileType(fileType)) {
                                    deleter.delete(rowId);
                                    if (path.toLowerCase(Locale.US).endsWith("/.nomedia")) {
                                        deleter.flush();
                                        String parent = new File(path).getParent();
                                        mMediaProvider.call(mPackageName, MediaStore.UNHIDE_CALL,
                                                parent, null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        finally {
            if (c != null) {
                c.close();
            }
            deleter.flush();
        }

        try {
            // compute original size of images
            mOriginalCount = 0;
            c = mMediaProvider.query(mPackageName, mImagesUri, ID_PROJECTION, null, null, null, null);
            if (c != null) {
                mOriginalCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
            mOriginalVideoCount = 0;
            c = mMediaProvider.query(mPackageName, mVideoUri, ID_PROJECTION, null, null, null, null);
            if (c != null) {
                mOriginalVideoCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
            /// M: log audio count in device
            c = mMediaProvider.query(mPackageName, mAudioUri, ID_PROJECTION, null, null, null, null);
            if (c != null) {
                audioCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
        /// M: Make sure cursor to be closed. @{
        } finally {
            if (null != c) {
                c.close();
            }
        /// @}
        }

        /// M: Adds log to debug setting ringtones and how many file scanned.
        Log.v(TAG, "prescan<<< imageCount=" + mOriginalCount + ",videoCount=" + mOriginalVideoCount
                + ", audioCount=" + audioCount + ", lastId=" + lastId + ",isEmpty=" + mWasEmptyPriorToScan);
    }

    private boolean inScanDirectory(String path, String[] directories) {
        for (int i = 0; i < directories.length; i++) {
            String directory = directories[i];
            if (path.startsWith(directory)) {
                return true;
            }
        }
        return false;
    }

    private void pruneDeadThumbnailFiles() {
        Log.v(TAG, "pruneDeadThumbnailFiles>>>");
        HashSet<String> existingFiles = new HashSet<String>();
        String directory = Environment.getExternalStorageDirectory().getPath() + "/"
                + MiniThumbFile.getMiniThumbFileDirectoryPath();
        String [] files = (new File(directory)).list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            String fullPathString = directory + "/" + files[i];
            existingFiles.add(fullPathString);
        }

        int imageThumbCount = 0;
        int videoThumbCount = 0;
        Cursor c = null;
        try {
            /// M: remove useful image thumbnail files
            c = mMediaProvider.query(
                    mPackageName,
                    mThumbsUri,
                    new String [] { "_data" },
                    null,
                    null,
                    null, null);
            if (c != null && c.moveToFirst()) {
                imageThumbCount = c.getCount();
                do {
                    String fullPathString = c.getString(0);
                    existingFiles.remove(fullPathString);
                } while (c.moveToNext());
            }
            /// M: close image thumb query cursor
            if (c != null) {
                c.close();
                c = null;
            }
            /// M: remove useful video thumbnail files
            c = mMediaProvider.query(
                    mPackageName,
                    mVideoThumbsUri,
                    new String [] { "_data" },
                    null,
                    null,
                    null, null);
            if (c != null && c.moveToFirst()) {
                videoThumbCount = c.getCount();
                do {
                    String fullPathString = c.getString(0);
                    existingFiles.remove(fullPathString);
                } while (c.moveToNext());
            }
            if (c != null) {
                c.close();
                c = null;
            }
            /// M: If exist image or video thumbnail, remove MiniThumbFiles
            if (imageThumbCount > 0 || videoThumbCount > 0) {
                String miniThumbFilePath = directory + "/" + MiniThumbFile.getMiniThumbFilePrefix();
                Iterator<String> iterator = existingFiles.iterator();
                while (iterator.hasNext()) {
                    String path = iterator.next();
                    if (path.startsWith(miniThumbFilePath)) {
                        iterator.remove();
                    }
                }
            }

            for (String fileToDelete : existingFiles) {
                Log.v(TAG, "delete dead thumbnail file " + fileToDelete);
                try {
                    (new File(fileToDelete)).delete();
                } catch (SecurityException ex) {
                    /// M: Adds log for debug.
                    Log.e(TAG, "pruneDeadThumbnailFiles: path=" + fileToDelete, ex);
                }
            }
        } catch (RemoteException e) {
            // We will soon be killed...
            Log.e(TAG, "pruneDeadThumbnailFiles: RemoteException!", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        Log.v(TAG, "pruneDeadThumbnailFiles<<< for " + directory);
    }

    static class MediaBulkDeleter {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>(100);
        final IContentProvider mProvider;
        final String mPackageName;
        final Uri mBaseUri;

        public MediaBulkDeleter(IContentProvider provider, String packageName, Uri baseUri) {
            mProvider = provider;
            mPackageName = packageName;
            mBaseUri = baseUri;
        }

        public void delete(long id) throws RemoteException {
            if (whereClause.length() != 0) {
                whereClause.append(",");
            }
            whereClause.append("?");
            whereArgs.add("" + id);
            if (whereArgs.size() > 100) {
                flush();
            }
        }

        public void flush() throws RemoteException {
            int size = whereArgs.size();
            if (size > 0) {
                String [] foo = new String [size];
                foo = whereArgs.toArray(foo);
                int numrows = mProvider.delete(mPackageName, mBaseUri,
                        MediaStore.MediaColumns._ID + " IN (" +
                        whereClause.toString() + ")", foo);
                //Log.i("@@@@@@@@@", "rows deleted: " + numrows);
                whereClause.setLength(0);
                whereArgs.clear();
            }
        }
    }

    private void postscan(String[] directories) throws RemoteException {

        // handle playlists last, after we know what media files are on the storage.
        if (mProcessPlaylists) {
            processPlayLists();
        }

        if ((mOriginalCount == 0 || mOriginalVideoCount == 0)
                && mImagesUri.equals(Images.Media.getContentUri("external"))) {
            pruneDeadThumbnailFiles();
        }

        // allow GC to clean up
        mPlayLists = null;
        mMediaProvider = null;
    }

    private void releaseResources() {
        // release the DrmManagerClient resources
        if (mDrmManagerClient != null) {
            mDrmManagerClient.release();
            mDrmManagerClient = null;
        }
    }

    private void initialize(String volumeName) {
        mMediaProvider = mContext.getContentResolver().acquireProvider("media");

        mAudioUri = Audio.Media.getContentUri(volumeName);
        mVideoUri = Video.Media.getContentUri(volumeName);
        mImagesUri = Images.Media.getContentUri(volumeName);
        mThumbsUri = Images.Thumbnails.getContentUri(volumeName);
        mVideoThumbsUri = Video.Thumbnails.getContentUri(volumeName);
        mFilesUri = Files.getContentUri(volumeName);
        mFilesUriNoNotify = mFilesUri.buildUpon().appendQueryParameter("nonotify", "1").build();

        if (!volumeName.equals("internal")) {
            // we only support playlists on external media
            mProcessPlaylists = true;
            mProcessGenres = true;
            mPlaylistsUri = Playlists.getContentUri(volumeName);

            mCaseInsensitivePaths = true;
        }
    }

    public void scanDirectories(String[] directories, String volumeName) {
        try {
            long start = System.currentTimeMillis();
            initialize(volumeName);
            prescan(null, true);
            long prescan = System.currentTimeMillis();

            if (ENABLE_BULK_INSERTS) {
                // create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(mMediaProvider, mPackageName, 500);
            }

            for (int i = 0; i < directories.length; i++) {
                processDirectory(directories[i], mClient);
            }

            if (ENABLE_BULK_INSERTS) {
                // flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }

            long scan = System.currentTimeMillis();
            postscan(directories);
            long end = System.currentTimeMillis();

            if (true) {
                Log.d(TAG, " prescan time: " + (prescan - start) + "ms\n");
                Log.d(TAG, "    scan time: " + (scan - prescan) + "ms\n");
                Log.d(TAG, "postscan time: " + (end - scan) + "ms\n");
                Log.d(TAG, "   total time: " + (end - start) + "ms\n");
            }
        } catch (SQLException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        } finally {
            releaseResources();
        }
    }

    // this function is used to scan a single file
    public Uri scanSingleFile(String path, String volumeName, String mimeType) {
        try {
            initialize(volumeName);
            prescan(path, true);

            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "scanSingleFile: Not exist path=" + path);
                return null;
            }

            // lastModified is in milliseconds on Files.
            long lastModifiedSeconds = file.lastModified() / 1000;

            // always scan the file, so we can return the content://media Uri for existing files
            return mClient.doScanFile(path, mimeType, lastModifiedSeconds, file.length(),
                    file.isDirectory(), true, isNoMediaPath(path));
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            return null;
        } finally {
            releaseResources();
        }
    }

    private static boolean isNoMediaFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) return false;

        // special case certain file names
        // I use regionMatches() instead of substring() below
        // to avoid memory allocation
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 2 < path.length()) {
            // ignore those ._* files created by MacOS
            if (path.regionMatches(lastSlash + 1, "._", 0, 2)) {
                return true;
            }

            // ignore album art files created by Windows Media Player:
            // Folder.jpg, AlbumArtSmall.jpg, AlbumArt_{...}_Large.jpg
            // and AlbumArt_{...}_Small.jpg
            if (path.regionMatches(true, path.length() - 4, ".jpg", 0, 4)) {
                if (path.regionMatches(true, lastSlash + 1, "AlbumArt_{", 0, 10) ||
                        path.regionMatches(true, lastSlash + 1, "AlbumArt.", 0, 9)) {
                    return true;
                }
                int length = path.length() - lastSlash - 1;
                if ((length == 17 && path.regionMatches(
                        true, lastSlash + 1, "AlbumArtSmall", 0, 13)) ||
                        (length == 10
                         && path.regionMatches(true, lastSlash + 1, "Folder", 0, 6))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static HashMap<String,String> mNoMediaPaths = new HashMap<String,String>();
    private static HashMap<String,String> mMediaPaths = new HashMap<String,String>();

    /* MediaProvider calls this when a .nomedia file is added or removed */
    public static void clearMediaPathCache(boolean clearMediaPaths, boolean clearNoMediaPaths) {
        synchronized (MediaScanner.class) {
            if (clearMediaPaths) {
                mMediaPaths.clear();
            }
            if (clearNoMediaPaths) {
                mNoMediaPaths.clear();
            }
        }
    }

    public static boolean isNoMediaPath(String path) {
        if (path == null) {
            return false;
        }
        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) {
            return true;
        }

        int firstSlash = path.lastIndexOf('/');
        if (firstSlash <= 0) {
            return false;
        }
        String parent = path.substring(0,  firstSlash);

        synchronized (MediaScanner.class) {
            if (mNoMediaPaths.containsKey(parent)) {
                return true;
            } else if (!mMediaPaths.containsKey(parent)) {
                // check to see if any parent directories have a ".nomedia" file
                // start from 1 so we don't bother checking in the root directory
                int offset = 1;
                while (offset >= 0) {
                    int slashIndex = path.indexOf('/', offset);
                    if (slashIndex > offset) {
                        slashIndex++; // move past slash
                        File file = new File(path.substring(0, slashIndex) + ".nomedia");
                        if (file.exists()) {
                            // we have a .nomedia in one of the parent directories
                            mNoMediaPaths.put(parent, "");
                            return true;
                        }
                    }
                    /// M: avoid loop in while@{
                    else if(slashIndex == offset) {
                        slashIndex++; //pass "//" case
                    }
                    /// @}
                    offset = slashIndex;
                }
                mMediaPaths.put(parent, "");
            }
        }

        return isNoMediaFile(path);
    }

    public void scanMtpFile(String path, String volumeName, int objectHandle, int format) {
        initialize(volumeName);
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);
        File file = new File(path);
        long lastModifiedSeconds = file.lastModified() / 1000;

        if (!MediaFile.isAudioFileType(fileType) && !MediaFile.isVideoFileType(fileType) &&
            !MediaFile.isImageFileType(fileType) && !MediaFile.isPlayListFileType(fileType) &&
            !MediaFile.isDrmFileType(fileType)) {

            // no need to use the media scanner, but we need to update last modified and file size
            ContentValues values = new ContentValues();
            /// M: ALPS00670132, for WHQL ObjectSize folder case, folder file size must be 0
            values.put(Files.FileColumns.SIZE, format == MtpConstants.FORMAT_ASSOCIATION ? 0 : file.length());

            values.put(Files.FileColumns.DATE_MODIFIED, lastModifiedSeconds);
            try {
                String[] whereArgs = new String[] {  Integer.toString(objectHandle) };
                mMediaProvider.update(mPackageName, Files.getMtpObjectsUri(volumeName), values,
                        "_id=?", whereArgs);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in scanMtpFile", e);
            }
            return;
        }

        mMtpObjectHandle = objectHandle;
        Cursor fileList = null;
        try {
            if (MediaFile.isPlayListFileType(fileType)) {
                // build file cache so we can look up tracks in the playlist
                prescan(null, true);

                FileEntry entry = makeEntryFor(path);
                if (entry != null) {
                    fileList = mMediaProvider.query(mPackageName, mFilesUri,
                            FILES_PRESCAN_PROJECTION, null, null, null, null);
                    processPlayList(entry, fileList);
                }
            } else {
                // MTP will create a file entry for us so we don't want to do it in prescan
                prescan(path, false);

                // always scan the file, so we can return the content://media Uri for existing files
                mClient.doScanFile(path, mediaFileType.mimeType, lastModifiedSeconds, file.length(),
                    (format == MtpConstants.FORMAT_ASSOCIATION), true, isNoMediaPath(path));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
        } finally {
            mMtpObjectHandle = 0;
            if (fileList != null) {
                fileList.close();
            }
            releaseResources();
        }
    }

    FileEntry makeEntryFor(String path) {
        String where;
        String[] selectionArgs;

        Cursor c = null;
        try {
            where = Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { path };
            c = mMediaProvider.query(mPackageName, mFilesUriNoNotify, FILES_PRESCAN_PROJECTION,
                    where, selectionArgs, null, null);
            if (c.moveToFirst()) {
                long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                return new FileEntry(rowId, path, lastModified, format);
            }
        } catch (RemoteException e) {
            /// M: Adds log for debug.
            Log.e(TAG, "makeEntryFor: RemoteException! path=" + path, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    // returns the number of matching file/directory names, starting from the right
    private int matchPaths(String path1, String path2) {
        int result = 0;
        int end1 = path1.length();
        int end2 = path2.length();

        while (end1 > 0 && end2 > 0) {
            int slash1 = path1.lastIndexOf('/', end1 - 1);
            int slash2 = path2.lastIndexOf('/', end2 - 1);
            int backSlash1 = path1.lastIndexOf('\\', end1 - 1);
            int backSlash2 = path2.lastIndexOf('\\', end2 - 1);
            int start1 = (slash1 > backSlash1 ? slash1 : backSlash1);
            int start2 = (slash2 > backSlash2 ? slash2 : backSlash2);
            if (start1 < 0) start1 = 0; else start1++;
            if (start2 < 0) start2 = 0; else start2++;
            int length = end1 - start1;
            if (end2 - start2 != length) break;
            if (path1.regionMatches(true, start1, path2, start2, length)) {
                result++;
                end1 = start1 - 1;
                end2 = start2 - 1;
            } else break;
        }

        return result;
    }

    private boolean matchEntries(long rowId, String data) {

        int len = mPlaylistEntries.size();
        boolean done = true;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel == Integer.MAX_VALUE) {
                continue; // this entry has been matched already
            }
            done = false;
            if (data.equalsIgnoreCase(entry.path)) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = Integer.MAX_VALUE;
                continue; // no need for path matching
            }

            int matchLength = matchPaths(data, entry.path);
            if (matchLength > entry.bestmatchlevel) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = matchLength;
            }
        }
        return done;
    }

    private void cachePlaylistEntry(String line, String playListDirectory) {
        PlaylistEntry entry = new PlaylistEntry();
        // watch for trailing whitespace
        int entryLength = line.length();
        while (entryLength > 0 && Character.isWhitespace(line.charAt(entryLength - 1))) entryLength--;
        // path should be longer than 3 characters.
        // avoid index out of bounds errors below by returning here.
        if (entryLength < 3) return;
        if (entryLength < line.length()) line = line.substring(0, entryLength);

        // does entry appear to be an absolute path?
        // look for Unix or DOS absolute paths
        char ch1 = line.charAt(0);
        boolean fullPath = (ch1 == '/' ||
                (Character.isLetter(ch1) && line.charAt(1) == ':' && line.charAt(2) == '\\'));
        // if we have a relative path, combine entry with playListDirectory
        if (!fullPath)
            line = playListDirectory + line;
        entry.path = line;
        //FIXME - should we look for "../" within the path?

        mPlaylistEntries.add(entry);
    }

    private void processCachedPlaylist(Cursor fileList, ContentValues values, Uri playlistUri) {
        fileList.moveToPosition(-1);
        while (fileList.moveToNext()) {
            long rowId = fileList.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
            String data = fileList.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
            if (matchEntries(rowId, data)) {
                break;
            }
        }

        int len = mPlaylistEntries.size();
        int index = 0;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel > 0) {
                try {
                    values.clear();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(index));
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, Long.valueOf(entry.bestmatchid));
                    mMediaProvider.insert(mPackageName, playlistUri, values);
                    index++;
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException in MediaScanner.processCachedPlaylist()", e);
                    return;
                }
            }
        }
        mPlaylistEntries.clear();
    }

    private void processM3uPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.length() > 0 && line.charAt(0) != '#') {
                        cachePlaylistEntry(line, playListDirectory);
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
            }
        }
    }

    private void processPlsPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.startsWith("File")) {
                        int equals = line.indexOf('=');
                        if (equals > 0) {
                            cachePlaylistEntry(line.substring(equals + 1), playListDirectory);
                        }
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
            }
        }
    }

    class WplHandler implements ElementListener {

        final ContentHandler handler;
        String playListDirectory;

        public WplHandler(String playListDirectory, Uri uri, Cursor fileList) {
            this.playListDirectory = playListDirectory;

            RootElement root = new RootElement("smil");
            Element body = root.getChild("body");
            Element seq = body.getChild("seq");
            Element media = seq.getChild("media");
            media.setElementListener(this);

            this.handler = root.getContentHandler();
        }

        @Override
        public void start(Attributes attributes) {
            String path = attributes.getValue("", "src");
            if (path != null) {
                cachePlaylistEntry(path, playListDirectory);
            }
        }

       @Override
       public void end() {
       }

        ContentHandler getContentHandler() {
            return handler;
        }
    }

    private void processWplPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        FileInputStream fis = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                fis = new FileInputStream(f);

                mPlaylistEntries.clear();
                Xml.parse(fis, Xml.findEncodingByName("UTF-8"),
                        new WplHandler(playListDirectory, uri, fileList).getContentHandler());

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processWplPlayList()", e);
            }
        }
    }

    private void processPlayList(FileEntry entry, Cursor fileList) throws RemoteException {
        String path = entry.mPath;
        ContentValues values = new ContentValues();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) throw new IllegalArgumentException("bad path " + path);
        Uri uri, membersUri;
        long rowId = entry.mRowId;

        // make sure we have a name
        String name = values.getAsString(MediaStore.Audio.Playlists.NAME);
        if (name == null) {
            name = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (name == null) {
                // extract name from file name
                int lastDot = path.lastIndexOf('.');
                name = (lastDot < 0 ? path.substring(lastSlash + 1)
                        : path.substring(lastSlash + 1, lastDot));
            }
        }

        values.put(MediaStore.Audio.Playlists.NAME, name);
        values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, entry.mLastModified);

        if (rowId == 0) {
            values.put(MediaStore.Audio.Playlists.DATA, path);
            uri = mMediaProvider.insert(mPackageName, mPlaylistsUri, values);
            rowId = ContentUris.parseId(uri);
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
        } else {
            uri = ContentUris.withAppendedId(mPlaylistsUri, rowId);
            mMediaProvider.update(mPackageName, uri, values, null, null);

            // delete members of existing playlist
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
            mMediaProvider.delete(mPackageName, membersUri, null, null);
        }

        String playListDirectory = path.substring(0, lastSlash + 1);
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

        if (fileType == MediaFile.FILE_TYPE_M3U) {
            processM3uPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_PLS) {
            processPlsPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_WPL) {
            processWplPlayList(path, playListDirectory, membersUri, values, fileList);
        }
    }

    private void processPlayLists() throws RemoteException {
        Iterator<FileEntry> iterator = mPlayLists.iterator();
        Cursor fileList = null;
        try {
            // use the files uri and projection because we need the format column,
            // but restrict the query to just audio files
            fileList = mMediaProvider.query(mPackageName, mFilesUri, FILES_PRESCAN_PROJECTION,
                    "media_type=2", null, null, null);
            while (iterator.hasNext()) {
                FileEntry entry = iterator.next();
                // only process playlist files if they are new or have been modified since the last scan
                if (entry.mLastModifiedChanged) {
                    processPlayList(entry, fileList);
                }
            }
        } catch (RemoteException e1) {
            /// M: Adds log for debug.
            Log.e(TAG, "processPlayLists: RemoteException!", e1);
        } finally {
            if (fileList != null) {
                fileList.close();
            }
        }
    }

    private native void processDirectory(String path, MediaScannerClient client);
    private native void processFile(String path, String mimeType, MediaScannerClient client);
    public native void setLocale(String locale);

    public native byte[] extractAlbumArt(FileDescriptor fd);

    private static native final void native_init();
    private native final void native_setup();
    private native final void native_finalize();

    /**
     * Releases resources associated with this MediaScanner object.
     * It is considered good practice to call this method when
     * one is done using the MediaScanner object. After this method
     * is called, the MediaScanner object can no longer be used.
     */
    public void release() {
        native_finalize();
    }

    @Override
    protected void finalize() {
        mContext.getContentResolver().releaseProvider(mMediaProvider);
        native_finalize();
    }

    /// M: Checks out whether the mimetype is application/octet-stream. @(
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private boolean isValueslessMimeType(String mimetype) {
        boolean valueless = false;
        if (MIME_APPLICATION_OCTET_STREAM.equalsIgnoreCase(mimetype)) {
            valueless = true;
            Log.v(TAG, "isValueslessMimeType: mimetype=" + mimetype);
        }
        return valueless;
    }
    /// M: @}

    /// M: MediaScanner Performance turning {@
    /// Add some new api for MediaScanner performance enhancement feature,
    /// we use threadpool to scan every folder in directories.

    /**
     * M: Store playlist file path and return to MediaScannerService so that it will process them
     * when postScanAll called.
     */
    private ArrayList<String> mPlaylistFilePathList = new ArrayList<String>();

    /**
     * M: Pre-scan all, only call by this scanner created in thread pool.
     * @hide
     */
    public void preScanAll(String volume) {
        try {
            initialize(volume);
            prescan(null, true);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }
    }

    /**
     * M: Post scan all, only call by this scanner created in thread pool.
     *
     * @param playlistPath playlist file path, process them to database.
     * @hide
     */
    public void postScanAll(ArrayList<String> playlistFilePathList) {
        try {
            /// handle playlists last, after we know what media files are on the storage.
            /// Restore path list to file entry list, then process these playlist
            if (mProcessPlaylists) {
                for (String path : playlistFilePathList) {
                    FileEntry entry = makeEntryFor(path);
                    File file = new File(path);
                    long lastModified = file.lastModified();
                    // add some slack to avoid a rounding error
                    long delta = (entry != null) ? (lastModified - entry.mLastModified) : 0;
                    boolean wasModified = delta > 1 || delta < -1;
                    if (entry == null || wasModified) {
                        if (wasModified) {
                            entry.mLastModified = lastModified;
                        } else {
                            entry = new FileEntry(0, path, lastModified, 0);
                        }
                        entry.mLastModifiedChanged = true;
                    }
                    mPlayLists.add(entry);
                }
                processPlayLists();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        if ((mOriginalCount == 0 || mOriginalVideoCount == 0)
                && mImagesUri.equals(Images.Media.getContentUri("external"))) {
            pruneDeadThumbnailFiles();
        }

        /// allow GC to clean up
        mPlayLists = null;
        mMediaProvider = null;
        Log.v(TAG, "postScanAll");
    }

    /**
     * M: Scan all given folder with right method. Single file and empty folder need scan special one by one.
     *
     * @param insertHanlder use to do entries insert
     * @param folders The folders given to scan.
     * @param volume External or internal
     * @param isSingelFile whether the given folders is single file
     *
     * @return playlist file path scan in these folders
     *
     * @hide
     */
    public ArrayList<String> scanFolders(Handler insertHanlder, String[] folders, String volume, boolean isSingelFile) {
        try {
            initialize(volume);

            /// Init mPlaylist because we may insert playlist in begin file.
            if (mPlayLists == null) {
                mPlayLists = new ArrayList<FileEntry>();
            } else {
                mPlayLists.clear();
            }

            if (ENABLE_BULK_INSERTS) {
                /// create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(insertHanlder, 100);
            }
            /// Single file scan it directly and folder need scan all it's sub files.
            for (String path : folders) {
                if (isSingelFile) {
                    File file = new File(path);
                    long lastModifiedSeconds = file.lastModified() / 1000; // lastModified is in milliseconds on Files.
                    mClient.doScanFile(path, null, lastModifiedSeconds, file.length(),
                            file.isDirectory(), false, isNoMediaPath(path));
                } else {
                    processDirectory(path, mClient);
                }
            }
            if (ENABLE_BULK_INSERTS) {
                /// flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }
        } catch (SQLException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        /// allow GC to clean up
        mPlayLists = null;
        mMediaProvider = null;

        return mPlaylistFilePathList;
    }

    /**
     * M: Scan all given folder with right method. Single file and empty folder need scan special one by one.
     *
     * @param folders The folders given to scan.
     * @param volume External or internal
     * @param isSingelFileOrEmptyFolder whether the given folders is single file or empty folder
     *
     * @return playlist file path scan in these folders
     * @hide
     */
    public ArrayList<String> scanFolders(String[] folders, String volume, boolean isSingelFileOrEmptyFolder) {
        try {
            initialize(volume);

            /// Init mPlaylist because we may insert playlist in begin file.
            if (mPlayLists == null) {
                mPlayLists = new ArrayList<FileEntry>();
            } else {
                mPlayLists.clear();
            }

            if (ENABLE_BULK_INSERTS) {
                /// create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(mMediaProvider, mPackageName, 200);
            }
            /// M: Call doScanFile to scan folder and use processDirecitory to scan subfolders.
            for (String folder : folders) {
                  File file = new File(folder);
                  if (file.exists()) {
                      // lastModified is in milliseconds on Files.
                      long lastModifiedSeconds = file.lastModified() / 1000;
                      mClient.doScanFile(folder, null, lastModifiedSeconds, file.length(),
                              file.isDirectory(), false, isNoMediaPath(folder));
                  }

                  if (!isSingelFileOrEmptyFolder) {
                    processDirectory(folder, mClient);
                  }
            }

            if (ENABLE_BULK_INSERTS) {
                /// flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }
        } catch (SQLException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        /// allow GC to clean up
        mPlayLists = null;
        mMediaProvider = null;

        return mPlaylistFilePathList;
    }
    /// @}

    /**M: Added for parsing stereo info.@{**/
    private static final String XMP_HEADER_START = "http://ns.adobe.com/xap/1.0/\0";
    private static final String XMP_EXT_MAIN_HEADER1 = "http://ns.adobe.com/xmp/extension/";
    private static final String NS_GDEPTH = "http://ns.google.com/photos/1.0/depthmap/";
    private static final String MTK_REFOCUS_PREFIX = "MRefocus";

    private static final int SOI = 0xFFD8;
    private static final int SOS = 0xFFDA;
    private static final int APP1 = 0xFFE1;
    private static final int APPXTAG_PLUS_LENGTHTAG_BYTE_COUNT = 4;

    /**
     * Check if current photo is stereo or not.
     * @param filePath
     *            file path of photo for checking
     * @return true if stereo photo, false if not stereo photo
     */
    public static boolean isStereoPhoto(String filePath) {
        if (filePath == null) {
            Log.d(TAG, "<isStereoPhoto> filePath is null!!");
            return false;
        }

        File srcFile = new File(filePath);
        if (!srcFile.exists()) {
            Log.d(TAG, "<isStereoPhoto> " + filePath + " not exists!!!");
            return false;
        }

        long start = System.currentTimeMillis();
        ArrayList<Section> sections = parseApp1Info(filePath);
        if (sections == null || sections.size() < 0) {
            Log.d(TAG, "<isStereoPhoto> " + filePath + ", no app1 sections");
            return false;
        }
        RandomAccessFile rafIn = null;
        try {
            rafIn = new RandomAccessFile(filePath, "r");
            for (int i = 0; i < sections.size(); i++) {
                Section section = sections.get(i);
                if (isStereo(section, rafIn)) {
                    Log.d(TAG, "<isStereoPhoto> " + filePath + " is stereo photo");
                    return true;
                }
            }
            Log.d(TAG, "<isStereoPhoto> " + filePath + " is not stereo photo");
            return false;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "<isStereoPhoto> FileNotFoundException:", e);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "<isStereoPhoto> IllegalArgumentException:", e);
            return false;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "<isStereoPhoto> IOException:", e);
            }
            Log.d(TAG, "<isStereoPhoto> <performance> costs(ms): "
                    + (System.currentTimeMillis() - start));
        }
    }

    private static boolean isStereo(Section section, RandomAccessFile rafIn) {
        try {
            if (section.mIsXmpMain) {
                rafIn.seek(section.mOffset + 2);
                int len = rafIn.readShort() - 2;
                rafIn.skipBytes(XMP_HEADER_START.length());
                byte[] xmpBuffer = new byte[len - XMP_HEADER_START.length()];
                rafIn.read(xmpBuffer, 0, xmpBuffer.length);
                String xmpContent = new String(xmpBuffer);
                if (xmpContent == null) {
                    Log.d(TAG, "<isStereo> xmpContent is null");
                    return false;
                }
                if (xmpContent.contains(MTK_REFOCUS_PREFIX)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            Log.e(TAG, "<isStereo> IOException:", e);
            return false;
        }
    }

    private static ArrayList<Section> parseApp1Info(String filePath) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "r");
            int value = raf.readUnsignedShort();
            if (value != SOI) {
                Log.d(TAG, "<parseApp1Info> error, find no SOI");
                return new ArrayList<Section>();
            }
            int marker = -1;
            long offset = -1;
            int length = -1;
            ArrayList<Section> sections = new ArrayList<Section>();

            while ((value = raf.readUnsignedShort()) != -1 && value != SOS) {
                marker = value;
                offset = raf.getFilePointer() - 2;
                length = raf.readUnsignedShort();
                if (value == APP1) {
                    Section section =
                            new Section(marker, offset, length);
                    long currentPos = raf.getFilePointer();
                    section = checkIfMainXmpInApp1(raf, section);
                    if (section != null && section.mIsXmpMain) {
                        sections.add(section);
                        break;
                    }
                    raf.seek(currentPos);
                }
                raf.skipBytes(length - 2);
            }

            return sections;
        } catch (IOException e) {
            Log.e(TAG, "<parseApp1Info> IOException, path " + filePath, e);
            return null;
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                    raf = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "<parseApp1Info> IOException, path " + filePath, e);
            }
        }
    }

    private static Section checkIfMainXmpInApp1(RandomAccessFile raf, Section section) {
        if (section == null) {
            Log.d(TAG, "<checkIfMainXmpInApp1> section is null!!!");
            return null;
        }
        byte[] buffer = null;
        String str = null;
        try {
            if (section.mMarker == APP1) {
                raf.seek(section.mOffset + APPXTAG_PLUS_LENGTHTAG_BYTE_COUNT);
                buffer = new byte[XMP_EXT_MAIN_HEADER1.length()];
                raf.read(buffer, 0, buffer.length);
                str = new String(buffer, 0, XMP_HEADER_START.length());
                if (XMP_HEADER_START.equals(str)) {
                    section.mIsXmpMain = true;
                }
            }
            return section;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "<checkIfMainXmpInApp1> UnsupportedEncodingException" + e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "<checkIfMainXmpInApp1> IOException" + e);
            return null;
        }
    }

    /**
     * APP Section.
     */
    private static class Section {
        // e.g. 0xffe1, exif
        public int mMarker;
        // marker offset from start of file
        public long mOffset;
        // app length, follow spec, include 2 length bytes
        public int mLength;
        public boolean mIsXmpMain;

        /**
          * Create a Section.
          * @param marker section mark
          * @param offset section address offset
          * @param length section length
          */
        public Section(int marker, long offset, int length) {
            mMarker = marker;
            mOffset = offset;
            mLength = length;
        }
    }
    /**@}**/

    /**M:Added for conshot feature.@{**/
    interface IfdId {
    int TYPE_IFD_0 = 0;
    int TYPE_IFD_1 = 1;
    int TYPE_IFD_EXIF = 2;
    int TYPE_IFD_INTEROPERABILITY = 3;
    int TYPE_IFD_GPS = 4;
    int TYPE_IFD_COUNT = 5;
}

    static class ConshotExif {
        public static ConshotInfo getConshotInfo(InputStream stream) {
            ConshotInfo conshotInfo = new ConshotInfo();
            ExifInterfaceExt exifInterface = new ExifInterfaceExt();
            try {
                exifInterface.readExif(stream);
            } catch (IOException e) {
                return conshotInfo;
            }

            Long groupId = exifInterface.getTagLongValue(ExifInterfaceExt.TAG_GROUP_ID);
            if (groupId != null) {
                conshotInfo.groupId = groupId.longValue();
            }

            Integer index = exifInterface.getTagIntValue(ExifInterfaceExt.TAG_GROUP_INDEX);
            if (index != null) {
                conshotInfo.groupIndex = index.intValue();
            }

            Long focusHigh = exifInterface.getTagLongValue(ExifInterfaceExt.TAG_FOCUS_VALUE_HIGH);
            if (focusHigh != null) {
                conshotInfo.focusValueHigh = focusHigh.longValue();
            }

            Long focusLow = exifInterface.getTagLongValue(ExifInterfaceExt.TAG_FOCUS_VALUE_LOW);
            if (focusLow != null) {
                conshotInfo.focusValueLow = focusLow.longValue();
            }

            return conshotInfo;
        }
    }
    
    static class ConshotInfo {
        public long groupId;
        public int groupIndex;
        public long focusValueHigh;
        public long focusValueLow;
    }

    static class ExifInterfaceExt {
        public static final int DEFINITION_NULL = 0;
        public static final int TAG_GROUP_INDEX = defineTag(IfdId.TYPE_IFD_0, (short) 0x0220);
        public static final int TAG_GROUP_ID = defineTag(IfdId.TYPE_IFD_0, (short) 0x0221);
        public static final int TAG_FOCUS_VALUE_HIGH = defineTag(IfdId.TYPE_IFD_0, (short) 0x0222);
        public static final int TAG_FOCUS_VALUE_LOW = defineTag(IfdId.TYPE_IFD_0, (short) 0x0223);
        public static final int TAG_GPS_IFD = defineTag(IfdId.TYPE_IFD_0, (short) 0x8825);
        public static final int TAG_EXIF_IFD = defineTag(IfdId.TYPE_IFD_0, (short) 0x8769);
        public static final int TAG_JPEG_INTERCHANGE_FORMAT = defineTag(IfdId.TYPE_IFD_1,
                (short) 0x0201);
        public static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = defineTag(IfdId.TYPE_IFD_1,
                (short) 0x0202);
        public static final int TAG_INTEROPERABILITY_IFD = defineTag(IfdId.TYPE_IFD_EXIF,
                (short) 0xA005);
        public static final int TAG_STRIP_OFFSETS = defineTag(IfdId.TYPE_IFD_0, (short) 0x0111);
        public static final int TAG_STRIP_BYTE_COUNTS =
                defineTag(IfdId.TYPE_IFD_0, (short) 0x0117);
        private static final int IFD_NULL = -1;
        private static final ByteOrder DEFAULT_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
        private static final String NULL_ARGUMENT_STRING = "Argument is null";
        private static final HashSet<Short> sOffsetTags = new HashSet<Short>();

        static {
            sOffsetTags.add(getTrueTagKey(TAG_GPS_IFD));
            sOffsetTags.add(getTrueTagKey(TAG_EXIF_IFD));
            sOffsetTags.add(getTrueTagKey(TAG_JPEG_INTERCHANGE_FORMAT));
            sOffsetTags.add(getTrueTagKey(TAG_INTEROPERABILITY_IFD));
            sOffsetTags.add(getTrueTagKey(TAG_STRIP_OFFSETS));
        }
        
        private SparseIntArray mTagInfo = null;
        private ExifDataExt mData = null;

        public ExifInterfaceExt() {
            mData = new ExifDataExt(DEFAULT_BYTE_ORDER);
        }
        private static int defineTag(int ifdId, short tagId) {
            return (tagId & 0x0000ffff) | (ifdId << 16);
        }

        public static short getTrueTagKey(int tag) {
            return (short) tag;
        }

        private static int getTrueIfd(int tag) {
            return tag >>> 16;
        }

        static boolean isOffsetTag(short tag) {
            return sOffsetTags.contains(tag);
        }

        private static int getAllowedIfdFlagsFromInfo(int info) {
            return info >>> 24;
        }

        static boolean isIfdAllowed(int info, int ifd) {
            int[] ifds = IfdData.getIfds();
            int ifdFlags = getAllowedIfdFlagsFromInfo(info);
            for (int i = 0; i < ifds.length; i++) {
                if (ifd == ifds[i] && ((ifdFlags >> i) & 1) == 1) {
                    return true;
                }
            }
            return false;
        }

        private static int getFlagsFromAllowedIfds(int[] allowedIfds) {
            if (allowedIfds == null || allowedIfds.length == 0) {
                return 0;
            }
            int flags = 0;
            int[] ifds = IfdData.getIfds();
            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                for (int j : allowedIfds) {
                    if (ifds[i] == j) {
                        flags |= 1 << i;
                        break;
                    }
                }
            }
            return flags;
        }

        public void readExif(InputStream inStream) throws IOException {
            if (inStream == null) {
                throw new IllegalArgumentException(NULL_ARGUMENT_STRING);
            }
            ExifDataExt d = null;
            try {
                d = new ExifReader(this).read(inStream);
            } catch (ExifInvalidFormatException e) {
                throw new IOException("Invalid exif format : " + e);
            }
            mData = d;
        }

        private ExifTag getTag(int tagId, int ifdId) {
            if (!ExifTag.isValidIfd(ifdId)) {
                return null;
            }
            return mData.getTag(getTrueTagKey(tagId), ifdId);
        }

        public ExifTag getTag(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTag(tagId, ifdId);
        }

        public Object getTagValue(int tagId, int ifdId) {
            ExifTag t = getTag(tagId, ifdId);
            return (t == null) ? null : t.getValue();
        }

        private String getTagStringValue(int tagId, int ifdId) {
            ExifTag t = getTag(tagId, ifdId);
            if (t == null) {
                return null;
            }
            return t.getValueAsString();
        }

        public String getTagStringValue(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTagStringValue(tagId, ifdId);
        }

        private Long getTagLongValue(int tagId, int ifdId) {
            long[] l = getTagLongValues(tagId, ifdId);
            if (l == null || l.length <= 0) {
                return null;
            }
            return Long.valueOf(l[0]);
        }

        public Long getTagLongValue(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTagLongValue(tagId, ifdId);
        }

        private Integer getTagIntValue(int tagId, int ifdId) {
            int[] l = getTagIntValues(tagId, ifdId);
            if (l == null || l.length <= 0) {
                return null;
            }
            return Integer.valueOf(l[0]);
        }

        public Integer getTagIntValue(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTagIntValue(tagId, ifdId);
        }

        private long[] getTagLongValues(int tagId, int ifdId) {
            ExifTag t = getTag(tagId, ifdId);
            if (t == null) {
                return null;
            }
            return t.getValueAsLongs();
        }

        public long[] getTagLongValues(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTagLongValues(tagId, ifdId);
        }

        private int[] getTagIntValues(int tagId, int ifdId) {
            ExifTag t = getTag(tagId, ifdId);
            if (t == null) {
                return null;
            }
            return t.getValueAsInts();
        }

        public int[] getTagIntValues(int tagId) {
            int ifdId = getDefinedTagDefaultIfd(tagId);
            return getTagIntValues(tagId, ifdId);
        }

        private int getDefinedTagDefaultIfd(int tagId) {
            int info = getTagInfo().get(tagId);
            if (info == DEFINITION_NULL) {
                return IFD_NULL;
            }
            return getTrueIfd(tagId);
        }

        SparseIntArray getTagInfo() {
            if (mTagInfo == null) {
                mTagInfo = new SparseIntArray();
                initTagInfo();
            }
            return mTagInfo;
        }

        private void initTagInfo() {
            int[] ifdAllowedIfds = {
                    IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_1
            };
            int ifdFlags = getFlagsFromAllowedIfds(ifdAllowedIfds) << 24;
            mTagInfo.put(ExifInterfaceExt.TAG_GROUP_INDEX, ifdFlags
                    | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1);
            mTagInfo.put(ExifInterfaceExt.TAG_GROUP_ID, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16
                    | ExifTag.SIZE_UNDEFINED);
            mTagInfo.put(ExifInterfaceExt.TAG_FOCUS_VALUE_HIGH, ifdFlags
                    | ExifTag.TYPE_UNSIGNED_LONG << 16 | ExifTag.SIZE_UNDEFINED);
            mTagInfo.put(ExifInterfaceExt.TAG_FOCUS_VALUE_LOW, ifdFlags
                    | ExifTag.TYPE_UNSIGNED_LONG << 16 | ExifTag.SIZE_UNDEFINED);
        }
    }

    static class CountedDataInputStream extends FilterInputStream {
        private final byte mByteArray[] = new byte[8];
        private final ByteBuffer mByteBuffer = ByteBuffer.wrap(mByteArray);
        private int mCount = 0;

        public CountedDataInputStream(InputStream in) {
            super(in);
        }

        public int getReadByteCount() {
            return mCount;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int r = in.read(b);
            mCount += (r >= 0) ? r : 0;
            return r;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = in.read(b, off, len);
            mCount += (r >= 0) ? r : 0;
            return r;
        }

        @Override
        public int read() throws IOException {
            int r = in.read();
            mCount += (r >= 0) ? 1 : 0;
            return r;
        }

        @Override
        public long skip(long length) throws IOException {
            long skip = in.skip(length);
            mCount += skip;
            return skip;
        }

        private void skipOrThrow(long length) throws IOException {
            if (skip(length) != length)
                throw new EOFException();
        }

        public void skipTo(long target) throws IOException {
            long cur = mCount;
            long diff = target - cur;
            assert (diff >= 0);
            skipOrThrow(diff);
        }

        private void readOrThrow(byte[] b, int off, int len) throws IOException {
            int r = read(b, off, len);
            if (r != len)
                throw new EOFException();
        }

        private void readOrThrow(byte[] b) throws IOException {
            readOrThrow(b, 0, b.length);
        }

        public ByteOrder getByteOrder() {
            return mByteBuffer.order();
        }

        public void setByteOrder(ByteOrder order) {
            mByteBuffer.order(order);
        }

        public short readShort() throws IOException {
            readOrThrow(mByteArray, 0, 2);
            mByteBuffer.rewind();
            return mByteBuffer.getShort();
        }

        public int readUnsignedShort() throws IOException {
            return readShort() & 0xffff;
        }

        public int readInt() throws IOException {
            readOrThrow(mByteArray, 0, 4);
            mByteBuffer.rewind();
            return mByteBuffer.getInt();
        }

        public long readUnsignedInt() throws IOException {
            return readInt() & 0xffffffffL;
        }

        public String readString(int n, Charset charset) throws IOException {
            byte buf[] = new byte[n];
            readOrThrow(buf);
            return new String(buf, charset);
        }
    }

    static class ExifReader {
        private static final String TAG = "ExifReader";

        private final ExifInterfaceExt mInterface;

        ExifReader(ExifInterfaceExt iRef) {
            mInterface = iRef;
        }

        ExifDataExt read(InputStream inputStream) throws ExifInvalidFormatException, IOException {
            ExifParser parser = ExifParser.parse(inputStream, mInterface);
            ExifDataExt ExifDataExt = new ExifDataExt(parser.getByteOrder());
            ExifTag tag = null;

            int event = parser.next();
            while (event != ExifParser.EVENT_END) {
                switch (event) {
                    case ExifParser.EVENT_START_OF_IFD:
                        ExifDataExt.addIfdData(new IfdData(parser.getCurrentIfd()));
                        break;
                    case ExifParser.EVENT_NEW_TAG:
                        tag = parser.getTag();
                        if (!tag.hasValue()) {
                            parser.registerForTagValue(tag);
                        } else {
                            ExifDataExt.getIfdData(tag.getIfd()).setTag(tag);
                        }
                        break;
                    case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
                        tag = parser.getTag();
                        if (tag.getDataType() == ExifTag.TYPE_UNDEFINED) {
                            parser.readFullTagValue(tag);
                        }
                        ExifDataExt.getIfdData(tag.getIfd()).setTag(tag);
                        break;
                    case ExifParser.EVENT_COMPRESSED_IMAGE:
                        byte buf[] = new byte[parser.getCompressedImageSize()];
                        if (buf.length == parser.read(buf)) {
                            ExifDataExt.setCompressedThumbnail(buf);
                        } else {
                            Log.w(TAG, "Failed to read the compressed thumbnail");
                        }
                        break;
                    case ExifParser.EVENT_UNCOMPRESSED_STRIP:
                        buf = new byte[parser.getStripSize()];
                        if (buf.length == parser.read(buf)) {
                            ExifDataExt.setStripBytes(parser.getStripIndex(), buf);
                        } else {
                            Log.w(TAG, "Failed to read the strip bytes");
                        }
                        break;
                }
                event = parser.next();
            }
            return ExifDataExt;
        }
    }

    static class ExifParser {
        public static final int EVENT_START_OF_IFD = 0;
        public static final int EVENT_NEW_TAG = 1;
        public static final int EVENT_VALUE_OF_REGISTERED_TAG = 2;
        public static final int EVENT_COMPRESSED_IMAGE = 3;
        public static final int EVENT_UNCOMPRESSED_STRIP = 4;
        public static final int EVENT_END = 5;
        private static final int OPTION_IFD_0 = 1;
        private static final int OPTION_IFD_1 = 1 << 1;
        private static final int OPTION_IFD_EXIF = 1 << 2;
        private static final int OPTION_IFD_GPS = 1 << 3;
        private static final int OPTION_IFD_INTEROPERABILITY = 1 << 4;
        private static final int OPTION_THUMBNAIL = 1 << 5;
        private static final int EXIF_HEADER = 0x45786966; // EXIF header "Exif"
        private static final short EXIF_HEADER_TAIL = (short) 0x0000; // EXIF header in APP1
        // TIFF header
        private static final short LITTLE_ENDIAN_TAG = (short) 0x4949; // "II"
        private static final short BIG_ENDIAN_TAG = (short) 0x4d4d; // "MM"
        private static final short TIFF_HEADER_TAIL = 0x002A;
        private static final int TAG_SIZE = 12;
        private static final int OFFSET_SIZE = 2;
        private static final int DEFAULT_IFD0_OFFSET = 8;
        private static final boolean LOGV = false;
        private static final String TAG = "ExifParser";
        private static final Charset US_ASCII = Charset.forName("US-ASCII");
        private static final short TAG_EXIF_IFD = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_EXIF_IFD);
        private static final short TAG_GPS_IFD = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_GPS_IFD);
        private static final short TAG_INTEROPERABILITY_IFD = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_INTEROPERABILITY_IFD);
        private static final short TAG_JPEG_INTERCHANGE_FORMAT = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_JPEG_INTERCHANGE_FORMAT);
        private static final short TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH);
        private static final short TAG_STRIP_OFFSETS = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_STRIP_OFFSETS);
        private static final short TAG_STRIP_BYTE_COUNTS = ExifInterfaceExt
                .getTrueTagKey(ExifInterfaceExt.TAG_STRIP_BYTE_COUNTS);
        private final CountedDataInputStream mTiffStream;
        private final int mOptions;
        private final ExifInterfaceExt mInterface;
        private final TreeMap<Integer, Object> mCorrespondingEvent =
                new TreeMap<Integer, Object>();
        private int mIfdStartOffset = 0;
        private int mNumOfTagInIfd = 0;
        private int mIfdType;
        private ExifTag mTag;
        private ImageEvent mImageEvent;
        private ExifTag mStripSizeTag;
        private ExifTag mJpegSizeTag;
        private boolean mNeedToParseOffsetsInCurrentIfd;
        private boolean mContainExifDataExt = false;
        private int mApp1End;
        private int mOffsetToApp1EndFromSOF = 0;
        private byte[] mDataAboveIfd0;
        private int mIfd0Position;
        private int mTiffStartPosition;

        private ExifParser(InputStream inputStream, int options, ExifInterfaceExt iRef)
                throws IOException, ExifInvalidFormatException {
            if (inputStream == null) {
                throw new IOException("Null argument inputStream to ExifParser");
            }
            if (LOGV) {
                Log.v(TAG, "Reading exif...");
            }
            mInterface = iRef;
            mContainExifDataExt = seekTiffData(inputStream);
            mTiffStream = new CountedDataInputStream(inputStream);
            mOptions = options;
            if (!mContainExifDataExt) {
                return;
            }

            parseTiffHeader();
            long offset = mTiffStream.readUnsignedInt();
            if (offset > Integer.MAX_VALUE) {
                throw new ExifInvalidFormatException("Invalid offset " + offset);
            }
            mIfd0Position = (int) offset;
            mIfdType = IfdId.TYPE_IFD_0;
            if (isIfdRequested(IfdId.TYPE_IFD_0) || needToParseOffsetsInCurrentIfd()) {
                registerIfd(IfdId.TYPE_IFD_0, offset);
                if (offset != DEFAULT_IFD0_OFFSET) {
                    mDataAboveIfd0 = new byte[(int) offset - DEFAULT_IFD0_OFFSET];
                    read(mDataAboveIfd0);
                }
            }
        }

        static ExifParser parse(InputStream inputStream, ExifInterfaceExt iRef) throws IOException,
                ExifInvalidFormatException {
            return new ExifParser(inputStream, OPTION_IFD_0 | OPTION_IFD_1 | OPTION_IFD_EXIF
                    | OPTION_IFD_GPS | OPTION_IFD_INTEROPERABILITY | OPTION_THUMBNAIL, iRef);
        }

        private boolean isIfdRequested(int ifdType) {
            switch (ifdType) {
                case IfdId.TYPE_IFD_0:
                    return (mOptions & OPTION_IFD_0) != 0;
                case IfdId.TYPE_IFD_1:
                    return (mOptions & OPTION_IFD_1) != 0;
                case IfdId.TYPE_IFD_EXIF:
                    return (mOptions & OPTION_IFD_EXIF) != 0;
                case IfdId.TYPE_IFD_GPS:
                    return (mOptions & OPTION_IFD_GPS) != 0;
                case IfdId.TYPE_IFD_INTEROPERABILITY:
                    return (mOptions & OPTION_IFD_INTEROPERABILITY) != 0;
            }
            return false;
        }

        private boolean isThumbnailRequested() {
            return (mOptions & OPTION_THUMBNAIL) != 0;
        }

        int next() throws IOException, ExifInvalidFormatException {
            if (!mContainExifDataExt) {
                return EVENT_END;
            }
            int offset = mTiffStream.getReadByteCount();
            int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
            if (offset < endOfTags) {
                mTag = readTag();
                if (mTag == null) {
                    return next();
                }
                if (mNeedToParseOffsetsInCurrentIfd) {
                    checkOffsetOrImageTag(mTag);
                }
                return EVENT_NEW_TAG;
            } else if (offset == endOfTags) {
                // There is a link to ifd1 at the end of ifd0
                if (mIfdType == IfdId.TYPE_IFD_0) {
                    long ifdOffset = readUnsignedLong();
                    if (isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested()) {
                        if (ifdOffset != 0) {
                            registerIfd(IfdId.TYPE_IFD_1, ifdOffset);
                        }
                    }
                } else {
                    int offsetSize = 4;
                    // Some camera models use invalid length of the offset
                    if (mCorrespondingEvent.size() > 0) {
                        offsetSize =
                                mCorrespondingEvent.firstEntry().getKey()
                                        - mTiffStream.getReadByteCount();
                    }
                    if (offsetSize < 4) {
                        Log.w(TAG, "Invalid size of link to next IFD: " + offsetSize);
                    } else {
                        long ifdOffset = readUnsignedLong();
                        if (ifdOffset != 0) {
                            Log.w(TAG, "Invalid link to next IFD: " + ifdOffset);
                        }
                    }
                }
            }
            while (mCorrespondingEvent.size() != 0) {
                Entry<Integer, Object> entry = mCorrespondingEvent.pollFirstEntry();
                Object event = entry.getValue();
                try {
                    skipTo(entry.getKey());
                } catch (IOException e) {
                    Log.w(TAG, "Failed to skip to data at: " + entry.getKey() + " for "
                            + event.getClass().getName() + ", the file may be broken.");
                    continue;
                }
                if (event instanceof IfdEvent) {
                    mIfdType = ((IfdEvent) event).ifd;
                    mNumOfTagInIfd = mTiffStream.readUnsignedShort();
                    mIfdStartOffset = entry.getKey();

                    if (mNumOfTagInIfd * TAG_SIZE + mIfdStartOffset + OFFSET_SIZE > mApp1End) {
                        Log.w(TAG, "Invalid size of IFD " + mIfdType);
                        return EVENT_END;
                    }

                    mNeedToParseOffsetsInCurrentIfd = needToParseOffsetsInCurrentIfd();
                    if (((IfdEvent) event).isRequested) {
                        return EVENT_START_OF_IFD;
                    } else {
                        skipRemainingTagsInCurrentIfd();
                    }
                } else if (event instanceof ImageEvent) {
                    mImageEvent = (ImageEvent) event;
                    return mImageEvent.type;
                } else {
                    ExifTagEvent tagEvent = (ExifTagEvent) event;
                    mTag = tagEvent.tag;
                    if (mTag.getDataType() != ExifTag.TYPE_UNDEFINED) {
                        readFullTagValue(mTag);
                        checkOffsetOrImageTag(mTag);
                    }
                    if (tagEvent.isRequested) {
                        return EVENT_VALUE_OF_REGISTERED_TAG;
                    }
                }
            }
            return EVENT_END;
        }

        private void skipRemainingTagsInCurrentIfd() throws IOException,
                ExifInvalidFormatException {
            int endOfTags = mIfdStartOffset + OFFSET_SIZE + TAG_SIZE * mNumOfTagInIfd;
            int offset = mTiffStream.getReadByteCount();
            if (offset > endOfTags) {
                return;
            }
            if (mNeedToParseOffsetsInCurrentIfd) {
                while (offset < endOfTags) {
                    mTag = readTag();
                    offset += TAG_SIZE;
                    if (mTag == null) {
                        continue;
                    }
                    checkOffsetOrImageTag(mTag);
                }
            } else {
                skipTo(endOfTags);
            }
            long ifdOffset = readUnsignedLong();
            // For ifd0, there is a link to ifd1 in the end of all tags
            if (mIfdType == IfdId.TYPE_IFD_0
                    && (isIfdRequested(IfdId.TYPE_IFD_1) || isThumbnailRequested())) {
                if (ifdOffset > 0) {
                    registerIfd(IfdId.TYPE_IFD_1, ifdOffset);
                }
            }
        }

        private boolean needToParseOffsetsInCurrentIfd() {
            switch (mIfdType) {
                case IfdId.TYPE_IFD_0:
                    return isIfdRequested(IfdId.TYPE_IFD_EXIF)
                            || isIfdRequested(IfdId.TYPE_IFD_GPS)
                            || isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)
                            || isIfdRequested(IfdId.TYPE_IFD_1);
                case IfdId.TYPE_IFD_1:
                    return isThumbnailRequested();
                case IfdId.TYPE_IFD_EXIF:
                    // The offset to interoperability IFD is located in Exif IFD
                    return isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY);
                default:
                    return false;
            }
        }

        ExifTag getTag() {
            return mTag;
        }

        int getCurrentIfd() {
            return mIfdType;
        }

        int getStripIndex() {
            return mImageEvent.stripIndex;
        }

        int getStripSize() {
            if (mStripSizeTag == null)
                return 0;
            return (int) mStripSizeTag.getValueAt(0);
        }

        int getCompressedImageSize() {
            if (mJpegSizeTag == null) {
                return 0;
            }
            return (int) mJpegSizeTag.getValueAt(0);
        }

        private void skipTo(int offset) throws IOException {
            mTiffStream.skipTo(offset);
            while (!mCorrespondingEvent.isEmpty() && mCorrespondingEvent.firstKey() < offset) {
                mCorrespondingEvent.pollFirstEntry();
            }
        }

        void registerForTagValue(ExifTag tag) {
            if (tag.getOffset() >= mTiffStream.getReadByteCount()) {
                mCorrespondingEvent.put(tag.getOffset(), new ExifTagEvent(tag, true));
            }
        }

        private void registerIfd(int ifdType, long offset) {
            // Cast unsigned int to int since the offset is always smaller
            // than the size of APP1 (65536)
            mCorrespondingEvent.put((int) offset, new IfdEvent(ifdType, isIfdRequested(ifdType)));
        }

        private void registerCompressedImage(long offset) {
            mCorrespondingEvent.put((int) offset, new ImageEvent(EVENT_COMPRESSED_IMAGE));
        }

        private void registerUncompressedStrip(int stripIndex, long offset) {
            mCorrespondingEvent.put((int) offset, new ImageEvent(EVENT_UNCOMPRESSED_STRIP,
                    stripIndex));
        }

        private ExifTag readTag() throws IOException, ExifInvalidFormatException {
            short tagId = mTiffStream.readShort();
            short dataFormat = mTiffStream.readShort();
            long numOfComp = mTiffStream.readUnsignedInt();
            if (numOfComp > Integer.MAX_VALUE) {
                throw new ExifInvalidFormatException(
                        "Number of component is larger then Integer.MAX_VALUE");
            }
            // Some invalid image file contains invalid data type. Ignore those tags
            if (!ExifTag.isValidType(dataFormat)) {
                Log.w(TAG, String.format("Tag %04x: Invalid data type %d", tagId, dataFormat));
                mTiffStream.skip(4);
                return null;
            }
            // TODO: handle numOfComp overflow
            ExifTag tag =
                    new ExifTag(tagId, dataFormat, (int) numOfComp, mIfdType,
                            ((int) numOfComp) != ExifTag.SIZE_UNDEFINED);
            int dataSize = tag.getDataSize();
            if (dataSize > 4) {
                long offset = mTiffStream.readUnsignedInt();
                if (offset > Integer.MAX_VALUE) {
                    throw new ExifInvalidFormatException("offset is larger then Integer.MAX_VALUE");
                }
                if ((offset < mIfd0Position) && (dataFormat == ExifTag.TYPE_UNDEFINED)
                        && (mDataAboveIfd0 != null)) {
                    byte[] buf = new byte[(int) numOfComp];
                    if (mDataAboveIfd0 == null)
                        return null;
                    System.arraycopy(mDataAboveIfd0, (int) offset - DEFAULT_IFD0_OFFSET, buf, 0,
                            (int) numOfComp);
                    tag.setValue(buf);
                } else {
                    tag.setOffset((int) offset);
                }
            } else {
                boolean defCount = tag.hasDefinedCount();
                // Set defined count to 0 so we can add \0 to non-terminated strings
                tag.setHasDefinedCount(false);
                // Read value
                readFullTagValue(tag);
                tag.setHasDefinedCount(defCount);
                mTiffStream.skip(4 - dataSize);
                // Set the offset to the position of value.
                tag.setOffset(mTiffStream.getReadByteCount() - 4);
            }
            return tag;
        }

        private void checkOffsetOrImageTag(ExifTag tag) {
            // Some invalid formattd image contains tag with 0 size.
            if (tag.getComponentCount() == 0) {
                return;
            }
            short tid = tag.getTagId();
            int ifd = tag.getIfd();
            if (tid == TAG_EXIF_IFD && checkAllowed(ifd, ExifInterfaceExt.TAG_EXIF_IFD)) {
                if (isIfdRequested(IfdId.TYPE_IFD_EXIF)
                        || isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
                    registerIfd(IfdId.TYPE_IFD_EXIF, tag.getValueAt(0));
                }
            } else if (tid == TAG_GPS_IFD && checkAllowed(ifd, ExifInterfaceExt.TAG_GPS_IFD)) {
                if (isIfdRequested(IfdId.TYPE_IFD_GPS)) {
                    registerIfd(IfdId.TYPE_IFD_GPS, tag.getValueAt(0));
                }
            } else if (tid == TAG_INTEROPERABILITY_IFD
                    && checkAllowed(ifd, ExifInterfaceExt.TAG_INTEROPERABILITY_IFD)) {
                if (isIfdRequested(IfdId.TYPE_IFD_INTEROPERABILITY)) {
                    registerIfd(IfdId.TYPE_IFD_INTEROPERABILITY, tag.getValueAt(0));
                }
            } else if (tid == TAG_JPEG_INTERCHANGE_FORMAT
                    && checkAllowed(ifd, ExifInterfaceExt.TAG_JPEG_INTERCHANGE_FORMAT)) {
                if (isThumbnailRequested()) {
                    registerCompressedImage(tag.getValueAt(0));
                }
            } else if (tid == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
                    && checkAllowed(ifd, ExifInterfaceExt.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH)) {
                if (isThumbnailRequested()) {
                    mJpegSizeTag = tag;
                }
            } else if (tid == TAG_STRIP_OFFSETS
                    && checkAllowed(ifd, ExifInterfaceExt.TAG_STRIP_OFFSETS)) {
                if (isThumbnailRequested()) {
                    if (tag.hasValue()) {
                        for (int i = 0; i < tag.getComponentCount(); i++) {
                            if (tag.getDataType() == ExifTag.TYPE_UNSIGNED_SHORT) {
                                registerUncompressedStrip(i, tag.getValueAt(i));
                            } else {
                                registerUncompressedStrip(i, tag.getValueAt(i));
                            }
                        }
                    } else {
                        mCorrespondingEvent.put(tag.getOffset(), new ExifTagEvent(tag, false));
                    }
                }
            } else if (tid == TAG_STRIP_BYTE_COUNTS
                    && checkAllowed(ifd, ExifInterfaceExt.TAG_STRIP_BYTE_COUNTS)
                    && isThumbnailRequested() && tag.hasValue()) {
                mStripSizeTag = tag;
            }
        }

        private boolean checkAllowed(int ifd, int tagId) {
            int info = mInterface.getTagInfo().get(tagId);
            if (info == ExifInterfaceExt.DEFINITION_NULL) {
                return false;
            }
            return ExifInterfaceExt.isIfdAllowed(info, ifd);
        }

        void readFullTagValue(ExifTag tag) throws IOException {
            // Some invalid images contains tags with wrong size, check it here
            short type = tag.getDataType();
            if (type == ExifTag.TYPE_ASCII || type == ExifTag.TYPE_UNDEFINED
                    || type == ExifTag.TYPE_UNSIGNED_BYTE) {
                int size = tag.getComponentCount();
                if (mCorrespondingEvent.size() > 0) {
                    if (mCorrespondingEvent.firstEntry().getKey() < mTiffStream.getReadByteCount()
                            + size) {
                        Object event = mCorrespondingEvent.firstEntry().getValue();
                        if (event instanceof ImageEvent) {
                            // Tag value overlaps thumbnail, ignore thumbnail.
                            Log.w(TAG, "Thumbnail overlaps value for tag: \n" + tag.toString());
                            Entry<Integer, Object> entry = mCorrespondingEvent.pollFirstEntry();
                            Log.w(TAG, "Invalid thumbnail offset: " + entry.getKey());
                        } else {
                            // Tag value overlaps another tag, shorten count
                            if (event instanceof IfdEvent) {
                                Log.w(TAG, "Ifd " + ((IfdEvent) event).ifd
                                        + " overlaps value for tag: \n" + tag.toString());
                            } else if (event instanceof ExifTagEvent) {
                                Log.w(TAG, "Tag value for tag: \n"
                                        + ((ExifTagEvent) event).tag.toString()
                                        + " overlaps value for tag: \n" + tag.toString());
                            }
                            size =
                                    mCorrespondingEvent.firstEntry().getKey()
                                            - mTiffStream.getReadByteCount();
                            if (size > 0) {
                                Log.w(TAG, "<readFullTagValue> Invalid size of tag: \n"
                                        + tag.toString() + " setting count to: " + size);
                                tag.forceSetComponentCount(size);
                            }
                        }
                    }
                }
            }
            switch (tag.getDataType()) {
                case ExifTag.TYPE_UNSIGNED_BYTE:
                case ExifTag.TYPE_UNDEFINED: {
                    byte buf[] = new byte[tag.getComponentCount()];
                    read(buf);
                    tag.setValue(buf);
                }
                    break;
                case ExifTag.TYPE_ASCII:
                    tag.setValue(readString(tag.getComponentCount()));
                    break;
                case ExifTag.TYPE_UNSIGNED_LONG: {
                    long value[] = new long[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readUnsignedLong();
                    }
                    tag.setValue(value);
                }
                    break;
                case ExifTag.TYPE_UNSIGNED_RATIONAL: {
                    Rational value[] = new Rational[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readUnsignedRational();
                    }
                    tag.setValue(value);
                }
                    break;
                case ExifTag.TYPE_UNSIGNED_SHORT: {
                    int value[] = new int[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readUnsignedShort();
                    }
                    tag.setValue(value);
                }
                    break;
                case ExifTag.TYPE_LONG: {
                    int value[] = new int[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readLong();
                    }
                    tag.setValue(value);
                }
                    break;
                case ExifTag.TYPE_RATIONAL: {
                    Rational value[] = new Rational[tag.getComponentCount()];
                    for (int i = 0, n = value.length; i < n; i++) {
                        value[i] = readRational();
                    }
                    tag.setValue(value);
                }
                    break;
            }
            if (LOGV) {
                Log.v(TAG, "\n" + tag.toString());
            }
        }

        private void parseTiffHeader() throws IOException, ExifInvalidFormatException {
            short byteOrder = mTiffStream.readShort();
            if (LITTLE_ENDIAN_TAG == byteOrder) {
                mTiffStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            } else if (BIG_ENDIAN_TAG == byteOrder) {
                mTiffStream.setByteOrder(ByteOrder.BIG_ENDIAN);
            } else {
                throw new ExifInvalidFormatException("Invalid TIFF header");
            }

            if (mTiffStream.readShort() != TIFF_HEADER_TAIL) {
                throw new ExifInvalidFormatException("Invalid TIFF header");
            }
        }

        private boolean seekTiffData(InputStream inputStream) throws IOException,
                ExifInvalidFormatException {
            CountedDataInputStream dataStream = new CountedDataInputStream(inputStream);
            if (dataStream.readShort() != JpegHeader.SOI) {
                throw new ExifInvalidFormatException("Invalid JPEG format");
            }

            short marker = dataStream.readShort();
            while (marker != JpegHeader.EOI && !JpegHeader.isSofMarker(marker)) {
                int length = dataStream.readUnsignedShort();
                // Some invalid formatted image contains multiple APP1,
                // try to find the one with Exif data.
                if (marker == JpegHeader.APP1) {
                    int header = 0;
                    short headerTail = 0;
                    if (length >= 8) {
                        header = dataStream.readInt();
                        headerTail = dataStream.readShort();
                        length -= 6;
                        if (header == EXIF_HEADER && headerTail == EXIF_HEADER_TAIL) {
                            mTiffStartPosition = dataStream.getReadByteCount();
                            mApp1End = length;
                            mOffsetToApp1EndFromSOF = mTiffStartPosition + mApp1End;
                            return true;
                        }
                    }
                }
                if (length < 2 || (length - 2) != dataStream.skip(length - 2)) {
                    Log.w(TAG, "Invalid JPEG format.");
                    return false;
                }
                marker = dataStream.readShort();
            }
            return false;
        }

        protected int getOffsetToExifEndFromSOF() {
            return mOffsetToApp1EndFromSOF;
        }

        protected int getTiffStartPosition() {
            return mTiffStartPosition;
        }

        protected int read(byte[] buffer, int offset, int length) throws IOException {
            return mTiffStream.read(buffer, offset, length);
        }

        int read(byte[] buffer) throws IOException {
            return mTiffStream.read(buffer);
        }

        private String readString(int n) throws IOException {
            return readString(n, US_ASCII);
        }

        private String readString(int n, Charset charset) throws IOException {
            if (n > 0) {
                return mTiffStream.readString(n, charset);
            } else {
                return "";
            }
        }

        private int readUnsignedShort() throws IOException {
            return mTiffStream.readShort() & 0xffff;
        }

        private long readUnsignedLong() throws IOException {
            return readLong() & 0xffffffffL;
        }

        private Rational readUnsignedRational() throws IOException {
            long nomi = readUnsignedLong();
            long denomi = readUnsignedLong();
            return new Rational(nomi, denomi);
        }

        private int readLong() throws IOException {
            return mTiffStream.readInt();
        }

        private Rational readRational() throws IOException {
            int nomi = readLong();
            int denomi = readLong();
            return new Rational(nomi, denomi);
        }

        ByteOrder getByteOrder() {
            return mTiffStream.getByteOrder();
        }

        private static class ImageEvent {
            final int stripIndex;
            final int type;

            ImageEvent(int type) {
                this.stripIndex = 0;
                this.type = type;
            }

            ImageEvent(int type, int stripIndex) {
                this.type = type;
                this.stripIndex = stripIndex;
            }
        }

        private static class IfdEvent {
            final int ifd;
            final boolean isRequested;

            IfdEvent(int ifd, boolean isInterestedIfd) {
                this.ifd = ifd;
                this.isRequested = isInterestedIfd;
            }
        }

        private static class ExifTagEvent {
            final ExifTag tag;
            final boolean isRequested;

            ExifTagEvent(ExifTag tag, boolean isRequireByUser) {
                this.tag = tag;
                this.isRequested = isRequireByUser;
            }
        }
    }

    static class ExifDataExt {
        private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];
        private final ByteOrder mByteOrder;
        private final ArrayList<byte[]> mStripBytes = new ArrayList<byte[]>();
        private byte[] mThumbnail;

        ExifDataExt(ByteOrder order) {
            mByteOrder = order;
        }

        protected byte[] getCompressedThumbnail() {
            return mThumbnail;
        }

        void setCompressedThumbnail(byte[] thumbnail) {
            mThumbnail = thumbnail;
        }

        protected boolean hasCompressedThumbnail() {
            return mThumbnail != null;
        }

        void setStripBytes(int index, byte[] strip) {
            if (index < mStripBytes.size()) {
                mStripBytes.set(index, strip);
            } else {
                for (int i = mStripBytes.size(); i < index; i++) {
                    mStripBytes.add(null);
                }
                mStripBytes.add(strip);
            }
        }

        protected int getStripCount() {
            return mStripBytes.size();
        }

        protected byte[] getStrip(int index) {
            return mStripBytes.get(index);
        }

        protected boolean hasUncompressedStrip() {
            return mStripBytes.size() != 0;
        }

        protected ByteOrder getByteOrder() {
            return mByteOrder;
        }

        IfdData getIfdData(int ifdId) {
            if (ExifTag.isValidIfd(ifdId)) {
                return mIfdDatas[ifdId];
            }
            return null;
        }

        void addIfdData(IfdData data) {
            mIfdDatas[data.getId()] = data;
        }

        private IfdData getOrCreateIfdData(int ifdId) {
            IfdData ifdData = mIfdDatas[ifdId];
            if (ifdData == null) {
                ifdData = new IfdData(ifdId);
                mIfdDatas[ifdId] = ifdData;
            }
            return ifdData;
        }

        ExifTag getTag(short tag, int ifd) {
            IfdData ifdData = mIfdDatas[ifd];
            return (ifdData == null) ? null : ifdData.getTag(tag);
        }

        protected ExifTag addTag(ExifTag tag) {
            if (tag != null) {
                int ifd = tag.getIfd();
                return addTag(tag, ifd);
            }
            return null;
        }

        private ExifTag addTag(ExifTag tag, int ifdId) {
            if (tag != null && ExifTag.isValidIfd(ifdId)) {
                IfdData ifdData = getOrCreateIfdData(ifdId);
                return ifdData.setTag(tag);
            }
            return null;
        }

        protected void clearThumbnailAndStrips() {
            mThumbnail = null;
            mStripBytes.clear();
        }

        protected void removeTag(short tagId, int ifdId) {
            IfdData ifdData = mIfdDatas[ifdId];
            if (ifdData == null) {
                return;
            }
            ifdData.removeTag(tagId);
        }

        protected List<ExifTag> getAllTags() {
            ArrayList<ExifTag> ret = new ArrayList<ExifTag>();
            for (IfdData d : mIfdDatas) {
                if (d != null) {
                    ExifTag[] tags = d.getAllTags();
                    if (tags != null) {
                        Collections.addAll(ret, tags);
                    }
                }
            }
            if (ret.size() == 0) {
                return null;
            }
            return ret;
        }

        protected List<ExifTag> getAllTagsForIfd(int ifd) {
            IfdData d = mIfdDatas[ifd];
            if (d == null) {
                return null;
            }
            ExifTag[] tags = d.getAllTags();
            if (tags == null) {
                return null;
            }
            ArrayList<ExifTag> ret = new ArrayList<ExifTag>(tags.length);
            Collections.addAll(ret, tags);
            if (ret.size() == 0) {
                return null;
            }
            return ret;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof ExifDataExt) {
                ExifDataExt data = (ExifDataExt) obj;
                if (data.mByteOrder != mByteOrder || data.mStripBytes.size() != mStripBytes.size()
                        || !Arrays.equals(data.mThumbnail, mThumbnail)) {
                    return false;
                }
                for (int i = 0; i < mStripBytes.size(); i++) {
                    if (!Arrays.equals(data.mStripBytes.get(i), mStripBytes.get(i))) {
                        return false;
                    }
                }
                for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                    IfdData ifd1 = data.getIfdData(i);
                    IfdData ifd2 = getIfdData(i);
                    if (ifd1 != ifd2 && ifd1 != null && !ifd1.equals(ifd2)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    static class ExifInvalidFormatException extends Exception {
        public ExifInvalidFormatException(String meg) {
            super(meg);
        }
    }

    static class ExifTag {
        public static final short TYPE_UNSIGNED_BYTE = 1;
        public static final short TYPE_ASCII = 2;
        public static final short TYPE_UNSIGNED_SHORT = 3;
        public static final short TYPE_UNSIGNED_LONG = 4;
        public static final short TYPE_UNSIGNED_RATIONAL = 5;
        public static final short TYPE_UNDEFINED = 7;
        public static final short TYPE_LONG = 9;
        public static final short TYPE_RATIONAL = 10;
        static final int SIZE_UNDEFINED = 0;
        private static final int TYPE_TO_SIZE_MAP[] = new int[11];
        private static final int UNSIGNED_SHORT_MAX = 65535;
        private static final long UNSIGNED_LONG_MAX = 4294967295L;
        private static final long LONG_MAX = Integer.MAX_VALUE;
        private static final long LONG_MIN = Integer.MIN_VALUE;
        private static final Charset US_ASCII = Charset.forName("US-ASCII");

        // Exif TagId
        private final short mTagId;
        // Exif Tag Type
        private final short mDataType;
        // If tag has defined count
        private boolean mHasDefinedDefaultComponentCount;
        // Actual data count in tag (should be number of elements in value array)
        private int mComponentCountActual;
        // The ifd that this tag should be put in
        private int mIfd;
        // The value (array of elements of type Tag Type)
        private Object mValue;
        // Value offset in exif header.
        private int mOffset;

        // Use builtTag in ExifInterfaceExt instead of constructor.
        ExifTag(short tagId, short type, int componentCount, int ifd,
                boolean hasDefinedComponentCount) {
            TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_BYTE] = 1;
            TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
            TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_SHORT] = 2;
            TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_LONG] = 4;
            TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_RATIONAL] = 8;
            TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
            TYPE_TO_SIZE_MAP[TYPE_LONG] = 4;
            TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;

            mTagId = tagId;
            mDataType = type;
            mComponentCountActual = componentCount;
            mHasDefinedDefaultComponentCount = hasDefinedComponentCount;
            mIfd = ifd;
            mValue = null;
        }

        public static boolean isValidIfd(int ifdId) {
            return ifdId == IfdId.TYPE_IFD_0 || ifdId == IfdId.TYPE_IFD_1
                    || ifdId == IfdId.TYPE_IFD_EXIF || ifdId == IfdId.TYPE_IFD_INTEROPERABILITY
                    || ifdId == IfdId.TYPE_IFD_GPS;
        }

        public static boolean isValidType(short type) {
            return type == TYPE_UNSIGNED_BYTE || type == TYPE_ASCII || type == TYPE_UNSIGNED_SHORT
                    || type == TYPE_UNSIGNED_LONG || type == TYPE_UNSIGNED_RATIONAL
                    || type == TYPE_UNDEFINED || type == TYPE_LONG || type == TYPE_RATIONAL;
        }

        private static int getElementSize(short type) {
            return TYPE_TO_SIZE_MAP[type];
        }

        private static String convertTypeToString(short type) {
            switch (type) {
                case TYPE_UNSIGNED_BYTE:
                    return "UNSIGNED_BYTE";
                case TYPE_ASCII:
                    return "ASCII";
                case TYPE_UNSIGNED_SHORT:
                    return "UNSIGNED_SHORT";
                case TYPE_UNSIGNED_LONG:
                    return "UNSIGNED_LONG";
                case TYPE_UNSIGNED_RATIONAL:
                    return "UNSIGNED_RATIONAL";
                case TYPE_UNDEFINED:
                    return "UNDEFINED";
                case TYPE_LONG:
                    return "LONG";
                case TYPE_RATIONAL:
                    return "RATIONAL";
                default:
                    return "";
            }
        }

        public int getIfd() {
            return mIfd;
        }

        void setIfd(int ifdId) {
            mIfd = ifdId;
        }

        public short getTagId() {
            return mTagId;
        }

        public short getDataType() {
            return mDataType;
        }

        public int getDataSize() {
            return getComponentCount() * getElementSize(getDataType());
        }

        public int getComponentCount() {
            return mComponentCountActual;
        }

        void forceSetComponentCount(int count) {
            mComponentCountActual = count;
        }

        public boolean hasValue() {
            return mValue != null;
        }

        public boolean setValue(int[] value) {
            if (checkBadComponentCount(value.length)) {
                return false;
            }
            if (mDataType != TYPE_UNSIGNED_SHORT && mDataType != TYPE_LONG
                    && mDataType != TYPE_UNSIGNED_LONG) {
                return false;
            }
            if (mDataType == TYPE_UNSIGNED_SHORT && checkOverflowForUnsignedShort(value)) {
                return false;
            } else if (mDataType == TYPE_UNSIGNED_LONG && checkOverflowForUnsignedLong(value)) {
                return false;
            }

            long[] data = new long[value.length];
            for (int i = 0; i < value.length; i++) {
                data[i] = value[i];
            }
            mValue = data;
            mComponentCountActual = value.length;
            return true;
        }

        private boolean setValue(int value) {
            return setValue(new int[] {
                value
            });
        }

        public boolean setValue(long[] value) {
            if (checkBadComponentCount(value.length) || mDataType != TYPE_UNSIGNED_LONG) {
                return false;
            }
            if (checkOverflowForUnsignedLong(value)) {
                return false;
            }
            mValue = value;
            mComponentCountActual = value.length;
            return true;
        }

        private boolean setValue(long value) {
            return setValue(new long[] {
                value
            });
        }

        public boolean setValue(String value) {
            if (mDataType != TYPE_ASCII && mDataType != TYPE_UNDEFINED) {
                return false;
            }

            byte[] buf = value.getBytes(US_ASCII);
            byte[] finalBuf = buf;
            if (buf.length > 0) {
                int index = 0;
                while (index < buf.length) {
                    if (buf[index] == 0)
                        break;
                    index++;
                }
                // if index == buf.length, means there is not has '0' in buf. so should add '0' for
                // finalBuf(index +1).
                finalBuf = Arrays.copyOf(buf, index + 1);
                mComponentCountActual = mComponentCountActual - (buf.length - (index + 1));
            } else if (mDataType == TYPE_ASCII && mComponentCountActual == 1) {
                finalBuf = new byte[] {
                    0
                };
            }
            int count = finalBuf.length;
            if (checkBadComponentCount(count)) {
                return false;
            }
            mComponentCountActual = count;
            mValue = finalBuf;
            return true;
        }

        public boolean setValue(Rational[] value) {
            if (checkBadComponentCount(value.length)) {
                return false;
            }
            if (mDataType != TYPE_UNSIGNED_RATIONAL && mDataType != TYPE_RATIONAL) {
                return false;
            }
            if (mDataType == TYPE_UNSIGNED_RATIONAL && checkOverflowForUnsignedRational(value)) {
                return false;
            } else if (mDataType == TYPE_RATIONAL && checkOverflowForRational(value)) {
                return false;
            }

            mValue = value;
            mComponentCountActual = value.length;
            return true;
        }

        private boolean setValue(Rational value) {
            return setValue(new Rational[] {
                value
            });
        }

        private boolean setValue(byte[] value, int offset, int length) {
            if (checkBadComponentCount(length)) {
                return false;
            }
            if (mDataType != TYPE_UNSIGNED_BYTE && mDataType != TYPE_UNDEFINED) {
                return false;
            }
            mValue = new byte[length];
            System.arraycopy(value, offset, mValue, 0, length);
            mComponentCountActual = length;
            return true;
        }

        public boolean setValue(byte[] value) {
            return setValue(value, 0, value.length);
        }

        private boolean setValue(byte value) {
            return setValue(new byte[] {
                value
            });
        }

        public boolean setValue(Object obj) {
            if (obj == null) {
                return false;
            } else if (obj instanceof Short) {
                return setValue(((Short) obj).shortValue() & 0x0ffff);
            } else if (obj instanceof String) {
                return setValue((String) obj);
            } else if (obj instanceof int[]) {
                return setValue((int[]) obj);
            } else if (obj instanceof long[]) {
                return setValue((long[]) obj);
            } else if (obj instanceof Rational) {
                return setValue((Rational) obj);
            } else if (obj instanceof Rational[]) {
                return setValue((Rational[]) obj);
            } else if (obj instanceof byte[]) {
                return setValue((byte[]) obj);
            } else if (obj instanceof Integer) {
                return setValue(((Integer) obj).intValue());
            } else if (obj instanceof Long) {
                return setValue(((Long) obj).longValue());
            } else if (obj instanceof Byte) {
                return setValue(((Byte) obj).byteValue());
            } else if (obj instanceof Short[]) {
                // Nulls in this array are treated as zeroes.
                Short[] arr = (Short[]) obj;
                int[] fin = new int[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    fin[i] = (arr[i] == null) ? 0 : arr[i].shortValue() & 0x0ffff;
                }
                return setValue(fin);
            } else if (obj instanceof Integer[]) {
                // Nulls in this array are treated as zeroes.
                Integer[] arr = (Integer[]) obj;
                int[] fin = new int[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    fin[i] = (arr[i] == null) ? 0 : arr[i].intValue();
                }
                return setValue(fin);
            } else if (obj instanceof Long[]) {
                // Nulls in this array are treated as zeroes.
                Long[] arr = (Long[]) obj;
                long[] fin = new long[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    fin[i] = (arr[i] == null) ? 0 : arr[i].longValue();
                }
                return setValue(fin);
            } else if (obj instanceof Byte[]) {
                // Nulls in this array are treated as zeroes.
                Byte[] arr = (Byte[]) obj;
                byte[] fin = new byte[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    fin[i] = (arr[i] == null) ? 0 : arr[i].byteValue();
                }
                return setValue(fin);
            } else {
                return false;
            }
        }

        public String getValueAsString() {
            if (mValue == null) {
                return null;
            } else if (mValue instanceof String) {
                return (String) mValue;
            } else if (mValue instanceof byte[]) {
                return new String((byte[]) mValue, US_ASCII);
            }
            return null;
        }

        private byte[] getValueAsBytes() {
            if (mValue instanceof byte[]) {
                return (byte[]) mValue;
            }
            return null;
        }

        private Rational[] getValueAsRationals() {
            if (mValue instanceof Rational[]) {
                return (Rational[]) mValue;
            }
            return null;
        }

        private Rational getValueAsRational(Rational defaultValue) {
            Rational[] r = getValueAsRationals();
            if (r == null || r.length < 1) {
                return defaultValue;
            }
            return r[0];
        }

        public Rational getValueAsRational(long defaultValue) {
            Rational defaultVal = new Rational(defaultValue, 1);
            return getValueAsRational(defaultVal);
        }

        public int[] getValueAsInts() {
            if (mValue == null) {
                return null;
            } else if (mValue instanceof long[]) {
                long[] val = (long[]) mValue;
                int[] arr = new int[val.length];
                for (int i = 0; i < val.length; i++) {
                    arr[i] = (int) val[i]; // Truncates
                }
                return arr;
            }
            return null;
        }

        public long[] getValueAsLongs() {
            if (mValue instanceof long[]) {
                return (long[]) mValue;
            }
            return null;
        }

        public Object getValue() {
            return mValue;
        }

        public long forceGetValueAsLong(long defaultValue) {
            long[] l = getValueAsLongs();
            if (l != null && l.length >= 1) {
                return l[0];
            }
            byte[] b = getValueAsBytes();
            if (b != null && b.length >= 1) {
                return b[0];
            }
            Rational[] r = getValueAsRationals();
            if (r != null && r.length >= 1 && r[0].getDenominator() != 0) {
                return (long) r[0].toDouble();
            }
            return defaultValue;
        }

        private String forceGetValueAsString() {
            if (mValue == null) {
                return "";
            } else if (mValue instanceof byte[]) {
                if (mDataType == TYPE_ASCII) {
                    return new String((byte[]) mValue, US_ASCII);
                } else {
                    return Arrays.toString((byte[]) mValue);
                }
            } else if (mValue instanceof long[]) {
                if (((long[]) mValue).length == 1) {
                    return String.valueOf(((long[]) mValue)[0]);
                } else {
                    return Arrays.toString((long[]) mValue);
                }
            } else if (mValue instanceof Object[]) {
                if (((Object[]) mValue).length == 1) {
                    Object val = ((Object[]) mValue)[0];
                    if (val == null) {
                        return "";
                    } else {
                        return val.toString();
                    }
                } else {
                    return Arrays.toString((Object[]) mValue);
                }
            } else {
                return mValue.toString();
            }
        }

        long getValueAt(int index) {
            if (mValue instanceof long[]) {
                return ((long[]) mValue)[index];
            } else if (mValue instanceof byte[]) {
                return ((byte[]) mValue)[index];
            }
            throw new IllegalArgumentException("Cannot get integer value from "
                    + convertTypeToString(mDataType));
        }

        protected String getString() {
            if (mDataType != TYPE_ASCII) {
                throw new IllegalArgumentException("Cannot get ASCII value from "
                        + convertTypeToString(mDataType));
            }
            return new String((byte[]) mValue, US_ASCII);
        }

        protected byte[] getStringByte() {
            return (byte[]) mValue;
        }

        protected Rational getRational(int index) {
            if ((mDataType != TYPE_RATIONAL) && (mDataType != TYPE_UNSIGNED_RATIONAL)) {
                throw new IllegalArgumentException("Cannot get RATIONAL value from "
                        + convertTypeToString(mDataType));
            }
            return ((Rational[]) mValue)[index];
        }

        protected void getBytes(byte[] buf) {
            getBytes(buf, 0, buf.length);
        }

        private void getBytes(byte[] buf, int offset, int length) {
            if ((mDataType != TYPE_UNDEFINED) && (mDataType != TYPE_UNSIGNED_BYTE)) {
                throw new IllegalArgumentException("Cannot get BYTE value from "
                        + convertTypeToString(mDataType));
            }
            System.arraycopy(mValue, 0, buf, offset,
                    (length > mComponentCountActual) ? mComponentCountActual : length);
        }

        int getOffset() {
            return mOffset;
        }

        void setOffset(int offset) {
            mOffset = offset;
        }

        void setHasDefinedCount(boolean d) {
            mHasDefinedDefaultComponentCount = d;
        }

        boolean hasDefinedCount() {
            return mHasDefinedDefaultComponentCount;
        }

        private boolean checkBadComponentCount(int count) {
            return mHasDefinedDefaultComponentCount && mComponentCountActual != count;
        }

        private boolean checkOverflowForUnsignedShort(int[] value) {
            for (int v : value) {
                if (v > UNSIGNED_SHORT_MAX || v < 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkOverflowForUnsignedLong(long[] value) {
            for (long v : value) {
                if (v < 0 || v > UNSIGNED_LONG_MAX) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkOverflowForUnsignedLong(int[] value) {
            for (int v : value) {
                if (v < 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkOverflowForUnsignedRational(Rational[] value) {
            for (Rational v : value) {
                if (v.getNumerator() < 0 || v.getDenominator() < 0
                        || v.getNumerator() > UNSIGNED_LONG_MAX
                        || v.getDenominator() > UNSIGNED_LONG_MAX) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkOverflowForRational(Rational[] value) {
            for (Rational v : value) {
                if (v.getNumerator() < LONG_MIN || v.getDenominator() < LONG_MIN
                        || v.getNumerator() > LONG_MAX || v.getDenominator() > LONG_MAX) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof ExifTag) {
                ExifTag tag = (ExifTag) obj;
                if (tag.mTagId != this.mTagId
                        || tag.mComponentCountActual != this.mComponentCountActual
                        || tag.mDataType != this.mDataType) {
                    return false;
                }
                if (mValue != null) {
                    if (tag.mValue == null) {
                        return false;
                    } else if (mValue instanceof long[]) {
                        if (!(tag.mValue instanceof long[])) {
                            return false;
                        }
                        return Arrays.equals((long[]) mValue, (long[]) tag.mValue);
                    } else if (mValue instanceof Rational[]) {
                        if (!(tag.mValue instanceof Rational[])) {
                            return false;
                        }
                        return Arrays.equals((Rational[]) mValue, (Rational[]) tag.mValue);
                    } else if (mValue instanceof byte[]) {
                        if (!(tag.mValue instanceof byte[])) {
                            return false;
                        }
                        return Arrays.equals((byte[]) mValue, (byte[]) tag.mValue);
                    } else {
                        return mValue.equals(tag.mValue);
                    }
                } else {
                    return tag.mValue == null;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("tag id: %04X\n", mTagId) + "ifd id: " + mIfd + "\ntype: "
                    + convertTypeToString(mDataType) + "\ncount: " + mComponentCountActual
                    + "\noffset: " + mOffset + "\nvalue: " + forceGetValueAsString() + "\n";
        }

    }

    static class IfdData {

        private static final int[] sIfds = {
                IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_1, IfdId.TYPE_IFD_EXIF,
                IfdId.TYPE_IFD_INTEROPERABILITY, IfdId.TYPE_IFD_GPS
        };
        private final int mIfdId;
        private final Map<Short, ExifTag> mExifTags = new HashMap<Short, ExifTag>();
        private int mOffsetToNextIfd = 0;

        IfdData(int ifdId) {
            mIfdId = ifdId;
        }

        static int[] getIfds() {
            return sIfds;
        }

        ExifTag[] getAllTags() {
            return mExifTags.values().toArray(new ExifTag[mExifTags.size()]);
        }

        int getId() {
            return mIfdId;
        }

        ExifTag getTag(short tagId) {
            return mExifTags.get(tagId);
        }

        ExifTag setTag(ExifTag tag) {
            tag.setIfd(mIfdId);
            return mExifTags.put(tag.getTagId(), tag);
        }

        void removeTag(short tagId) {
            mExifTags.remove(tagId);
        }

        private int getTagCount() {
            return mExifTags.size();
        }

        protected int getOffsetToNextIfd() {
            return mOffsetToNextIfd;
        }

        protected void setOffsetToNextIfd(int offset) {
            mOffsetToNextIfd = offset;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof IfdData) {
                IfdData data = (IfdData) obj;
                if (data.getId() == mIfdId && data.getTagCount() == getTagCount()) {
                    ExifTag[] tags = data.getAllTags();
                    for (ExifTag tag : tags) {
                        if (ExifInterfaceExt.isOffsetTag(tag.getTagId())) {
                            continue;
                        }
                        ExifTag tag2 = mExifTags.get(tag.getTagId());
                        if (!tag.equals(tag2)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    static class JpegHeader {
        public static final short SOI = (short) 0xFFD8;
        public static final short APP1 = (short) 0xFFE1;
        public static final short EOI = (short) 0xFFD9;

        private static final short SOF0 = (short) 0xFFC0;
        private static final short SOF15 = (short) 0xFFCF;
        private static final short DHT = (short) 0xFFC4;
        private static final short JPG = (short) 0xFFC8;
        private static final short DAC = (short) 0xFFCC;

        public static boolean isSofMarker(short marker) {
            return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG
                    && marker != DAC;
        }
    }

    static class Rational {

        private final long mNumerator;
        private final long mDenominator;

        public Rational(long nominator, long denominator) {
            mNumerator = nominator;
            mDenominator = denominator;
        }

        public Rational(Rational r) {
            mNumerator = r.mNumerator;
            mDenominator = r.mDenominator;
        }

        public long getNumerator() {
            return mNumerator;
        }

        public long getDenominator() {
            return mDenominator;
        }

        public double toDouble() {
            return mNumerator / (double) mDenominator;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            if (obj instanceof Rational) {
                Rational data = (Rational) obj;
                return mNumerator == data.mNumerator && mDenominator == data.mDenominator;
            }
            return false;
        }

        @Override
        public String toString() {
            return mNumerator + "/" + mDenominator;
        }
    }
    /**@}**/  
}

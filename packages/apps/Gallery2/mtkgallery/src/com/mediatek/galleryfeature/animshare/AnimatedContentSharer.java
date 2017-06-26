package com.mediatek.galleryfeature.animshare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.mediatek.galleryframework.base.Generator;
import com.mediatek.galleryframework.base.MediaCenter;
import com.mediatek.galleryframework.base.MediaData;
import com.mediatek.galleryframework.base.MediaData.MediaType;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.galleryframework.util.RotateProgressFragment;

/**
 * choose sharing type (image or video) for special images and continue sharing
 * accordingly
 */
public class AnimatedContentSharer {
    private static final String TAG = "MtkGallery2/AnimatedContentSharer";
    private static final String DIALOG_TAG_SELECT_SHARE_TYPE = "DIALOG_TAG_SELECT_SHARE_TYPE";
    private static final String DIALOG_TAG_GENERATING_PROGRESS = "DIALOG_TAG_GENERATING_PROGRESS";
    private static final String MIME_TYPE_IMAGE = "image/*";
    private static final String MIME_TYPE_VIDEO = "video/*";
    private static final int MSG_SHARE_AS_VIDEO = 13815; // 2013/08/15
    private final Object reShareLock = new Object();
    private final String KEY_SHARE_AS_GIF = "key_share_as_gif";
    private boolean mIsInGenerating;

    private final Activity mActivity;
    private final Handler mHandler;
    private final MediaCenter mMediaCenter;
    private final IMediaDataGetter mMediaDataGetter;
    private int mShareType;
    private boolean mIsActive;
    private Uri mShareUri = null;
    private String mErrMsg = null;

    public interface IMediaDataGetter {
        MediaData getMediaData();
        String getLocalizedFolderName();
        void redirectCurrentMedia(Uri uri, boolean fromActivityResult);
        void setShareUri(Uri uri);
    }

    public AnimatedContentSharer(Activity activity, MediaCenter mediaCenter, IMediaDataGetter mediaDataUpdator) {
        MtkLog.i(TAG, "<new>");
        mActivity = activity;
        removeOldFragmentByTag(DIALOG_TAG_SELECT_SHARE_TYPE);
        mHandler = new Handler(mActivity.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_SHARE_AS_VIDEO:
                    MtkLog.i(TAG, "<handleMessage> MSG_SHARE_AS_VIDEO, call onShareAsVideoRequested");
                    onShareAsVideoRequested((Intent) (msg.obj));
                    break;
                default:
                    throw new AssertionError(msg.what);
                }
            }
        };
        mMediaCenter = mediaCenter;
        mMediaDataGetter = mediaDataUpdator;
    }

    public void onResume() {
        MtkLog.i(TAG, "<onResume> mIsInGenerating = " + mIsInGenerating);
        mIsActive = true;
        if (!mIsInGenerating) {
            removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
            // If the file of last media data is not exist now, not show select type dialog
            // The outstanding prerequisite: the return value of getVideoSharableImage is last media data.
            MediaData mediaData = getVideoSharableImage();
            if (mediaData == null) {
                removeOldFragmentByTag(DIALOG_TAG_SELECT_SHARE_TYPE);
            } else {
                File file = new File(mediaData.filePath);
                if (!file.exists()) {
                    MtkLog.i(TAG, "<onResume> file [" + mediaData.filePath
                            + "] is not exist now, remove select dialog");
                    removeOldFragmentByTag(DIALOG_TAG_SELECT_SHARE_TYPE);
                }
            }
        }
        synchronized (reShareLock) {
            reShareLock.notifyAll();
        }
    }

    public void onPause() {
        MtkLog.i(TAG, "<onPause> mIsInGenerating = " + mIsInGenerating);
        mIsActive = false;
    }

    public boolean onShareTargetSelected(final Intent intent) {
        tryToShareAsVideo(intent);
        return true;
    }

    private void onShareAsVideoRequested(final Intent finalIntent) {
        MtkLog.i(TAG, "<onShareAsVideoRequested> begin");
        final boolean isShareAsGif = finalIntent.getBooleanExtra(KEY_SHARE_AS_GIF, false);
        final String generateTip = (isShareAsGif ?
                mActivity.getString(R.string.m_generating_gif_tip) :
                    mActivity.getString(R.string.m_generating_tip));
        final String generateFailTip = (isShareAsGif ?
                mActivity.getString(R.string.m_generate_gif_fail) :
                    mActivity.getString(R.string.m_generate_video_fail));
        removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
        /// M: fix fragment InstantiationException: no empty constructor. @{
        final DialogFragment genProgressDialog = RotateProgressFragment.newInstance(generateTip);
        /// @}
        genProgressDialog.setCancelable(false);
        mIsInGenerating = true;
        genProgressDialog.show(mActivity.getFragmentManager(),
                DIALOG_TAG_GENERATING_PROGRESS);

        new Thread() {
            public void run() {
                MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> run begin");
                //M: add thread name
                setName("onShareAsVideoRequested");
                // TODO if item = mCurrentPhoto.getMediaData() is robust enough,
                // then getVideoSharableImage() can be finally removed
                final MediaData item = getVideoSharableImage();
                if (item == null) {
                    dismissDialogAndForwardIntent(finalIntent, genProgressDialog);
                    return;
                }

                MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> get share uri");
                if (isShareAsGif) {
                    mShareUri = getVideoShareUri(item, Generator.VTYPE_SHARE_GIF);
                    finalIntent.setType(MIME_TYPE_IMAGE);
                } else {
                    mShareUri = getVideoShareUri(item, Generator.VTYPE_SHARE);
                    finalIntent.setType(MIME_TYPE_VIDEO);
                }
                MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> mShareUri = " + mShareUri);
                if (mShareUri != null) {
                    finalIntent.putExtra(Intent.EXTRA_STREAM, mShareUri);
                    dismissDialogAndForwardIntent(finalIntent, genProgressDialog);
                } else if (mIsActive) {
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            mIsInGenerating = false;
                            removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
                            Toast.makeText(mActivity,
                                    generateFailTip, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    synchronized (reShareLock) {
                        try {
                            while (!mIsActive) {
                                reShareLock.wait();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> Wake up after mIsActive, send MSG_SHARE_AS_VIDEO");
                    mHandler.obtainMessage(MSG_SHARE_AS_VIDEO, finalIntent)
                            .sendToTarget();
                }
                MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> run end");
            }

            private void dismissDialogAndForwardIntent(
                    final Intent finalIntent,
                    final DialogFragment genProgressDialog) {
                MtkLog.i(TAG, "<Thread-onShareAsVideoRequested> dismissDialogAndForwardIntent");
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mIsInGenerating = false;
                        mMediaDataGetter.setShareUri(mShareUri);
                        removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
                        startActivity(mActivity, finalIntent);
                    }
                });
            }
        } .start();
        MtkLog.i(TAG, "<onShareAsVideoRequested> start thread to generate share content, end");
    }

    private void removeOldFragmentByTag(String tag) {
        MtkLog.i(TAG, "<removeOldFragmentByTag> start, tag = " + tag);
        FragmentManager fragmentManager = mActivity.getFragmentManager();
        DialogFragment oldFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
        MtkLog.i(TAG, "<removeOldFragmentByTag> oldFragment = " + oldFragment);
        if (null != oldFragment) {
            oldFragment.dismissAllowingStateLoss();
            MtkLog.i(TAG, "<removeOldFragmentByTag> remove oldFragment success");
        }
        MtkLog.i(TAG, "<removeOldFragmentByTag> end");
    }

    private void tryToShareAsVideo(Intent intent) {
        MtkLog.i(TAG, "<tryToShareAsVideo> begin");
        final MediaData item = getVideoSharableImage();
        if (item == null) {
            MtkLog.i(TAG, "<tryToShareAsVideo> item is null, startActivity directly");
            startActivity(mActivity, intent);
            return;
        }

        if (item.mediaType.equals(MediaData.MediaType.CONTAINER)
                && item.subType.equals(MediaData.SubType.CONSHOT)) {
            MediaData currentData = mMediaDataGetter.getMediaData();
            if (currentData != null &&
                    !(currentData.mediaType.equals(MediaData.MediaType.CONTAINER))) {
                MtkLog.i(TAG, "<tryToShareAsVideo> currentData not container, startActivity directly");
                startActivity(mActivity, intent);
                return;
            }
            MtkLog.i(TAG, "<tryToShareAsVideo> currentData is conshot, call onShareAsVideoRequested");
            onShareAsVideoRequested(intent);
            MtkLog.i(TAG, "<tryToShareAsVideo> end");
            return;
        }

        removeOldFragmentByTag(DIALOG_TAG_SELECT_SHARE_TYPE);

        final String[] shareFormats = getShareFormatStrings(item);
        final int SHARE_TYPE_VIDEO = 1;
        final int SHARE_TYPE_GIF = 2;
        final SelectDialogFragment shareTypeSelectDialog = SelectDialogFragment
                .newInstance(shareFormats, null,
                        mActivity.getString(R.string.m_share_format_choose_title),
                        true, 0, null);
        final Intent finalIntent = intent;
        shareTypeSelectDialog.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichItemSelect) {
                MtkLog.i(TAG, "<onClick> when tryToShareAsVideo, whichItemSelect = "
                        + whichItemSelect);
                if (!mIsActive) {
                    MtkLog.i(TAG, "<onClick> when tryToShareAsVideo, not active, ignore, return");
                    return;
                }
                if ((whichItemSelect >= 0) && (whichItemSelect < shareFormats.length)) {
                    mShareType = whichItemSelect;
                } else if (whichItemSelect == DialogInterface.BUTTON_POSITIVE) {
                    shareTypeSelectDialog.dismissAllowingStateLoss();
                    if (mShareType == SHARE_TYPE_VIDEO || mShareType == SHARE_TYPE_GIF) {
                        if (mShareType == SHARE_TYPE_GIF) {
                            finalIntent.putExtra(KEY_SHARE_AS_GIF, true);
                        }
                        onShareAsVideoRequested(finalIntent);
                    } else {
                        startActivity(mActivity, finalIntent);
                    }
                }
            }
        });
        mShareType = 0;
        shareTypeSelectDialog.show(mActivity.getFragmentManager(),
                DIALOG_TAG_SELECT_SHARE_TYPE);
        MtkLog.i(TAG, "<tryToShareAsVideo> show shareTypeSelectDialog, return");
    }

    private String[] getShareFormatStrings(MediaData mediaData) {
        if (mediaData.mediaType.equals(MediaData.MediaType.CONTAINER)
                && mediaData.subType.equals(MediaData.SubType.MOTRACK)
                || (mediaData.mediaType == MediaType.MAV)) {
            return new String[] {
                    mActivity.getString(R.string.m_share_format_image),
                    mActivity.getString(R.string.m_share_format_video),
                    mActivity.getString(R.string.m_share_format_gif) };
        } else {
            return new String[] {
                    mActivity.getString(R.string.m_share_format_image),
                    mActivity.getString(R.string.m_share_format_video) };
        }
    }

    private Uri getVideoShareUri(MediaData mediaData, int shareType) {
        if (mediaData == null) {
            return null;
        }
        Generator generator = mMediaCenter.getGenerator(mediaData);
        if (generator == null) {
            return null;
        }
        String sharePath = generator.generateVideo(mediaData, shareType);
        Uri shareUri = ((sharePath == null) ?
                null : Uri.fromFile(new File(sharePath)));
        return shareUri;
    }

    private MediaData getVideoSharableImage() {
        MediaData mediaData = mMediaDataGetter.getMediaData();
        if ((mediaData == null) || mediaData.isVideo
                || (mMediaCenter.getGenerator(mediaData) == null)) {
            return null;
        }
        return mediaData;
    }

    /// M: export as video @{
    private final class MediaItemExporter implements Runnable {
        private static final String PREFIX_IMG = "IMG";
        private static final String PREFIX_VID = "VID";
        private static final String POSTFIX_JPG = ".jpg";
        private static final String POSTFIX_GIF = ".gif";
        private static final String POSTFIX_MP4 = ".mp4";
        private static final String TIME_STAMP_NAME = "_yyyyMMdd_HHmmss_SSS";
        private final static int EXPORT_TYPE_JPG = 0;
        private final static int EXPORT_TYPE_VIDEO = 1;
        private final static int EXPORT_TYPE_GIF = 2;
        private final static int EXPORT_FAIL_TIP = R.string.m_storage_not_enough;

        private final int mShareType;
        private final MediaData mItem;

        private MediaItemExporter(int shareType, MediaData item) {
            this.mShareType = shareType;
            this.mItem = item;
        }

        private void copyFile(File s, File t) {
            FileInputStream fi = null;
            FileOutputStream fo = null;
            FileChannel in = null;
            FileChannel out = null;
            try {
                fi = new FileInputStream(s);
                fo = new FileOutputStream(t);
                in = fi.getChannel();
                out = fo.getChannel();
                in.transferTo(0, in.size(), out);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fi != null) {
                        fi.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (fo != null) {
                        fo.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean saveAs(final Uri uri, final String prefix, final String postfix) {
            MtkLog.d(TAG, "<saveAs> uri=" + uri + ", mItem.filePath=" + mItem.filePath);
            File source = new File(mItem.filePath);
            File folder = source.getParentFile();
            source = new File(uri.getPath());
            String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                    System.currentTimeMillis()));
            File target = new File(folder, prefix + filename + postfix);
            if (!Generator.isStorageSafeForGenerating(folder.getAbsolutePath())) {
                return false;
            }
            copyFile(source, target);
            MediaScannerConnection.scanFile(mActivity, new String[] {target
                    .getAbsolutePath() }, null, new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path1, Uri uri) {
                            MtkLog.d(TAG, "<onScanCompleted> path=" + path1 + ", uri=" + uri);
                            if (uri == null) {
                                MtkLog.e(TAG, "<onScanCompleted> media scanner scanning failed!");
                                return;
                            }
                            mMediaDataGetter.redirectCurrentMedia(uri, false);
                        }
                    });
            return true;
        }

        public void run() {
            removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);

            String albumName = mMediaDataGetter.getLocalizedFolderName();
            final String tip = mActivity.getString(R.string.m_saving_image, albumName);
            final DialogFragment genProgressDialog = RotateProgressFragment
                    .newInstance(tip);
            genProgressDialog.setCancelable(false);
            mIsInGenerating = true;
            genProgressDialog.show(mActivity.getFragmentManager(),
                    DIALOG_TAG_GENERATING_PROGRESS);

            new Thread() {
                private void exportOK() {
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            mIsInGenerating = false;
                            removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
                        }
                    });
                }

                private void exportFail() {
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            mIsInGenerating = false;
                            removeOldFragmentByTag(DIALOG_TAG_GENERATING_PROGRESS);
                            Toast.makeText(
                                    mActivity,
                                    mActivity.getString(EXPORT_FAIL_TIP),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                public void run() {
                    setName("export(motion_track)");

                    Uri exportUri = null;
                    if (mShareType == EXPORT_TYPE_JPG) {
                        exportUri = Uri.fromFile(new File(mItem.filePath));
                        if (saveAs(exportUri, PREFIX_IMG, POSTFIX_JPG)) {
                            exportOK();
                            return;
                        }
                    } else if (mShareType == EXPORT_TYPE_GIF) {
                        exportUri = getVideoShareUri(mItem, Generator.VTYPE_SHARE_GIF);
                        if (exportUri != null) {
                            if (saveAs(exportUri, PREFIX_IMG, POSTFIX_GIF)) {
                                exportOK();
                                return;
                            }
                        }
                    } else if (mShareType == EXPORT_TYPE_VIDEO)  {
                        exportUri = getVideoShareUri(mItem, Generator.VTYPE_SHARE);
                        if (exportUri != null) {
                            if (saveAs(exportUri, PREFIX_VID, POSTFIX_MP4)) {
                                exportOK();
                                return;
                            }
                        }
                    }

                    if (mIsActive) {
                        exportFail();
                        return;
                    } else {
                        synchronized (reShareLock) {
                            try {
                                while (!mIsActive) {
                                    reShareLock.wait();
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        export();
                    }
                }
            } .start();
        }

        public void export() {
            mActivity.runOnUiThread(this);
        }
    }

    public void exportCurrentPhoto() {
        final MediaData item = getVideoSharableImage();

        if ((item == null) ||
                !(item.mediaType.equals(MediaData.MediaType.CONTAINER)
                        && item.subType.equals(MediaData.SubType.MOTRACK))
                        && (item.mediaType != MediaType.MAV)) {
            return;
        }

        removeOldFragmentByTag(DIALOG_TAG_SELECT_SHARE_TYPE);
        final String[] shareFormats = getShareFormatStrings(item);
        final SelectDialogFragment shareTypeSelectDialog = SelectDialogFragment
        .newInstance(shareFormats, null,
                mActivity.getString(R.string.m_share_format_choose_title),
                true, 0, null);
        shareTypeSelectDialog.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichItemSelect) {
                MtkLog.i(TAG, "<onClick> when exportCurrentPhoto, whichItemSelect = "
                        + whichItemSelect);
                if (!mIsActive) {
                    MtkLog.i(TAG, "<onClick> when exportCurrentPhoto, not active, ignore, return");
                    return;
                }
                if ((whichItemSelect >= 0) && (whichItemSelect < shareFormats.length)) {
                    mShareType = whichItemSelect;
                } else if (whichItemSelect == DialogInterface.BUTTON_POSITIVE) {
                    shareTypeSelectDialog.dismissAllowingStateLoss();
                    new MediaItemExporter(mShareType, item).export();
                }
            }
        });
        mShareType = 0;
        shareTypeSelectDialog.show(mActivity.getFragmentManager(),
                DIALOG_TAG_SELECT_SHARE_TYPE);
    }

    public void setErrMsgWhenStartActivityFail(String errMsg) {
        mErrMsg = errMsg;
    }

    private void startActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e1) {
            MtkLog.i(TAG, "<startActivity> ActivityNotFoundException, mErrMsg = " + mErrMsg);
            if (mErrMsg != null && !mErrMsg.equals("")) {
                MtkLog.i(TAG, "<startActivity> start activity fail, show toast: "
                        + mErrMsg);
                Toast.makeText(context, mErrMsg, Toast.LENGTH_SHORT).show();
            }
        }
    }
}

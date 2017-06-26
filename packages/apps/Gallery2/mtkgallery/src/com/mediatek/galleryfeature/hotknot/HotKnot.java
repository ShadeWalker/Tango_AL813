package com.mediatek.galleryfeature.hotknot;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.ActivityChooserView;

import com.android.gallery3d.R;
import com.mediatek.galleryfeature.config.FeatureConfig;
import com.mediatek.galleryframework.util.MtkLog;
import com.mediatek.hotknot.HotKnotAdapter;

public class HotKnot {
    private static final String TAG = "MtkGallery2/HotKnot";

    private static final String ACTION_SHARE = "com.mediatek.hotknot.action.SHARE";
    private static final String EXTRA_SHARE_URIS = "com.mediatek.hotknot.extra.SHARE_URIS";

    public static final int HOTKNOT_SHARE_STATE_NORMAL = 0;
    public static final int HOTKNOT_SHARE_STATE_WAITING = 1;
    public static final int HOTKNOT_SHARE_STATE_LIMIT = 2;

    private HotKnotAdapter mHotKnotAdapter = null;
    private Context mContext;
    private Uri[] mSendUris = null;
    private boolean mIsSupportHotknot = false;
    private MenuItem mHotKnotItem = null;
    private int mShareState;

    public HotKnot(Context context) {
        mContext = context;
        mHotKnotAdapter = HotKnotAdapter.getDefaultAdapter(context);
        if (mHotKnotAdapter == null) {
            mIsSupportHotknot = false;
            MtkLog.d(TAG, "<HotKnot> mHotKnotAdapter is null, disable hotKnot feature");
            return;
        }
        if (FeatureConfig.supportHotKnot) {
            mIsSupportHotknot = true;
        } else {
            mIsSupportHotknot = false;
        }
        MtkLog.d(TAG, "<HotKnot> isHotKnotSupported:" + mIsSupportHotknot);
    }

    public void setUris(Uri[] uris) {
        MtkLog.d(TAG, "setUris");
        if (uris != null) {
            for (Uri uri : uris) {
                MtkLog.d(TAG, "<setUris> uri:" + uri);
            }
        }
        setShareState(HOTKNOT_SHARE_STATE_NORMAL);
        mSendUris = uris;
    }

    public boolean send() {
        MtkLog.d(TAG, "<send> send start");
        if (mShareState == HOTKNOT_SHARE_STATE_LIMIT) {
            Toast.makeText(mContext,
                    mContext.getString(R.string.m_share_limit),
                    Toast.LENGTH_SHORT).show();
            return false;
        } else if (mShareState == HOTKNOT_SHARE_STATE_WAITING) {
            Toast.makeText(mContext, com.android.internal.R.string.wait,
                    Toast.LENGTH_SHORT).show();
            return false;
        } else if (mSendUris == null) {
            MtkLog.d(TAG, "<send> uri is null");
            return false;
        }
        Intent intent = new Intent(ACTION_SHARE);
        intent.putExtra(EXTRA_SHARE_URIS, mSendUris);

        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            MtkLog.d(TAG, "<send> HotKnot share activity not found!");
        }
        setShareState(HOTKNOT_SHARE_STATE_NORMAL);
        return true;
    }

    public void sendUri(Uri uri, String mimeType) {
        MtkLog.d(TAG, "<sendUri> hotKnotSend:" + uri);
        if (uri == null) {
            MtkLog.d(TAG, "<sendUri> uri is null");
            return;
        }
        Uri uris[] = new Uri[1];
        uris[0] = Uri.parse(uri.toString() + "?show=yes&mimetype=" + mimeType);
        setUris(uris);
        send();
    }

    public void sendZip(Uri[] uris) {
        if (uris == null) {
            MtkLog.d(TAG, "<sendZip> uris is null");
            return;
        }
        uris[0] = Uri.parse(uris[0].toString() + "?zip=yes&show=yes");
        setUris(uris);
        send();
    }

    public void updateMenu(Menu menu, int shareAction, int hotKnotAction,
            boolean needShow) {
        if (menu == null) {
            MtkLog.d(TAG, "<updateMenu> hotKnotUpdateMenu: menu is null");
            return;
        }
        mHotKnotItem = menu.findItem(hotKnotAction);
        MenuItem shareItem = menu.findItem(shareAction);
        MtkLog.d(TAG, "<updateMenu> hotKnotUpdateMenu, Enable:" + mIsSupportHotknot);

        if (mHotKnotItem != null && shareItem != null) {
            mHotKnotItem.setVisible(mIsSupportHotknot && needShow);
            ((ActivityChooserView) shareItem.getActionView())
                    .setRecentButtonEnabled(!mIsSupportHotknot);
            MtkLog.d(TAG, "<updateMenu> hotKnotUpdateMenu, success");
        }
    }

    public void showIcon(boolean isShow) {
        if (mHotKnotItem != null && mIsSupportHotknot) {
            mHotKnotItem.setEnabled(isShow);
            mHotKnotItem.setVisible(isShow);
            MtkLog.d(TAG, "<showIcon> icon show status:" + isShow);
        }
    }

    public void setShareState(int state) {
        mShareState = state;
        MtkLog.d(TAG, "<setShareState> share status:" + state);
    }

}

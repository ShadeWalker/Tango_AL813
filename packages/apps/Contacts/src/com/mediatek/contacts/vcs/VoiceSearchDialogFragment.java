package com.mediatek.contacts.vcs;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.util.PhoneCapabilityTester;
import com.mediatek.contacts.util.LogUtils;
import com.android.contacts.R;

public class VoiceSearchDialogFragment extends DialogFragment implements VoiceSearchCircle.CircleDrawListener {

    private static final String TAG = "VoiceSearchDialogFragment";
    private static final int MAX_CONTACTS_COUNT = 6;
    private static final float TIME_SHOW_ITEM_PER = 0.77f;

    private Context mContext;
    private LayoutInflater mInflater;

    private View mSearchPanel; // Show search status
    private LinearLayout mContactsPanel; // Show contacts list
    private VoiceSearchCircle mCirclesPanel;

    private TextView mSearchTextView;
    private ImageView mSearchImageView;

    private int mContactsCount; // contacts count from voice search
    private int mDialogPanelHeight; // 360 dp
    private int mSearchPanelWidth; // 260 dp
    private int mSearchPanelHeight; // 80 dp
    private int mContactRowHeight; // 40 dp
    private int mSearchPanelTranslation; // the distance searchpanel need move
    private int mContactsPanelPaddingTop; // workaround

    private Dialog mDialog = null;
    private ContactPhotoManager mPhotoManager;
    private VoiceSearchRow[] mContactsRowList;
    private VoiceDialogListener mVoiceDialogListener;

    private static final int MSG_START_SEARCHPANEL = 0;
    private static final int MSG_START_LIST = 1;
    private static final int MSG_REFRESH_DELAY = 2;

    // For Status
    private int mStatus;
    private static final int STATUS_SHOWSEARCHPANEL = 0;
    private static final int STATUS_ANIM_SEARCHPANEL = 1;
    private static final int STATUS_ANIM_CONTACTSLIST = 2;
    private static final int STATUS_SHOW_RESULT = 3;

    private static final int TIMER_SHOW_SEARCHPANEL = 520;
    private static final int DELAY_SHOW_ROWLIST = 400;
    private static final int DELAY_SHOW_ROW = 90;

    private VoiceSearchIndicator mVoiceIndicator = null;

    private boolean mIsFirstTimeShow = true;

    public static interface VoiceDialogListener {
        void onRefreshDone();
        void onCancel();
        /**
         * call back when user click the icon on the top view
         */
        boolean onSearchPanelClick();
        void onContactsRowClick(Uri uri, String name);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            LogUtils.d(TAG, "[handleMessage] msg.what:" + msg.what);
            if (msg.what == MSG_START_SEARCHPANEL) {
                if (mContactsCount == 0) {
                    mSearchTextView.setText(R.string.vcs_msg_no_voice_contacts);
                    mSearchImageView.setImageResource(R.drawable.vcs_contact_no_find);
                } else {
                    mSearchImageView.setImageResource(R.drawable.ic_voice_search);
                    mSearchTextView.setText("");
                }
                int aniDuration = (int) (TIMER_SHOW_SEARCHPANEL * mContactsCount / (float) MAX_CONTACTS_COUNT);

                //make sure the mContactsPanel is below at mSearchPanel
                //and they between in the screen center.
                mContactsPanel.setY(mContactsPanel.getTop() + mSearchPanelHeight / 2);
                int toY = mSearchPanel.getTop() - mSearchPanelTranslation;
                if (VcsUtils.isAnimatorEnable()) {
                    ObjectAnimator animator = ObjectAnimator.ofFloat(mSearchPanel, "y", toY);
                    animator.setDuration(aniDuration);
                    animator.start();
                    // delay 400ms
                    int delayShowList = (int) (aniDuration * TIME_SHOW_ITEM_PER);
                    startContactsListAnimation(delayShowList);
                } else {
                    mSearchPanel.setY(toY);
                    startContactsListAnimation(0);
                }
            } else if (msg.what == MSG_START_LIST) {
                if (mContactsCount == 0) {
                    LogUtils.w(TAG, "[handleMessage] mContactsCount is 0");
                    mHandler.sendEmptyMessage(MSG_REFRESH_DELAY);
                    return;
                }
                int index = msg.arg1;
                if (VcsUtils.isAnimatorEnable()) {
                    mContactsRowList[index].startAnimation(mContext);
                }
                LogUtils.i(TAG, "[vcs][performance],show Result list item:" + index + ",time:" + System.currentTimeMillis());
                mContactsRowList[index].getView().setClickable(true);
                mContactsRowList[index].getView().setVisibility(View.VISIBLE);
                if (index == mContactsCount - 1) {
                    int delay = VcsUtils.isAnimatorEnable() ? 100 : 0;
                    mHandler.sendEmptyMessageDelayed(MSG_REFRESH_DELAY, delay);
                    LogUtils.i(TAG, "[vcs][performance],-----show Done-----,time:" + System.currentTimeMillis());
                }
            } else if (msg.what == MSG_REFRESH_DELAY) {
                // Refresh done
                mVoiceDialogListener.onRefreshDone();
                mIsFirstTimeShow = false;
                if (mContactsCount > 0) {
                    mSearchTextView.setText(R.string.vcs_tap_to_search_again);
                    mSearchPanel.setOnClickListener(mOnClickListener);
                }
            }
        }
    };

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //for mSearchPanel
            if (mVoiceDialogListener != null && (v == mSearchPanel)) {
                boolean success = mVoiceDialogListener.onSearchPanelClick();
                if (!success) {
                    LogUtils.w(TAG, "[onClick] started vcs failed..");
                    return;
                }
                mSearchTextView.setText("");
                mSearchPanel.setOnClickListener(null);
            }
        }
    };

    public VoiceSearchDialogFragment(Context context) {

    }

    public VoiceSearchDialogFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.d(TAG, "[onCreate] [vcs-wyx]");
        init();
        if (savedInstanceState == null) {

        } else {
            int state = savedInstanceState.getInt("state");
            dismiss();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        View view = mInflater.inflate(R.layout.vcs_dialog, null);
        mSearchPanel = view.findViewById(R.id.searchpanel);
        mContactsPanel = (LinearLayout) view.findViewById(R.id.resultlist);
        mSearchTextView = (TextView) view.findViewById(R.id.voice_msg);
        mSearchImageView = (ImageView) view.findViewById(R.id.voice_image);
        mCirclesPanel = (VoiceSearchCircle) view.findViewById(R.id.circles);
        mCirclesPanel.mDrawListener = this;
        // mDialog = new Dialog(mContext, R.style.VoiceSearchDialog);
        mDialog = new Dialog(mContext, android.R.style.Theme_Translucent_NoTitleBar);
        mDialog.getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        mDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, 1);
        mDialog.setContentView(view);
        mDialog.setCancelable(true);
        if (savedInstanceState != null) {
            mSearchPanel.setVisibility(View.INVISIBLE);
            mContactsPanel.setVisibility(View.INVISIBLE);
            mCirclesPanel.dismiss();
        }

        mVoiceIndicator = new VoiceSearchIndicator(mSearchImageView);

        mVoiceIndicator.setDrakIcon(R.drawable.ic_voice_search);
        mVoiceIndicator.setOffIcon(R.drawable.ic_voice_search);
        mVoiceIndicator.setLightIcon(R.drawable.ic_voice_search_light);
        return mDialog;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        saveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    private void saveInstanceState(Bundle outState) {
        outState.putInt("state", mStatus);
        outState.putInt("contact_count", mContactsCount);
//        for (int i = 0; i < mContactsCount; i++) {
//            outState.putParcelable("rowList" + i, mContactsRowList[i]);
//        }
//        outState.putParcelableArray("rowList", mContactsRowList);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        mContactsCount = 0;
        if (mVoiceDialogListener != null) {
            mVoiceDialogListener.onCancel();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mContactsCount = 0;
        LogUtils.d(TAG, "[onDismiss] [vcs-wyx]");
        if (mHandler != null) {
            clearHandler();
        }
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        if (mVoiceDialogListener != null) {
            mVoiceDialogListener.onCancel();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtils.d(TAG, "[onDestroy] [vcs-wyx]");
    }

    private void init() {
        mContext = getActivity();
        mInflater = ((Activity) mContext).getLayoutInflater();
        mPhotoManager = getPhotoManager();
        mDialogPanelHeight = (int) mContext.getResources().getDimension(R.dimen.vcs_dialogpanel_height);
        mSearchPanelWidth = (int) mContext.getResources().getDimension(R.dimen.vcs_people_row_width);
        mSearchPanelHeight = (int) mContext.getResources().getDimension(R.dimen.vcs_people_title_height);
        mContactRowHeight = (int) mContext.getResources().getDimension(R.dimen.vcs_people_row_height);

        LogUtils.d(TAG, "[init] [vcs-wyx] : mDialogPanelHeight :" + mDialogPanelHeight + ",mSearchPanelWidth : "
                + mSearchPanelWidth + ",mSearchPanelHeight : " + mSearchPanelHeight + ",mContactRowHeight : "
                + mContactRowHeight);
    }

    private void initRowList(Cursor cursor) {
        if (mContactsPanel != null) {
            mContactsPanel.removeAllViews();
        }
        LogUtils.d(TAG, "[initRowList] [vcs-wyx]");
        if (cursor == null) {
            mContactsCount = 0;
            mContactsRowList = new VoiceSearchRow[0];
        } else {
            try {
                mContactsCount = cursor.getCount();
                mContactsRowList = new VoiceSearchRow[mContactsCount];

                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex("display_name"));
                    String photoUriString = cursor.getString(ContactQuery.CONTACT_PHOTO_URI);
                    Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);

                    long contactId = Long.parseLong(cursor.getString(cursor.getColumnIndex(Contacts._ID)));
                    String lookupKey = cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
                    Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);

                    mContactsRowList[cursor.getPosition()] = generateContactRow(cursor.getPosition(), photoUri, contactName,
                            contactUri, lookupKey);
                }
            } finally {
                if (cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }
        }


        mSearchPanelTranslation = (mContactsCount * mContactRowHeight) / 2;

        for (VoiceSearchRow row : mContactsRowList) {
            mContactsPanel.addView(row.getView());
        }
        mContactsPanel.setVisibility(View.VISIBLE);
    }

    private VoiceSearchRow generateContactRow(int index, Uri photoUri, String contactName, Uri contactUri,
            String lookupKey) {

        VoiceSearchRow contactRow = new VoiceSearchRow();
        LinearLayout  view = (LinearLayout) mInflater.inflate(R.layout.vcs_peoplerow, null);
        ImageView imageView = (ImageView) view.findViewById(R.id.peopleicon);
        if (mPhotoManager != null) {
            DefaultImageRequest request = new DefaultImageRequest(contactName, lookupKey, true);
            mPhotoManager.loadDirectoryPhoto(imageView, photoUri, false, true, request);
        }
        TextView ContactNameView = (TextView) view.findViewById(R.id.peoplename);
        ContactNameView.setText(contactName);

        if (index == 0) {
            ContactNameView.setTextColor(mContext.getResources().getColor(R.color.vcs_people_name_first));
        }
        view.setVisibility(View.INVISIBLE);
        view.setId(index);
        view.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Uri uri = mContactsRowList[v.getId()].getContactUri();
                String name = mContactsRowList[v.getId()].getName();
                mVoiceDialogListener.onContactsRowClick(uri, name);
            }
        });

        contactRow.setName(contactName);
        contactRow.setIcon(photoUri);
        contactRow.setContactUri(contactUri);
        contactRow.setView(view);

        return contactRow;
    }

    private ContactPhotoManager getPhotoManager() {
        return ContactPhotoManager.getInstance(mContext);
    }

    public void setVoiceDialogListener(VoiceDialogListener listener) {
        mVoiceDialogListener = listener;
    }

    /**
     * Contacts search voice contacts done
     *
     * @param cursor
     *            Contain result list
     */
    public void searchDone(Cursor cursor) {
        LogUtils.i(TAG, "[vcs][performance],searchDone(),ready to show Result List,time:" + System.currentTimeMillis());
        int lastContactsCount = mContactsCount;

        initRowList(cursor);
        LogUtils.i(TAG, "[searchDone] mContactsCount:" + mContactsCount + ",mCirclesVisib:" + mCirclesPanel.getVisibility());
        if (mCirclesPanel.getVisibility() == View.VISIBLE) {
            mCirclesPanel.drawLastCircle();
            return;
        }

        clearHandler();

        if (lastContactsCount == mContactsCount) {
            startContactsListAnimation(0);
            return;
        }
        // the mCirclesPanel will be Visible only when first show.
        // if not first show,not show circle again.
        mHandler.sendEmptyMessage(MSG_START_SEARCHPANEL);
    }

    private void clearHandler() {
        mHandler.removeMessages(MSG_START_SEARCHPANEL);
        mHandler.removeMessages(MSG_START_LIST);
        mHandler.removeMessages(MSG_REFRESH_DELAY);
    }

    /**
     *
     * @param cursor
     *            contains voice contacts list
     *            @deprecated
     */
    public void refreshContactsList(Cursor cursor) {
        LogUtils.d(TAG, "[refreshContactsList] [vcs]");
        mContactsPanel.removeAllViews();

        initRowList(cursor);
        mHandler.sendEmptyMessage(MSG_START_SEARCHPANEL);
    }

    private void startContactsListAnimation(int delay) {
        for (int i = 0; i < mContactsCount; i++) {
            Message startListMsg = new Message();
            startListMsg.what = MSG_START_LIST;
            startListMsg.arg1 = i;
            if (VcsUtils.isAnimatorEnable()) {
                mHandler.sendMessageDelayed(startListMsg, delay + DELAY_SHOW_ROW * i);
            } else {
                mHandler.sendMessageDelayed(startListMsg, 0);
            }

        }
    }

    /**
     * Call back from VoiceSearchCircle, means circles are drew done, next step is move search panel
     */
    public void circleDrawDone() {
        // When no contact found, don't show animation
        clearHandler();
        mHandler.sendEmptyMessage(MSG_START_SEARCHPANEL);
    }

    protected int dip2px(float dpValue) {
        final float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     *
     * @param start
     */
    public void updateVcsIndicator(boolean start) {
        if (mContactsCount <= 0) {
            LogUtils.w(TAG, "[updateVcsIndicator] ignore updateVcsIndicator.start:" + start + ",mContactsCount"
                    + mContactsCount);
            return;
        }
        if (mSearchImageView == null || mVoiceIndicator == null) {
            return;
        }
        mVoiceIndicator.updateIndicator(start);
    }

    public boolean isShowing() {
        if (getDialog() != null && getDialog().isShowing()) {
            return true;
        }
        return false;
    }

    /** M: Bug Fix for ALPS01706025 @{ */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
    /** @} */

}

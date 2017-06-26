package com.mediatek.contacts.vcs;

import java.util.ArrayList;

import android.R.menu;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentManager;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.android.contacts.activities.ActionBarAdapter;
import com.android.contacts.activities.ActionBarAdapter.TabState;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.util.PhoneCapabilityTester;

import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.vcs.VcsAppGuide.OnGuideFinishListener;

public class VcsController {
    private static final String TAG = "PeopleActivityVcs";
    private static final long DELAY_TIME_AFTER_TOUCH = 400;
    private static final int MSG_DISPATCH_TOUCH_EVENT = 100;

    private Activity mActivity = null;
    private ActionBarAdapter mActionBarAdapter = null;
    private DefaultContactBrowseListFragment mAllFragment;

    private VoiceSearchManager mVoiceSearchManager = null;
    private VoiceSearchDialogFragment mVoiceSearchDialogFragment;
    private int mContactsCount = 0;
    private MenuItem mVCSItem = null;
    private boolean mIsShowingGuide = false;
    private VoiceSearchIndicator mVoiceIndicator = null;
    private Handler mHandler = new Handler();

    public VcsController(Activity activity, ActionBarAdapter actionBarAdapter,
            DefaultContactBrowseListFragment allFragment) {
        mActivity = activity;
        mActionBarAdapter = actionBarAdapter;
        mAllFragment = allFragment;
        mAllFragment.setContactsLoadListener(mContactsLoadListener);
    }

    /**
     * VoiceListener
     */
    private VoiceSearchManager.VoiceListener mVoiceListener = new VoiceSearchManager.VoiceListener() {
        @Override
        public void onVoiceStop() {
            if (mVoiceSearchDialogFragment.isShowing()) {
                mVoiceSearchDialogFragment.updateVcsIndicator(false);
                mVoiceIndicator.updateIndicator(false);
            } else if (mVoiceIndicator != null && mVoiceIndicator.isIndicatorEnable()) {
                mVoiceIndicator.updateIndicator(false);
            }
        }

        @Override
        public void onVoiceEnable() {
            if (!checkStartVcsCondition()) {
                LogUtils.w(TAG, "[onVoiceEnable] can not enable voice..");
                updateVoice();
                return;
            }
            if (mVCSItem == null) {
                LogUtils.w(TAG, "[onVoiceEnable] the mVCSItem is null..");
            }
            if (mVoiceIndicator == null) {
                LogUtils.w(TAG, "[onVoiceEnable] the mVoiceIndicator is null..");
                return;
            }

            if (mVoiceSearchDialogFragment.isShowing()) {
                mVoiceSearchDialogFragment.updateVcsIndicator(true);
                mVoiceIndicator.updateIndicator(false);
            } else if (mVoiceIndicator != null && !mVoiceIndicator.isIndicatorEnable()) {
                mVoiceIndicator.updateIndicator(true);
            }
        }

        @Override
        public void onVoiceDisble() {
            if (mVoiceSearchDialogFragment.isShowing()) {
                mVoiceSearchDialogFragment.updateVcsIndicator(false);
                mVoiceIndicator.updateIndicator(false);
            } else if (mVoiceIndicator != null && mVoiceIndicator.isIndicatorEnable()) {
                mVoiceIndicator.updateIndicator(false);
            }
        }

        @Override
        public void onVoiceConnected() {
            boolean enable = updateVoice();
            LogUtils.i(TAG, "[onVoiceConnected]...enable:" + enable);
        }
    };

    /**
     * SpeechLister
     */
    private VoiceSearchManager.SpeechLister mSpeechLister = new VoiceSearchManager.SpeechLister() {
        @Override
        public void onSpeechResult(ArrayList<String> nameList) {
            AsyncTask<ArrayList<String>, Void, Cursor> task = new AsyncTask<ArrayList<String>, Void, Cursor>() {
                CursorLoader loader = new CursorLoader(mActivity.getApplicationContext());

                @Override
                public void onPreExecute() {
                    mVoiceSearchManager.disableVoice();
                    LogUtils.i(TAG, "[vcs][performance],onQueryContactsInfo start,time:" + System.currentTimeMillis());
                }

                @Override
                public Cursor doInBackground(ArrayList<String>... names) {
                    return VcsUtils.getCursorByAudioName(mAllFragment, names[0], loader);
                }

                @Override
                protected void onPostExecute(Cursor cursor) {
                    LogUtils.i(TAG, "[vcs][performance],onQueryContactsInfo end,time:" + System.currentTimeMillis());
                    if (cursor == null) {
                        LogUtils.w(TAG, "[onPostExecute][vcs] cursor is null");
                    }
                    try{
                        if (!mActivity.isResumed()) {
                            LogUtils.w(TAG, "[onPostExecute] Activity is not in Resumed,ignore... ");
                            return;
                        }
                        int count = cursor == null ? -1 : cursor.getCount();
                        LogUtils.d(TAG, "[onPostExecute][vcs] cursor counts:" + count);

                        Dialog dialog = mVoiceSearchDialogFragment.getDialog();
                        // Bug fix ALPS01647494, if current tab is not in people list, dismiss the dialog.
                        if (dialog != null && dialog.isShowing() && mActionBarAdapter.getCurrentTab() != TabState.ALL) {
                            dialog.dismiss();
                        }
                        if (dialog != null && dialog.isShowing()) {
                            mVoiceSearchDialogFragment.searchDone(cursor);
                        } else {
                            updateVoice();
                            LogUtils.w(TAG, "[onPostExecute] Dialog is not showing..dialog:" + dialog);
                        }
                    }finally{
                    	/*HQ_zhangjing add for CQ HQ01393920 begin*/
						if( cursor != null ){
                        	cursor.close(); 
						}
						/*HQ_zhangjing add for CQ HQ01393920 end*/
                    }
                }
            };
            // execute
            task.execute(nameList);
        }

        @Override
        public void onSpeechDetected() {
            if (mVCSItem == null || mVoiceIndicator == null) {
                LogUtils.w(TAG, "[onPostExecute] UI not ready,ignore...mVCSItem:" + mVCSItem + ",mVoiceIndicator:"
                        + mVoiceIndicator);
                return;
            }
            LogUtils.d(TAG, "[onSpeechDetected]--------------------------------------------");
            if (!mActivity.isResumed()) {
                LogUtils.w(TAG, "[onPostExecute] Activity is not in Resumed,ignore... ");
                return;
            }
            if (!mVoiceSearchDialogFragment.isShowing() && (mContactsCount > 0)
                    && (mActionBarAdapter.getCurrentTab() == TabState.ALL)) {
                LogUtils.d(TAG, "[onSpeechDetected][vcs] need show wave dialog");
                // remove or dismiss dialog fragment ahead of time
                FragmentManager fManager = mActivity.getFragmentManager();
                dismissDlgFrag(fManager);
                // show dialog
                mVoiceSearchDialogFragment.show(fManager, "VoiceSearchDialog");
            } else {
                LogUtils.w(TAG, "[onSpeechDetected] not show Dialog,mContactsCount:" + mContactsCount + ",tab:"
                        + mActionBarAdapter.getCurrentTab() + ",isDialogShowing:" + mVoiceSearchDialogFragment.isShowing());
            }
        }
    };

    /**
     * VoiceDialogListener
     */
    private VoiceSearchDialogFragment.VoiceDialogListener mVoiceDialogListener = new VoiceSearchDialogFragment.VoiceDialogListener() {
        @Override
        public boolean onSearchPanelClick() {
            updateVoice();
            return true;
        }

        @Override
        public void onRefreshDone() {
            // Refresh done
            Dialog dialog = mVoiceSearchDialogFragment.getDialog();
            // Bug fix ALPS01897914 if current tab is not in people list, dismiss the dialog.
            if (dialog != null && dialog.isShowing()
                    && mActionBarAdapter.getCurrentTab() != TabState.ALL) {
                dialog.dismiss();
            }
            LogUtils.d(TAG, "[refreshDone][vcs] table: " + mActionBarAdapter.getCurrentTab());
        }

        @Override
        public void onContactsRowClick(Uri uri, String name) {
            LogUtils.d(TAG, "[onContactsRowClick] uri:" + uri + ",name:" + name);
            // dismiss dialog
            FragmentManager fManager = mActivity.getFragmentManager();
            dismissDlgFrag(fManager);
            // show contact detail info when clicking the item
            mAllFragment.viewContact(uri);
            // to learn user selected result
            mVoiceSearchManager.setVoiceLearn(name);
        }

        @Override
        public void onCancel() {
            LogUtils.d(TAG, "[onCancel]...");                       
            if (mVoiceSearchManager.isInEnableStatus()){
                if (mVoiceIndicator != null && !mVoiceIndicator.isIndicatorEnable()) {
                    mVoiceIndicator.updateIndicator(true);
                }
            } else {
                updateVoice();
            }
        }
    };

    /**
     * Vcs Guide Listener
     */
    private OnGuideFinishListener mGuideFinishListener = new OnGuideFinishListener() {
        @Override
        public void onGuideFinish() {
            if (VcsUtils.isVcsFeatureEnable()) {
                mIsShowingGuide = false;
                updateVoice();
            }
        }
    };

    /**
     * M: [vcs] for vcs.
     */
    public interface ContactsLoadListener {
        public void onContactsLoad(int count);
    }

    /**
     * DefaultContactBrowseListFragment show count listener.
     */
    private ContactsLoadListener mContactsLoadListener = new ContactsLoadListener() {
        /**
         * M: default contacts list load finished, not to activate VCS if no
         * contacts in database.
         */
        @Override
        public void onContactsLoad(final int count) {
            if (!VcsUtils.isVcsFeatureEnable()) {
                return;
            }
            // can not do transaction in onLoadFinish()
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mContactsCount = count;
                    if (count <= 0) {
                        dismissDlgFrag(mActivity.getFragmentManager());
                        mVoiceSearchManager.disableVoice();
                    } else if (!mVoiceSearchDialogFragment.isShowing()) {
                        updateVoice();
                    }
                }
            });
            return;
        }
    };

    /**
     *
     * @return true if can start vcs,false else;
     */
    private boolean checkStartVcsCondition() {
        int tab = mActionBarAdapter == null ? TabState.DEFAULT : mActionBarAdapter.getCurrentTab();
        boolean isSearchMode = mActionBarAdapter == null ? false : mActionBarAdapter.isSearchMode();

        if ((!mIsShowingGuide) && mActivity.isResumed() && (!isSearchMode) && (tab == TabState.ALL) && mContactsCount > 0
                && VcsUtils.isVcsEnableByUser(mActivity)) {
            return true;
        }
        return false;
    }

    private boolean updateVoice() {
        if (checkStartVcsCondition()) {
            mVoiceSearchManager.enableVoice();
            return true;
        } else {
            mVoiceSearchManager.disableVoice();
        }
        return false;
    }

    /**
     * M: to dismiss the voice search process or results list dialog
     *
     * @param fManager
     */
    private void dismissDlgFrag(FragmentManager fManager) {
        LogUtils.d(TAG, "[dismissDlgFrag][vcs]");
        if (mVoiceSearchDialogFragment.isAdded()) {
            mVoiceSearchDialogFragment.dismiss();
        }
    }

    public void init() {
        if (VcsUtils.isVcsFeatureEnable()) {
            mVoiceSearchManager = new VoiceSearchManager(mActivity);
            mVoiceSearchManager.setSpeechLister(mSpeechLister);
            mVoiceSearchManager.setVoiceListener(mVoiceListener);
            mVoiceSearchDialogFragment = new VoiceSearchDialogFragment(mActivity);
            mVoiceSearchDialogFragment.setVoiceDialogListener(mVoiceDialogListener);
        }
    }

    public void dispatchTouchEventVcs(MotionEvent ev) {
        if (mActionBarAdapter == null) {
            LogUtils.w(TAG, "[dispatchTouchEventVcs] mActionBarAdapter is null");
            return;
        }
        if (VcsUtils.isVcsFeatureEnable()) {
            int action = ev.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) {
                if (mActionBarAdapter.getCurrentTab() == TabState.ALL) {
                    mVoiceSearchManager.disableVoice();
                }
            } else if (action == MotionEvent.ACTION_UP && !mVoiceSearchDialogFragment.isShowing()) {
                if (mActionBarAdapter.getCurrentTab() == TabState.ALL) {
                    mHandler.removeCallbacks(mUpdateVoiceRunnable);
                    mHandler.postDelayed(mUpdateVoiceRunnable, DELAY_TIME_AFTER_TOUCH);
                }
            }
        }
    }

    private Runnable mUpdateVoiceRunnable = new Runnable() {
        @Override
        public void run() {
            updateVoice();
        }
    };

    public void onPauseVcs() {
        if (VcsUtils.isVcsFeatureEnable()) {
            LogUtils.i(TAG, "[onPause] [vcs] call stopVoiceSearch");
            // remove or dismiss no contacts dialog fragment when "home"
            FragmentManager fManager = mActivity.getFragmentManager();
            dismissDlgFrag(fManager);
            mVoiceSearchManager.stopVoice();
        }
    }

    public void onVoiceDialogClick(View v) {
        LogUtils.d(TAG, "[onClickDialog][vcs] view id:" + v.getId());
        FragmentManager fManager = mActivity.getFragmentManager();
        dismissDlgFrag(fManager);
    }

    public void onResumeVcs() {
        if (VcsUtils.isVcsFeatureEnable()) {
            // /M:show vcs app guide when first launch contacts
            mIsShowingGuide = mVoiceSearchManager.setVcsAppGuideVisibility(mActivity, true, mGuideFinishListener);
            updateVoice();
        }
    }

    public void onDestoryVcs() {
        if (VcsUtils.isVcsFeatureEnable()) {
            // destroy the last loader with id:0
            mVoiceSearchManager.destoryVoice();
            mVoiceSearchManager.setVcsAppGuideVisibility(mActivity, false, mGuideFinishListener);
            if (mVoiceIndicator != null) {
                mVoiceIndicator.updateIndicator(false);
                mVoiceIndicator = null;
            }
        }
    }

    public void onPageSelectedVcs() {
        if (VcsUtils.isVcsFeatureEnable() && mActionBarAdapter.getCurrentTab() == TabState.ALL) {
            LogUtils.d(TAG, "[onPageSelected] [vcs] onVoiceSearchProcess.");
            updateVoice();
        } else {
            LogUtils.d(TAG, "[onPageSelected] [vcs] " +
                    "disbale vcs and current tab:" + mActionBarAdapter.getCurrentTab());
        }
    }

    public void onActionVcs(int action) {
        switch (action) {
        case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
            // M: add for VCS-need to stop voice search contacts.@{
            if (VcsUtils.isVcsFeatureEnable()) {
                mVoiceSearchManager.disableVoice();
            }
            break;
        case ActionBarAdapter.Listener.Action.STOP_SEARCH_MODE:
            // M: add for VCS-need to restart voice search contacts.@{
            if (VcsUtils.isVcsFeatureEnable()) {
                updateVoice();
            }
            break;

        default:
            break;
        }

    }

    public void onSelectedTabChangedEx() {
        if (VcsUtils.isVcsFeatureEnable()) {
            LogUtils.i(TAG, "[onSelectedTabChanged] [vcs] onVoiceSearchProcess");
            updateVoice();
        }
    }

    public void onCreateOptionsMenuVcs(Menu men) {
        if (VcsUtils.isVcsFeatureEnable()) {
            mVCSItem = men.findItem(com.android.contacts.R.id.menu_vcs);
            // set item not clickable if need
            if (mVCSItem != null) {
                if (mVoiceIndicator != null) {
                    mVoiceIndicator.updateIndicator(false);
                    mVoiceIndicator = null;
                }
                mVoiceIndicator = new VoiceSearchIndicator(mVCSItem);
                updateVoice();
            }
        }
    }

    public void onPrepareOptionsMenuVcs(Menu menu) {
        boolean showVcsItem = VcsUtils.isVcsFeatureEnable() && (mActionBarAdapter.getCurrentTab() == TabState.ALL)
                && (mActionBarAdapter.isSearchMode() == false);
        MenuItem item = menu.findItem(com.android.contacts.R.id.menu_vcs);
        if (item != null) {
            item.setVisible(showVcsItem);
        }
        if (VcsUtils.isVcsFeatureEnable() && !showVcsItem) {
            // if current not show vcs item,stop voice service.
            mVoiceSearchManager.disableVoice();
        }
        if (VcsUtils.isVcsFeatureEnable() && !VcsUtils.isVcsEnableByUser(mActivity) && showVcsItem) {
            // if current will show vcs item,and the vcs if disable by user.show
            // disable icon.
            // make sure the vcs be stop.
            mVoiceSearchManager.disableVoice();
            if (mVoiceIndicator == null) {
                LogUtils.w(TAG, "[onPrepareOptionsMenuVcs] the mVoiceIndicator is null..");
            } else {
                mVoiceIndicator.updateIndicator(false);
            }
        }
    }

    public void onVcsItemSelected() {
        boolean enableByUser = VcsUtils.isVcsEnableByUser(mActivity);
        boolean enableByUserCurrent = !enableByUser;
        VcsUtils.setVcsEnableByUser(enableByUserCurrent, mActivity);
        if (enableByUserCurrent) {
            updateVoice();
        } else {
            mVoiceSearchManager.disableVoice();
            Dialog dialog = mVoiceSearchDialogFragment == null ? null : mVoiceSearchDialogFragment.getDialog();
            // Bug fix ALPS01647494, if current tab is not in people list, dismiss the dialog.
            if (dialog != null && dialog.isShowing()) {
                LogUtils.i(TAG, "[onVcsItemSelected] dismiss Dialog..");
                dialog.dismiss();
            }
        }
    }

}

/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.calllog;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper;
import com.mediatek.dialer.calllog.PhoneAccountPickerDialog;
import com.mediatek.dialer.calllog.PhoneAccountPickerDialog.AccountItem;
import com.mediatek.dialer.calllog.PhoneAccountPickerDialog.PhoneAccountPickListener;
import com.mediatek.dialer.activities.CallLogMultipleDeleteActivity;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.ext.ICallLogExtension.ICallLogAction;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class CallLogActivity extends Activity implements CallLogQueryHandler.Listener,
    ViewPager.OnPageChangeListener
    /// M: for Plug-in @{
    , ICallLogAction {
    /// @}
    private Handler mHandler;
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private CallLogFragment mAllCallsFragment;
    private CallLogFragment mMissedCallsFragment;
    private CallLogFragment mVoicemailFragment;
    private VoicemailStatusHelper mVoicemailStatusHelper;

    private static final int WAIT_FOR_VOICEMAIL_PROVIDER_TIMEOUT_MS = 300;
    private boolean mSwitchToVoicemailTab;

    private CharSequence[] mTabTitles;

    private static final int TAB_INDEX_ALL = 0;
    /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
    private static final boolean TAB_INCOMING_OUTGOING_ENABLE = DialerFeatureOptions
            .isCallLogIOFilterEnabled();
    private static final int TAB_INDEX_INCOMING = 1;
    private static final int TAB_INDEX_OUTGOING = 2;
    private static final int TAB_INDEX_MISSED = TAB_INCOMING_OUTGOING_ENABLE ? 3 : 1;
    private static final int TAB_INDEX_VOICEMAIL = TAB_INCOMING_OUTGOING_ENABLE ? 4 : 2;

    private static final int TAB_INDEX_COUNT_DEFAULT = TAB_INCOMING_OUTGOING_ENABLE ? 4 : 2;
    private static final int TAB_INDEX_COUNT_WITH_VOICEMAIL = TAB_INCOMING_OUTGOING_ENABLE ? 5 : 3;

    private CallLogFragment mIncomingCallsFragment;
    private CallLogFragment mOutgoingCallsFragment;
    /// @}

    private boolean mHasActiveVoicemailProvider;

    private final Runnable mWaitForVoicemailTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            mViewPagerTabs.setViewPager(mViewPager);
            mViewPager.setCurrentItem(TAB_INDEX_ALL);
            mSwitchToVoicemailTab = false;
        }
    };

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            /** M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{ */
            /* Original code
            switch (position) {
                case TAB_INDEX_ALL:
                    return new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
                case TAB_INDEX_MISSED:
                    return new CallLogFragment(Calls.MISSED_TYPE);
                case TAB_INDEX_VOICEMAIL:
                    return new CallLogFragment(Calls.VOICEMAIL_TYPE);
                case TAB_INDEX_INCOMING:
                    return new CallLogFragment(Calls.INCOMING_TYPE);
                case TAB_INDEX_OUTGOING:
                    return new CallLogFragment(Calls.OUTGOING_TYPE);
            }
            */
            if (position == TAB_INDEX_ALL) {
                return new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
            } else if (position == TAB_INDEX_MISSED) {
                return new CallLogFragment(Calls.MISSED_TYPE);
            } else if (position == TAB_INDEX_VOICEMAIL) {
                return new CallLogFragment(Calls.VOICEMAIL_TYPE);
            } else if (position == TAB_INDEX_INCOMING) {
                return new CallLogFragment(Calls.INCOMING_TYPE);
            } else if (position == TAB_INDEX_OUTGOING) {
                return new CallLogFragment(Calls.OUTGOING_TYPE);
            }
            /** @} */
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
			position = ExtensionManager.getInstance().getCallLogExtension().getPosition(position);
            final CallLogFragment fragment =
                    (CallLogFragment) super.instantiateItem(container, position);
            /** M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{ */
            /* Original code
            switch (position) {
                case TAB_INDEX_ALL:
                    mAllCallsFragment = fragment;
                    break;
                case TAB_INDEX_MISSED:
                    mMissedCallsFragment = fragment;
                    break;
                case TAB_INDEX_VOICEMAIL:
                    mVoicemailFragment = fragment;
                    break;
                case TAB_INDEX_INCOMING:
                    mIncomingCallsFragment = fragment;
                    break;
                case TAB_INDEX_OUTGOING:
                    mOutgoingCallsFragment = fragment;
                    break;
            }*/

            if (position == TAB_INDEX_ALL) {
                mAllCallsFragment = fragment;
            } else if (position == TAB_INDEX_MISSED) {
                mMissedCallsFragment = fragment;
            } else if (position == TAB_INDEX_VOICEMAIL) {
                mVoicemailFragment = fragment;
            } else if (position == TAB_INDEX_INCOMING) {
                mIncomingCallsFragment = fragment;
            } else if (position == TAB_INDEX_OUTGOING) {
                mOutgoingCallsFragment = fragment;
            }
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }

        @Override
        public int getCount() {
            int count = mHasActiveVoicemailProvider ? TAB_INDEX_COUNT_WITH_VOICEMAIL :
                    TAB_INDEX_COUNT_DEFAULT;
            count = ExtensionManager.getInstance().getCallLogExtension().getTabCount(count);
            return count;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            TouchPointManager.getInstance().setPoint((int) ev.getRawX(), (int) ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
        if (TAB_INCOMING_OUTGOING_ENABLE) {
            setContentView(R.layout.mtk_call_log_activity);
        } else {
            setContentView(R.layout.call_log_activity);
        }
        /// @}
        getWindow().setBackgroundDrawable(null);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setElevation(0);

        int startingTab = TAB_INDEX_ALL;
        final Intent intent = getIntent();
        if (intent != null) {
			try{
            	final int callType = intent.getIntExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, -1);
			
            if (callType == CallLog.Calls.MISSED_TYPE) {
                startingTab = TAB_INDEX_MISSED;
            } else if (callType == CallLog.Calls.VOICEMAIL_TYPE) {
                startingTab = TAB_INDEX_VOICEMAIL;
            }

			}catch(RuntimeException e){
					e.printStackTrace();
			}
        }

        mTabTitles = new CharSequence[TAB_INDEX_COUNT_WITH_VOICEMAIL];
        mTabTitles[0] = getString(R.string.call_log_all_title);
        mTabTitles[1] = getString(R.string.call_log_missed_title);
        mTabTitles[2] = getString(R.string.call_log_voicemail_title);
        /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
        if (TAB_INCOMING_OUTGOING_ENABLE) {
            initTabIcons();
        }
        /// @}

        mViewPager = (ViewPager) findViewById(R.id.call_log_pager);

        mViewPagerAdapter = new ViewPagerAdapter(getFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setOnPageChangeListener(this);

        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.viewpager_header);

        if (startingTab == TAB_INDEX_VOICEMAIL) {
            // The addition of the voicemail tab is an asynchronous process, so wait till the tab
            // is added, before attempting to switch to it. If the querying of CP2 for voicemail
            // providers takes too long, give up and show the first tab instead.
            mSwitchToVoicemailTab = true;
            mHandler.postDelayed(mWaitForVoicemailTimeoutRunnable,
                    WAIT_FOR_VOICEMAIL_PROVIDER_TIMEOUT_MS);
        } else {
            mViewPagerTabs.setViewPager(mViewPager);
            mViewPager.setCurrentItem(startingTab);
        }

        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();

        /** M: Fix CR ALPS01588898. Save and restore the fragments. @{ */
        restoreFragments(savedInstanceState);
        /** @} */
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallLogQueryHandler callLogQueryHandler =
                new CallLogQueryHandler(this.getContentResolver(), this);
        callLogQueryHandler.fetchVoicemailStatus();
        sendScreenViewForChildFragment(mViewPager.getCurrentItem());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.call_log_options, menu);

        /// M: for Plug-in
        ExtensionManager.getInstance().getCallLogExtension().createCallLogMenu(this, menu, mViewPagerTabs, this);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);

        /// M: [Multi-Delete] for CallLog multiple delete @{
        final MenuItem itemDelete = menu.findItem(R.id.delete);
        CallLogFragment fragment = getCurrentCallLogFragment();
        if (fragment != null && itemDelete != null) {
            final CallLogAdapter adapter = fragment.getAdapter();
            itemDelete.setVisible(DialerFeatureOptions.MULTI_DELETE
                    /** M: Fix CR ALPS01884065. The isEmpty() be overrided with loading state of data.
                     *  Here, it should not care about the loading state. So, use getCount() to check
                     *  is the adapter really empty. @{ */
                    /*
                    && adapter != null && !adapter.isEmpty())
                    */
                    && adapter != null && adapter.getCount() > 0);
        }
        ///@}

        if (mAllCallsFragment != null && itemDeleteAll != null) {
            // If onPrepareOptionsMenu is called before fragments are loaded, don't do anything.
            final CallLogAdapter adapter = mAllCallsFragment.getAdapter();
            /** M: Fix CR ALPS01884065. The isEmpty() be overrided with loading state of data.
             *  Here, it should not care about the loading state. So, use getCount() to check
             *  is the adapter really empty. @{ */
            /*
            itemDeleteAll.setVisible(adapter != null && !adapter.isEmpty())
            */
            itemDeleteAll.setVisible(adapter != null && adapter.getCount() > 0);
        }

        /// M :[Call Log Account Filter] @{
        // hide choose account menu if only one or no account
        final MenuItem itemChooseAccount = menu.findItem(R.id.select_account);
        if (mAllCallsFragment != null && itemChooseAccount != null) {
            TelecomManager telecomManager =
                (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            itemChooseAccount.setVisible(DialerFeatureOptions.CALL_LOG_ACCOUNT_FILTER
                    && telecomManager.hasMultipleCallCapableAccounts());
        }
        /// @}

        /// M: for plug-in
        ExtensionManager.getInstance().getCallLogExtension().prepareCallLogMenu(menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                /// M: for Plug-in @{
                if (ExtensionManager.getInstance().getCallLogExtension().onHomeButtonClick(
                        mViewPagerAdapter, item)) {
                    return true;
                }
                /// @}

                final Intent intent = new Intent(this, DialtactsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            case R.id.delete_all:
                ClearCallLogDialog.show(getFragmentManager());
                return true;
            /// M :[Call Log Account Filter] @{
            case R.id.select_account:
                // phone account Selection, select an account to filter out the Calllog
                final List<AccountItem> items = createPhoneAccountItems();
                /** M: [ALPS01963693] show dialog fragment with allowing state loss @{ */
                PhoneAccountPickerDialog selectAccountFragment = PhoneAccountPickerDialog.build(this)
                .setData(items)
                .setShowSelection(true)
                .setSelection(getPreferedAccountItemIndex(items))
                .addListener(new PhoneAccountPickListener() {
                    @Override
                    public void onPhoneAccountPicked(String selectId) {
                        PhoneAccountInfoHelper.INSTANCE.setPreferAccountId(selectId);
                    }
                });
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.add(selectAccountFragment, "select_account");
                ft.commitAllowingStateLoss();
                /** @} */
                return true;
             /// @}
                /// M: [Multi-Delete] for CallLog multiple delete @{
            case R.id.delete:
                if (DialerFeatureOptions.MULTI_DELETE) {
                    final Intent delIntent = new Intent(this, CallLogMultipleDeleteActivity.class);
                    delIntent.putExtra(CallLogQueryHandler.CALL_LOG_TYPE_FILTER,
                            getCurrentCallLogFilteType());
                    startActivity(delIntent);
                }
                /// M: CR: ALPS01883767, Ensure menu item is invisible after clicking on delete item for some wired scene@{
                item.setVisible(false);
                /// @}
                return true;
                ///@}
        }
        return super.onOptionsItemSelected(item);
    }

    private int getPreferedAccountItemIndex(List<AccountItem> data) {
        String id = PhoneAccountInfoHelper.INSTANCE.getPreferAccountId();
        if (!TextUtils.isEmpty(id) && data != null) {
            for (int i = 0; i < data.size(); i++) {
                if (id.equals(data.get(i).id))
                    return i;
            }
        }
        return 0;
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (this.isFinishing()) {
            return;
        }

        mHandler.removeCallbacks(mWaitForVoicemailTimeoutRunnable);
        // Update mHasActiveVoicemailProvider, which controls the number of tabs displayed.
        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        if (activeSources > 0 != mHasActiveVoicemailProvider) {
            mHasActiveVoicemailProvider = activeSources > 0;
            mViewPagerAdapter.notifyDataSetChanged();
            mViewPagerTabs.setViewPager(mViewPager);
            if (mSwitchToVoicemailTab) {
                mViewPager.setCurrentItem(TAB_INDEX_VOICEMAIL, false);
            }
        } else if (mSwitchToVoicemailTab) {
            // The voicemail tab was requested, but it does not exist because there are no
            // voicemail sources. Just fallback to the first item instead.
            mViewPagerTabs.setViewPager(mViewPager);
        }
    }

    @Override
    public boolean onCallsFetched(Cursor statusCursor) {
        // Return false; did not take ownership of cursor
        return false;
    }

    @Override
    public void onBackPressed() {
        /// M: for Plug-in @{
        ExtensionManager.getInstance().getCallLogExtension().onBackPressed(mViewPagerAdapter, this);
        /// @}
    }

    /// M: for Plug-in @{
    @Override
    public void processBackPressed() {
        super.onBackPressed();
    }
    /// @}

    /// M: for Plug-in @{
    @Override
    public void updateCallLogScreen() {
        mViewPagerAdapter.notifyDataSetChanged();
        mViewPagerTabs.setViewPager(mViewPager);
		/// M: Fix CR ALPS01969424. reset the adapter@{
		mViewPager.setAdapter(mViewPagerAdapter);
		/// @}
        //mViewPager.setCurrentItem(TAB_INDEX_ALL);
        if (mAllCallsFragment != null && mAllCallsFragment.isAdded() && mAllCallsFragment.isVisible()) {
			LogUtils.d(TAG, "updateCallLogScreen mAllCallsFragment");
            mAllCallsFragment.forceToRefreshData();
        } else {
			final FragmentTransaction ftAll = getFragmentManager().beginTransaction();
            ftAll.hide(mAllCallsFragment);
            ftAll.commit();
		}
		
        if (mMissedCallsFragment != null && mMissedCallsFragment.isAdded() && mMissedCallsFragment.isVisible()) {
			LogUtils.d(TAG, "updateCallLogScreen mMissedCallsFragment");
			mMissedCallsFragment.forceToRefreshData();
        } else {
			final FragmentTransaction ftMiss = getFragmentManager().beginTransaction();
            ftMiss.hide(mMissedCallsFragment);
            ftMiss.commit();
		}
        /*if (mMissedCallsFragment != null && mMissedCallsFragment.isAdded()) {
            mMissedCallsFragment.forceToRefreshData();
        }*/
    }
    /// @}

    /// M: [Multi-Delete] For CallLog delete @{
    @Override
    public void onCallsDeleted() {
        // Do nothing
    }
    /// @}

    /// ------------------------------Mediatek--------------------------------------
    /** M: Fix CR ALPS01588898. Save and restore the fragments. @{ */
    private static final String TAG = "CallLogActivity";
    private static final String FRAGMENT_TAG_ALL = "fragment_tag_all";
    private static final String FRAGMENT_TAG_MISSED = "fragment_tag_missed";
    private String mAllCallsFragmentTag = null;
    private String mMissedCallsFragmentTag = null;
    /// [CallLog I/O Filter] for support calllog incoming/outgoing filter
    private static final String FRAGMENT_TAG_INCOMING = "fragment_tag_incoming";
    private static final String FRAGMENT_TAG_OUTGOING = "fragment_tag_outgoing";
    private String mIncomingCallsFragmentTag = null;
    private String mOutgoingCallsFragmentTag = null;
    /** @} */

    /** M: Fix CR ALPS01588898. Save and restore the fragments. @{ */
    private void restoreFragments(Bundle savedInstanceState) {
        LogUtils.d(TAG, "restoreFragments savedInstanceState= " + savedInstanceState);
        if (savedInstanceState != null) {
            mAllCallsFragmentTag = savedInstanceState.getString(FRAGMENT_TAG_ALL, null);
            mMissedCallsFragmentTag = savedInstanceState.getString(FRAGMENT_TAG_MISSED, null);
        }
        if (mAllCallsFragment == null && mAllCallsFragmentTag != null) {
            mAllCallsFragment = (CallLogFragment) getFragmentManager()
                    .findFragmentByTag(mAllCallsFragmentTag);
            LogUtils.d(TAG, "onResume findFragment all ~ " + mAllCallsFragment);
        }
        if (mMissedCallsFragment == null && mMissedCallsFragmentTag != null) {
            mMissedCallsFragment = (CallLogFragment) getFragmentManager()
                    .findFragmentByTag(mMissedCallsFragmentTag);
            LogUtils.d(TAG, "onResume findFragment missed ~ " + mMissedCallsFragment);
        }
        /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
        if (TAB_INCOMING_OUTGOING_ENABLE) {
            if (savedInstanceState != null) {
                mIncomingCallsFragmentTag = savedInstanceState
                        .getString(FRAGMENT_TAG_INCOMING, null);
                mOutgoingCallsFragmentTag = savedInstanceState
                        .getString(FRAGMENT_TAG_OUTGOING, null);
            }
            if (mIncomingCallsFragment == null && mIncomingCallsFragmentTag != null) {
                mIncomingCallsFragment = (CallLogFragment) getFragmentManager()
                        .findFragmentByTag(mIncomingCallsFragmentTag);
                LogUtils.d(TAG, "onResume findFragment incoming ~ " + mIncomingCallsFragment);
            }
            if (mOutgoingCallsFragment == null && mOutgoingCallsFragmentTag != null) {
                mOutgoingCallsFragment = (CallLogFragment) getFragmentManager()
                        .findFragmentByTag(mOutgoingCallsFragmentTag);
                LogUtils.d(TAG, "onResume findFragment outgoing ~ " + mOutgoingCallsFragment);
            }
        }
        ExtensionManager.getInstance().getCallLogExtension().restoreFragments(this,
                savedInstanceState, mViewPagerAdapter, mViewPagerTabs);
        /// @}
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAllCallsFragment != null) {
            outState.putString(FRAGMENT_TAG_ALL, mAllCallsFragment.getTag());
        }
        if (mMissedCallsFragment != null) {
            outState.putString(FRAGMENT_TAG_MISSED, mMissedCallsFragment.getTag());
        }
        /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
        if (TAB_INCOMING_OUTGOING_ENABLE) {
            if (mIncomingCallsFragment != null) {
                outState.putString(FRAGMENT_TAG_INCOMING, mIncomingCallsFragment.getTag());
            }
            if (mOutgoingCallsFragment != null) {
                outState.putString(FRAGMENT_TAG_OUTGOING, mOutgoingCallsFragment.getTag());
            }
        }
        ExtensionManager.getInstance().getCallLogExtension().onSaveInstanceState(outState);
        /// @}
    }
    /** @} */

    /**
     * M: [Multi-Delete] Get the current displaying call log fragment. @{
     */
    public CallLogFragment getCurrentCallLogFragment() {
        int position = mViewPager.getCurrentItem();
        if (position == TAB_INDEX_ALL) {
            return mAllCallsFragment;
        } else if (position == TAB_INDEX_MISSED) {
            return mMissedCallsFragment;
        } else if (position == TAB_INDEX_VOICEMAIL) {
            return mVoicemailFragment;
        } else if (position == TAB_INDEX_INCOMING) {
            return mIncomingCallsFragment;
        } else if (position == TAB_INDEX_OUTGOING) {
            return mOutgoingCallsFragment;
        }
        return null;
    }

    public int getCurrentCallLogFilteType() {
        int position = mViewPager.getCurrentItem();
        if (position == TAB_INDEX_ALL) {
            return CallLogQueryHandler.CALL_TYPE_ALL;
        } else if (position == TAB_INDEX_MISSED) {
            return Calls.MISSED_TYPE;
        } else if (position == TAB_INDEX_VOICEMAIL) {
            return Calls.VOICEMAIL_TYPE;
        } else if (position == TAB_INDEX_INCOMING) {
            return Calls.INCOMING_TYPE;
        } else if (position == TAB_INDEX_OUTGOING) {
            return Calls.OUTGOING_TYPE;
        }
        return CallLogQueryHandler.CALL_TYPE_ALL;
    }
    /** @} */

    /** M: [CallLog I/O Filter] for support calllog incoming/outgoing filter. @{ */
    private void initTabIcons() {
        CallTypeIconsView.Resources resources = new CallTypeIconsView.Resources(this);
        mTabTitles[1] = createSpannableString(resources.incoming);
        mTabTitles[2] = createSpannableString(resources.outgoing);
        mTabTitles[3] = createSpannableString(resources.missed);
        mTabTitles[4] = createSpannableString(resources.voicemail);
    }

    private SpannableString createSpannableString(Drawable drawable) {
        //Enlarge the icon by 1.5 times
        drawable.setBounds(0, 0, (drawable.getIntrinsicWidth() * 3) / 2,
                (drawable.getIntrinsicHeight() * 3) / 2);
        SpannableString sp = new SpannableString("i");
        ImageSpan iconsp = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
        sp.setSpan(iconsp, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return sp;
    }
    /** @} */

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
	    ExtensionManager.getInstance().getCallLogExtension().setPosition(position);
        mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
    }

    @Override
    public void onPageSelected(int position) {
        if (isResumed()) {
            sendScreenViewForChildFragment(position);
        }
        mViewPagerTabs.onPageSelected(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mViewPagerTabs.onPageScrollStateChanged(state);
    }

    private void sendScreenViewForChildFragment(int position) {
        AnalyticsUtil.sendScreenView(CallLogFragment.class.getSimpleName(), this,
                getFragmentTagForPosition(position));
    }

    /**
     * Returns the fragment located at the given position in the {@link ViewPagerAdapter}. May
     * be null if the position is invalid.
     */
    private String getFragmentTagForPosition(int position) {
        /** M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{ */
        /* Original code
        switch (position) {
            case TAB_INDEX_ALL:
                return "All";
            case TAB_INDEX_MISSED:
                return "Missed";
            case TAB_INDEX_VOICEMAIL:
                return "Voicemail";
            case TAB_INDEX_INCOMING:
                return "Incoming";
            case TAB_INDEX_OUTGOING:
                return "Outgoing";
        }
        return null;
        */
        if (position == TAB_INDEX_ALL) {
            return "All";
        } else if (position == TAB_INDEX_MISSED) {
            return "Missed";
        } else if (position == TAB_INDEX_VOICEMAIL) {
            return "Voicemail";
        } else if (position == TAB_INDEX_INCOMING) {
            return "Incoming";
        } else if (position == TAB_INDEX_OUTGOING) {
            return "Outgoing";
        }
        return null;
    }

    /// M: [Call Log Account Filter] @{
    private List<AccountItem> createPhoneAccountItems() {
        List<AccountItem> accountItems = new ArrayList<AccountItem>();
        // fist item is "all accounts"
        accountItems.add(new AccountItem(R.string.all_accounts,
                PhoneAccountInfoHelper.FILTER_ALL_ACCOUNT_ID));

        final TelecomManager telecomManager = (TelecomManager) getApplicationContext()
                .getSystemService(Context.TELECOM_SERVICE);
        final List<PhoneAccountHandle> accounts = telecomManager.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle account : accounts) {
            accountItems.add(new AccountItem(account));
        }
        return accountItems;
    }
    /// @}
}

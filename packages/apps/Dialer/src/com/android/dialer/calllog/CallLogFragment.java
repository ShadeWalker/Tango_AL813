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

package com.android.dialer.calllog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.VoicemailContract.Status;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.list.ListsFragment.HostInterface;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.EmptyLoader;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialerbind.ObjectFactory;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper;
import com.mediatek.dialer.calllog.PhoneAccountInfoHelper.AccountInfoListener;
import com.mediatek.dialer.ext.ExtensionManager;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.LogUtils;

import java.util.List;

import com.android.incallui.InCallApp;



// / Added by guofeiyao
import android.widget.Toast;
import android.view.MotionEvent;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.widget.AdapterView;
import huawei.android.widget.TimeAxisWidget;

import com.android.dialer.calllog.CallLogListItemViews;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.activities.DialerPlugIn;


// / End
//add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
import android.telephony.ServiceState;

import com.mediatek.telephony.TelephonyManagerEx;

import android.telephony.PhoneStateListener;

import com.android.internal.telephony.PhoneConstants;

import android.telephony.TelephonyManager;
import android.provider.Settings;
import android.os.SystemProperties;
//add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end

/**
 * Displays a list of call log entries. To filter for a particular kind of call
 * (all, missed or voicemails), specify it in the constructor.
 */
public class CallLogFragment extends ListFragment
        implements CallLogQueryHandler.Listener, CallLogAdapter.OnReportButtonClickListener,
        CallLogAdapter.CallFetcher,
        CallLogAdapter.CallItemExpandedListener,
        AccountInfoListener
        {
    private static final String TAG = "CallLogFragment";

    private static final String REPORT_DIALOG_TAG = "report_dialog";
    private String mReportDialogNumber;
    private boolean mIsReportDialogShowing;

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_LOG_LIMIT = "log_limit";
    private static final String KEY_DATE_LIMIT = "date_limit";
    private static final String KEY_SHOW_FOOTER = "show_footer";
    private static final String KEY_IS_REPORT_DIALOG_SHOWING = "is_report_dialog_showing";
    private static final String KEY_REPORT_DIALOG_NUMBER = "report_dialog_number";
    private static final String KEY_NEED_ACCOUNT_FILTER = "need_account_filter";

    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;

    /** Whether there is at least one voicemail source installed. */
    private boolean mVoicemailSourcesAvailable = false;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private KeyguardManager mKeyguardManager;
    private View mFooterView;

    private TextView mNoticeText;
    private View mNoticeTextDivider;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;

    private float mExpandedItemTranslationZ;
    private int mFadeInDuration;
    private int mFadeInStartDelay;
    private int mFadeOutDuration;
    private int mExpandCollapseDuration;

    private final Handler mHandler = new Handler();

	private View timeView;
    //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
    private Context mContext;
	private ServiceState mServiceState1;
    private ServiceState mServiceState2;
    private final PhoneStateListener mPhoneStateListener1 = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            //Log.d("zhaizhanfeng","onServiceStateChanged1, serviceState = " + serviceState);
            mServiceState1 = serviceState;
            refreshAlert();
        }
    };
    private final PhoneStateListener mPhoneStateListener2 = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            //Log.d("zhaizhanfeng","onServiceStateChanged2, serviceState = " + serviceState);
            mServiceState2 = serviceState;
            refreshAlert();
        }
    };
    //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end
    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    }

    // See issue 6363009
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private final ContentObserver mVoicemailStatusObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    // Exactly same variable is in Fragment as a package private.
    private boolean mMenuVisible = true;

    // Default to all calls.
    private int mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;

    // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
    // will be used.
    private int mLogLimit = -1;

    // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
    // the date filter are included.  If zero, no date-based filtering occurs.
    private long mDateLimit = 0;

    // Whether or not to show the Show call history footer view
    private boolean mHasFooterView = false;

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, -1);
    }

    public CallLogFragment(int filterType) {
        this(filterType, -1);
    }

    public CallLogFragment(int filterType, int logLimit) {
        super();
        mCallTypeFilter = filterType;
        mLogLimit = logLimit;
    }

    /**
     * Creates a call log fragment, filtering to include only calls of the desired type, occurring
     * after the specified date.
     * @param filterType type of calls to include.
     * @param dateLimit limits results to calls occurring on or after the specified date.
     */
    public CallLogFragment(int filterType, long dateLimit) {
        this(filterType, -1, dateLimit, true);
    }

    /**
     * Creates a call log fragment, filtering to include only calls of the desired type, occurring
     * after the specified date.  Also provides a means to limit the number of results returned.
     * @param filterType type of calls to include.
     * @param logLimit limits the number of results to return.
     * @param dateLimit limits results to calls occurring on or after the specified date.
     */
    public CallLogFragment(int filterType, int logLimit, long dateLimit, boolean needAccoutFilter) {
        this(filterType, logLimit);
        mDateLimit = dateLimit;
        mNeedAccountFilter = needAccoutFilter;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mContext = getActivity();//add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128
        if (state != null) {
            mCallTypeFilter = state.getInt(KEY_FILTER_TYPE, mCallTypeFilter);
            mLogLimit = state.getInt(KEY_LOG_LIMIT, mLogLimit);
            mDateLimit = state.getLong(KEY_DATE_LIMIT, mDateLimit);
            mHasFooterView = state.getBoolean(KEY_SHOW_FOOTER, mHasFooterView);
            mIsReportDialogShowing = state.getBoolean(KEY_IS_REPORT_DIALOG_SHOWING,
                    mIsReportDialogShowing);
            mReportDialogNumber = state.getString(KEY_REPORT_DIALOG_NUMBER, mReportDialogNumber);
            mNeedAccountFilter = state.getBoolean(KEY_NEED_ACCOUNT_FILTER);
        }

        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mAdapter = ObjectFactory.newCallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso), this, this, true);
        setListAdapter(mAdapter);
        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, mLogLimit);
        mKeyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        getActivity().getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);
        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
        getActivity().getContentResolver().registerContentObserver(
                Status.CONTENT_URI, true, mVoicemailStatusObserver);
        setHasOptionsMenu(true);

        // / Added by guofeiyao
        refreshCallTypeFilter();
        // / End
        updateCallList(mCallTypeFilter, mDateLimit);

        mExpandedItemTranslationZ =
                getResources().getDimension(R.dimen.call_log_expanded_translation_z);
        mFadeInDuration = getResources().getInteger(R.integer.call_log_actions_fade_in_duration);
        mFadeInStartDelay = getResources().getInteger(R.integer.call_log_actions_fade_start);
        mFadeOutDuration = getResources().getInteger(R.integer.call_log_actions_fade_out_duration);
        mExpandCollapseDuration = getResources().getInteger(
                R.integer.call_log_expand_collapse_duration);

        if (mIsReportDialogShowing) {
            DialogFragment df = ObjectFactory.getReportDialogFragment(mReportDialogNumber);
            if (df != null) {
                df.setTargetFragment(this, 0);
                df.show(getActivity().getFragmentManager(), REPORT_DIALOG_TAG);
            }
        }

        PhoneAccountInfoHelper.INSTANCE.registerForAccountChange(this);

        // / M: for Plug-in
        ExtensionManager.getInstance().getCallLogExtension().onCreateForCallLogFragment(
                getActivity().getApplicationContext(), this);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public boolean onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            // Return false; we did not take ownership of the cursor
            LogUtils.d(TAG, "Goes into the wrong state");

            if (mAdapter != null) {
                mAdapter.changeCursor(null);
            }

            return false;
        }

        /// M: support calllog performance @{
        LogUtils.i("sera","[Performance test][Dialer] Calllog_Performance_001 end [" + System.currentTimeMillis() + "]");
        /// M: @ }

        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.


	//add by zhangjinqiang for al812--start
	if( timeView != null ){
		if(mAdapter.getCount() == 0){
			timeView.setVisibility(View.GONE);
		}else{
			timeView.setVisibility(View.VISIBLE);
		}
	}
	//add by zhangjinqiang for al812--end
	
        getActivity().invalidateOptionsMenu();
        if (mScrollToTop) {
            final ListView listView = getListView();
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   listView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
        return true;
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        /// M: ALPS01953565 avoid timing issue, it is possible query completed before view created @{
        // if (getActivity() == null || getActivity().isFinishing())
        if (getActivity() == null || getActivity().isFinishing() || !isResumed()) {
        /// @}
            return;
        }
        updateVoicemailStatusMessage(statusCursor);

        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        setVoicemailSourcesAvailable(activeSources != 0);
        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    /** Sets whether there are any voicemail sources available in the platform. */
    private void setVoicemailSourcesAvailable(boolean voicemailSourcesAvailable) {
        if (mVoicemailSourcesAvailable == voicemailSourcesAvailable) return;
        mVoicemailSourcesAvailable = voicemailSourcesAvailable;

        Activity activity = getActivity();
        if (activity != null) {
            // This is so that the options menu content is updated.
            activity.invalidateOptionsMenu();
        }
    }

	// / Added by guofeiyao
	private View rootView;

	public View getRootView() {
         return rootView;
	}

	private View emergencyAlert;
	private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;

	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }
	// / End

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);

        mNoticeText = (TextView) view.findViewById(R.id.notice_text);
        mNoticeTextDivider = view.findViewById(R.id.notice_text_divider);

	timeView = view.findViewById(R.id.time_line_view);

        // / Added by guofeiyao for HQ01301732
        emergencyAlert = view.findViewById(R.id.ll_emergency_alert);
        rootView = view.findViewById(R.id.rl_call_container);
		// / End
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //add by jinlibo for performance
        if(getActivity() instanceof PeopleActivity){
            getRootView().setOnTouchListener((OnTouchListener) getActivity());
            getListView().setOnTouchListener((OnTouchListener)getActivity());
        }
        getListView().setEmptyView(view.findViewById(R.id.empty_list_view));
        getListView().setItemsCanFocus(true);
        maybeAddFooterView();

        updateEmptyMessage(mCallTypeFilter);

        /// M: for Plug-in
        ExtensionManager.getInstance().getCallLogExtension().onViewCreatedForCallLogFragment(view, savedInstanceState);

        // / Added by guofeiyao
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
        
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // When clicked, show a toast with the TextView text
                
                     TimeAxisWidget taw = (TimeAxisWidget)view.findViewById(R.id.item_date);
		             View tView = taw.getContent();
		 
		             CallLogListItemViews views = (CallLogListItemViews)tView.getTag();
		             views.number=CallUtil.claroSpecialOperator(views.number, mContext);
		             //Log.i("tang", "the num is "+views.number);
					 CallLogAdapter.DialCallback dialCallback = (CallLogAdapter.DialCallback)views.primaryActionView.getTag(R.string.dialcallback_data);
					 dialCallback.setNumber(views.number);
			    if ( null != dialCallback ) {
                    dialCallback.onClick(views.primaryActionView);
				}
            }
        });

		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			
			@Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                // When clicked, show a toast with the TextView text
                
                
                     TimeAxisWidget taw = (TimeAxisWidget)view.findViewById(R.id.item_date);
		             View tView = taw.getContent();
		 
		             CallLogListItemViews views = (CallLogListItemViews)tView.getTag();
					 CallLogAdapter.DialCallback dialCallback = (CallLogAdapter.DialCallback)views.primaryActionView.getTag(R.string.dialcallback_data);
			    if ( null != dialCallback ) {
                    dialCallback.onLongClick(views.primaryActionView, position);
                    return true;
				}
				return false;
            }
        });
        // / End
    }

// /added by guofeiyao
private void refreshAlert(){
        if ( null == getActivity() ) return;
        //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
        Boolean isSim1Emergency = (mServiceState1 != null && (mServiceState1.getState() == ServiceState.STATE_OUT_OF_SERVICE ||                 mServiceState1.getState() == ServiceState.STATE_POWER_OFF || mServiceState1.getState() == ServiceState.STATE_EMERGENCY_ONLY));
        Boolean isSim2Emergency = (mServiceState2 != null && (mServiceState2.getState() == ServiceState.STATE_OUT_OF_SERVICE || mServiceState2.getState() == ServiceState.STATE_POWER_OFF || mServiceState2.getState() == ServiceState.STATE_EMERGENCY_ONLY));
        TelephonyManagerEx telephonyManagerEx = TelephonyManagerEx.getDefault();
        //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end
	
        boolean isAirplaneModeOn = android.provider.Settings.System.getInt(getActivity().getContentResolver(),  
                  android.provider.Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        if (null != emergencyAlert) {
	    	getSubInfoList();
			if (mSubCount == 0 || isAirplaneModeOn){
                emergencyAlert.setVisibility(View.VISIBLE);
			} else {
			    emergencyAlert.setVisibility(View.GONE);
			}
            /* HQ_guomiao 2015-10-16 modified HQ01356496 begin */
            if (isAirplaneModeOn) {
                ((TextView)((LinearLayout)emergencyAlert).getChildAt(0)).setText(R.string.flight_mode_turned_on);
            } else {
                ((TextView)((LinearLayout)emergencyAlert).getChildAt(0)).setText(R.string.emergency_only);
            }
            /* HQ_guomiao 2015-10-16 modified HQ01356496 end */

            //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
            if(SystemProperties.get("ro.hq.Atick.Authenticate").equals("1")){
                if ((telephonyManagerEx != null) && (telephonyManagerEx.getSimState(PhoneConstants.SIM_ID_1) != TelephonyManager.SIM_STATE_READY) && (telephonyManagerEx.getSimState(PhoneConstants.SIM_ID_2) != TelephonyManager.SIM_STATE_READY)) {
                    emergencyAlert.setVisibility(View.VISIBLE);
                } else if(isSim1Emergency && isSim2Emergency) {
                    emergencyAlert.setVisibility(View.VISIBLE);
                } else {
                    emergencyAlert.setVisibility(View.GONE);
                }
            }
            //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end
    	}
	}
	// /end

    /**
     * Based on the new intent, decide whether the list should be configured
     * to scroll up to display the first item.
     */
    public void configureScreenFromIntent(Intent newIntent) {
        // Typically, when switching to the call-log we want to show the user
        // the same section of the list that they were most recently looking
        // at.  However, under some circumstances, we want to automatically
        // scroll to the top of the list to present the newest call items.
        // For example, immediately after a call is finished, we want to
        // display information about that call.
        mScrollToTop = Calls.CONTENT_TYPE.equals(newIntent.getType());
    }

    @Override
    public void onStart() {
        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null,
                new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //updateCallType();
        refreshData();

		// / Added by guofeiyao
		refreshAlert();
		// / End
        //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 start
        TelephonyManagerEx.getDefault().listen(mPhoneStateListener1, PhoneStateListener.LISTEN_SERVICE_STATE, PhoneConstants.SIM_ID_1);
        TelephonyManagerEx.getDefault().listen(mPhoneStateListener2, PhoneStateListener.LISTEN_SERVICE_STATE, PhoneConstants.SIM_ID_2);
        //add by zhaizhanfeng for HQ01533309&HQ01533600 at 151128 end
    }

    private void updateCallType() {
        getSubInfoList();
        if (mSubCount == 2) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("CallLog_type", Activity.MODE_PRIVATE);
            int mCallLog = sharedPreferences.getInt("CallLog", CallLogQueryHandler.CALL_TYPE_ALL);
            String mSimType = sharedPreferences.getString("SimType", "all_account");
            updateCallList(mCallLog,0,mSimType);
            mRefreshDataRequired = false;
        }
    }

    private void updateVoicemailStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            mStatusMessageView.setVisibility(View.GONE);
        } else {
            mStatusMessageView.setVisibility(View.VISIBLE);
            // TODO: Change the code to show all messages. For now just pick the first message.
            final StatusMessage message = messages.get(0);
            if (message.showInCallLog()) {
                mStatusMessageText.setText(message.callLogMessageId);
            }
            if (message.actionMessageId != -1) {
                mStatusMessageAction.setText(message.actionMessageId);
            }
            if (message.actionUri != null) {
                mStatusMessageAction.setVisibility(View.VISIBLE);
                mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, message.actionUri));
                    }
                });
            } else {
                mStatusMessageAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
		mAdapter.onPause();//modified by guofeiyao
    }

    @Override
    public void onStop() {
        super.onStop();
        updateOnExit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
        getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);

        PhoneAccountInfoHelper.INSTANCE.unRegisterForAccountChange(this);

        /// M: for Plug-in
        ExtensionManager.getInstance().getCallLogExtension().onDestroyForCallLogFragment();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FILTER_TYPE, mCallTypeFilter);
        outState.putInt(KEY_LOG_LIMIT, mLogLimit);
        outState.putLong(KEY_DATE_LIMIT, mDateLimit);
        outState.putBoolean(KEY_SHOW_FOOTER, mHasFooterView);
        outState.putBoolean(KEY_IS_REPORT_DIALOG_SHOWING, mIsReportDialogShowing);
        outState.putString(KEY_REPORT_DIALOG_NUMBER, mReportDialogNumber);
        outState.putBoolean(KEY_NEED_ACCOUNT_FILTER, mNeedAccountFilter);
    }

    @Override
    public void fetchCalls() {
    	if(null != mCallLogQueryHandler) {

            // / Added by guofeiyao
            refreshCallTypeFilter();
            // / End

    		mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit, getAccountFilterId());
    	}
    }

            // / Added by guofeiyao,we need confirm the CallTypeFilter rely on DialerPlugIn
            private void refreshCallTypeFilter() {
                if (getActivity() instanceof PeopleActivity) {
                    Log.i(TAG, "getActivity() instanceof PeopleActivity");
                    PeopleActivity p = (PeopleActivity) getActivity();
                    DialerPlugIn dialerPlugIn = p.getDialerPlugIn();
                    if (null != dialerPlugIn) {
                        int chosenType = dialerPlugIn.getChosenType();
                        if (0 == chosenType) {
                            mCallTypeFilter = DialerPlugIn.CALL_TYPE_ALL;

                        } else if (1 == chosenType) {
                            mCallTypeFilter = DialerPlugIn.CALL_TYPE_MISS;

                        } else if (2 == chosenType) {
                            mCallTypeFilter = DialerPlugIn.CALL_TYPE_OUTGOING;

                        } else if (3 == chosenType) {
                            mCallTypeFilter = DialerPlugIn.CALL_TYPE_INCOMING;

                        } else {
                            Log.e(TAG, "Error chosenType!!!");
                        }
                    } else {
                        Log.e(TAG, "null == dialerPlugIn");
                    }
                } else {
                    Log.e(TAG, "[getActivity() instanceof PeopleActivity] return false!!!!");
                }
            }
            // / End

            public void startCallsQuery() {
                mAdapter.setLoading(true);
                if (null != mCallLogQueryHandler) {

                    // / Added by guofeiyao
                    refreshCallTypeFilter();
                    // / End

                    mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit, getAccountFilterId());
                }
            }

    private void startVoicemailStatusQuery() {
    	if(null != mCallLogQueryHandler) {
    		mCallLogQueryHandler.fetchVoicemailStatus();
    	}
    }

    public void updateCallList(int filterType, long dateLimit) {
    	if(null != mCallLogQueryHandler) {
    		mCallLogQueryHandler.fetchCalls(filterType, dateLimit, getAccountFilterId());
    	}
    }

	//add by zhangjinqiang for view call log based on sim1 or sim2 --start
	public void updateCallList(int filterType, long dateLimit,String accountId) {
	   if(accountId.equals("all_account")){
	    	if(null != mCallLogQueryHandler) {
	    		mCallLogQueryHandler.fetchCalls(filterType, dateLimit,accountId);
	    	}
	    }else{
	         int[] subId = SubscriptionManager.getSubId(Integer.parseInt(accountId));
			Log.d("subId.length",subId.length+"");
			if(subId.length>0) {
					if(null != mCallLogQueryHandler) {
						mCallLogQueryHandler.fetchCalls(filterType, dateLimit,subId[0]+"");
					}
			}
		}
    } 
	//add by zhangjinqiang --end

    private void updateEmptyMessage(int filterType) {
        final int messageId;
        switch (filterType) {
            case Calls.MISSED_TYPE:
                messageId = R.string.recentMissed_empty;
                break;
            case Calls.VOICEMAIL_TYPE:
                messageId = R.string.recentVoicemails_empty;
                break;
            case CallLogQueryHandler.CALL_TYPE_ALL:
                messageId = R.string.recentCalls_empty;
                break;
            /// M: [CallLog I/O Filter] for support calllog incoming/outgoing filter @{
            case Calls.INCOMING_TYPE:
                messageId = R.string.recentCalls_empty;
                break;
            case Calls.OUTGOING_TYPE:
                messageId = R.string.recentCalls_empty;
                break;
            /// @}
            default:
                throw new IllegalArgumentException("Unexpected filter type in CallLogFragment: "
                        + filterType);
        }
		// / Modified by guofeiyao
		/*
        DialerUtils.configureEmptyListView(
                getListView().getEmptyView(), R.drawable.empty_call_log, messageId, getResources());
                */
        DialerUtils.configureEmptyListView(
                getListView().getEmptyView(), R.drawable.ic_empty_phonecall, messageId, getResources());
		// / End
    }

    CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (!menuVisible) {
                updateOnExit();
            } else if (isResumed()) {
                refreshData();
            }
        }
    }

    /** Requests updates to the data to be shown. */
    public void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so they will be looked up
            // again once being shown.
            mAdapter.invalidateCache();
            startCallsQuery();
            startVoicemailStatusQuery();
            updateOnEntry();
            mRefreshDataRequired = false;
        /// M: for ALPS01772987 @{
        // need to update data without re-query
        } else {
            // / Obeserve it,Commented out by guofeiyao
            Log.i(TAG, "need to update data without re-query????  refreshData()");
            mAdapter.notifyDataSetChanged();
            // / End
        }
        /// @}

        if (mNeedAccountFilter) {
            updateNotice();
        }
    }

    /** Updates call data and notification state while leaving the call log tab. */
    private void updateOnExit() {
        updateOnTransition(false);
    }

    /** Updates call data and notification state while entering the call log tab. */
    private void updateOnEntry() {
        updateOnTransition(true);
    }

    // TODO: Move to CallLogActivity
    private void updateOnTransition(boolean onEntry) {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        // This might be called before onCreate() and thus we need to check null explicitly.
        if (mKeyguardManager != null && !mKeyguardManager.inKeyguardRestrictedInputMode()) {
            // On either of the transitions we update the missed call and voicemail notifications.
            // While exiting we additionally consume all missed calls (by marking them as read).
        	if(null != mCallLogQueryHandler) {
        		mCallLogQueryHandler.markNewCallsAsOld();
        	}
            if (!onEntry) {
            	if(null != mCallLogQueryHandler) {
            		mCallLogQueryHandler.markMissedCallsAsRead();
            	}
            }
            CallLogNotificationsHelper.removeMissedCallNotifications(getActivity());
            CallLogNotificationsHelper.updateVoicemailNotifications(getActivity());
        }
    }

    /**
     * Enables/disables the showing of the view full call history footer
     *
     * @param hasFooterView Whether or not to show the footer
     */
    public void setHasFooterView(boolean hasFooterView) {
        mHasFooterView = hasFooterView;
        maybeAddFooterView();
    }

    /**
     * Determine whether or not the footer view should be added to the listview. If getView()
     * is null, which means onCreateView hasn't been called yet, defer the addition of the footer
     * until onViewCreated has been called.
     */
    private void maybeAddFooterView() {
        if (!mHasFooterView || getView() == null) {
            return;
        }

	//add by zhangjinqiang for al812--start
	if(InCallApp.gIsHwUi){
		return;
		}
	//add by zhangjinqiang for al812--end
	
        if (mFooterView == null) {
            mFooterView = getActivity().getLayoutInflater().inflate(
                    R.layout.recents_list_footer, getListView(), false);
            mFooterView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((HostInterface) getActivity()).showCallHistory();
                }
            });
        }

        final ListView listView = getListView();
        listView.removeFooterView(mFooterView);
        listView.addFooterView(mFooterView);

        ViewUtil.addBottomPaddingToListViewForFab(listView, getResources());
    }

    @Override
    public void onItemExpanded(final View view) {
        final int startingHeight = view.getHeight();
        final CallLogListItemViews viewHolder = (CallLogListItemViews) view.getTag();
        final ViewTreeObserver observer = getListView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called for every draw.
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                // Calculate some values to help with the animation.
                final int endingHeight = view.getHeight();
                final int distance = Math.abs(endingHeight - startingHeight);
                final int baseHeight = Math.min(endingHeight, startingHeight);
                final boolean isExpand = endingHeight > startingHeight;

                // Set the views back to the start state of the animation
                view.getLayoutParams().height = startingHeight;
                if (!isExpand) {
                    viewHolder.actionsView.setVisibility(View.VISIBLE);
                }
                CallLogAdapter.expandVoicemailTranscriptionView(viewHolder, !isExpand);

                // Set up the fade effect for the action buttons.
                if (isExpand) {
                    // Start the fade in after the expansion has partly completed, otherwise it
                    // will be mostly over before the expansion completes.
                    viewHolder.actionsView.setAlpha(0f);
                    viewHolder.actionsView.animate()
                            .alpha(1f)
                            .setStartDelay(mFadeInStartDelay)
                            .setDuration(mFadeInDuration)
                            .start();
                } else {
                    viewHolder.actionsView.setAlpha(1f);
                    viewHolder.actionsView.animate()
                            .alpha(0f)
                            .setDuration(mFadeOutDuration)
                            .start();
                }
                view.requestLayout();

                // Set up the animator to animate the expansion and shadow depth.
                ValueAnimator animator = isExpand ? ValueAnimator.ofFloat(0f, 1f)
                        : ValueAnimator.ofFloat(1f, 0f);

                // Figure out how much scrolling is needed to make the view fully visible.
                final Rect localVisibleRect = new Rect();
                view.getLocalVisibleRect(localVisibleRect);
                final int scrollingNeeded = localVisibleRect.top > 0 ? -localVisibleRect.top
                        : view.getMeasuredHeight() - localVisibleRect.height();
                final ListView listView = getListView();
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                    private int mCurrentScroll = 0;

                    @Override
                    public void onAnimationUpdate(ValueAnimator animator) {
                        Float value = (Float) animator.getAnimatedValue();

                        // For each value from 0 to 1, animate the various parts of the layout.
                        view.getLayoutParams().height = (int) (value * distance + baseHeight);
                        float z = mExpandedItemTranslationZ * value;
                        viewHolder.callLogEntryView.setTranslationZ(z);
                        view.setTranslationZ(z); // WAR
                        view.requestLayout();

                        if (isExpand) {
                            if (listView != null) {
                                int scrollBy = (int) (value * scrollingNeeded) - mCurrentScroll;
                                listView.smoothScrollBy(scrollBy, /* duration = */ 0);
                                mCurrentScroll += scrollBy;
                            }
                        }
                    }
                });
                // Set everything to their final values when the animation's done.
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.getLayoutParams().height = LayoutParams.WRAP_CONTENT;

                        if (!isExpand) {
                            viewHolder.actionsView.setVisibility(View.GONE);
                        } else {
                            // This seems like it should be unnecessary, but without this, after
                            // navigating out of the activity and then back, the action view alpha
                            // is defaulting to the value (0) at the start of the expand animation.
                            viewHolder.actionsView.setAlpha(1);
                        }
                        CallLogAdapter.expandVoicemailTranscriptionView(viewHolder, isExpand);
                    }
                });

                animator.setDuration(mExpandCollapseDuration);
                animator.start();

                // Return false so this draw does not occur to prevent the final frame from
                // being drawn for the single frame before the animations start.
                return false;
            }
        });
    }

    /**
     * Retrieves the call log view for the specified call Id.  If the view is not currently
     * visible, returns null.
     *
     * @param callId The call Id.
     * @return The call log view.
     */
    @Override
    public View getViewForCallId(long callId) {
        ListView listView = getListView();

        int firstPosition = listView.getFirstVisiblePosition();
        int lastPosition = listView.getLastVisiblePosition();

        for (int position = 0; position <= lastPosition - firstPosition; position++) {
            View view = listView.getChildAt(position);

            if (view != null) {
                final CallLogListItemViews viewHolder = (CallLogListItemViews) view.getTag();
                if (viewHolder != null && viewHolder.rowId == callId) {
                    return view;
                }
            }
        }

        return null;
    }

    public void onBadDataReported(String number) {
        mIsReportDialogShowing = false;
        if (number == null) {
            return;
        }
        mAdapter.onBadDataReported(number);
        mAdapter.notifyDataSetChanged();
    }

    public void onReportButtonClick(String number) {
        DialogFragment df = ObjectFactory.getReportDialogFragment(number);
        if (df != null) {
            df.setTargetFragment(this, 0);
            df.show(getActivity().getFragmentManager(), REPORT_DIALOG_TAG);
            mReportDialogNumber = number;
            mIsReportDialogShowing = true;
        }
    }

    public void forceToRefreshData() {
        mRefreshDataRequired = true;
        /// M: for ALPS01683374
        // refreshData only when CallLogFragment is in foreground
        if(isResumed()) {
            refreshData();
        }
    }

	

    //--------------------------------------------Mediatek-----------------------------------------

    /// M: [Multi-Delete] For CallLog delete @{
    @Override
    public void onCallsDeleted() {
        // Do nothing
    }
    /// @}

    /// M: [Call Log Account Filter] @{

    // Whether or not to use account filter, currently call log screen use account filter
    // while recents call log  need not

    // / Modified by guofeiyao,we dont need AccountFilter obey the new demand
//    private boolean mNeedAccountFilter = DialerFeatureOptions.CALL_LOG_ACCOUNT_FILTER;
    private boolean mNeedAccountFilter = false;
    // / End

    private String getAccountFilterId() {
        if (mNeedAccountFilter) {
            return PhoneAccountInfoHelper.INSTANCE.getPreferAccountId();
        } else {
            return PhoneAccountInfoHelper.FILTER_ALL_ACCOUNT_ID;
        }
    }

    private void updateNotice() {
        String lable = null;
        String id = PhoneAccountInfoHelper.INSTANCE.getPreferAccountId();
        if (getActivity() != null && !PhoneAccountInfoHelper.FILTER_ALL_ACCOUNT_ID.equals(id)) {
            PhoneAccountHandle account = PhoneAccountUtils.getPhoneAccountById(getActivity(), id);
            if (account != null) {
                lable = PhoneAccountUtils.getAccountLabel(getActivity(), account);
            }
        }
        if (!TextUtils.isEmpty(lable) && mNoticeText != null && mNoticeTextDivider != null) {
            mNoticeText.setText(getActivity().getString(R.string.call_log_via_sim_name_notice,
                    lable));
            mNoticeText.setVisibility(View.VISIBLE);
            mNoticeTextDivider.setVisibility(View.VISIBLE);
        } else {
            mNoticeText.setVisibility(View.GONE);
            mNoticeTextDivider.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAccountInfoUpdate() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onPreferAccountChanged(String id) {
        forceToRefreshData();
    }
    /// @}

    /// M: for plug-in @{
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ExtensionManager.getInstance().getCallLogExtension().onListItemClickForCallLogFragment(l, v, position, id);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        ExtensionManager.getInstance().getCallLogExtension().onCreateContextMenuForCallLogFragment(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (ExtensionManager.getInstance().getCallLogExtension().onContextItemSelectedForCallLogFragment(item)) {
            return true;
        }
        return super.onContextItemSelected(item);
    }
    /// @}
}

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
package com.mediatek.dialer.dialersearch;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.android.phone.common.animation.AnimUtils;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.IntentProvider;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.util.DialerUtils;
import com.mediatek.dialer.util.DialerFeatureOptions;
import com.mediatek.dialer.util.LogUtils;

// / Added by guofeiyao
import com.android.contacts.activities.PeopleActivity;
// / End


public class SearchFragmentEx extends PhoneNumberPickerFragment {

    private final String TAG = "SearchFragmentEx";

    private static final int DIALER_SEARCH_CONTENT_CHANGE = 1252;
    private static final long WAIT_CURSOR_DELAY_TIME = 500;
    private final ContentObserver mContactsObserver = new ContactsObserver();

    private OnListFragmentScrolledListener mActivityScrollListener;

    /*
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
    private int mShadowHeight;
    private int mPaddingTop;
    private int mShowDialpadDuration;
    private int mHideDialpadDuration;

    private HostInterface mActivity;

	//[add by lizhao for HQ01611777 at 20151231 begain
	private List<SubscriptionInfo> mSubInfoList;
	private int mSubCount;
	private void getSubInfoList() {
        mSubInfoList = SubscriptionManager.from(getActivity()).getActiveSubscriptionInfoList();
        mSubCount = (mSubInfoList != null && !mSubInfoList.isEmpty()) ? mSubInfoList.size() : 0;
    }
    
	private void showDialog(final String number) {
		final AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
		alertDialog.show();
		Window window = alertDialog.getWindow();
		window.setContentView(R.layout.call_alert_dialog);
		String sim1 = mSubInfoList.get(0).getDisplayName().toString();
		Button btnOne = (Button) window.findViewById(R.id.btn_sim_one);
		btnOne.setText(sim1);
		btnOne.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Intent intent = CallUtil
						.getCallIntent(number,getActivity());
				// "slot_id", 0);
				intent.putExtra("slot_id", 0);
				DialerUtils.startActivityWithErrorToast(getActivity(), intent);
				alertDialog.dismiss();
			}
		});

		String sim2 = mSubInfoList.get(1).getDisplayName().toString();
		Button btnTwo = (Button) window.findViewById(R.id.btn_sim_two);
		btnTwo.setText(sim2);
		btnTwo.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Intent intent = CallUtil
						.getCallIntent(number,getActivity());
				// "slot_id", 1);
				intent.putExtra("slot_id", 1);
				DialerUtils.startActivityWithErrorToast(getActivity(), intent);
				alertDialog.dismiss();
			}
		});
	}
	//add by lizhao for HQ01611777 at 20151231 end]
	
    public interface HostInterface {
        public boolean isActionBarShowing();
        public boolean isDialpadShown();
        public int getActionBarHideOffset();
        public int getActionBarHeight();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }

        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.AUTHORITY_URI, true, mContactsObserver);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = mActivity.getActionBarHeight();
        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
        mShowDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
        mHideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

        final View parentView = getView();

        final ListView listView = getListView();

        listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });

        //add by lizhao for HQ01611777 at 20151231
        getSubInfoList();
        
        // / Annotated by guofeiyao,u dont need it when u adapt to Huawei style
        //updatePosition(false /* animate */);
        // / End
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapterEx adapter = new DialerPhoneNumberListAdapterEx(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapterEx adapter = (DialerPhoneNumberListAdapterEx) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;

        switch (shortcutType) {
            case DialerPhoneNumberListAdapterEx.SHORTCUT_INVALID:
                /// M: Support suggest account @{
                if (DialerFeatureOptions.isSuggestedAccountSupport()) {
                    final PhoneAccountHandle phoneAccountHandle = adapter.getSuggestPhoneAccountHandle(position);
                    String number = getPhoneNumber(position);
                    
                    number=CallUtil.claroSpecialOperator(number, getActivity());
                    
                    //[add by lizhao for HQ01611777 at 20151231 begain
                    /*IntentProvider intentProvider = IntentProvider.getSuggestedReturnCallIntentProvider(number, phoneAccountHandle);

                    if (intentProvider != null) {
                        final Intent intent = intentProvider.getIntent(getActivity());
                        if (intent != null) {
                           DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                        }
                    }*/
                    if(mSubCount == 2){
                    	showDialog(number);
                    } else {
                    	IntentProvider intentProvider = IntentProvider.getSuggestedReturnCallIntentProvider(number, phoneAccountHandle);
                    	
                    	if (intentProvider != null) {
                    		final Intent intent = intentProvider.getIntent(getActivity());
                    		if (intent != null) {
                    			DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                    		}
                    	}
                    }
                  //add by lizhao for HQ01611777 at 20151231 end]
                /// @}
                } else {
                    super.onItemClick(position, id);
                }

                // / Added by guofeiyao
                Activity ac = getActivity();
				if ( ac instanceof PeopleActivity ) {
					 LogUtils.d(TAG, "(PeopleActivity)ac.clearDigits();");
                     ((PeopleActivity)ac).clearDigits();
				}
                // / End
                break;
            case DialerPhoneNumberListAdapterEx.SHORTCUT_DIRECT_CALL:
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(adapter.getFormattedQueryString());
                }
                break;
            case DialerPhoneNumberListAdapterEx.SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                /// M: [VOLTE] For Volte SIP URI Call @{
                if (DialerFeatureOptions.isVolteCallSupport()
                        && PhoneNumberUtils.isUriNumber(adapter.getQueryString())) {
                    final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    intent.putExtra(Intents.Insert.IMS_ADDRESS, adapter.getQueryString());
                    intent.setType(Contacts.CONTENT_ITEM_TYPE);
                    DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                            R.string.add_contact_not_available);
                    break;
                }
                /// @}
                final String number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                final Intent intent = DialtactsActivity.getAddNumberToContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
            case DialerPhoneNumberListAdapterEx.SHORTCUT_MAKE_VIDEO_CALL:
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(adapter.getFormattedQueryString()
                            , true /* isVideoCall */);
                }
                break;
            /// M: [VOLTE] For Volte SIP URI Call @{
            case DialerPhoneNumberListAdapterEx.SHORTCUT_MAKE_VOLTE_CALL:
                String callOrigin = getActivity() instanceof DialtactsActivity ?
                        ((DialtactsActivity)getActivity()).getCallOrigin() : null;
                Intent callIntent = CallUtil.getCallIntent(
                        Uri.fromParts(PhoneAccount.SCHEME_TEL, adapter.getQueryString(), null),
                        callOrigin, Constants.DIAL_NUMBER_INTENT_IMS);
                DialerUtils.startActivityWithErrorToast(getActivity(), callIntent);
                break;
            /// @}
        }
    }

    /**
     * Updates the position and padding of the search fragment, depending on whether the dialpad is
     * shown. This can be optionally animated.
     * @param animate
     */
    public void updatePosition(boolean animate) {
        // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
        int startTranslationValue =
                mActivity.isDialpadShown() ? mActionBarHeight - mShadowHeight : -mShadowHeight;
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (animate || mActivity.isActionBarShowing()) {
            endTranslationValue =
                    mActivity.isDialpadShown() ? 0 : mActionBarHeight - mShadowHeight;
        }
        if (animate) {
            Interpolator interpolator =
                    mActivity.isDialpadShown() ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT ;
            int duration =
                    mActivity.isDialpadShown() ? mShowDialpadDuration : mHideDialpadDuration;
            getView().setTranslationY(startTranslationValue);
            getView().animate()
                    .translationY(endTranslationValue)
                    .setInterpolator(interpolator)
                    .setDuration(duration);
        } else {
            getView().setTranslationY(endTranslationValue);
        }

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        listView.setPaddingRelative(
                listView.getPaddingStart(),
                paddingTop,
                listView.getPaddingEnd(),
                listView.getPaddingBottom());
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case DIALER_SEARCH_CONTENT_CHANGE:
                forceReloadData();
                break;
            default:
                break;
            }
        }
    };

    private class ContactsObserver extends ContentObserver {
        public ContactsObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (SearchFragmentEx.this.isDetached()) {
                LogUtils.d(TAG, "Should update datas but fragment detached.");
                return;
            }
            mHandler.sendMessageDelayed(mHandler.obtainMessage(DIALER_SEARCH_CONTENT_CHANGE), WAIT_CURSOR_DELAY_TIME);
            LogUtils.d(TAG, "Should update datas");
        }
    }

    private void forceReloadData() {
        if (!this.isAdded()) {
            LogUtils.d(TAG, "Update data but fragment not added.");
            return;
        }
        reloadData();
        mHandler.removeMessages(DIALER_SEARCH_CONTENT_CHANGE);
        LogUtils.d(TAG, "Update data");
    }

    @Override
    public void onResume() {
        super.onResume();
        // The content observer may miss the contacts change. Post a reload message.
        mHandler.sendMessageDelayed(mHandler.obtainMessage(DIALER_SEARCH_CONTENT_CHANGE),
                WAIT_CURSOR_DELAY_TIME);
    }
}

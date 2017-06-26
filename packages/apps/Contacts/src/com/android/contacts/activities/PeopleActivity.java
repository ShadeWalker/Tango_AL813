/*
 * Copyright (C) 2014 MediaTek Inc.
 * Modification based on code covered by the mentioned copyright
 * and/or permission notice(s).
 */
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.String;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Inflater;
import java.lang.StackTraceElement;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Contacts.People;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.QuickContact;
import android.provider.Settings;
import android.support.v13.app.FragmentCompat;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import android.widget.AdapterView.OnItemClickListener;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter.TabState;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.common.list.ContactTileAdapter.DisplayType;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.common.preference.DisplayOptionsPreferenceFragment;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.ViewUtil;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.list.ContactTileListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactsUnavailableActionListener;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.preference.ContactsPreferenceActivity;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.AccountPromptUtils;
import com.android.contacts.util.DialogManager;
import com.android.contacts.util.HelpUtils;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.util.ViewPagerListenersUtil;
//import com.android.contacts.widget.QuickAlphabeticBar;
import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.activities.ActivitiesUtils;
import com.mediatek.contacts.activities.ContactImportExportActivity;
import com.mediatek.contacts.activities.GroupBrowseActivity;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.PDebug;
import com.mediatek.contacts.util.SetIndicatorUtils;
import com.mediatek.contacts.util.VolteUtils;
import com.mediatek.contacts.vcs.VcsController;
import com.mediatek.contacts.vcs.VcsUtils;
import com.mediatek.contacts.widget.LocationPermissionDialogFragment;
import com.android.contacts.common.widget.SideBar;
import com.android.contacts.common.widget.SideBar.OnTouchingLetterChangedListener;
import com.cootek.smartdialer_plugin_oem.CooTekSmartdialerOemModule;
import com.cootek.smartdialer_plugin_oem.IServiceStateCallback;
/*HQ_zhangjing add for dialer merge to contact begin*/
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.list.AllContactsFragment;
import com.android.dialer.list.ListsFragment;
import com.android.dialer.widget.ActionBarController;
import com.android.dialer.widget.SearchEditTextLayout;
import com.android.dialer.calllog.CallLogActivity;
/*HQ_zhangjing add for dialer merge to contact end*/

// / Added by guofeiyao
import android.view.WindowManager;
import android.widget.AbsListView.OnScrollListener;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.widget.FloatingActionButtonControllerEM;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.list.OnListFragmentScrolledListener;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.settings.DialerSettingsActivity;
import com.mediatek.dialer.dialersearch.RegularSearchFragmentEx;
import com.mediatek.dialer.dialersearch.SearchFragmentEx;
import com.mediatek.dialer.dialersearch.SmartDialSearchFragmentEx;
import com.mediatek.dialer.dialersearch.SmartDialSearchFragmentEx.HideDialpadCallback;
import com.mediatek.dialer.dialersearch.SearchCallback;
import com.mediatek.dialer.util.DialerFeatureOptions;
// / End

import com.mediatek.contacts.list.service.*;
import com.mediatek.contacts.util.JoinContactsutils;

/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
import com.android.mms.ui.ConversationListFragment;
import android.app.WallpaperManager;
import android.graphics.drawable.ColorDrawable;
/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends ContactsActivity implements
        View.OnCreateContextMenuListener, View.OnClickListener,
        ActionBarAdapter.Listener,
        DialogManager.DialogShowingViewActivity,
        ContactListFilterController.ContactListFilterListener,
        // added by guofeiyao begin
        DialpadFragment.OnDialpadQueryChangedListener,
        SearchFragmentEx.HostInterface, SearchFragment.HostInterface,
        OnListFragmentScrolledListener,
        OnPhoneNumberPickerActionListener,
        SearchCallback,
        HideDialpadCallback,
        View.OnTouchListener,
        /*HQ_zhangjing 2015-08-10 modified for MMS merge */
        ConversationListFragment.OnActionModeCreateListener,
        // end
        /* HQ_zhangjing add for dialer merge to contact begin */
        // ProviderStatusListener {
        ProviderStatusListener, ListsFragment.HostInterface, IServiceStateCallback {
    /* HQ_zhangjing add for dialer merge to contact end */
    private static final String TAG = "PeopleActivity";

	private static final String ENABLE_DEBUG_OPTIONS_HIDDEN_CODE = "debug debug!";

	// These values needs to start at 2. See {@link ContactEntryListFragment}.
	private static final int SUBACTIVITY_ACCOUNT_FILTER = 2;

	private final DialogManager mDialogManager = new DialogManager(this);

	// added by guofeiyao for HQ01207444 begin
	private static final String TAG_DIALPAD_FRAGMENT = "dialpad_fragment";

	private DialerPlugIn mDialerPlugIn;
	private ViewPagerListenersUtil mViewPagerListenersUtil;
	private View mParentLayout;
	private View floatingActionButtonContainer;
	private FloatingActionButtonControllerEM mFloatingActionButtonController;
	private FloatingActionButtonControllerEM dialpadContainer;

	protected DialpadFragment mDialpadFragment;

	/**
	 * Fragment for searching phone numbers using the alphanumeric keyboard.
	 */
	private RegularSearchFragment mRegularSearchFragment;

	/**
	 * Fragment for searching phone numbers using the dialpad.
	 */
	private SmartDialSearchFragment mSmartDialSearchFragment;
	// end

	private ContactsIntentResolver mIntentResolver;
	private ContactsRequest mRequest;

	private ActionBarAdapter mActionBarAdapter;

	private ContactTileListFragment.Listener mFavoritesFragmentListener = new StrequentContactListFragmentListener();

	private ContactListFilterController mContactListFilterController;

	private ContactsUnavailableFragment mContactsUnavailableFragment;
	private ProviderStatusWatcher mProviderStatusWatcher;
	private ProviderStatusWatcher.Status mProviderStatus;

	private boolean mOptionsMenuContactsAvailable;

	/*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 begin*/
	private boolean isShouldSkipOnPageScrolled = false;
	private int mPreTabPosition = -1;
	private int mCurTabPosition = -1;
	/*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 end*/



	/**
	 * Showing a list of Contacts. Also used for showing search results in
	 * search mode.
	 */
	private DefaultContactBrowseListFragment mAllFragment;
	/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
	//private ContactTileListFragment mFavoritesFragment;
	private ConversationListFragment mConversationListFragment;
	/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/

	/* HQ_zhangjing add for dialer merge to contact begin */
	private CallLogFragment mRecentsFragment;
	private static final int MAX_RECENTS_ENTRIES = -1;//20;
	// Oldest recents entry to display is 2 weeks old.
	private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;
	/* HQ_zhangjing add for dialer merge to contact end */
	/** ViewPager for swipe */
	private ViewPager mTabPager;
	private ViewPagerTabs mViewPagerTabs;
	private TabPagerAdapter mTabPagerAdapter;
	private String[] mTabTitles;
	private final TabPagerListener mTabPagerListener = new TabPagerListener();

	private boolean mEnableDebugMenuOptions;

	/**
	 * True if this activity instance is a re-created one. i.e. set true after
	 * orientation change. This is set in {@link #onCreate} for later use in
	 * {@link #onStart}.
	 */
	private boolean mIsRecreatedInstance;

	/**
	 * If {@link #configureFragments(boolean)} is already called. Used to avoid
	 * calling it twice in {@link #onStart}. (This initialization only needs to
	 * be done once in onStart() when the Activity was just created from scratch
	 * -- i.e. onCreate() was just called)
	 */
	private boolean mFragmentInitialized;

	/**
	 * This is to disable {@link #onOptionsItemSelected} when we trying to stop
	 * the activity.
	 */
	private boolean mDisableOptionItemSelected;

	/** Sequential ID assigned to each instance; used for logging */
	private final int mInstanceId;
	private static final AtomicInteger sNextInstanceId = new AtomicInteger();

	private static final int CONTACTS_TO_DISPLAY = 0;

	protected static final int IMPORT_EXPORT = 1;

	protected static final int ACCOUNTS = 2;

	protected static final int DELETE_CONTACTS = 3;

	protected static final int JOIN_CONTACTS = 4;
	private static CooTekSmartdialerOemModule csom;
	
	/**
	 * 获取触宝初始化对象，不要重复创建，防止侧漏……
	 * @return
	 */
	public static CooTekSmartdialerOemModule getCsom() {
		return csom;
	}


	private EditText mSearchView;
	
    /* HQ_zhangjing add for dialer merge to contact begin */
    private final static String SPEEDDIAL_TAG = "tab-pager-dial";
    /* HQ_zhangjing add for dialer merge to contact end */
    /*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
    //final String FAVORITE_TAG = "tab-pager-favorite";
    private final static String MMS_TAG = "tab-pager-mms";
    /*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
    private final static String ALL_TAG = "tab-pager-all";
    
    private boolean chubaoConnected=false;
	//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
	AlertDialog menuDialogForContacts = null;
	
	/*HQ_zhangjing modified for smooth stuck begin*/
	private boolean mmsHasInstantiate = false;
	private boolean isEnterFromDialerFirst = false;//to mark if is first enter and from dialer
	/*HQ_zhangjing modified for smooth stuck end*/

    private boolean mShowContactsWithNumberOnly = false;
    
	public EditText getmSearchView() {
		if(mSearchView==null){
			mSearchView = (EditText) mAllFragment.getDefaultContactBrowseListFragmentView()
					.findViewById(R.id.search_view);		}
		return mSearchView;
	}

	// /Annotated by guofeiyao
//	private RelativeLayout MyDock;
    // /End

	private SideBar sideBar;
	public SideBar getSideBar() {
		if(sideBar==null){
			sideBar=(SideBar)mAllFragment.getDefaultContactBrowseListFragmentView().findViewById(R.id.sideBar);
		}
		return sideBar;
	}

	// /added by guofeiyao
	public ViewPagerTabs getPagerTabs(){
	    return mViewPagerTabs;
	}
	// /end

	private TextView dialog;

	private LinearLayout MyDock_layout;

	private PopupWindow popup;

	TypedArray array;
	private SharedPreferences chubao;
	private SharedPreferences.Editor chubaoEditor;
	/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
	private CheckBox isNomore;
	/* HQ_liujin modified end */
	public PeopleActivity() {
		mInstanceId = sNextInstanceId.getAndIncrement();
		mIntentResolver = new ContactsIntentResolver(this);
		/** M: Bug Fix for ALPS00407311 @{ */
		mProviderStatusWatcher = ProviderStatusWatcher
				.getInstance(ContactsApplicationEx.getContactsApplication());
		/** @} */
	}

	// added by guofeiyao begin
	public DialerPlugIn getDialerPlugIn() {
		return mDialerPlugIn;
	}

	// end

	@Override
	public String toString() {
		// Shown on logcat
		return String.format("%s@%d", getClass().getSimpleName(), mInstanceId);
	}

	public boolean areContactsAvailable() {
		return (mProviderStatus != null)
				&& mProviderStatus.status == ProviderStatus.STATUS_NORMAL;
	}

	private boolean areContactWritableAccountsAvailable() {
		return ContactsUtils.areContactWritableAccountsAvailable(this);
	}

	private boolean areGroupWritableAccountsAvailable() {
		return ContactsUtils.areGroupWritableAccountsAvailable(this);
	}

	/**
	 * Initialize fragments that are (or may not be) in the layout.
	 * 
	 * For the fragments that are in the layout, we initialize them in
	 * {@link #createViewsAndFragments(Bundle)} after inflating the layout.
	 * 
	 * However, the {@link ContactsUnavailableFragment} is a special fragment
	 * which may not be in the layout, so we have to do the initialization here.
	 * 
	 * The ContactsUnavailableFragment is always created at runtime.
	 */
	@Override
	public void onAttachFragment(Fragment fragment) {
		PDebug.Start("Contacts.onAttachFragment");
		//add by jinlibo for launch performance
		if(fragment instanceof DefaultContactBrowseListFragment){
			if(mAllFragment == null){
				mAllFragment = (DefaultContactBrowseListFragment)fragment;
			}
		}
		//jinlibo add end
		
		if (fragment instanceof ContactsUnavailableFragment) {
			mContactsUnavailableFragment = (ContactsUnavailableFragment) fragment;
			mContactsUnavailableFragment
					.setOnContactsUnavailableActionListener(new ContactsUnavailableFragmentListener());
		}
		PDebug.End("Contacts.onAttachFragment");

		// added by guofeiyao begin

		if (fragment instanceof DialpadFragment) {
			mDialpadFragment = (DialpadFragment) fragment;
//			if (!mShowDialpadOnResume) {
//				final FragmentTransaction transaction = getFragmentManager()
//						.beginTransaction();
//				transaction.hide(mDialpadFragment);
//				transaction.commit();
//			}

			// / M: Support MTK-DialerSearch @{
		} else if (DialerFeatureOptions.isDialerSearchEnabled()
				&& fragment instanceof SmartDialSearchFragmentEx) {
			mEnhancedSmartDialSearchFragment = (SmartDialSearchFragmentEx) fragment;
			mEnhancedSmartDialSearchFragment
					.setOnPhoneNumberPickerActionListener(this);
			
			// /added by guofeiyao
            mEnhancedSmartDialSearchFragment.setHideDialpadCallback(this);
			mEnhancedSmartDialSearchFragment.setSearchCallback(this);
			//Log.i("guofeiyao_" + "onAttachFragment", "SmartDialSearchFragmentEx");
			// /end
			
		} else if (DialerFeatureOptions.isDialerSearchEnabled()
				&& fragment instanceof RegularSearchFragmentEx) {
			mEnhancedRegularSearchFragment = (RegularSearchFragmentEx) fragment;
			mEnhancedRegularSearchFragment
					.setOnPhoneNumberPickerActionListener(this);
			// / @}

			// /added by guofeiyao
			mEnhancedRegularSearchFragment.setSearchCallback(this);
			//Log.i("guofeiyao_" + "onAttachFragment", "RegularSearchFragmentEx");
			// /end
			
		} else if (fragment instanceof SmartDialSearchFragment) {
			mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
			mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
		} else if (fragment instanceof SearchFragment) {
			mRegularSearchFragment = (RegularSearchFragment) fragment;
			mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
		}
		// end
	}

	// / @}



	@Override
	protected void onCreate(Bundle savedState) {
		PDebug.Start("Contacts.onCreate");
		//modify by liruihong for HQ01459617
		csom= new CooTekSmartdialerOemModule(getApplicationContext(),this);
		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
		}

		/*
		 * int themeId = getResources().getIdentifier(
		 * "androidhwext:style/Theme.Emui.WithActionBar", null, null); if
		 * (themeId > 0) { setTheme(themeId); }
		 */
		super.onCreate(savedState);
		if (!processIntent(false)) {
			finish();
			LogUtils.w(TAG, "[onCreate]can not process intent:" + getIntent());
			return;
		}
		

		LogUtils.d(TAG,
				"[Performance test][Contacts] loading data start time: ["
						+ System.currentTimeMillis() + "]");

		mContactListFilterController = ContactListFilterController
				.getInstance(this);
		mContactListFilterController.checkFilterValidity(false);
		mContactListFilterController.addListener(this);

		mProviderStatusWatcher.addListener(this);

		mIsRecreatedInstance = (savedState != null);

		PDebug.Start("createViewsAndFragments");
		createViewsAndFragments(savedState);

		if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
			Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
		}
		getWindow().setBackgroundDrawable(null);

		/**
		 * M: For plug-in @{ register context to plug-in, so that the plug-in
		 * can use host context to show dialog
		 */
		PDebug.Start("init plugin");
		// / M: op09
		ExtensionManager.getInstance().getContactListExtension()
				.registerHostContext(this, null);
		PDebug.End("init plugin");
		// / M: [vcs] VCS featrue. @{
		if (VcsUtils.isVcsFeatureEnable()) {
			mVcsController = new VcsController(this, mActionBarAdapter,
					mAllFragment);
			mVcsController.init();
		}
		// / @}
		/** @} */
		PDebug.End("Contacts.onCreate");

		// added by guofeiyao for HQ01207444

		mFirstLaunch = true;
		
		Bundle savedInstanceState = savedState;
		if (savedInstanceState != null) {
			mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
			mInRegularSearch = savedInstanceState
					.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
			mInDialpadSearch = savedInstanceState
					.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
			// mFirstLaunch = savedInstanceState.getBoolean(KEY_FIRST_LAUNCH);
			mShowDialpadOnResume = savedInstanceState
					.getBoolean(KEY_IS_DIALPAD_SHOWN);
		}

        mShowContactsWithNumberOnly = ContactListFilter.getNumberOnlyFromPreferences(getSharedPreferences());

		// /added by guofeiyao begin{
		Log.e(GTAG,"onCreate");
		// }end
		// end
	}

	@Override
	protected void onNewIntent(Intent intent) {
	Log.e("duanze_on","onnewintent");
		PDebug.Start("onNewIntent");
		setIntent(intent);
		if (!processIntent(true)) {
			finish();
			LogUtils.w(TAG, "[onNewIntent]can not process intent:"
					+ getIntent());
			return;
		}
		mActionBarAdapter.initialize(null, mRequest);
		/*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 begin*/
		if( mRequest != null ){
			mPreTabPosition = mCurTabPosition;
			mCurTabPosition = mRequest.getActionCode();
		}
		if( (mPreTabPosition == -1 && mCurTabPosition == ContactsRequest.ACTION_MMS) || (mPreTabPosition == ContactsRequest.ACTION_DEFAULT && mCurTabPosition == ContactsRequest.ACTION_MMS) ){
			isShouldSkipOnPageScrolled = true;
		}
		/*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 end*/

		mContactListFilterController.checkFilterValidity(false);

		// Re-configure fragments.
		configureFragments(true /* from request */);
		invalidateOptionsMenuIfNeeded();
		PDebug.End("onNewIntent");

		// / Added by guofeiyao
		mStateSaved = false;
		if ( null != mDialpadFragment ){
		     mDialpadFragment.setStartedFromNewIntent(true);

            // / Forgive me.
			// / I really don't know whether the followings will cause some bugs or not :(
			if ( mDialpadFragment.isAdded() && null != mFloatingActionButtonController ) {
				    mFloatingActionButtonController.setVisible(false);
                    mIsDialpadShown = true;
        			final FragmentTransaction transaction = getFragmentManager()
				    		.beginTransaction();
	    	    	transaction.show(mDialpadFragment);
		        	transaction.commit();
			}
		}
		// / End
	}

	/**
	 * Resolve the intent and initialize {@link #mRequest}, and launch another
	 * activity if redirect is needed.
	 * 
	 * @param forNewIntent
	 *            set true if it's called from {@link #onNewIntent(Intent)}.
	 * @return {@code true} if {@link PeopleActivity} should continue running.
	 *         {@code false} if it shouldn't, in which case the caller should
	 *         finish() itself and shouldn't do farther initialization.
	 */
	private boolean processIntent(boolean forNewIntent) {
		// Extract relevant information from the intent
		
		try{
			mRequest = mIntentResolver.resolveIntent(getIntent());
		}catch(RuntimeException e){
			e.printStackTrace();
			return false;
		}
		if (Log.isLoggable(TAG, Log.DEBUG)) {
			Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent
					+ " intent=" + getIntent() + " request=" + mRequest);
		}
		if (!mRequest.isValid()) {
			setResult(RESULT_CANCELED);
			return false;
		}
		/*HQ_zhangjing modified for smooth stuck begin*/
		if( mRequest.getActionCode() == ContactsRequest.ACTION_DIAL ){
			Log.d("jinlibo", "first enter from dialer");
			isEnterFromDialerFirst = true;
		}
		/*HQ_zhangjing modified for smooth stuck end*/
		Intent redirect = mRequest.getRedirectIntent();
		if (redirect != null) {
			// Need to start a different activity
			startActivity(redirect);
			return false;
		}

		if (mRequest.getActionCode() == ContactsRequest.ACTION_VIEW_CONTACT) {
			redirect = new Intent(this, QuickContactActivity.class);
			redirect.setAction(Intent.ACTION_VIEW);
			redirect.setData(mRequest.getContactUri());
			startActivity(redirect);
			return false;
		}
		return true;
	}

	// /added by guofeiyao
	private EditText digits;
	private TextView lo;
	// /end

	private void createViewsAndFragments(Bundle savedState) {
		PDebug.Start("createViewsAndFragments, prepare fragments");
		// Disable the ActionBar so that we can use a Toolbar. This needs to be
		// called before
		// setContentView().
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.people_activity);

		final FragmentManager fragmentManager = getFragmentManager();

		// Hide all tabs (the current tab will later be reshown once a tab is
		// selected)
		final FragmentTransaction transaction = fragmentManager
				.beginTransaction();

		mTabTitles = new String[TabState.COUNT];
		/* HQ_zhangjing add for dialer merge to contact begin */
		mTabTitles[TabState.DIAL] = getString(R.string.dial_tab_label);
		/* HQ_zhangjing add for dialer merge to contact end */
		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		//mTabTitles[TabState.FAVORITES] = getString(R.string.favorites_tab_label);
		mTabTitles[TabState.MMS] = getString(R.string.mms_tab_label);
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
		mTabTitles[TabState.ALL] = getString(R.string.contactsList);
		mTabPager = getView(R.id.tab_pager);
		mTabPagerAdapter = new TabPagerAdapter();
		mTabPager.setAdapter(mTabPagerAdapter);

		// modified by guofeiyao for HQ01207444 delay this operation to line 581
		mTabPager.setOnPageChangeListener(mTabPagerListener);//HQ_zhangjing add for set the scelection to contacts first

		// Configure toolbar and toolbar tabs. If in landscape mode, we
		// configure tabs differntly.
		final Toolbar toolbar = getView(R.id.toolbar);
		setActionBar(toolbar);
		// getActionBar().setTitle(null);
		// toolbar.setVisibility(View.GONE);

		ViewPagerTabs portraitViewPagerTabs = (ViewPagerTabs) findViewById(R.id.lists_pager_header);
		ViewPagerTabs landscapeViewPagerTabs = null;
		if (portraitViewPagerTabs == null) {
			landscapeViewPagerTabs = (ViewPagerTabs) getLayoutInflater()
					.inflate(R.layout.people_activity_tabs_lands, toolbar,
							false);
			mViewPagerTabs = landscapeViewPagerTabs;

		} else {
			mViewPagerTabs = portraitViewPagerTabs;
		}
		mViewPagerTabs.setViewPager(mTabPager);

		// Create the fragments and add as children of the view pager.
		// The pager adapter will only change the visibility; it'll never
		// create/destroy
		// fragments.
		// However, if it's after screen rotation, the fragments have been
		// re-created by
		// the fragment manager, so first see if there're already the target
		// fragments
		// existing.
		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		if( (fragmentManager.findFragmentByTag(MMS_TAG) ) instanceof ConversationListFragment) {
			mConversationListFragment = (ConversationListFragment) fragmentManager
					.findFragmentByTag(MMS_TAG);
		}
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
		mAllFragment = (DefaultContactBrowseListFragment) fragmentManager
				.findFragmentByTag(ALL_TAG);
		/* HQ_zhangjing add for dialer merge to contact begin */
		if( (fragmentManager.findFragmentByTag(SPEEDDIAL_TAG)) instanceof CallLogFragment) {
			mRecentsFragment = (CallLogFragment) fragmentManager
					.findFragmentByTag(SPEEDDIAL_TAG);
		}
		/* HQ_zhangjing add for dialer merge to contact end */
        /* begin: change by donghongjing for HQ01473003
         * sometimes when 2 sim inserted, mRecentsFragment == null
         * but mConversationListFragment is not null, mRecentsFragment has been recycled when recreated ?
         * just move mRecentsFragment out of if (mConversationListFragment == null) to avoid null pointer error */
        if (mRecentsFragment == null) {
			// / Modified by guofeiyao
			/*
			mRecentsFragment = new CallLogFragment(
					CallLogQueryHandler.CALL_TYPE_ALL, MAX_RECENTS_ENTRIES,
					System.currentTimeMillis() - OLDEST_RECENTS_DATE, false);
					*/
			mRecentsFragment = new CallLogFragment(
					CallLogQueryHandler.CALL_TYPE_ALL, MAX_RECENTS_ENTRIES,
					0, false);
			// / End
			
			mRecentsFragment.setHasFooterView(true);
        }
        /* end: change by donghongjing for HQ01473003 */
		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		if (mConversationListFragment == null) {
			mConversationListFragment = new ConversationListFragment();
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
			mAllFragment = new DefaultContactBrowseListFragment();
			/* HQ_zhangjing add for dialer merge to contact begin */

			/*HQ_zhangjing modified for smooth stuck begin*/
			if( isEnterFromDialerFirst && !mmsHasInstantiate ){
			 Log.d("jinlibo","createViewsAndFragments,add mConversationListFragment");
			 transaction.add(R.id.tab_pager, mConversationListFragment, MMS_TAG);
			 isEnterFromDialerFirst = false;
			 mmsHasInstantiate = true;
			}
			/*HQ_zhangjing modified for smooth stuck end*/
		}

		// added by guofeiyao begin
        View container = findViewById(R.id.fl_search_ui);
        digits = (EditText)findViewById(R.id.det_digits);
        digits.addTextChangedListener(mPhoneSearchQueryTextListener);
		lo = (TextView) findViewById(R.id.tv_lo);

            // /just for another weird situation when 2 sim cause the activity create again.
            if (mDialpadFragment != null) {
                transaction.remove(mDialpadFragment);
			}
			// /end
        mDialpadFragment = new DialpadFragment(container,digits);

			// /Just for the WTF weird situation that 2 sim cause the activity re-create.
			// /u kown,new demand,new bug :(, I am trying to fix it ::>_<::
            if ( null != mDialpadFragment ) {
    			if ( null == mEnhancedSmartDialSearchFragment ) {
                     mDialpadFragment.updateAboveBtn( false );
		    	} else {
                     mDialpadFragment.updateAboveBtn( mEnhancedSmartDialSearchFragment.getIsSearchEmpty() );
				}
            }
		// end
		

		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		//mFavoritesFragment.setListener(mFavoritesFragmentListener);
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
        /* begin: add by donghongjing for HQ01461545
         * mAllFragment == null means that mConversationListFragment is not null,
         * but mAllFragment has been recycled ????? */
        if (mAllFragment == null) {
            mAllFragment = new DefaultContactBrowseListFragment();
        }
        /* end: add by donghongjing for HQ01461545 */
		mAllFragment
				.setOnContactListActionListener(new ContactBrowserActionListener());

		// Hide all fragments for now. We adjust visibility when we get
		// onSelectedTabChanged()
		// from ActionBarAdapter.

		transaction.commitAllowingStateLoss();
		fragmentManager.executePendingTransactions();

		// Setting Properties after fragment is created
		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		//mFavoritesFragment.setDisplayType(DisplayType.STREQUENT);
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/

		mActionBarAdapter = new ActionBarAdapter(this, this, getActionBar(),
				portraitViewPagerTabs, landscapeViewPagerTabs, toolbar);
		mActionBarAdapter.initialize(savedState, mRequest);
		PDebug.End("createViewsAndFragments, Configure action bar");

		// Add shadow under toolbar
		ViewUtil.addRectangularOutlineProvider(
				findViewById(R.id.toolbar_parent), getResources());

		// modified by guofeiyao for HQ01207444 begin
		// Configure action button
		floatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
		// ViewUtil.setupFloatingActionButton(floatingActionButtonContainer,
		// getResources());
		// final ImageButton floatingActionButton = (ImageButton)
		// findViewById(R.id.floating_action_button);
		// floatingActionButton.setOnClickListener(this);

		boolean mIsLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		View floatingActionButton = findViewById(R.id.ib_dialpad);
		mFloatingActionButtonController = new FloatingActionButtonControllerEM(
				this, floatingActionButtonContainer, floatingActionButton);

		View flDialpad = findViewById(R.id.fl_dialpad);
		dialpadContainer = new FloatingActionButtonControllerEM(this,
				flDialpad, flDialpad);

		WindowManager wm = (WindowManager) this
				.getSystemService(Context.WINDOW_SERVICE);

		int width = wm.getDefaultDisplay().getWidth();
		int height = wm.getDefaultDisplay().getHeight();
		mFloatingActionButtonController.setScreenWidth(width);
		mFloatingActionButtonController.setVisible(false);
		
		dialpadContainer.setScreenWidth(width);

		mDialerPlugIn = new DialerPlugIn(this, mFloatingActionButtonController,
				dialpadContainer, mIsLandscape, mDialpadFragment,
				mRecentsFragment);

		mViewPagerListenersUtil = new ViewPagerListenersUtil();
		mViewPagerListenersUtil.addOnPageChangeListener(mDialerPlugIn);
		mViewPagerListenersUtil.addOnPageChangeListener(mTabPagerListener);

		mTabPager.setOnPageChangeListener(mViewPagerListenersUtil);
		// end

		invalidateOptionsMenuIfNeeded();
		PDebug.End("createViewsAndFragments, prepare fragments");

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			if (getActionBar().isShowing()) {
				getActionBar().hide();// hide by tanghuaizhe for f*g huawei UI
			}
		} else {
			getActionBar().show();
		}

	}

    // / Annotated by guofeiyao,move these operatotions to the Fragment
    
	/**
	 * 
	 * @param DefaultContactBrowseListFragmentView
	 */

	/*
	public void setupHqView(View DefaultContactBrowseListFragmentView) {
	*/
	
		// TODO Auto-generated method stub

		/*
		 * RelativeLayout SearchView =
		 * (RelativeLayout)DefaultContactBrowseListFragmentView
		 * .findViewById(R.id.SearchView);
		 */

       /*
	    View newContatcsView = DefaultContactBrowseListFragmentView
				.findViewById(R.id.newContatcsView);
		View menuView = DefaultContactBrowseListFragmentView
				.findViewById(R.id.menuView);
		View Contactlistcontent_headview = mAllFragment
				.getContactlistcontent_headview();
		final RelativeLayout MyGroup = (RelativeLayout) Contactlistcontent_headview
				.findViewById(R.id.MyGroup);
		final RelativeLayout LifeService = (RelativeLayout) Contactlistcontent_headview
				.findViewById(R.id.LifeService);
		
		MyDock_layout = (LinearLayout) DefaultContactBrowseListFragmentView
				.findViewById(R.id.MyDock_layout);

		mSearchView = (EditText) DefaultContactBrowseListFragmentView
				.findViewById(R.id.search_view);
		MyGroup.setOnClickListener(this);
		LifeService.setOnClickListener(this);
		newContatcsView.setOnClickListener(this);
		menuView.setOnClickListener(this);
	}
    */
    
	// / End

	@Override
	protected void onStart() {
		PDebug.Start("Contacts.onStart");
		if (!mFragmentInitialized) {
			mFragmentInitialized = true;
			/*
			 * Configure fragments if we haven't.
			 * 
			 * Note it's a one-shot initialization, so we want to do this in
			 * {@link #onCreate}.
			 * 
			 * However, because this method may indirectly touch views in
			 * fragments but fragments created in {@link #configureContentView}
			 * using a {@link FragmentTransaction} will NOT have views until
			 * {@link Activity#onCreate} finishes (they would if they were
			 * inflated from a layout), we need to do it here in {@link
			 * #onStart()}.
			 * 
			 * (When {@link Fragment#onCreateView} is called is different in the
			 * former case and in the latter case, unfortunately.)
			 * 
			 * Also, we skip most of the work in it if the activity is a
			 * re-created one. (so the argument.)
			 */
			configureFragments(!mIsRecreatedInstance);
		}
		super.onStart();
		PDebug.End("Contacts.onStart");

        // /added by guofeiyao begin{
		Log.e(GTAG,"onStart");
		// }end
	}

	@Override
	protected void onPause() {
		/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin */
		if( mDialerPlugIn != null ){
			Log.d("onPause","dismiss dialer menu dialog");
			mDialerPlugIn.dismissDialog();
		}
		if( menuDialogForContacts != null && menuDialogForContacts.isShowing()){
			Log.d("onPause","dismiss contacts menu dialog");
			menuDialogForContacts.dismiss();
			menuDialogForContacts = null;
		}
		/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
		mOptionsMenuContactsAvailable = false;
		mProviderStatusWatcher.stop();
		/** M: New Feature CR ID: ALPS00112598 */
		SetIndicatorUtils.getInstance().showIndicator(this, false);
		// / M:[vcs] VCS Feature. @{
		if (mVcsController != null) {
			mVcsController.onPauseVcs();
		}
		// / @}
		super.onPause();

		// /added by guofeiyao begin{
		Log.e(GTAG,"onPause");
		// }end
	}
	@Override
	public void onServiceConnected() {
//		Toast.makeText(PeopleActivity.this, "号码助手Service通信成功！", Toast.LENGTH_SHORT).show();  
		chubaoConnected=true;
	}
	
	@Override
	public void onServiceDisconnected() {
		Toast.makeText(PeopleActivity.this, "号码助手Service通信连接失败！！", Toast.LENGTH_LONG).show(); 
		chubaoConnected=false;
	}
	@Override
	protected void onResume() {
		super.onResume();
		// added by guofeiyao
		mStateSaved = false;
		mFirstLaunch = false;
		// end
		PDebug.Start("Contacts.onResume");
		
		mProviderStatusWatcher.start();
		LogUtils.d(TAG,
				"call showContactsUnavailableFragmentIfNecessary in onresume");
		updateViewConfiguration(true);

		// Re-register the listener, which may have been cleared when
		// onSaveInstanceState was
		// called. See also: onSaveInstanceState
		mActionBarAdapter.setListener(this);
		mDisableOptionItemSelected = false;
		if (mTabPager != null) {
			// modified by guofeiyao for HQ01207444 begin
			// mTabPager.setOnPageChangeListener(mTabPagerListener);
			mTabPager.setOnPageChangeListener(mViewPagerListenersUtil);
			// end
		}
		// Current tab may have changed since the last onSaveInstanceState().
		// Make sure
		// the actual contents match the tab.
		updateFragmentsVisibility();
		/** M: New Feature CR ID: ALPS00112598 */
		SetIndicatorUtils.getInstance().showIndicator(this, true);

		LogUtils.d(TAG, "[Performance test][Contacts] loading data end time: ["
				+ System.currentTimeMillis() + "]");
		// / M: [vcs] VCS feature @{
		if (mVcsController != null) {
			mVcsController.onResumeVcs();
		}
		// / @}
		///M: CTA test for Location permission @{
        if (ContactsSystemProperties.MTK_CTA_SUPPORT) { 
            LocationPermissionDialogFragment.show(this, getFragmentManager());
        }
        /// @}
		PDebug.End("Contacts.onResume");

		// / Added by guofeiyao
		// Revision on 2016/1/20
    	if (null != mDialerPlugIn){
		    mDialerPlugIn.setPosition( mActionBarAdapter.getCurrentTab() );

			if (digits != null ){
				if( !digits.isRtlLocale()&&mDialerPlugIn.getPosition() != 0
					|| digits.isRtlLocale()&&mDialerPlugIn.getPosition() != 2){
                   digits.getText().clear();
				}
            }
            mDialerPlugIn.updateFloatingActionButtonControllerAlignment(false);
            mDialerPlugIn.updateDialpadContainerAlignment(false);
		} else {
            Log.e(GTAG,"error in onResume(), null == mDialerPlugIn");
		}

        boolean showContactsWithNumberOnly = ContactListFilter.getNumberOnlyFromPreferences(getSharedPreferences());
        if (mShowContactsWithNumberOnly != showContactsWithNumberOnly) {
            mShowContactsWithNumberOnly = showContactsWithNumberOnly;
            mAllFragment.reloadData();
        }
        Log.e(GTAG, "onResume");
        // / End
	}

    // /added by guofeiyao for Debug
    private static final String GTAG = "guofeiyao_PeopleActivity";
	
	@Override
	protected void onStop() {
		PDebug.Start("onStop");
		// / M: @{
		if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
			mActionBarAdapter.setSearchMode(false);
			invalidateOptionsMenu();
		}
		// / @
		super.onStop();
		PDebug.End("onStop");
		
		// / Added by guofeiyao 2016/1/20
		Log.e(GTAG,"onStop");
		// / End
	}

	@Override
	protected void onDestroy() {

		PDebug.Start("onDestroy");
		mProviderStatusWatcher.removeListener(this);

		// Some of variables will be null if this Activity redirects Intent.
		// See also onCreate() or other methods called during the Activity's
		// initialization.
		if (mActionBarAdapter != null) {
			mActionBarAdapter.setListener(null);
		}
		if (mContactListFilterController != null) {
			mContactListFilterController.removeListener(this);
		}

		// / M: [vcs] VCS feature.
		if (mVcsController != null) {
			mVcsController.onDestoryVcs();
		}

		PDebug.End("onDestroy");

		// /added by guofeiyao begin{
		Log.e(GTAG,"onDestroy");
		// }end
		if(csom != null){
			try{
			csom.destroy();
			}catch(Exception e){
				Log.e(TAG,"csom:e = " + e);
			}
		}
		super.onDestroy();
		
	}

	private void configureFragments(boolean fromRequest) {
		if (fromRequest) {
			ContactListFilter filter = null;
			int actionCode = mRequest.getActionCode();
			boolean searchMode = mRequest.isSearchMode();
			/*final*/ int tabToOpen;
			switch (actionCode) {
			case ContactsRequest.ACTION_ALL_CONTACTS:
				filter = ContactListFilter
						.createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
				tabToOpen = TabState.ALL;
				break;
			case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
				filter = ContactListFilter
						.createFilterWithType(ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
				tabToOpen = TabState.ALL;
				break;

			case ContactsRequest.ACTION_FREQUENT:
			case ContactsRequest.ACTION_STREQUENT:
			/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
			/*case ContactsRequest.ACTION_STARRED:
				tabToOpen = TabState.FAVORITES;
				break;
				*/
			/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
			case ContactsRequest.ACTION_VIEW_CONTACT:
				tabToOpen = TabState.ALL;
				break;
			/*HQ_zhangjing modified for contacts TAB selected begin*/
            case ContactsRequest.ACTION_DIAL:
                tabToOpen = TabState.DIAL;
                    //if (isDialIntent(getIntent()) && mDialerFragment != null) {
                        //m.setStartedFromNewIntent(true);
                    //}
                break;
			case ContactsRequest.ACTION_MMS:
				tabToOpen = TabState.MMS;
				break;
            case ContactsRequest.ACTION_DEFAULT:
                tabToOpen = TabState.ALL;
                break;
			/*HQ_zhangjing modified for contacts TAB selected begin*/		
			default:
				tabToOpen = -1;
				break;
			}

			if(isRTL()){

				tabToOpen = 3-1-tabToOpen;

			}
			if (tabToOpen != -1) {
				mActionBarAdapter.setCurrentTab(tabToOpen);
			}

			if (filter != null) {
				mContactListFilterController
						.setContactListFilter(filter, false);
				searchMode = false;
			}

			if (mRequest.getContactUri() != null) {
				searchMode = false;
			}

			mActionBarAdapter.setSearchMode(searchMode);
			//modified by jinlibo for performance
//			configureContactListFragmentForRequest();
		}
        //modified by jinlibo for performance
//		configureContactListFragment();

		invalidateOptionsMenuIfNeeded();
	}

	@Override
	public void onContactListFilterChanged() {
		if (mAllFragment == null || !mAllFragment.isAdded()) {
			return;
		}

		mAllFragment.setFilter(mContactListFilterController.getFilter());

		invalidateOptionsMenuIfNeeded();
	}

	/**
	 * Handler for action bar actions.
	 */
	@Override
	public void onAction(int action) {
		// / M: [vcs] @{
		if (mVcsController != null) {
			mVcsController.onActionVcs(action);
		}
		// / @}
		switch (action) {
		case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
			// Tell the fragments that we're in the search mode
			configureFragments(false /* from request */);
			updateFragmentsVisibility();
			invalidateOptionsMenu();
			break;
		case ActionBarAdapter.Listener.Action.STOP_SEARCH_MODE:
			setQueryTextToFragment("");
			updateFragmentsVisibility();
			invalidateOptionsMenu();
			break;
		case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
			setQueryTextToFragment(mActionBarAdapter.getQueryString());
			break;
		default:
			throw new IllegalStateException("Unkonwn ActionBarAdapter action: "
					+ action);
		}
	}

	@Override
	public void onSelectedTabChanged() {
		// / M: [vcs] @{
		if (mVcsController != null) {
			mVcsController.onSelectedTabChangedEx();
		}
		// / @}
		updateFragmentsVisibility();
	}

	@Override
	public void onUpButtonPressed() {
		onBackPressed();
	}

	private void updateDebugOptionsVisibility(boolean visible) {
		if (mEnableDebugMenuOptions != visible) {
			mEnableDebugMenuOptions = visible;
			invalidateOptionsMenu();
		}
	}

	/**
	 * Updates the fragment/view visibility according to the current mode, such
	 * as {@link ActionBarAdapter#isSearchMode()} and
	 * {@link ActionBarAdapter#getCurrentTab()}.
	 */
	private void updateFragmentsVisibility() {
		int tab = mActionBarAdapter.getCurrentTab();
		Log.i(TAG, "the current tab is "+tab);

		if (mActionBarAdapter.isSearchMode()) {
			mTabPagerAdapter.setSearchMode(true);
		} else {
			// No smooth scrolling if quitting from the search mode.
			final boolean wasSearchMode = mTabPagerAdapter.isSearchMode();
			mTabPagerAdapter.setSearchMode(false);
			if (mTabPager.getCurrentItem() != tab) {
				mTabPager.setCurrentItem(tab, !wasSearchMode);
			}
		}
		invalidateOptionsMenu();
		showEmptyStateForTab(tab);
	}

	private void showEmptyStateForTab(int tab) {
		if (mContactsUnavailableFragment != null) {
			switch (getTabPositionForTextDirection(tab)) {
			/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
			case TabState.MMS:
			/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
				mContactsUnavailableFragment.setMessageText(
						R.string.listTotalAllContactsZeroStarred, -1);
				break;
			case TabState.ALL:
				mContactsUnavailableFragment.setMessageText(
						R.string.noContacts, -1);
				break;
			}
			// When using the mContactsUnavailableFragment the ViewPager doesn't
			// contain two views.
			// Therefore, we have to trick the ViewPagerTabs into thinking we
			// have changed tabs
			// when the mContactsUnavailableFragment changes. Otherwise the tab
			// strip won't move.
			mViewPagerTabs.onPageScrolled(tab, 0, 0);
		}
	}

	private class TabPagerListener implements ViewPager.OnPageChangeListener {

		// This package-protected constructor is here because of a possible
		// compiler bug.
		// PeopleActivity$1.class should be generated due to the private
		// outer/inner class access
		// needed here. But for some reason, PeopleActivity$1.class is missing.
		// Since $1 class is needed as a jvm work around to get access to the
		// inner class,
		// changing the constructor to package-protected or public will solve
		// the problem.
		// To verify whether $1 class is needed, javap
		// PeopleActivity$TabPagerListener and look for
		// references to PeopleActivity$1.
		//
		// When the constructor is private and PeopleActivity$1.class is
		// missing, proguard will
		// correctly catch this and throw warnings and error out the build on
		// user/userdebug builds.
		//
		// All private inner classes below also need this fix.
		TabPagerListener() {
		}

		@Override
		public void onPageScrollStateChanged(int state) {
			if (!mTabPagerAdapter.isSearchMode()) {
				mViewPagerTabs.onPageScrollStateChanged(state);
			}
		}

		@Override
		public void onPageScrolled(int position, float positionOffset,
				int positionOffsetPixels) {
		  /*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 begin*/
			if( isShouldSkipOnPageScrolled && positionOffset > 0.0f ){
				isShouldSkipOnPageScrolled = false;
				return;
			}
			/*HQ_zhangjing 2015-10-27 modified for CQ HQ01419307 end*/
			/*HQ_zhangjing add for merge mms finish the actionMode in other page begin*/
			if( position != TabState.MMS ){
				if( mConversationListFragment != null ){
					mConversationListFragment.finishActionMode();
				}
			}
			/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
			if (!mTabPagerAdapter.isSearchMode()) {
				mViewPagerTabs.onPageScrolled(position, positionOffset,
						positionOffsetPixels);
			}
		}
	
		
		@Override
		public void onPageSelected(int position) {
			// Make sure not in the search mode, in which case position !=
			// TabState.ordinal().
			hideInputMethod();//HQ_zhangjing 2015-10-23 modified for CQ HQ01462112
			if (!mTabPagerAdapter.isSearchMode()) {
				mActionBarAdapter.setCurrentTab(position, false);
				mViewPagerTabs.onPageSelected(position);
				showEmptyStateForTab(position);
				// / M: [vcs] @{
				if (mVcsController != null) {
					mVcsController.onPageSelectedVcs();
				}
				// / @}
				if(mAllFragment.getDefaultContactBrowseListFragmentView()!=null){
					if(position==TabState.DIAL){
						getSideBar().setVisibility(View.INVISIBLE);
						getmSearchView().setVisibility(View.INVISIBLE);
					}else {
						getSideBar().setVisibility(View.VISIBLE);
						getmSearchView().setVisibility(View.VISIBLE);
					}
					}
				invalidateOptionsMenu();
			}
		}
	}

	/**
	 * Adapter for the {@link ViewPager}. Unlike {@link FragmentPagerAdapter},
	 * {@link #instantiateItem} returns existing fragments, and
	 * {@link #instantiateItem}/ {@link #destroyItem} show/hide fragments
	 * instead of attaching/detaching.
	 * 
	 * In search mode, we always show the "all" fragment, and disable the swipe.
	 * We change the number of items to 1 to disable the swipe.
	 * 
	 * TODO figure out a more straight way to disable swipe.
	 */
	private class TabPagerAdapter extends PagerAdapter {
		private final FragmentManager mFragmentManager;
		private FragmentTransaction mCurTransaction = null;

		private boolean mTabPagerAdapterSearchMode;

		private Fragment mCurrentPrimaryItem;

		public TabPagerAdapter() {
			mFragmentManager = getFragmentManager();
		}

		public boolean isSearchMode() {
			return mTabPagerAdapterSearchMode;
		}

		public void setSearchMode(boolean searchMode) {
			if (searchMode == mTabPagerAdapterSearchMode) {
				return;
			}
			mTabPagerAdapterSearchMode = searchMode;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mTabPagerAdapterSearchMode ? 1 : TabState.COUNT;
		}

		/** Gets called when the number of items changes. */
		@Override
		public int getItemPosition(Object object) {
			if (mTabPagerAdapterSearchMode) {
				if (object == mAllFragment) {
					return 0; // Only 1 page in search mode
				}
			} else {
				/* HQ_zhangjing add for dialer merge to contact begin */
				if (object == mRecentsFragment) {
					return getTabPositionForTextDirection(TabState.DIAL);
				}
				/* HQ_zhangjing add for dialer merge to contact end */
				/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
				if (object == mConversationListFragment) {
					return getTabPositionForTextDirection(TabState.MMS);
				/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
				}
				if (object == mAllFragment) {
					return getTabPositionForTextDirection(TabState.ALL);
				}
			}
			return POSITION_NONE;
		}

		@Override
		public void startUpdate(ViewGroup container) {
		}

		private Fragment getFragment(int position) {
			position = getTabPositionForTextDirection(position);
			if (mTabPagerAdapterSearchMode) {
				if (position != 0) {
					// This has only been observed in monkey tests.
					// Let's log this issue, but not crash
					Log.w(TAG, "Request fragment at position=" + position
							+ ", eventhough we " + "are in search mode");
				}
				return mAllFragment;
			} else {
				/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
				if (position == TabState.MMS) {
					return mConversationListFragment;
				/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
				} else if (position == TabState.ALL) {
					return mAllFragment;
					/* HQ_zhangjing add for dialer merge to contact begin */
				} else if (position == TabState.DIAL) {
					return mRecentsFragment;
				}
				/* HQ_zhangjing add for dialer merge to contact end */
			}
			throw new IllegalArgumentException("position: " + position);
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			if (mCurTransaction == null) {
		        mCurTransaction = mFragmentManager.beginTransaction();
			}
			//jinlibo modified begin for performance
			String tag = null;
			switch(getTabPositionForTextDirection(position)){
			    case TabState.DIAL:
			        tag = SPEEDDIAL_TAG;
	                 if(mDialpadFragment !=null && !mDialpadFragment.isAdded()){
	                        mCurTransaction.add(R.id.fl_dialpad, mDialpadFragment, TAG_DIALPAD_FRAGMENT);
	                    }
			        break;
			    case TabState.ALL:
			        tag = ALL_TAG;
			        break;
			    case TabState.MMS:
			        tag = MMS_TAG;
					//HQ_zhangjing modified for smooth stuck  
					mmsHasInstantiate = true;
			        break;
			}
			Fragment f = mFragmentManager.findFragmentByTag(tag);
			if(f != null){
			    mCurTransaction.show(f);
			    Log.i("jinlibo","attach,tag = "+ tag);
			}else{
			    f = getFragment(position);
			    if(null != f) {
			    	mCurTransaction.add(R.id.tab_pager, f, tag);
					Log.i("jinlibo", "add,null != f and tag = " + tag);
			    }
			    Log.i("jinlibo", "add");
			}
	        if (f != null && f != mCurrentPrimaryItem) {
	            FragmentCompat.setMenuVisibility(f, false);
	            FragmentCompat.setUserVisibleHint(f, false);
	        }
            //jinlibo modify end
			// Non primary pages are not visible.
//			f.setUserVisibleHint(f == mCurrentPrimaryItem);
			return f;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
            /* begin: add by donghongjing for HQ01473003 */
            if(object == null || !(object instanceof Fragment)){
                return;
            }
            /* end: add by donghongjing for HQ01473003 */

			if (mCurTransaction == null) {
				mCurTransaction = mFragmentManager.beginTransaction();
			}
			//jinlibo modified for performance
			mCurTransaction.hide((Fragment)object);
		}

		@Override
		public void finishUpdate(ViewGroup container) {
			if (mCurTransaction != null) {
				mCurTransaction.commitAllowingStateLoss();
				mCurTransaction = null;
				mFragmentManager.executePendingTransactions();
			}
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			//return ((Fragment) object).getView() == view;
			//modify by zhangjinqiang for HQ01473643s
			if(object==null|| !(object instanceof Fragment)){
				return false;
			}else{
				return ((Fragment) object).getView() == view;
			}
		}

		@Override
		public void setPrimaryItem(ViewGroup container, int position,
				Object object) {
		    //jinlibo modified begin for performance
	        Fragment fragment = (Fragment)object;
	        Log.i("jinlibo", "jinlibo");
	        if (fragment != mCurrentPrimaryItem) {
	            if (mCurrentPrimaryItem != null) {
	                FragmentCompat.setMenuVisibility(mCurrentPrimaryItem, false);
	                FragmentCompat.setUserVisibleHint(mCurrentPrimaryItem, false);
	            }
	            if (fragment != null) {
	                FragmentCompat.setMenuVisibility(fragment, true);
	                FragmentCompat.setUserVisibleHint(fragment, true);
	            }
	            mCurrentPrimaryItem = fragment;
	        }
	        //jinlibo modified end
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void restoreState(Parcelable state, ClassLoader loader) {
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return mTabTitles[position];
		}
	}

	private void setQueryTextToFragment(String query) {
		mAllFragment.setQueryString(query, true);
		mAllFragment.setVisibleScrollbarEnabled(!mAllFragment.isSearchMode());
	}

	private void configureContactListFragmentForRequest() {
		/*HQ_zhangjing modified for monkey contacts crash for CQ HQ01400844 begin*/
		if ( mRequest != null && mRequest.getContactUri() != null) {
			Uri contactUri = mRequest.getContactUri();
		/*HQ_zhangjing modified for monkey contacts crash for CQ HQ01400844 end*/
			mAllFragment.setSelectedContactUri(contactUri);
		}

		mAllFragment.setFilter(mContactListFilterController.getFilter());
		setQueryTextToFragment(mActionBarAdapter.getQueryString());

		if (mRequest.isDirectorySearchEnabled()) {
			mAllFragment
					.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
		} else {
			mAllFragment
					.setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
		}
	}

	private void configureContactListFragment() {
		// Filter may be changed when this Activity is in background.
		mAllFragment.setFilter(mContactListFilterController.getFilter());

		mAllFragment.setVerticalScrollbarPosition(getScrollBarPosition());
		mAllFragment.setSelectionVisible(false);
	}

	private int getScrollBarPosition() {
		return isRTL() ? View.SCROLLBAR_POSITION_LEFT
				: View.SCROLLBAR_POSITION_RIGHT;
	}

	private boolean isRTL() {
		final Locale locale = Locale.getDefault();
		return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
	}

	@Override
	public void onProviderStatusChange() {
		updateViewConfiguration(false);
	}

	private void updateViewConfiguration(boolean forceUpdate) {
		ProviderStatusWatcher.Status providerStatus = mProviderStatusWatcher
				.getProviderStatus();
		if (!forceUpdate && (mProviderStatus != null)
				&& (providerStatus.status == mProviderStatus.status))
			return;

		mProviderStatus = providerStatus;
		View contactsUnavailableView = findViewById(R.id.contacts_unavailable_view);

		if (mProviderStatus.status == ProviderStatus.STATUS_NORMAL) {
			// Ensure that the mTabPager is visible; we may have made it
			// invisible below.
			contactsUnavailableView.setVisibility(View.GONE);
			if (mTabPager != null) {
				mTabPager.setVisibility(View.VISIBLE);
			}

			if (mAllFragment != null) {
				mAllFragment.setEnabled(true);
			}
		} else {
			// If there are no accounts on the device and we should show the
			// "no account" prompt
			// (based on {@link SharedPreferences}), then launch the account
			// setup activity so the
			// user can sign-in or create an account.
			//
			// Also check for ability to modify accounts. In limited user mode,
			// you can't modify
			// accounts so there is no point sending users to account setup
			// activity.
			final UserManager userManager = UserManager.get(this);
			final boolean disallowModifyAccounts = userManager
					.getUserRestrictions().getBoolean(
							UserManager.DISALLOW_MODIFY_ACCOUNTS);
			if (!disallowModifyAccounts
					&& !areContactWritableAccountsAvailable()
					&& AccountPromptUtils.shouldShowAccountPrompt(this)) {
				AccountPromptUtils.neverShowAccountPromptAgain(this);
				AccountPromptUtils.launchAccountPrompt(this);
				return;
			}

			// Otherwise, continue setting up the page so that the user can
			// still use the app
			// without an account.
			if (mAllFragment != null) {
				mAllFragment.setEnabled(false);
			}
			if (mContactsUnavailableFragment == null) {
				mContactsUnavailableFragment = new ContactsUnavailableFragment();
				mContactsUnavailableFragment
						.setOnContactsUnavailableActionListener(new ContactsUnavailableFragmentListener());
				getFragmentManager()
						.beginTransaction()
						.replace(R.id.contacts_unavailable_container,
								mContactsUnavailableFragment)
						.commitAllowingStateLoss();
			}
			mContactsUnavailableFragment.updateStatus(mProviderStatus);

			// Show the contactsUnavailableView, and hide the mTabPager so that
			// we don't
			// see it sliding in underneath the contactsUnavailableView at the
			// edges.
			/**
			 * M: Bug Fix @{ CR ID: ALPS00113819 Descriptions: remove
			 * ContactUnavaliableFragment Fix wait cursor keeps showing while no
			 * contacts issue
			 */
			ActivitiesUtils.setAllFramgmentShow(contactsUnavailableView,
					mAllFragment, this, mTabPager,
					mContactsUnavailableFragment, mProviderStatus);

			showEmptyStateForTab(mActionBarAdapter.getCurrentTab());
		}

		invalidateOptionsMenuIfNeeded();
	}

	private final class ContactBrowserActionListener implements
			OnContactBrowserActionListener {
		ContactBrowserActionListener() {
		}

		@Override
		public void onSelectionChange() {
		}

		@Override
		public void onViewContactAction(Uri contactLookupUri) {
			Intent intent = QuickContact.composeQuickContactsIntent(
					PeopleActivity.this, (Rect) null, contactLookupUri,
					QuickContactActivity.MODE_FULLY_EXPANDED, null);
			startActivity(intent);
		}

		@Override
		public void onDeleteContactAction(Uri contactUri) {
			ContactDeletionInteraction.start(PeopleActivity.this, contactUri,
					false);
		}

		@Override
		public void onFinishAction() {
			onBackPressed();
		}

		@Override
		public void onInvalidSelection() {
			ContactListFilter filter;
			ContactListFilter currentFilter = mAllFragment.getFilter();
			if (currentFilter != null
					&& currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
				filter = ContactListFilter
						.createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
				mAllFragment.setFilter(filter);
			} else {
				filter = ContactListFilter
						.createFilterWithType(ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
				mAllFragment.setFilter(filter, false);
			}
			mContactListFilterController.setContactListFilter(filter, true);
		}
	}

	private class ContactsUnavailableFragmentListener implements
			OnContactsUnavailableActionListener {
		ContactsUnavailableFragmentListener() {
		}

		@Override
		public void onCreateNewContactAction() {
			startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
		}

		@Override
		public void onAddAccountAction() {
			Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			intent.putExtra(Settings.EXTRA_AUTHORITIES,
					new String[] { ContactsContract.AUTHORITY });
			startActivity(intent);
		}

		@Override
		public void onImportContactsFromFileAction() {
			/** M: New Feature @{ */
			final Intent intent = new Intent(PeopleActivity.this,
					ContactImportExportActivity.class);
			startActivity(intent);
			/** @} */

		}

		@Override
		public void onFreeInternalStorageAction() {
			startActivity(new Intent(
					Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
		}
	}

	private final class StrequentContactListFragmentListener implements
			ContactTileListFragment.Listener {
		StrequentContactListFragmentListener() {
		}

		@Override
		public void onContactSelected(Uri contactUri, Rect targetRect) {
			Intent intent = QuickContact.composeQuickContactsIntent(
					PeopleActivity.this, targetRect, contactUri,
					QuickContactActivity.MODE_FULLY_EXPANDED, null);
			startActivity(intent);
		}

		@Override
		public void onCallNumberDirectly(String phoneNumber) {
			// No need to call phone number directly from People app.
			Log.w(TAG, "unexpected invocation of onCallNumberDirectly()");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		PDebug.Start("onCreateOptionsMenu");
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.people_options, menu);
		// View PopupWindow =getLayoutInflater().inflate(R.menu.people_options,
		// null);
		// popup= new
		// android.widget.PopupWindow(PopupWindow,LayoutParams.FILL_PARENT,
		// LayoutParams.WRAP_CONTENT);
		// popup.setFocusable(true);
		// / M: Op01 will add "show sim capacity" item
		ExtensionManager.getInstance().getOp01Extension()
				.addOptionsMenu(this, menu);
		// / M: op09
		ExtensionManager.getInstance().getContactListExtension()
				.addOptionsMenu(menu, null);
		// / M: [vcs] VCS new feature @{
		if (mVcsController != null) {
			mVcsController.onCreateOptionsMenuVcs(menu);
		}
		// / @}
		PDebug.End("onCreateOptionsMenu");
		return true;
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		// TODO Auto-generated method stub
		// if (popup != null){
		// if (popup.isShowing())
		// popup.dismiss();
		// else{
		// // View layout = getLayoutInflater().inflate(R.layout.main, null);
		// popup.showAtLocation(getParent().getWindow().getDecorView(),
		// Gravity.CENTER, 0, 0);/*居中弹出菜单*/
		// }
		// }
		return super.onMenuOpened(featureId, menu);

	}

	private void invalidateOptionsMenuIfNeeded() {
		if (isOptionsMenuChanged()) {
			invalidateOptionsMenu();
		}
	}

	public boolean isOptionsMenuChanged() {
		if (mOptionsMenuContactsAvailable != areContactsAvailable()) {
			return true;
		}

		if (mAllFragment != null && mAllFragment.isOptionsMenuChanged()) {
			return true;
		}

		return false;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		PDebug.Start("onPrepareOptionsMenu");
		// / M: Fix ALPS01612926,smartbook issue @{
		if (mActionBarAdapter == null) {
			LogUtils.w(TAG,
					"[onPrepareOptionsMenu]mActionBarAdapter is null,return..");
			return true;
		}
		// / @}

		// Get references to individual menu items in the menu
		final MenuItem contactsFilterMenu = menu
				.findItem(R.id.menu_contacts_filter);

		/** M: New Feature @{ */
		final MenuItem deleteContactMenu = menu
				.findItem(R.id.menu_delete_contact);
		final MenuItem groupMenu = menu.findItem(R.id.menu_groups);
		/** @} */
		// / M: [VoLTE ConfCall]
		final MenuItem conferenceCallMenu = menu
				.findItem(R.id.menu_conference_call);

		final MenuItem clearFrequentsMenu = menu
				.findItem(R.id.menu_clear_frequents);
		final MenuItem helpMenu = menu.findItem(R.id.menu_help);

		final boolean isSearchMode = mActionBarAdapter.isSearchMode();
		if (isSearchMode) {
			contactsFilterMenu.setVisible(false);
			clearFrequentsMenu.setVisible(false);
			helpMenu.setVisible(false);
			/** M: New Feature @{ */
			deleteContactMenu.setVisible(false);
			groupMenu.setVisible(false);
			/** @} */
			// / M: [VoLTE ConfCall]
			conferenceCallMenu.setVisible(false);
		} else {
			switch (getTabPositionForTextDirection(mActionBarAdapter
					.getCurrentTab())) {
			/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
			case TabState.MMS:
			/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
				contactsFilterMenu.setVisible(false);
				clearFrequentsMenu.setVisible(hasFrequents());
				/** M: New Feature */
				deleteContactMenu.setVisible(false);
				break;
			case TabState.ALL:
				contactsFilterMenu.setVisible(true);
				clearFrequentsMenu.setVisible(false);
				break;
			}
			HelpUtils.prepareHelpMenuItem(this, helpMenu,
					R.string.help_url_people_main);
		}
		final boolean showMiscOptions = !isSearchMode;
		makeMenuItemVisible(menu, R.id.menu_search, showMiscOptions);
		makeMenuItemVisible(menu, R.id.menu_import_export, showMiscOptions
				&& ActivitiesUtils.showImportExportMenu(this));
		makeMenuItemVisible(menu, R.id.menu_accounts, showMiscOptions);
		makeMenuItemVisible(menu, R.id.menu_settings, showMiscOptions
				&& !ContactsPreferenceActivity.isEmpty(this));
		/** M: New Feature */
		makeMenuItemVisible(menu, R.id.menu_share_visible_contacts,
				showMiscOptions);
		/** M: For VCS new feature */
		ActivitiesUtils.prepareVcsMenu(menu, mVcsController);
		PDebug.End("onPrepareOptionsMenu");

		// / M: [VoLTE ConfCall] @{
		if (!VolteUtils.isVoLTEConfCallEnable(this)) {
			conferenceCallMenu.setVisible(false);
		}
		// / @}
		return true;
	}

	/**
	 * Returns whether there are any frequently contacted people being displayed
	 * 
	 * @return
	 */
	private boolean hasFrequents() {
		/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
		return true;
		/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/
	}

	private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
		MenuItem item = menu.findItem(itemId);
		if (item != null) {
			item.setVisible(visible);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDisableOptionItemSelected) {
			return false;
		}

		switch (item.getItemId()) {
		case android.R.id.home: {
			// The home icon on the action bar is pressed
			if (mActionBarAdapter.isUpShowing()) {
				// "UP" icon press -- should be treated as "back".
				onBackPressed();
			}
			return true;
		}
		case R.id.menu_settings: {
			final Intent intent = new Intent(this,
					ContactsPreferenceActivity.class);
			// Since there is only one section right now, make sure it is
			// selected on
			// small screens.
			intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
					DisplayOptionsPreferenceFragment.class.getName());
			// By default, the title of the activity should be equivalent to the
			// fragment
			// title. We set this argument to avoid this. Because of a bug, the
			// following
			// line isn't necessary. But, once the bug is fixed this may become
			// necessary.
			// b/5045558 refers to this issue, as well as another.
			intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE,
					R.string.activity_title_settings);
			startActivity(intent);
			return true;
		}
		case R.id.menu_contacts_filter: {
			AccountFilterUtil.startAccountFilterActivityForResult(this,
					SUBACTIVITY_ACCOUNT_FILTER,
					mContactListFilterController.getFilter());
			return true;
		}
		case R.id.menu_search: {
			onSearchRequested();
			return true;
		}
		case R.id.menu_import_export: {
			/** M: Change Feature */
			return ActivitiesUtils.doImportExport(this);
		}
		case R.id.menu_clear_frequents: {
			ClearFrequentsDialog.show(getFragmentManager());
			return true;
		}
		case R.id.menu_accounts: {
			final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
			intent.putExtra(Settings.EXTRA_AUTHORITIES,
					new String[] { ContactsContract.AUTHORITY });
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			startActivity(intent);
			return true;
		}
		/** M: New feature @{ */

		/** M: Cancel delete contacts if import/export contact is processing */
		case R.id.menu_delete_contact: {
			return ActivitiesUtils.deleteContact(this);
		}
		/** M: Share contacts */
		case R.id.menu_share_visible_contacts: {
			startActivity(new Intent()
					.setClassName(getApplicationContext(),
							"com.mediatek.contacts.list.ContactListMultiChoiceActivity")
					.setAction(
							com.mediatek.contacts.util.ContactsIntent.LIST.ACTION_SHARE_MULTICONTACTS));
			return true;
		}
		/** M: [vcs] */
		case R.id.menu_vcs: {
			if (mVcsController != null) {
				mVcsController.onVcsItemSelected();
			}
			return true;
		}
		/** M: Group related */
		case R.id.menu_groups: {
			startActivity(new Intent(PeopleActivity.this,
					GroupBrowseActivity.class));
			return true;
		}
		/** @} */
		/** M: [VoLTE ConfCall]Conference call @{ */
		case R.id.menu_conference_call: {
			return ActivitiesUtils.conferenceCall(this);
		}
		/** @} */

		}
		return false;
	}

	@Override
	public boolean onSearchRequested() { // Search key pressed.
		mActionBarAdapter.setSearchMode(true);
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case SUBACTIVITY_ACCOUNT_FILTER: {
            if (resultCode == Activity.RESULT_OK) {
                mShowContactsWithNumberOnly = ContactListFilter.getNumberOnlyFromPreferences(getSharedPreferences());
            }
			AccountFilterUtil.handleAccountFilterResult(
					mContactListFilterController, resultCode, data);
			break;
		}

		// TODO: Using the new startActivityWithResultFromFragment API this
		// should not be needed
		// anymore
		case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
			if (resultCode == RESULT_OK) {
				mAllFragment.onPickerResult(data);
			}

			// TODO fix or remove multipicker code
			// else if (resultCode == RESULT_CANCELED && mMode ==
			// MODE_PICK_MULTIPLE_PHONES) {
			// // Finish the activity if the sub activity was canceled as back
			// key is used
			// // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
			// finish();
			// }
			// break;
			break;
		case  98:
//			Toast.makeText(PeopleActivity.this,"!!!!!!", Toast.LENGTH_SHORT).show();
			
			
			break;
			
		}

		// added by guofeiyao begin
		// if (requestCode == ACTIVITY_REQUEST_CODE_VOICE_SEARCH) {
		// if (resultCode == RESULT_OK) {
		// final ArrayList<String> matches = data
		// .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
		// if (matches.size() > 0) {
		// final String match = matches.get(0);
		// mVoiceSearchQuery = match;
		// } else {
		// Log.e(TAG, "Voice search - nothing heard");
		// }
		// } else {
		// Log.e(TAG, "Voice search failed");
		// }
		// }
		// /** M: [VoLTE ConfCall] Handle the volte conference call. @{ */
		// else if (requestCode ==
		// DialerVolteUtils.ACTIVITY_REQUEST_CODE_PICK_PHONE_CONTACTS) {
		// if (resultCode == RESULT_OK) {
		// DialerVolteUtils.launchVolteConfCall(this, data);
		// } else {
		// Log.e(TAG, "Volte conference call not pick contacts");
		// }
		// }
		// /** @} */
		// end

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO move to the fragment
		switch (keyCode) {
		// case KeyEvent.KEYCODE_CALL: {
		// if (callSelection()) {
		// return true;
		// }
		// break;
		// }

		case KeyEvent.KEYCODE_DEL: {
			if (deleteSelection()) {
				return true;
			}
			break;
		}
		default: {
			// Bring up the search UI if the user starts typing
			final int unicodeChar = event.getUnicodeChar();
			if ((unicodeChar != 0)
					// If COMBINING_ACCENT is set, it's not a unicode character.
					&& ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
					&& !Character.isWhitespace(unicodeChar)) {
				String query = new String(new int[] { unicodeChar }, 0, 1);
				if (!mActionBarAdapter.isSearchMode()) {
					mActionBarAdapter.setSearchMode(true);
					mActionBarAdapter.setQueryString(query);
					return true;
				}
			}
		}
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		if (mActionBarAdapter.isSearchMode()) {
			mActionBarAdapter.setSearchMode(false);
			/** M: New Feature @{ */
		} else if (!ContactsSystemProperties.MTK_PERF_RESPONSE_TIME
				&& isTaskRoot()) {
			// Instead of stopping, simply push this to the back of the stack.
			// This is only done when running at the top of the stack;
			// otherwise, we have been launched by someone else so need to
			// allow the user to go back to the caller.
			moveTaskToBack(false);
			/** @} */
		} else {
			// / Modified by guofeiyao,now the activity can be kept in memory permanently
			//super.onBackPressed();
			Intent home = new Intent(Intent.ACTION_MAIN);
			home.addCategory(Intent.CATEGORY_HOME);
			startActivity(home);
			// / End
		}
	}

	private boolean deleteSelection() {
		// TODO move to the fragment
		// if (mActionCode == ContactsRequest.ACTION_DEFAULT) {
		// final int position = mListView.getSelectedItemPosition();
		// if (position != ListView.INVALID_POSITION) {
		// Uri contactUri = getContactUri(position);
		// if (contactUri != null) {
		// doContactDelete(contactUri);
		// return true;
		// }
		// }
		// }
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mActionBarAdapter.onSaveInstanceState(outState);

		// Clear the listener to make sure we don't get callbacks after
		// onSaveInstanceState,
		// in order to avoid doing fragment transactions after it.
		// TODO Figure out a better way to deal with the issue.
		mDisableOptionItemSelected = true;
		mActionBarAdapter.setListener(null);
		if (mTabPager != null) {
			mTabPager.setOnPageChangeListener(null);
		}

		// added by guofeiyao
		outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
		outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
		outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
		// outState.putBoolean(KEY_FIRST_LAUNCH, mFirstLaunch);
		outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
		mStateSaved = true;
		// end
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		// In our own lifecycle, the focus is saved and restore but later taken
		// away by the
		// ViewPager. As a hack, we force focus on the SearchView if we know
		// that we are searching.
		// This fixes the keyboard going away on screen rotation
		if (mActionBarAdapter.isSearchMode()) {
			mActionBarAdapter.setFocusOnSearchView();
		}
	}

	@Override
	public DialogManager getDialogManager() {
		return mDialogManager;
	}

	@Override
	public void onClick(View view) {

		switch (view.getId()) {
		case R.id.floating_action_button:
		case R.id.newContatcsView:
			Intent intent = new Intent(Intent.ACTION_INSERT,
					Contacts.CONTENT_URI);
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				intent.putExtras(extras);
			}
			try {
				startActivity(intent);
			} catch (ActivityNotFoundException ex) {
				Toast.makeText(PeopleActivity.this, R.string.missing_app,
						Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.menuView:
			// openOptionsMenu();
			//HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked
			if( menuDialogForContacts == null ){
				Builder builder = new AlertDialog.Builder(PeopleActivity.this);

			builder.setItems(getResources().getStringArray(R.array.menuItems),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							// Toast.makeText(PeopleActivity.this,
							// "dianji "+which, Toast.LENGTH_SHORT).show();
							switch (which) {
							case CONTACTS_TO_DISPLAY://
								AccountFilterUtil
										.startAccountFilterActivityForResult(
												PeopleActivity.this,
												SUBACTIVITY_ACCOUNT_FILTER,
												mContactListFilterController
														.getFilter());
								break;

							case IMPORT_EXPORT://
								ActivitiesUtils
										.doImportExport(PeopleActivity.this);
								break;

							case ACCOUNTS://
								final Intent intent = new Intent(
										Settings.ACTION_SYNC_SETTINGS);
								/*intent.putExtra(
										Settings.EXTRA_AUTHORITIES,
										new String[] { ContactsContract.AUTHORITY });
								intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);*/
								startActivity(intent);
								break;

							case DELETE_CONTACTS://
								ActivitiesUtils
										.deleteContact(PeopleActivity.this);
								break;

							case JOIN_CONTACTS://
								Intent ContactsSettingIntent = new Intent(
										PeopleActivity.this,
										ContactsSettingActivity.class);
                                                                startActivity(ContactsSettingIntent);
								break;

							default:
								break;
							}

						}
					});
			/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked begin*/
				menuDialogForContacts = builder.create();
			}else{
				Log.d("dismissdialog","do not need to create contacts dialog and shou direct");
			}
			menuDialogForContacts.show();
			/*HQ_zhangjing 2015-10-15 modified for dismiss menu dialog when home key is clicked end*/
			break;
		case R.id.MyGroup:
			startActivity(new Intent(PeopleActivity.this,
					GroupBrowseActivity.class));
			break;
		case R.id.LifeService:
			if(chubaoConnected==false){
				Toast.makeText(PeopleActivity.this, "触宝号码助手通讯失败，可能已经被卸载。", Toast.LENGTH_SHORT).show();
				break;
			}
			LayoutInflater inflater=LayoutInflater.from(PeopleActivity.this);
			View chubao_confirm_dialog  = inflater.inflate(R.layout.chubao_confirm_dialog,null);
			/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
            		isNomore = (CheckBox)chubao_confirm_dialog.findViewById(R.id.nomore);
			/* HQ_liujin modified end */
			chubao=getSharedPreferences("chubao", MODE_PRIVATE);
			chubaoEditor=chubao.edit();
			if(!chubao.getBoolean("chubaoWarn", false)){
				Builder builder1 = new AlertDialog.Builder(PeopleActivity.this);
				builder1.setTitle(R.string.user_aggreement);
				/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
				builder1.setView(chubao_confirm_dialog);
				/* HQ_liujin modified end */
				builder1.setPositiveButton(R.string.confirm_hw, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
						csom.setNetworkAccessible(true);
						csom.launchYellowPage();
						chubaoEditor.putBoolean("chubaoWarn", isNomore.isChecked());
						/* HQ_liujin modified end */
						chubaoEditor.commit();
					}
				});
				
				builder1.setNegativeButton(R.string.cancel_hw, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
						csom.setNetworkAccessible(false);
						csom.launchYellowPage();
						/* HQ_liujin modified end */
					}
				});
				
				builder1.create();
				builder1.show();
				
			}else {
				/* HQ_liujin modified for HQ01356021 to add Positioning function begin */
				csom.setNetworkAccessible(true);
				/* HQ_liujin modified end */
				csom.launchYellowPage();
			}
			break;
		default:
			Log.wtf(TAG, "Unexpected onClick event from " + view);
		}
	}

//	private CompoundButton.OnCheckedChangeListener  onCheckedChangeListener  = new  CompoundButton.OnCheckedChangeListener() {
//		
//		@Override
//		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//			// TODO Auto-generated method stub
//			if(isChecked){
//				chubaoEditor.putBoolean("chubaoWarn", true);
//			}else {
//				chubaoEditor.putBoolean("chubaoWarn", false);
//			}
//			chubaoEditor.commit();
//		}
//	};
	
	private ProgressDialog progressDialog;
	
	public void setFocusOnSearchView() {
		/*
		 * mSearchView.requestFocus(); showInputMethod(mSearchView);
		 */// Workaround for the "IME not popping up"
			// issue.
	}

	private void showInputMethod(View view) {
		final InputMethodManager imm = (InputMethodManager) PeopleActivity.this
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.showSoftInput(view, 0);
		}
	}

    /*HQ_zhangjing 2015-10-23 modified for CQ HQ01462112 begin*/
    private void hideInputMethod() {
		final InputMethodManager imm = (InputMethodManager) PeopleActivity.this
				.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && this.getWindow() != null && this.getWindow().getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(this.getWindow().getCurrentFocus().getWindowToken(), 0);
			if(mSearchView != null){
				mSearchView.setText("");
			}
        }
    }
    /*HQ_zhangjing 2015-10-23 modified for CQ HQ01462112 end*/

	/**
	 * Returns the tab position adjusted for the text direction.
	 */
	private int getTabPositionForTextDirection(int position) {
		if (isRTL()) {
			return TabState.COUNT - 1 - position;
		}
		return position;
	}

	// / M: [VCS]Voice Search Contacts Feature @{
	private VcsController mVcsController = null;

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (mVcsController != null) {
			mVcsController.dispatchTouchEventVcs(ev);
		}
		return super.dispatchTouchEvent(ev);
	}

	/**
	 * M: Used to dismiss the dialog floating on.
	 * 
	 * @param v
	 */
	@SuppressWarnings({ "UnusedDeclaration" })
	public void onClickDialog(View v) {
		if (mVcsController != null) {
			mVcsController.onVoiceDialogClick(v);
		}
	}

	// / @}
	/* HQ_zhangjing add for dialer merge to contact begin */

	@Override
	public void showCallHistory() {
		// Use explicit CallLogActivity intent instead of ACTION_VIEW +
		// CONTENT_TYPE, so that we always open our call log from our dialer
		final Intent intent = new Intent(this, CallLogActivity.class);
		startActivity(intent);
	}

	/* HQ_zhangjing add for dialer merge to contact end */

	@Override
	public ActionBarController getActionBarController() {
		return null;
	}

	/* HQ_zhangjing add for dialer merge to contact end */

	// added by guofeiyao begin
	private static final int ACTIVITY_REQUEST_CODE_VOICE_SEARCH = 1;
	private static final String TAG_REGULAR_SEARCH_FRAGMENT = "search";
	private static final String TAG_SMARTDIAL_SEARCH_FRAGMENT = "smartdial";

	private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
	private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
	private static final String KEY_SEARCH_QUERY = "search_query";
	private static final String KEY_FIRST_LAUNCH = "first_launch";
	private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";

	// / M: For the purpose of debugging in eng load
	public static final boolean DEBUG = Build.TYPE.equals("eng");

	public static final String SHARED_PREFS_NAME = "com.android.dialer_preferences";

	/**
	 * The text returned from a voice search query. Set in
	 * {@link #onActivityResult} and used in {@link #onResume()} to populate the
	 * search box.
	 */
	private String mVoiceSearchQuery;

	/**
     * True when this activity has been launched for the first time.
     */
    private boolean mFirstLaunch;

	private String mSearchQuery;
	private boolean mStateSaved;
	private boolean mIsRestarting;
	private boolean mInDialpadSearch;
	private boolean mInRegularSearch;
	private boolean mClearSearchOnPause;
	// !!!!
	private boolean mIsDialpadShown = true;
	private boolean mInCallDialpadUp = false;

	private boolean mShowDialpadOnResume;

	public boolean isDialpadShown() {
		return mIsDialpadShown;
	}

	public String getSearchQuery() {
		return mSearchQuery;
	}

	public void setIsDialpadShown(boolean b) {
		mIsDialpadShown = b;
	}

	public boolean isInCallDialpadUp() {
		return mInCallDialpadUp;
	}

	public void setInCallDialpadUp(boolean b) {
		mInCallDialpadUp = b;
	}

	public boolean isStateSaved() {
		return mStateSaved;
	}

	public int getRtlPosition(int position) {
		if (DialerUtils.isRtl()) {
			// stupid code it:(
			return 3 - 1 - position;
		}
		return position;
	}

	@Override
	public void onDialpadQueryChanged(String query) {
        // / Annotated by guofeiyao
		//Log.d(TAG, "---query---:" + query);
		// / End
		
        /// M: Support MTK-DialerSearch @{
        if (DialerFeatureOptions.isDialerSearchEnabled()) {
            if (mEnhancedSmartDialSearchFragment != null) {
                mEnhancedSmartDialSearchFragment.setAddToContactNumber(query);                
            }
        /// @}
        } else {
            if (mSmartDialSearchFragment != null) {
                mSmartDialSearchFragment.setAddToContactNumber(query);
            }
        }
        
        final String normalizedQuery = SmartDialNameMatcher.normalizeNumber(query,
                SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);

//        if (!TextUtils.equals(mSearchView.getText(), normalizedQuery)) {
//            if (DEBUG) {
//                Log.d(TAG, "onDialpadQueryChanged - new query: " + query);
//            }
//            if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
//                // This callback can happen if the dialpad fragment is recreated because of
//                // activity destruction. In that case, don't update the search view because
//                // that would bring the user back to the search fragment regardless of the
//                // previous state of the application. Instead, just return here and let the
//                // fragment manager correctly figure out whatever fragment was last displayed.
//                if (!TextUtils.isEmpty(normalizedQuery)) {
//                    mPendingSearchViewQuery = normalizedQuery;
//                }
//                return;
//            }
//            mSearchView.setText(normalizedQuery);
//        }
        
        //added by guofeiyao begin
        mDialerPlugIn.updateMenuButton(TextUtils.isEmpty(query));
        //end
	}

	public void setNotInSearchUi() {
		mInDialpadSearch = false;
		mInRegularSearch = false;
	}


	/**
	 * @return True if the search UI was exited, false otherwise
	 */
	public boolean maybeExitSearchUi() {
		if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery)) {
			exitSearchUi();
			DialerUtils.hideInputMethod(mParentLayout);
			return true;
		}
		return false;
	}

	/**
	 * Shows the search fragment
	 */
	public void enterSearchUi(boolean smartDialSearch, String query) {
		if (mStateSaved || getFragmentManager().isDestroyed()) {
			// Weird race condition where fragment is doing work after the
			// activity is destroyed
			// due to talkback being on (b/10209937). Just return since we can't
			// do any
			// constructive here.
			return;
		}

		// if (DEBUG) {
		// Log.d(TAG, "Entering search UI - smart dial " + smartDialSearch);
		// }

		final FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();

		// / M: Support MTK-DialerSearch @{
		SearchFragmentEx enFragment = null;
		SearchFragment fragment = null;

		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			if (mInDialpadSearch && mEnhancedSmartDialSearchFragment != null) {
				transaction.remove(mEnhancedSmartDialSearchFragment);
			} else if (mInRegularSearch
					&& mEnhancedRegularSearchFragment != null) {
				transaction.remove(mEnhancedRegularSearchFragment);
			}
			// / @}
		} else {
			if (mInDialpadSearch && mSmartDialSearchFragment != null) {
				transaction.remove(mSmartDialSearchFragment);
			} else if (mInRegularSearch && mRegularSearchFragment != null) {
				transaction.remove(mRegularSearchFragment);
			}
		}

		final String tag;
		if (smartDialSearch) {
			tag = TAG_SMARTDIAL_SEARCH_FRAGMENT;
		} else {
			tag = TAG_REGULAR_SEARCH_FRAGMENT;
		}
		mInDialpadSearch = smartDialSearch;
		mInRegularSearch = !smartDialSearch;

		// / M: Support MTK-DialerSearch @{
		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			enFragment = (SearchFragmentEx) getFragmentManager()
					.findFragmentByTag(tag);
		} else {
			// / @}
			fragment = (SearchFragment) getFragmentManager().findFragmentByTag(
					tag);
		}

		transaction.setCustomAnimations(android.R.animator.fade_in, 0);

		// / M: Support MTK-DialerSearch @{
		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			if (enFragment == null) {
				if (smartDialSearch) {
					enFragment = new SmartDialSearchFragmentEx();
				} else {
					enFragment = new RegularSearchFragmentEx();
				}
				// modified by guofeiyao begin
				// transaction.add(R.id.dialtacts_frame, enFragment, tag);
				transaction.add(R.id.fl_search_ui, enFragment, tag);
			} else {
				transaction.show(enFragment);
			}

			enFragment.setHasOptionsMenu(false);
			enFragment.setShowEmptyListForNullQuery(true);
			enFragment.setQueryString(query, false /* delaySelection */);
			// / @}
		} else {
			if (fragment == null) {
				if (smartDialSearch) {
					fragment = new SmartDialSearchFragment();
				} else {
					fragment = new RegularSearchFragment();
				}
				// modified by guofeiyao begin
				// transaction.add(R.id.dialtacts_frame, fragment, tag);
				transaction.add(R.id.fl_search_ui, fragment, tag);
				// end
			} else {
				transaction.show(fragment);
			}
			// DialtactsActivity will provide the options menu
			fragment.setHasOptionsMenu(false);
			fragment.setShowEmptyListForNullQuery(true);
			fragment.setQueryString(query, false /* delaySelection */);
		}
		// / M: for ALPS01763072 @{
		// avoid illegalstate exception in fragment
		// transaction.commit();
		transaction.commitAllowingStateLoss();
		// / @}

		// mListsFragment.getView().animate().alpha(0).withLayer();
		// mListsFragment.setUserVisibleHint(false);
	}

	/**
	 * Hides the search fragment
	 */
	public void exitSearchUi() {
		// See related bug in enterSearchUI();
		if (getFragmentManager().isDestroyed() || mStateSaved) {
			return;
		}
		
		mDialpadFragment.clearDialpad();
		setNotInSearchUi();

		final FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();

		// / M: Support MTK-DialerSearch @{
		if (DialerFeatureOptions.isDialerSearchEnabled()) {
			if (mEnhancedSmartDialSearchFragment != null) {
				transaction.remove(mEnhancedSmartDialSearchFragment);
			}
			if (mEnhancedRegularSearchFragment != null) {
				transaction.remove(mEnhancedRegularSearchFragment);
			}
			// / @}
		} else {
			if (mSmartDialSearchFragment != null) {
				transaction.remove(mSmartDialSearchFragment);
			}
			if (mRegularSearchFragment != null) {
				transaction.remove(mRegularSearchFragment);
			}
		}
		// / M: fix CR:ALPS01798991, use commitAllowingStateLoss() instead of
		// commit(). @{
		/**
		 * original code: transaction.commit();
		 */
		transaction.commitAllowingStateLoss();
		// / @}

		// mListsFragment.getView().animate().alpha(1).withLayer();
		if (!mDialpadFragment.isVisible()) {
			// If the dialpad fragment wasn't previously visible, then send a
			// screen view because
			// we are exiting regular search. Otherwise, the screen view will be
			// sent by
			// {@link #hideDialpadFragment}.
			// mListsFragment.sendScreenViewForCurrentPosition();
			// mListsFragment.setUserVisibleHint(true);
		}

		// mActionBarController.onSearchUiExited();
	}

	public boolean isInSearchUi() {
		return mInDialpadSearch || mInRegularSearch;
	}


	/**
	 * Listener used to send search queries to the phone search fragment.
	 */
	private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			final String newText = s.toString();
			if (newText.equals(mSearchQuery)) {
				// If the query hasn't changed (perhaps due to activity being
				// destroyed
				// and restored, or user launching the same DIAL intent twice),
				// then there is
				// no need to do anything here.
				return;
			}
			if (DEBUG) {
				//Log.d(TAG,
				//		"onTextChange for mSearchView called with new query: "
				//				+ newText);
				Log.d(TAG, "Previous Query: " + mSearchQuery);
			}
			mSearchQuery = newText;

			// Show search fragment only when the query string is changed to
			// non-empty text.
			if (!TextUtils.isEmpty(newText)) {
				// Call enterSearchUi only if we are switching search modes, or
				// showing a search
				// fragment for the first time.
				final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch)
						|| (!mIsDialpadShown && mInRegularSearch);
				if (!sameSearchMode) {
					enterSearchUi(mIsDialpadShown, mSearchQuery);
				}
			}

			// / M: Support MTK-DialerSearch @{
			if (DialerFeatureOptions.isDialerSearchEnabled()) {
				if (mEnhancedSmartDialSearchFragment != null
						&& mEnhancedSmartDialSearchFragment.isVisible()) {
					LogUtils.d(TAG,
							"MTK-DialerSearch, mEnhancedSmartDialSearchFragment");

					mEnhancedSmartDialSearchFragment.setQueryString(
							mSearchQuery, false);
				} else if (mEnhancedRegularSearchFragment != null
						&& mEnhancedRegularSearchFragment.isVisible()) {
					LogUtils.d(TAG,
							"MTK-DialerSearch, mEnhancedRegularSearchFragment");

					mEnhancedRegularSearchFragment.setQueryString(mSearchQuery,
							false);
				}
				// / @}
			} else {
				if (mSmartDialSearchFragment != null
						&& mSmartDialSearchFragment.isVisible()) {
					mSmartDialSearchFragment
							.setQueryString(mSearchQuery, false /* delaySelection */);
				} else if (mRegularSearchFragment != null
						&& mRegularSearchFragment.isVisible()) {
					mRegularSearchFragment
							.setQueryString(mSearchQuery, false /* delaySelection */);
				}
			}
		}

		@Override
		public void afterTextChanged(Editable s) {
			// / Added by guofeiyao
			if (null == csom) {
				Log.w(GTAG, "PeopleAc   null == csom !!!");
				return;
			}
			String lo = csom.getPhoneAttribute(s.toString());
//			Log.w(GTAG, "afterTextChanged:" + s.toString() + " lo:" + lo);
			if (null != lo && lo.length() > 0) {
				updateNumberLocation(true, lo);
			} else {
				updateNumberLocation(false, lo);
			}
			// / End
		}

	};

	//added by guofeiyao begin
	public void clearDigits() {
        if ( null != digits ) {
			 Log.e(GTAG, "clearDigits(),digits.getText().clear();");
             digits.getText().clear();
		}
	}

	private void updateNumberLocation(boolean b, String location) {
		if (null == lo) {
			Log.e(GTAG, "Error in updateNumberLocation(boolean b),null==lo");
			return;
		}
		if (b) {
			lo.setText(location);
		} else {
			lo.setText("");
		}
	}

	public TextWatcher getTextWatcher(){
		return mPhoneSearchQueryTextListener;
	}


	@Override
	public int getActionBarHideOffset() {
		return getActionBar().getHideOffset();
	}

	@Override
	public int getActionBarHeight() {
		return getActionBar().getHeight();
	}

	@Override
	public boolean isActionBarShowing() {
		// modified by guofeiyao
		return false;
	}

	@Override
	public void onListFragmentScrollStateChange(int scrollState) {
		if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
			getDialerPlugIn().hideDialpadFragment(true, false);
			// DialerUtils.hideInputMethod(mParentLayout);
		}
	}

	@Override
	public void onListFragmentScroll(int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		// TODO: No-op for now. This should eventually show/hide the actionBar
		// based on
		// interactions with the ListsFragments.
	}

	public void updateSearchFragmentPosition() {
		SearchFragment fragment = null;
		if (mSmartDialSearchFragment != null
				&& mSmartDialSearchFragment.isVisible()) {
			fragment = mSmartDialSearchFragment;
		} else if (mRegularSearchFragment != null
				&& mRegularSearchFragment.isVisible()) {
			fragment = mRegularSearchFragment;
		}
		if (fragment != null && fragment.isVisible()) {
			fragment.updatePosition(true /* animate */);
		}
	}

	@Override
    public void onPickPhoneNumberAction(Uri dataUri) {
        // Specify call-origin so that users will see the previous tab instead of
        // CallLog screen (search UI will be automatically exited).
        PhoneNumberInteraction.startInteractionForPhoneCall(
                PeopleActivity.this, dataUri, mDialerPlugIn.getCallOrigin());
//        mClearSearchOnPause = true;
    }
	
	@Override
    public void onCallNumberDirectly(String phoneNumber) {
        onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
        Intent intent = isVideoCall ?
                CallUtil.getVideoCallIntent(phoneNumber, mDialerPlugIn.getCallOrigin()) :
                CallUtil.getCallIntent(phoneNumber, mDialerPlugIn.getCallOrigin());
        DialerUtils.startActivityWithErrorToast(this, intent);
//        mClearSearchOnPause = true;
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
    }

    @Override
    public void onHomeInActionBarSelected() {
//        exitSearchUi();
    }

    @Override
    public void hideDialpad() {
        if (mDialerPlugIn == null)
            return;

        if (mIsDialpadShown) {
            mDialerPlugIn.hideDialpadFragment(true, false);
        }
    }

    private float yDown;
    private float yMove;
    private float yUp;
    private int yDistance;
    private boolean interrupt = false;

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        //Log.e("guofeiyao", "PeopleAc_onTouch");
        if (!isDialpadShown()) return false;

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                yDown = motionEvent.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                yMove = motionEvent.getRawY();
                yDistance = -(int) (yMove - yDown);
                //Log.e("guofeiyao", "-----------------dist = " + yDistance);
                if (Math.abs(yDistance) > 10) {
                    interrupt = true;
                    hideDialpad();
                }
                break;

            case MotionEvent.ACTION_UP:

                yUp = motionEvent.getRawY();

                break;
            default:
                break;
        }


        if (interrupt) {
            interrupt = false;
            return true;
        }

        return false;
    }

    // -------------------------------------------------MTK
    // -------------------------------------------------
    // / M: Support MTK-DialerSearch @{
    private SmartDialSearchFragmentEx mEnhancedSmartDialSearchFragment;
    private RegularSearchFragmentEx mEnhancedRegularSearchFragment;

	public void updateSearchFragmentExPosition() {
		SearchFragmentEx enFragment = null;
		if (mEnhancedSmartDialSearchFragment != null
				&& mEnhancedSmartDialSearchFragment.isVisible()) {
			enFragment = mEnhancedSmartDialSearchFragment;
		} else if (mEnhancedRegularSearchFragment != null
				&& mEnhancedRegularSearchFragment.isVisible()) {
			enFragment = mEnhancedRegularSearchFragment;
		}
		if (enFragment != null && enFragment.isVisible()) {
			enFragment.updatePosition(true /* animate */);
		}
	}

	// / @}

	@Override
	public void maybeShowBtn() {
	    //Log.e("guofeiyao","maybeShowBtn()");
        if ( null == digits || null == mDialpadFragment ) return;
		//Log.e("guofeiyao","digits.length():" + digits.length());
		if ( digits.length() > 3 ) {
             mDialpadFragment.updateAboveBtn( true );
		}
	}
	
	@Override
	public void maybeHideBtn() {
	    //Log.e("guofeiyao","maybeHideBtn()");
        if ( null == digits || null == mDialpadFragment ) return;
		mDialpadFragment.updateAboveBtn( false );
	}
	
	public void handleMenuSettings() {
	    
        //final Intent intent = new Intent(this, DialerSettingsActivity.class);
        //startActivity(intent);

        if ( !((TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE)).isMultiSimEnabled() ) {
                Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(callSettingsIntent);
        } else {
		Intent phoneAccountSettingsIntent =
                        new Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(phoneAccountSettingsIntent);
        }
    }
	
	// end
	/*HQ_zhangjing 2015-08-10 modified for MMS merge begin*/
	public void onActionModeCreate(boolean isActionMode){
		if( isActionMode ){
			mViewPagerTabs.setVisibility( View.GONE );
		}else{
			mViewPagerTabs.setVisibility( View.VISIBLE );
		}
	}
	/*HQ_zhangjing 2015-08-10 modified for MMS merge end*/

	public  CooTekSmartdialerOemModule getCooTekSDK(){
		return csom;
	}

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

} 

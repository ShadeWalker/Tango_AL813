
package com.mediatek.contacts.list;

import android.app.ActionBar;
import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;

import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.list.ContactsRequest;
import com.mediatek.contacts.activities.ContactImportExportActivity;
import com.mediatek.contacts.list.DropMenu.DropDownMenu;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.list.AbstractPickerFragment.ISelectAllStatus;

/**
 * Displays a list of contacts (or phone numbers or postal addresses) for the
 * purposes of selecting multiple contacts.
 */

public class ContactListMultiChoiceActivity extends ContactsActivity implements
        View.OnCreateContextMenuListener, OnQueryTextListener, OnClickListener,
        OnCloseListener, OnFocusChangeListener ,ISelectAllStatus,
        AbstractPickerFragment.EmptyViewCallBack{
    private static final String TAG = ContactListMultiChoiceActivity.class.getSimpleName();

    private static final int SUBACTIVITY_ADD_TO_EXISTING_CONTACT = 0;
    public static final int CONTACTGROUPLISTACTIVITY_RESULT_CODE = 1;

    private static final String KEY_ACTION_CODE = "actionCode";
    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    public static final String RESTRICT_LIST = "restrictlist";

    private ContactsIntentResolverEx mIntentResolverEx;
    protected AbstractPickerFragment mListFragment;

    private int mActionCode = -1;

    private ContactsRequest mRequest;
    private SearchView mSearchView;

    // the dropdown menu with "Select all" and "Deselect all"
    private DropDownMenu mSelectionMenu;
    private boolean mIsSelectedAll = true;
    private boolean mIsSelectedNone = true;
    // if Search Mode now, decide the menu display or not.
    private boolean mIsSearchMode = false;

    // for CT NEW FEATURE
    private int mNumberBalance = 100;
	private int mSelectLimit = 0;//HQ_zhangjing 2015-10-09 modified for CQ HQ01435286
	// HQ_xiatao modify all-selected button  start
	public static ContactListMultiChoiceActivity instanceSelect;
	private boolean isRefreshed = true ;
	private String mSelect;
	// HQ_xiatao modify all-selected button  start

    private enum SelectionMode {
        SearchMode,
        ListMode,
        GroupMode
    };

	 

    public ContactListMultiChoiceActivity() {
        mIntentResolverEx = new ContactsIntentResolverEx(this);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof AbstractPickerFragment) {
            mListFragment = (AbstractPickerFragment) fragment;
        }
    }

	// HQ_xiatao modify all-selected button  start
	@Override
	public void onSelectAllChanged(boolean status){
		if(status){
			selectAll.setTitle(R.string.menu_select_none);
		}else{
			selectAll.setTitle(R.string.menu_select_all);
		}
	}
	// HQ_xiatao modify all-selected button  end

    @Override
    protected void onCreate(Bundle savedState) {
    	
       	int themeId = getResources().getIdentifier("androidhwext:style/Theme.Emui.WithActionBar", null, null);
    		if (themeId > 0){
    			setTheme(themeId);
    		}
    	instanceSelect = this;
        super.onCreate(savedState);
        // for ct new feature
        Intent mmsIntent = this.getIntent();
        if (mmsIntent != null) {
		try {
            		mNumberBalance = mmsIntent.getIntExtra("NUMBER_BALANCE", 100);
		} catch (RuntimeException e) {
			e.printStackTrace();
			finish();
			return;
		}
			/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286*/
			mSelectLimit = mmsIntent.getIntExtra("pick_contacts_for_sms", 0);
        }

        if (savedState != null) {
            mActionCode = savedState.getInt(KEY_ACTION_CODE);
            mNumberBalance = savedState.getInt("NUMBER_BALANCE");
			/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286*/
			mSelectLimit = savedState.getInt("pick_contacts_for_sms");
        }

        // Extract relevant information from the intent
        mRequest = mIntentResolverEx.resolveIntent(getIntent());
        if (!mRequest.isValid()) {
            LogUtils.d(TAG, "Request is invalid!");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            finish();
            return;
        }

        setContentView(R.layout.contact_picker);

        try {
		configureListFragment();
	} catch (RuntimeException e) {
		e.printStackTrace();
		finish();
		return;
	}

        // Disable Search View in listview
        if (mSearchView != null) {
            mSearchView.setVisibility(View.GONE);
        }

        // Disable create new contact button
        /** qinglei deleted this because L changed this button
        View createNewContactButton = (View) findViewById(R.id.new_contact);
        if (createNewContactButton != null) {
            createNewContactButton.setVisibility(View.GONE);
        }
        */

        showActionBar(SelectionMode.ListMode);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.i(TAG, "[onDestroy]");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTION_CODE, mActionCode);
     // for ct new feature
        outState.putInt("NUMBER_BALANCE", mNumberBalance);
		/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286*/
	 	outState.putInt("pick_contacts_for_sms", mSelectLimit);
    }

	private MenuItem selectAll;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mtk_list_multichoice, menu);      
		// HQ_xiatao modify all-selected button  start
        MenuItem optionItem = menu.findItem(R.id.search_menu_item);
		selectAll = menu.findItem(R.id.action_select_all);
		if(isRefreshed){
			selectAll.setTitle(R.string.menu_select_all);
		}else{
			selectAll.setTitle(getSelectTitle());
		}
		// HQ_xiatao modify all-selected button  end

		optionItem.setTitle(R.string.menu_search);
        return true;
    }

    /*modified for HQ02061159 by zhangzhihua begin */
	@Override
	public void isVisibility(int visibility){
	    if(visibility == View.VISIBLE){
			//selectAll.setTitle(R.string.menu_select_all);
			selectAll.setEnabled(false);
		}else{
		    selectAll.setEnabled(true);
		}
	}
	/*modified for HQ02061159 by zhangzhihua end */
	
    /* HQ_xiatao modify all-selected button  start*/
	public void setSelectTitle(String title){
		mSelect = title ;
	}
	public String getSelectTitle(){
		return mSelect;
	}
	 /*HQ_xiatao modify all-selected button  end*/

    @Override
    public void onClick(View v) {
        final int resId = v.getId();

        switch (resId) {
            case R.id.search_menu_item:
                if (mListFragment != null) {
		        mListFragment.updateSelectedItemsView();
		        showActionBar(SelectionMode.SearchMode);
		        closeOptionsMenu();
                }
                break;

            case R.id.menu_option:
                if (mListFragment != null) {
		        if (mListFragment instanceof MultiContactsDuplicationFragment) {
		            LogUtils.d(TAG, "Send result for copy action");
		            setResult(ContactImportExportActivity.RESULT_CODE);
		        }
		        if (mListFragment instanceof MultiPhoneAndEmailsPickerFragment) {
		            MultiPhoneAndEmailsPickerFragment fragment = (MultiPhoneAndEmailsPickerFragment) mListFragment;
		            fragment.setNumberBalance(mNumberBalance);
		            fragment.onOptionAction();
		        } else {
		            mListFragment.onOptionAction();
		        }
                }
                break;
			/*
            case R.id.select_items:
                //if the Window of this Activity hasn't been created,
                //don't show Popup. because there is no any window to attach .
                if (getWindow() == null) {
                    LogUtils.w(TAG, "onClick,current Activity dinsow is null");
                    return;
                }
                if (mSelectionMenu == null || !mSelectionMenu.isShown()) {
                    View parent = (View) v.getParent();
                    mSelectionMenu = updateSelectionMenu(parent);
                    mSelectionMenu.show();
                } else {
                    LogUtils.w(TAG, "mSelectionMenu is already showing, ignore this click");
                }
                break;
                */

            default:
                break;
        }
        return;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int itemId = item.getItemId();
        //if click the search menu, into the SearchMode and disable the search menu
        if (mListFragment != null && itemId == R.id.search_menu_item) {
            mListFragment.updateSelectedItemsView();
            mIsSelectedNone = mListFragment.isSelectedNone();
            showActionBar(SelectionMode.SearchMode);
            item.setVisible(false);
            return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * Creates the fragment based on the current request.
     */
    public void configureListFragment() {
        if (mActionCode == mRequest.getActionCode()) {
            return;
        }

        Bundle bundle = new Bundle();
        mActionCode = mRequest.getActionCode();
        LogUtils.d(TAG, "configureListFragment action code is " + mActionCode);
		Intent intent = getIntent();
        if(intent.getBooleanExtra("com.huawei.community.action.MULTIPLE_PICK", false)){
            switch (mActionCode) {
                case ContactsRequest.ACTION_PICK_CONTACT:
                    mActionCode = ContactsRequestAction.ACTION_PICK_MULTIPLE_CONTACTS;
                    break;
                case ContactsRequest.ACTION_PICK_EMAIL:
                    mActionCode = ContactsRequestAction.ACTION_PICK_MULTIPLE_EMAILS;
                    break;
                case ContactsRequest.ACTION_PICK_PHONE:
                    mActionCode = ContactsRequestAction.ACTION_PICK_MULTIPLE_PHONES;
                    break;
            }
        }

        switch (mActionCode) {

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_CONTACTS:
                mListFragment = new MultiContactsPickerBaseFragment();
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_CONTACTS
                    | ContactsIntentResolverEx.MODE_MASK_VCARD_PICKER:
                mListFragment = new ContactsVCardPickerFragment();
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_CONTACTS
                    | ContactsIntentResolverEx.MODE_MASK_IMPORT_EXPORT_PICKER:
                mListFragment = new MultiContactsDuplicationFragment();
                bundle.putParcelable(MultiContactsPickerBaseFragment.FRAGMENT_ARGS, getIntent());
                mListFragment.setArguments(bundle);
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_EMAILS:
                mListFragment = new MultiEmailsPickerFragment();
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_PHONES:
                mListFragment = new MultiPhoneNumbersPickerFragment();
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_DATAS:
                mListFragment = new MultiDataItemsPickerFragment();
                bundle.putParcelable(MultiContactsPickerBaseFragment.FRAGMENT_ARGS, getIntent());
                mListFragment.setArguments(bundle);
                break;

            case ContactsRequestAction.ACTION_DELETE_MULTIPLE_CONTACTS:
                mListFragment = new ContactsMultiDeletionFragment();
                break;

            case ContactsRequestAction.ACTION_GROUP_MOVE_MULTIPLE_CONTACTS:
                mListFragment = new ContactsGroupMultiPickerFragment();
                bundle.putParcelable(MultiContactsPickerBaseFragment.FRAGMENT_ARGS, getIntent());
                mListFragment.setArguments(bundle);
                break;

            case ContactsRequestAction.ACTION_PICK_MULTIPLE_PHONEANDEMAILS:
                mListFragment = new MultiPhoneAndEmailsPickerFragment();
                break;

            case ContactsRequestAction.ACTION_SHARE_MULTIPLE_CONTACTS:
                mListFragment = new MultiContactsShareFragment();
                break;

            case ContactsRequestAction.ACTION_GROUP_ADD_MULTIPLE_CONTACTS:
                mListFragment = new ContactsGroupAddMultiContactsFragment();
                bundle.putParcelable(MultiContactsPickerBaseFragment.FRAGMENT_ARGS, getIntent());
                mListFragment.setArguments(bundle);
                break;
            case ContactsRequestAction.ACTION_PICK_MULTIPLE_PHONE_IMS_SIP_CALLS:
                mListFragment = new MultiConferenceCallsPickerFragment();
                bundle.putParcelable(MultiConferenceCallsPickerFragment.FRAGMENT_ARGS, getIntent());
                mListFragment.setArguments(bundle);
                break;

            default:
                throw new IllegalStateException("Invalid action code: " + mActionCode);
        }

        mListFragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
        mListFragment.setQueryString(mRequest.getQueryString(), false);
        mListFragment.setDirectoryResultLimit(DEFAULT_DIRECTORY_RESULT_LIMIT);
        mListFragment.setVisibleScrollbarEnabled(true);

        /*modified for HQ02061159 by zhangzhihua begin */
		mListFragment.setEmptyViewCallBack(this);
		/*modified for HQ02061159 by zhangzhihua end */

        getFragmentManager().beginTransaction().replace(R.id.list_container, mListFragment)
                .commitAllowingStateLoss();
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mListFragment.startSearch(newText);
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onClose() {
        if (mSearchView == null) {
            return false;
        }
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        showActionBar(SelectionMode.ListMode);
        mListFragment.updateSelectedItemsView();
        return true;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view.getId() == R.id.search_view) {
            if (hasFocus) {
                showInputMethod(mSearchView.findFocus());
            }
        }
    }

    private void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(view, 0)) {
                LogUtils.w(TAG, "Failed to show soft input method.");
            }
        }
    }

    public void returnPickerResult(Uri data) {
        Intent intent = new Intent();
        intent.setData(data);
        returnPickerResult(intent);
    }

    public void returnPickerResult(Intent intent) {
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SUBACTIVITY_ADD_TO_EXISTING_CONTACT) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    startActivity(data);
                }
                finish();
            }
        }

        if (resultCode == ContactImportExportActivity.RESULT_CODE) {
            finish();
        }

        if (resultCode == CONTACTGROUPLISTACTIVITY_RESULT_CODE) {
            long[] ids = data.getLongArrayExtra("checkedids");
            if (mListFragment instanceof MultiPhoneAndEmailsPickerFragment) {
                MultiPhoneAndEmailsPickerFragment fragment = (MultiPhoneAndEmailsPickerFragment) mListFragment;
                fragment.markItemsAsSelectedForCheckedGroups(ids);
            }
        }

    }

    public void onBackPressed() {
        if (mSearchView != null && !mSearchView.isFocused()) {
            if (!TextUtils.isEmpty(mSearchView.getQuery())) {
                mSearchView.setQuery(null, true);
            }
            showActionBar(SelectionMode.ListMode);
            mListFragment.updateSelectedItemsView();
            return;
        }
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        LogUtils.i(TAG, "[onConfigurationChanged]" + newConfig);
        super.onConfigurationChanged(newConfig);
        // do nothing
    }

    private void showActionBar(SelectionMode mode) {
        ActionBar actionBar = getActionBar();
        switch (mode) {
            case SearchMode:
                Log.d(TAG, "search mode");
                mIsSearchMode = true;
                invalidateOptionsMenu();
                final View searchViewContainer = LayoutInflater.from(actionBar.getThemedContext())
                        .inflate(R.layout.mtk_multichoice_custom_action_bar, null);
                // in SearchMode,disable the doneMenu and selectView.
                Button selectView = (Button) searchViewContainer.findViewById(R.id.select_items);
                selectView.setVisibility(View.GONE);

                mSearchView = (SearchView) searchViewContainer.findViewById(R.id.search_view);
                mSearchView.setVisibility(View.VISIBLE);
                mSearchView.setIconifiedByDefault(true);
                mSearchView.setQueryHint(getString(R.string.hint_findContacts));
                mSearchView.setIconified(false);
                mSearchView.setOnQueryTextListener(this);
                mSearchView.setOnCloseListener(this);
                mSearchView.setOnQueryTextFocusChangeListener(this);

                // when no Query String,do not display the "X"
                mSearchView.onActionViewExpanded();

                actionBar.setCustomView(searchViewContainer, new LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                actionBar.setDisplayShowCustomEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(true);

                //display the "OK" button.
                Button optionView = (Button) searchViewContainer.findViewById(R.id.menu_option);
                optionView.setTypeface(Typeface.DEFAULT_BOLD);
                if (mIsSelectedNone) {
                    // if there is no item selected, the "OK" button is disable.
                    optionView.setEnabled(false);
                    optionView.setTextColor(Color.LTGRAY);
                } else {
                    optionView.setEnabled(true);
                    optionView.setTextColor(Color.WHITE);
                }
                optionView.setOnClickListener(this);
                break;

            case ListMode:
                mIsSearchMode = false;
                Log.d(TAG, "list mode");
                invalidateOptionsMenu();
                // Inflate a custom action bar that contains the "done" button for multi-choice
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View customActionBarView = inflater.inflate(R.layout.mtk_multichoice_custom_action_bar,
                        null);
                // in the listMode,disable the SearchView
                mSearchView = (SearchView) customActionBarView
                        .findViewById(R.id.search_view);
                mSearchView.setVisibility(View.GONE);

                // set dropDown menu on selectItems.
                Button selectItems = (Button) customActionBarView
                        .findViewById(R.id.select_items);
                selectItems.setOnClickListener(this);

                Button menuOption = (Button) customActionBarView
                        .findViewById(R.id.menu_option);
                menuOption.setTypeface(Typeface.DEFAULT_BOLD);
                String optionText = menuOption.getText().toString();
                menuOption.setOnClickListener(this);

                // Show the custom action bar but hide the home icon and title
                actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_HOME_AS_UP,
                        ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME
                                | ActionBar.DISPLAY_SHOW_TITLE);
                actionBar.setCustomView(customActionBarView);
                // in onBackPressed() used. If mSearchView is null,return prePage.
                mSearchView = null;
                break;

            case GroupMode:
                break;

            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            hideSoftKeyboard(mSearchView);
            // Fix CR:ALPS01945610
            if (isResumed()) {
                onBackPressed();
            }
            return true;
        }
        if (item.getItemId() == R.id.groups) {
            startActivityForResult(new Intent(ContactListMultiChoiceActivity.this,
                    ContactGroupListActivity.class), CONTACTGROUPLISTACTIVITY_RESULT_CODE);
            return true;
        }

		// HQ_xiatao modify all-selected button  start
        if(mListFragment != null) {
		mListFragment.updateSelectedItemsView();
       	mIsSelectedAll = mListFragment.isSelectedAll();
		if(mIsSelectedAll){
			selectAll.setTitle(R.string.menu_select_none);
		}
		if(selectAll.getItemId() == R.id.action_select_all){
        	if (mIsSelectedAll) {
				selectAll.setTitle(R.string.menu_select_all);
				isRefreshed = false;
				setSelectTitle(getResources().getString(R.string.menu_select_all));
				showActionBar(SelectionMode.ListMode);
            	mListFragment.onClearSelect();
        	} else {
				selectAll.setTitle(R.string.menu_select_none);
				isRefreshed = false;
				setSelectTitle(getResources().getString(R.string.menu_select_none));
				showActionBar(SelectionMode.ListMode);
				/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 begin*/
				if( mSelectLimit == 1 ){
					mListFragment.setSelectLimit(mNumberBalance);
				}else{
					mListFragment.setSelectLimit(AbstractPickerFragment.DEFAULT_MULTI_CHOICE_MAX_COUNT);
				}
				/*HQ_zhangjing 2015-10-09 modified for CQ HQ01435286 end*/
            	mListFragment.onSelectAll();
        	}
		}
		}// HQ_xiatao modify all-selected button  end
        return super.onOptionsItemSelected(item);
    }

    /**
     * add dropDown menu on the selectItems.The menu is "Select all" or "Deselect all"
     * @param customActionBarView
     * @return The updated DropDownMenu
     */
     /*
    private DropDownMenu updateSelectionMenu(View customActionBarView) {
        DropMenu dropMenu = new DropMenu(this);
        // new and add a menu.
        DropDownMenu selectionMenu = dropMenu.addDropDownMenu((Button) customActionBarView
                .findViewById(R.id.select_items), R.menu.mtk_selection);

        Button selectView = (Button) customActionBarView
                .findViewById(R.id.select_items);
        // when click the selectView button, display the dropDown menu.
        selectView.setOnClickListener(this);
        MenuItem item = selectionMenu.findItem(R.id.action_select_all);

        // get mIsSelectedAll from fragment.
        mListFragment.updateSelectedItemsView();
        mIsSelectedAll = mListFragment.isSelectedAll();
        // if select all items, the menu is "Deselect all"; else the menu is "Select all".
        if (mIsSelectedAll) {
            // dropDown menu title is "Deselect all".
            item.setTitle(R.string.menu_select_none);
            // click the menu, deselect all items
            dropMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    showActionBar(SelectionMode.ListMode);
                    // clear select all items
                    mListFragment.onClearSelect();
                    return false;
                }
            });
        } else {
            // dropDown Menu title is "Select all"
            item.setTitle(R.string.menu_select_all);
            // click the menu, select all items.
            dropMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    showActionBar(SelectionMode.ListMode);
                    // select all of itmes
                    mListFragment.onSelectAll();
                    return false;
                }
            });
        }
        return selectionMenu;
    }
	*/

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.search_menu_item);
        if (mIsSearchMode) {
            // if SearchMode, search Menu is disable.
            //menuItem.setVisible(false);
            //return false;
            return super.onPrepareOptionsMenu(menu);
        } else {
            // if ListMode, search Menu is display.
            menuItem.setVisible(true);
            if (mListFragment instanceof MultiPhoneAndEmailsPickerFragment) {
                MenuItem groupsItem = menu.findItem(R.id.groups);
                groupsItem.setVisible(true);
            }
            return super.onPrepareOptionsMenu(menu);
        }
    }

    private void hideSoftKeyboard(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } else {
            LogUtils.w(TAG, "Failed to hide soft Key board: imm = " + imm + ", view = " + view);
        }
    }

}

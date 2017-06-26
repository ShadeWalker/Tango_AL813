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
package com.android.contacts.common.list;

import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.widget.SideBar;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ContactsCommonListUtils;
import android.os.Build;
import android.os.SystemProperties;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippets;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import android.telephony.SubscriptionManager;
import com.android.contacts.common.R;
//import com.android.contacts.list.DefaultContactBrowseListFragment;
/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type.
 */
public class DefaultContactListAdapter extends ContactListAdapter {
    /// M: Add tag string for log.
    public static final String TAG = "DefaultContactListAdapter";

    public static final char SNIPPET_START_MATCH = '[';
    public static final char SNIPPET_END_MATCH = ']';
    private Context mContext;

    public DefaultContactListAdapter(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        if (loader instanceof ProfileAndContactsLoader) {
            /** M: New Feature SDN. */
            mSDNLoader = (ProfileAndContactsLoader) loader;
            ((ProfileAndContactsLoader) loader).setLoadProfile(shouldIncludeProfile());
        }

        ContactListFilter filter = getFilter();
        if (Build.TYPE.equals("eng")) {
            Log.d(TAG, "[configureLoader] filter: " + filter + ",loader:" + loader + ",isSearchMode:" + isSearchMode());
        }
        if (isSearchMode()) {
            String query = getQueryString();
            if (query == null) {
                query = "";
            }
            query = query.trim();
            if (TextUtils.isEmpty(query)) {
                // Regardless of the directory, we don't want anything returned,
                // so let's just send a "nothing" query to the local directory.
                loader.setUri(Contacts.CONTENT_URI);
                loader.setProjection(getProjection(false));
                loader.setSelection("0");
            } else {
                Builder builder = Contacts.CONTENT_FILTER_URI.buildUpon();
                builder.appendPath(query);      // Builder will encode the query
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
                if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                    builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(getDirectoryResultLimit(getDirectoryById(directoryId))));
                }
                builder.appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY,"1");
                loader.setUri(builder.build());
                loader.setProjection(getProjection(true));
            }
        } else {
            configureUri(loader, directoryId, filter);
            loader.setProjection(getProjection(false));
            configureSelection(loader, directoryId, filter);
        }

        /** M: Bug Fix for ALPS00112614. Descriptions: only show phone contact if it's from sms @{ */
        if (mOnlyShowPhoneContacts) {
            ContactsCommonListUtils.configureOnlyShowPhoneContactsSelection(loader, directoryId, filter);
        }
        /** @} */

        String sortOrder;
        if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            sortOrder = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortOrder = Contacts.SORT_KEY_ALTERNATIVE;
        }

        loader.setSortOrder(sortOrder);
    }

    protected void configureUri(CursorLoader loader, long directoryId, ContactListFilter filter) {
        Uri uri = Contacts.CONTENT_URI;
        if (filter != null && filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            String lookupKey = getSelectedContactLookupKey();
            if (lookupKey != null) {
                uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            } else {
                uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, getSelectedContactId());
            }
        }

        if (directoryId == Directory.DEFAULT && isSectionHeaderDisplayEnabled()) {
            uri = ContactListAdapter.buildSectionIndexerUri(uri);
        }

        // The "All accounts" filter is the same as the entire contents of Directory.DEFAULT
        if (filter != null
                && filter.filterType != ContactListFilter.FILTER_TYPE_CUSTOM
                && filter.filterType != ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
            final Uri.Builder builder = uri.buildUpon();
            builder.appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
            /**
             * M: Change Feature: <br>
             * As Local Phone account contains null account and Phone Account,
             * the Account Query Parameter could not meet this requirement. So,
             * We should keep to query contacts with selection. @{
             */
            /*
             * if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
             * filter.addAccountQueryParameterToUrl(builder); }
             */
            /** @} */
            uri = builder.build();
        }

        loader.setUri(uri);
    }

    /// M: New Feature SDN.
    protected void configureSelection(
            CursorLoader loader, long directoryId, ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();
        boolean isNuberOnly = ContactListFilter.getNumberOnlyFromPreferences(getSharedPreferences());

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                // We have already added directory=0 to the URI, which takes care of this
                // filter
                /** M: New Feature SDN. */
                selection.append(RawContacts.IS_SDN_CONTACT + " < 1");
                if (isNuberOnly) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                break;
            }
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                // We have already added the lookup key to the URI, which takes care of this
                // filter
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                selection.append(Contacts.STARRED + "!=0");
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                selection.append(Contacts.HAS_PHONE_NUMBER + "=1");
                /** M: New Feature SDN. */
                selection.append(" AND " + RawContacts.IS_SDN_CONTACT + " < 1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                if (isCustomFilterForPhoneNumbersOnly() || isNuberOnly) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                /** M: New Feature SDN. */
                selection.append(" AND " + RawContacts.IS_SDN_CONTACT + " < 1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                // We use query parameters for account filter, so no selection to add here.
                /** M: Change Feature: As Local Phone account contains null account and Phone
                 * Account, the Account Query Parameter could not meet this requirement. So,
                 * We should keep to query contacts with selection. @{ */
                buildSelectionForFilterAccount(filter, selection, selectionArgs);
                if (isNuberOnly) {
                    selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                }
                break;
            }
        }
        Log.d(TAG, "[configureSelection] selection: " + selection.toString());
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        
        //add by niubi tang for HQ01667787
//        int sectionIndex=getSectionForPosition(position);
//        Log.i("tang", "the index is "+sectionIndex);
//        
//        int section = -1;
//        int partition1 = getPartitionForPosition(position);
//        if (partition1 == getIndexedPartition()) {
//            int offset = getOffsetInPartition(position);
//            Log.i("tang", "the offset is "+offset);
//            if (offset != -1) {
//                section = getSectionForPosition(offset);
//            }
//        }
//        if(section!=-1&&section<=getSections().length){
//        	String secString=(String)getSections()[section];
//        	Log.i("tang", "the section is "+secString);
//        	SideBar sidebar=DefaultContactBrowseListFragment.getSideBar();
//        	
//        	if(sidebar!=null){
//        		sidebar.setPosition(secString);
//        	}
//        }
        //end
        
        final ContactListItemView view = (ContactListItemView)itemView;
        if (cursor.getCount() > 0 && view != null) {
            int subId = cursor.getInt(cursor.getColumnIndex(Contacts.INDICATE_PHONE_SIM));
			Log.d("contact_image","subId = " + subId +" cursor.getCount() = " + cursor.getCount());
            if (subId > 0) {
			   int slotId =  SubscriptionManager.getSlotId(subId);
			   Log.d("contact_image","slotId = " + slotId);
			   if (slotId == 0) {
			       //HQ_wuruijun add for HQ01548104 start
			       if (!SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
			           view.setSimLabelDrawable(R.drawable.contacts_card);
			       } else {
			           view.setSimLabelDrawable(R.drawable.contacts_card1);
			       }
			       //HQ_wuruijun add end
			   } else{
				  view.setSimLabelDrawable(R.drawable.contacts_card2);
			   }
            }else{
				view.setSimLabelDrawable(-1);
		   		//view.getSimLabelView().setVisibility(View.GONE);
            }
        }
        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);

        if (isSelectionVisible()) {
            view.setActivated(isSelectedContact(partition, cursor));
        }

        bindSectionHeaderAndDivider(view, position, cursor);
        /// M: [RCS-e].
        view.bindDataForCustomView(cursor.getLong(cursor.getColumnIndex(Contacts._ID)));

        if (isQuickContactEnabled()) {
            bindQuickContact(view, partition, cursor, ContactQuery.CONTACT_PHOTO_ID,
                    ContactQuery.CONTACT_PHOTO_URI, ContactQuery.CONTACT_ID,
                    ContactQuery.CONTACT_LOOKUP_KEY, ContactQuery.CONTACT_DISPLAY_NAME);
        } else {
            if (getDisplayPhotos()) {
                bindPhoto(view, partition, cursor);
            }
        }

        bindNameAndViewId(view, cursor);
        bindPresenceAndStatusMessage(view, cursor);

        if (isSearchMode()) {
            bindSearchSnippet(view, cursor);
        } else {
            view.setSnippet(null);
        }
    }

    private boolean isCustomFilterForPhoneNumbersOnly() {
        // TODO: this flag should not be stored in shared prefs.  It needs to be in the db.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getBoolean(ContactsPreferences.PREF_DISPLAY_ONLY_PHONES,
                ContactsPreferences.PREF_DISPLAY_ONLY_PHONES_DEFAULT);
    }

    /** M: Bug Fix for ALPS00112614 Descriptions: only show phone contact if it's from sms @{ */
    private boolean mOnlyShowPhoneContacts = false;
    public ProfileAndContactsLoader mSDNLoader = null;

    public void setOnlyShowPhoneContacts(boolean showPhoneContacts) {
        mOnlyShowPhoneContacts = showPhoneContacts;
    }
    /** @} */
    /** 
	 * M: New Feature for SDN.
     */
    @Override
    public void updateIndexer(Cursor cursor) {
        super.updateIndexer(cursor);
        ContactsSectionIndexer sectionIndexer = (ContactsSectionIndexer) this.getIndexer();
        if (mSDNLoader != null) {
            if (mSDNLoader.getSdnContactCount() > 0) {
                sectionIndexer.setSdnHeader("SDN", mSDNLoader.getSdnContactCount());
            }
        }
    }

    /** 
     * M: Change Feature: As Local Phone account contains null account and Phone
     * Account, the Account Query Parameter could not meet this requirement. So,
     * We should keep to query contacts with selection. */
    private void buildSelectionForFilterAccount(ContactListFilter filter, StringBuilder selection,
            List<String> selectionArgs) {
        if (AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE.equals(filter.accountType)) {
            selection.append("EXISTS ("
                            + "SELECT DISTINCT " + RawContacts.CONTACT_ID
                            + " FROM view_raw_contacts"
                            + " WHERE ( ");
            selection.append(RawContacts.IS_SDN_CONTACT + " < 1 AND ");
            selection.append(RawContacts.CONTACT_ID + " = " + "view_contacts."
                            + Contacts._ID
                            + " AND (" + RawContacts.ACCOUNT_TYPE + " IS NULL "
                            + " AND " + RawContacts.ACCOUNT_NAME + " IS NULL "
                            + " AND " +  RawContacts.DATA_SET + " IS NULL "
                            + " OR " + RawContacts.ACCOUNT_TYPE + "=? "
                            + " AND " + RawContacts.ACCOUNT_NAME + "=? ");
        } else {
            selection.append("EXISTS ("
                            + "SELECT DISTINCT " + RawContacts.CONTACT_ID
                            + " FROM view_raw_contacts"
                            + " WHERE ( ");
            selection.append(RawContacts.IS_SDN_CONTACT + " < 1 AND ");
            selection.append(RawContacts.CONTACT_ID + " = " + "view_contacts."
                            + Contacts._ID
                            + " AND (" + RawContacts.ACCOUNT_TYPE + "=?"
                            + " AND " + RawContacts.ACCOUNT_NAME + "=?");
        }
        ContactsCommonListUtils.buildSelectionForFilterAccount(filter, selection, selectionArgs);
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

}

package com.mediatek.contacts.util;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.Drawable;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.net.Uri;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.content.CursorLoader;

import com.android.contacts.common.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ProfileAndContactsLoader;
import com.android.contacts.common.list.ContactListAdapter.ContactQuery;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.util.AccountFilterUtil;
import com.mediatek.contacts.ContactsSystemProperties;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simcontact.SubInfoUtils;
import com.mediatek.contacts.widget.WaitCursorView;

/** define some util functions for ContactsCommon/list*/
public class ContactsCommonListUtils {
    private static final String TAG = "ContactsCommonListUtils";

    /**
     * For multiuser in 3gdatasms
     */
    public static boolean isUserOwner() {
        if (ContactsSystemProperties.MTK_OWNER_SIM_SUPPORT) {
            int userId = UserHandle.myUserId();
            if (userId != UserHandle.USER_OWNER) {
                return false;
            }
        }
        return true;
    }

    /**
     * ALPS913966 cache displayname in account filter and  push to intent.
     **/
    public static void addToAccountFilter(Context context, AccountWithDataSet account, 
            ArrayList<ContactListFilter> accountFilters, AccountType accountType) {
        int subId = ((AccountWithDataSetEx) account).mSubId;
        int slotId=-1;
        SubscriptionInfo sfr = SubInfoUtils.getSubInfoUsingSubId(subId);
        if (sfr != null) {
            slotId = sfr.getSimSlotIndex();
        }
        String displayName = AccountFilterUtil.getAccountDisplayNameByAccount(account.type, account.name);
        Log.d(TAG, "[AccountFilterActivity] displayName : " + displayName + " subId : " + subId);
        Drawable icon = accountType.getDisplayIconBySubId(context, subId);
        //add by tanghuaizhe 
        if(slotId==0){
            //HQ_wuruijun add for HQ01548084
            if (!SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
                displayName=context.getResources().getString(R.string.sim_card);
            } else {
                displayName=context.getResources().getString(R.string.card_1);
            }
            //HQ_wuruijun add end
        }else  if(slotId==1){
        	displayName=context.getResources().getString(R.string.card_2);
		}
        Log.d(TAG, "[AccountFilterActivity] displayName : " + displayName + " subId : " + subId+" display  name:"+displayName);
        accountFilters.add(ContactListFilter.createAccountFilter(
                account.type, account.name, account.dataSet, icon, displayName));
    }

    /**
     * Bug Fix For ALPS00115673 Descriptions: add wait cursor.
     */
    public static WaitCursorView initLoadingView(Context context, View listLayout, View loadingContainer,
            TextView loadingContact, ProgressBar progress) {
        loadingContainer = listLayout.findViewById(R.id.loading_container);
        loadingContainer.setVisibility(View.GONE);
        loadingContact = (TextView) listLayout.findViewById(R.id.loading_contact);
        loadingContact.setVisibility(View.GONE);
        progress = (ProgressBar) listLayout.findViewById(R.id.progress_loading_contact);
        progress.setVisibility(View.GONE);
        return new WaitCursorView(
                context, loadingContainer, progress, loadingContact);
    }

    /**
     * for SIM name display
     */
    public static void setAccountTypeText(Context context, AccountType accountType, 
            TextView accountTypeView, TextView accountUserNameView, ContactListFilter filter) {
        String displayName = null; 
        displayName = AccountFilterUtil.getAccountDisplayNameByAccount(filter.accountType, filter.accountName);
        if (TextUtils.isEmpty(displayName)) {
            accountTypeView.setText(filter.accountName);
        } else {
            accountTypeView.setText(filter.displayName);//modified by niubi tang 
        }
        if (AccountWithDataSetEx.isLocalPhone(accountType.accountType)) {
            accountUserNameView.setVisibility(View.GONE);
            accountTypeView.setText(context.getResources().getString(R.string.Local_phone));
        }
    }

    /**
     * For multiuser in 3gdatasms
     */
    public static boolean isAccountTypeSimUsim(AccountType accountType) {
        if (accountType != null && AccountTypeUtils.isAccountTypeIccCard(accountType.accountType)) {
            return true;
        }
        return false;
    }

    /**
     * Bug Fix CR ID: ALPS00112614
     * Descriptions: only show phone contact if it's from sms
     */
    public static void configureOnlyShowPhoneContactsSelection(CursorLoader loader, long directoryId,
            ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        if (directoryId != Directory.DEFAULT) {
            return;
        }

        StringBuilder selection = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        selection.append(Contacts.INDICATE_PHONE_SIM + "= ?");
        selectionArgs.add("-1");

        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    /**
     * Change Feature: As Local Phone account contains null account and Phone
     * Account, the Account Query Parameter could not meet this requirement. So,
     * We should keep to query contacts with selection.
     */
    public static void buildSelectionForFilterAccount(ContactListFilter filter, StringBuilder selection,
        List<String> selectionArgs) {
        selectionArgs.add(filter.accountType);
        selectionArgs.add(filter.accountName);
        if (filter.dataSet != null) {
            selection.append(" AND " + RawContacts.DATA_SET + "=? )");
            selectionArgs.add(filter.dataSet);
        } else {
            selection.append(" AND " +  RawContacts.DATA_SET + " IS NULL )");
        }
        selection.append("))");
    }

    private static Cursor loadSDN(Context context, ProfileAndContactsLoader profileAndContactsLoader) {
        Cursor sdnCursor = null;
        if (null != profileAndContactsLoader.getSelection()
                && profileAndContactsLoader.getSelection().indexOf(
                        RawContacts.IS_SDN_CONTACT + " < 1") >= 0) {
            Uri uri = profileAndContactsLoader.getUri();
            String[] projection = profileAndContactsLoader.getProjection();
            String newSelection = profileAndContactsLoader.getSelection().replace(
                    RawContacts.IS_SDN_CONTACT + " < 1", RawContacts.IS_SDN_CONTACT + " = 1");
            String[] selectionArgs = profileAndContactsLoader.getSelectionArgs();
            String sortOrder = profileAndContactsLoader.getSortOrder();
            sdnCursor = context.getContentResolver().query(uri, projection, newSelection,
                    selectionArgs, sortOrder);
            if (sdnCursor == null) {
                Log.d(TAG, "sdnCursor is null need to check");
                return null;
            }
            MatrixCursor matrix = new MatrixCursor(projection);
            try {
                Object[] row = new Object[projection.length];
                while (sdnCursor.moveToNext()) {
                    for (int i = 0; i < row.length; i++) {
                        row[i] = sdnCursor.getString(i);
                    }
                    matrix.addRow(row);
                }
                Log.d(TAG, "loadSDN sdnCursor : " + sdnCursor);
                return matrix;
            } finally {
                if (null != sdnCursor) {
                    sdnCursor.close();
                }
            }
        }
        Log.d(TAG, "loadSDN return null");
        return null;
    }

    /** 
     * New Feature SDN
     * */
    public static int addCursorAndSetSelection(Context context,
            ProfileAndContactsLoader profileAndContactsLoader, List<Cursor> cursors, int sdnContactCount) {
        String oldSelection = profileAndContactsLoader.getSelection();
        Cursor sdnCursor = loadSDN(context, profileAndContactsLoader);
        if (sdnCursor != null) {
            sdnContactCount = sdnCursor.getCount();
        }
        if (null != sdnCursor) {
            cursors.add(sdnCursor);
        }
        profileAndContactsLoader.setSelection(oldSelection);
        return sdnContactCount;
    }
    /**
     * For SIM contact there must be pass subId and sdnId, in case to draw sub and sdn icons.
     * Cursor cursor The contact cursor.
     * String displayName Contact display name.
     * String lookupKey
     *
     */
    public static DefaultImageRequest getDefaultImageRequest(Cursor cursor,
            String displayName, String lookupKey, boolean circularPhotos) {
        DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey, circularPhotos);
        final int subId = cursor.getInt(
                cursor.getColumnIndexOrThrow(Contacts.INDICATE_PHONE_SIM));
        if (subId > 0) {
            request.subId = subId;
            request.photoId = getSdnPhotoId(cursor);
        }
        return request;
    }

    private static int getSdnPhotoId(Cursor cursor) {
        int sdnId = 0;
        int isSdnContact = cursor.getInt(cursor.getColumnIndexOrThrow(Contacts.IS_SDN_CONTACT));
        if (isSdnContact > 0) {
            sdnId = SimContactPhotoUtils.SimPhotoIdAndUri.SIM_PHOTO_ID_SDN_LOCKED;
        }
        return sdnId;
    }
}

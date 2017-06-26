package com.mediatek.contacts.list;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.ImsCall;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ListView;

import com.android.contacts.common.list.ContactListFilter;
import com.mediatek.contacts.util.LogUtils;

public class MultiConferenceCallsPickerAdapter extends MultiPhoneNumbersPickerAdapter {
    private static final String TAG = "MultiReferenceCallsPickerAdapter";
    public static final Uri PICK_CONFERENCE_CALL_URI = Callable.CONTENT_URI;
    public static final Uri PICK_CONFERENCE_CALL_FILTER_URI =
        Callable.CONTENT_FILTER_URI;

    public MultiConferenceCallsPickerAdapter(Context context, ListView lv) {
        super(context, lv);
        // TODO Auto-generated constructor stub
    }
    @Override
    protected Uri configLoaderUri(long directoryId) {
        Uri uri;

        if (directoryId != Directory.DEFAULT) {
            LogUtils.w(TAG, "PhoneNumberListAdapter is not ready for non-default directory ID ("
                    + "directoryId: " + directoryId + ")");
        }

        if (isSearchMode()) {
            String query = getQueryString();
            Builder builder = PICK_CONFERENCE_CALL_FILTER_URI.buildUpon();
            if (TextUtils.isEmpty(query)) {
                builder.appendPath("");
            } else {
                builder.appendPath(query); // Builder will encode the query
            }

            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, String
                    .valueOf(directoryId));
            builder.appendQueryParameter("checked_ids_arg", PICK_CONFERENCE_CALL_URI.toString());
            uri = builder.build();
        } else {
            uri = PICK_CONFERENCE_CALL_URI;
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
            if (isSectionHeaderDisplayEnabled()) {
                uri = buildSectionIndexerUri(uri);
            }
        }
        Log.d(TAG, "uri: " + uri);

        return uri;
    }

    protected void configureSelection(CursorLoader loader, long directoryId,
            ContactListFilter filter) {
        if (filter == null || directoryId != Directory.DEFAULT) {
            return;
        }
        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
            case ContactListFilter.FILTER_TYPE_DEFAULT:
                LogUtils.d(TAG, "filterType" + filter.filterType);
                break;
            default:
                LogUtils.w(TAG, "Unsupported filter type came " + "(type: " + filter.filterType + ", toString: " + filter + ")"
                        + " showing all contacts.");
                // No selection.
                break;
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }
}

package com.mediatek.contacts.util;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.provider.ContactsContract.ProviderStatus;

import com.android.contacts.R;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ProviderStatusWatcher;
import com.mediatek.contacts.widget.WaitCursorView;
/** define some util functions for contact/list */
public class ContactsListUtils {
    private static final String CONTACT_PHOTO = "contactPhoto";
    private static final int DISPLAYNAME_LENGTH = 18;

    /**
     * Change Feature.
     * CR ID: ALPS00111821. Descriptions: change layout.­
     */
    public static ContactsRequest setActionCodeForContentItemType(
            Intent intent, ContactsRequest request) {
        boolean getPhoto = intent.getBooleanExtra(CONTACT_PHOTO, false);
        if (!getPhoto) {
            request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
        } else {
            request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
        }
        return request;
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
     * Bug Fix for ALPS00117275
     */
    public static String getBlurb(Context context, String displayName) {
        String blurb;
        if (displayName != null && displayName.length() > DISPLAYNAME_LENGTH) {
            String strTemp = displayName.subSequence(0, DISPLAYNAME_LENGTH).toString();
            strTemp = strTemp + "...";
            blurb = context.getString(R.string.blurbJoinContactDataWith, strTemp);
        } else {
            blurb = context.getString(R.string.blurbJoinContactDataWith, displayName);
        }
        return blurb;
    }

    /**
     * Bug Fix. CR ID: ALPS00115673 Descriptions: add wait cursor
     */
    public static boolean isNoAccountsNoContacts(Boolean destroyed, ProviderStatusWatcher.Status providerStatus) {
        return destroyed || providerStatus.status == ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS;
    }
}
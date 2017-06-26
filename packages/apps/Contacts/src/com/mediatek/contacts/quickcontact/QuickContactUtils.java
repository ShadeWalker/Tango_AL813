package com.mediatek.contacts.quickcontact;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.Insert;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.GroupMetaData;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.util.Constants;
import com.android.contacts.quickcontact.ExpandingEntryCardView;
import com.android.contacts.quickcontact.ExpandingEntryCardView.Entry;
import com.android.contacts.quickcontact.ExpandingEntryCardView.EntryContextMenuInfo;

import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.ext.IViewCustomExtension.QuickContactCardViewCustom;

import java.util.ArrayList;
import java.util.List;

/**
 * Extract some util method in QuickContactActivity to this class.
 */
public class QuickContactUtils {
    private static String TAG = "QuickContactUtils";
    private static String sSipAddress = null;

    /**
     * Bug fix ALPS01747019.
     * @param context Context
     * @param contactData Contact
     * @param aboutCardEntries List<List<Entry>>
     */
    public static void buildPhoneticNameToAboutEntry(Context context, Contact contactData,
            List<List<Entry>> aboutCardEntries) {
        // Phonetic name is not a data item, so the entry needs to be created separately
        final String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            Entry phoneticEntry = new Entry(/* viewId = */ -1,
                    /* icon = */ null,
                    context.getResources().getString(R.string.name_phonetic),
                    phoneticName,
                    /* subHeaderIcon = */ null,
                    /* text = */ null,
                     /* duration = */ null,
                     /*type=*/0,
                     /*subscriptionId*/-1,
                    /* textIcon = */ null,
                    /* primaryContentDescription = */ null,
                    /* intent = */ null,
                    /* alternateIcon = */ null,
                    /* alternateIntent = */ null,
                    /* alternateContentDescription = */ null,
                    /* shouldApplyColor = */ false,
                    /* isEditable = */ false,
                    /* EntryContextMenuInfo = */ new EntryContextMenuInfo(phoneticName,
                            context.getResources().getString(R.string.name_phonetic),
                    /* mimeType = */ null, /* id = */ -1, /* isPrimary = */ false),
                    /* thirdIcon = */ null,
                    /* thirdIntent = */ null,
                    /* thirdContentDescription = */ null,
                    /* iconResourceId = */ 0);
            List<Entry> phoneticList = new ArrayList<>();
            phoneticList.add(phoneticEntry);
            // Phonetic name comes after nickname. Check to see if the first entry type is nickname
            if (aboutCardEntries.size() > 0 && aboutCardEntries.get(0).get(0).getHeader().equals(
                    context.getResources().getString(R.string.header_nickname_entry))) {
                aboutCardEntries.add(1, phoneticList);
            } else {
                aboutCardEntries.add(0, phoneticList);
            }
        }
    }

    /**
     * Dial IP call.
     * @param context Context
     * @param number String
     * @return true if send intent successfully, else false.
     */
    public static boolean dialIpCall(Context context, String number) {
        if (number == null) {
            return false;
        }
        Uri callUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.EXTRA_IS_IP_DIAL, true);
        context.startActivity(intent);
        return true;
    }

    /**
     * Get group title based on the groupId.
     * @param groupMetaData List<GroupMetaData>
     * @param groupId long
     * @return group title
     */
    public static String getGroupTitle(List<GroupMetaData> groupMetaData, long groupId) {
        if (groupMetaData == null) {
            return null;
        }

        for (GroupMetaData group : groupMetaData) {
            if (group.getGroupId() == groupId) {
                if (!group.isDefaultGroup() && !group.isFavorites()) {
                    String title = group.getTitle();
                    if (!TextUtils.isEmpty(title)) {
                        return title;
                    }
                }
                break;
            }
        }

        return null;
    }

    /**
     * Send this contact to bluetooth for print.
     * @param context Context
     * @param contactData Contact
     * @return true if send  PRINT action,false else.
     */
    public static boolean printContact(Context context, Contact contactData) {
        if (contactData == null) {
            return false;
        }
        final String lookupKey = contactData.getLookupKey();
        final Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
        final Intent intent = new Intent();
        intent.setAction("mediatek.intent.action.PRINT");
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, shareUri);
        Log.d(TAG, "start print");

        try {
            context.startActivity(Intent.createChooser(intent,
                    context.getText(R.string.printContact)));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(context, R.string.no_way_to_print, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /**
     * For RCS-e, create JoynCard.
     * @param context Context
     * @param anchorView ExpandingEntryCardView
     * @param lookupUri Uri
     * @return ExpandingEntryCardView, the plug-in created card view.
     */
    public static ExpandingEntryCardView createPluginCardView(Context context,
            ExpandingEntryCardView anchorView, Uri lookupUri) {
        QuickContactCardViewCustom quickContactCardViewCustom = ExtensionManager.getInstance()
                .getViewCustomExtension().getQuickContactCardViewCustom();
        if (quickContactCardViewCustom != null) {
            LinearLayout container = (LinearLayout) ((Activity) context)
                    .findViewById(R.id.card_container);
            return (ExpandingEntryCardView) quickContactCardViewCustom.createCardView(container,
                    (View) anchorView, lookupUri, context);
        }
        return null;
    }

    /**
     * Set RCS-e JOYN card view theme color.
     * @param joynCard ExpandingEntryCardView
     * @param color int
     * @param colorFilter PorterDuffColorFilter
     */
    public static void setPluginThemeColor(ExpandingEntryCardView joynCard, int color,
            PorterDuffColorFilter colorFilter) {
        if (joynCard != null) {
            joynCard.setColorAndFilter(color, colorFilter);
        }
    }

    // Bug fix for ALPS01907789 @{
    public static void setSipAddress(String address) {
        sSipAddress = address;
        Log.d(TAG,"sSipAddress = " + sSipAddress);
    }

    public static void resetSipAddress() {
        sSipAddress = null;
    }

    public static void addSipExtra(Intent intent) {
        Log.d(TAG,"addSipExtra with sip = " + sSipAddress);
        if (intent != null && sSipAddress != null) {
            intent.putExtra(Insert.SIP_ADDRESS, sSipAddress);
        }
    }
    //@}
}

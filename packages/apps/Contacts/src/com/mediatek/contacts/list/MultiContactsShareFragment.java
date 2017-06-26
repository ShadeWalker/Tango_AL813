package com.mediatek.contacts.list;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.widget.Toast;

import com.android.contacts.R;
import com.mediatek.contacts.list.MultiContactsBasePickerAdapter.PickListItemCache.PickListItemData;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.MtkToast;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;


public class MultiContactsShareFragment extends MultiContactsPickerBaseFragment {

    private static final String TAG = "MultiContactsShareFragment";
    /**
     * M: change for CR ALPS00779683. Binder on one process can use 2M memory,
     * but there has 16 binder thread, every binder thread can't put data large
     * than 128KB (2M/16).
     * Origin MAX_DATA_SIZE = 400000; So change shareUri max data
     * size less 124KB.
     */
    private static final int MAX_DATA_SIZE = 124 * 1024;

    /**
     * The max multi choice count limit for share. The Cursor window allocates
     * 2M bytes memory for each client. If the data size is very big, the cursor
     * window would not allocate the memory for Cursor.moveWindow. To avoid
     * malicious operations, we only allow user to handle 1000 items.
     */
    public static final int MULTI_CHOICE_MAX_COUNT_FOR_SHARE = 1000;

    @Override
    public void onOptionAction() {
        final int selectedCount = getCheckedItemIds().length;
        if (selectedCount == 0) {
            Toast.makeText(getContext(), R.string.multichoice_no_select_alert, Toast.LENGTH_SHORT).show();
            return;
        } else if (selectedCount > getMultiChoiceLimitCount()) {
            String msg = getResources().getString(R.string.share_contacts_limit, getMultiChoiceLimitCount());
            MtkToast.toast(getActivity().getApplicationContext(), msg);
            return;
        }

        final String[] uriArray = getLoopUriArray();
        final Intent retIntent = new Intent();
        retIntent.putExtra(RESULTINTENTEXTRANAME, uriArray);
        boolean result = doShareVisibleContacts("Multi_Contact", null, uriArray);
        if (result) {
            getActivity().setResult(Activity.RESULT_OK, retIntent);
            getActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private boolean doShareVisibleContacts(String type, Uri uri, String[] idArrayUriLookUp) {
        if (idArrayUriLookUp == null || idArrayUriLookUp.length == 0) {
            LogUtils.w(TAG, "[doShareVisibleContacts],idArrayUriLookUp is error:" + idArrayUriLookUp);
            return true;
        }

        StringBuilder uriListBuilder = new StringBuilder();
        int index = 0;
        for (int i = 0; i < idArrayUriLookUp.length; i++) {
            if (index != 0) {
                uriListBuilder.append(":");
            }
            // find lookup key
            uriListBuilder.append(idArrayUriLookUp[i]);
            index++;
        }
        int dataSize = uriListBuilder.length();
        Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(uriListBuilder.toString()));
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, shareUri);
        intent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
            PeopleActivity.class.getName());

        /** M: Bug Fix for CR: ALPS00395378 @{
         * Original Code:
         * intent.putExtra("LOOKUPURIS", uriListBuilder.toString());
         */
        /** @} M: Bug fix for CR: ALPS00395378 */

        // Launch chooser to share contact via
        final CharSequence chooseTitle = getText(R.string.share_via);
        final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

        try {
            LogUtils.i(TAG, "[doShareVisibleContacts] dataSize : " + dataSize);
            if (dataSize < MAX_DATA_SIZE) {
                startActivity(chooseIntent);
                return true;
            } else {
                Toast.makeText(getContext(), R.string.share_too_large, Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getContext(), R.string.share_error, Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    public Uri getUri() {
        final long[] checkArray = getCheckedItemIds();
        final int selectedCount = checkArray.length;
        if (selectedCount == 0) {
            Toast.makeText(getContext(), R.string.multichoice_no_select_alert, Toast.LENGTH_SHORT).show();
            return null;
        } else if (selectedCount > getMultiChoiceLimitCount()) {
            String msg = getResources().getString(R.string.share_contacts_limit, getMultiChoiceLimitCount());
            MtkToast.toast(getActivity().getApplicationContext(), msg);
            return null;
        }

        final String[] uriArray = getLoopUriArray();

        StringBuilder uriListBuilder = new StringBuilder();
        boolean isFirstItem = true;
        for (String uri : uriArray) {
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                uriListBuilder.append(":");
            }
            // find lookup key
            uriListBuilder.append(uri);
        }
        Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(uriListBuilder.toString()));

        return shareUri;
    }

    private String[] getLoopUriArray() {
        final long[] checkArray = getCheckedItemIds();
        final int selectedCount = checkArray.length;
        final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();

        int curArray = 0;
        String[] uriArray = new String[selectedCount];

        for (long id : checkArray) {
            if (curArray > selectedCount) {
                break;
            }
            PickListItemData item = adapter.getListItemCache().getItemData(id);
            if (item != null) {
                uriArray[curArray++] = item.lookupUri;
            } else {
                LogUtils.e(TAG, "#getLoopUriArray(),the item is null. may some error happend.curArray:" + curArray
                        + ",id:" + id + ",checkArray.length:" + selectedCount + ",ListViewCheckedCount:"
                        + getListView().getCheckedItemCount());
            }
        }

        return uriArray;
    }

    @Override
    public int getMultiChoiceLimitCount() {
        return MULTI_CHOICE_MAX_COUNT_FOR_SHARE;
    }
}

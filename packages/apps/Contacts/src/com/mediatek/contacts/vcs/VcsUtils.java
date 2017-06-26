package com.mediatek.contacts.vcs;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.SystemProperties;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.util.Log;

import com.mediatek.contacts.util.LogUtils;
import com.android.contacts.list.DefaultContactBrowseListFragment;
//import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;

public class VcsUtils {
    private static final String TAG = "VcsUtils";
    private static boolean IS_ANIMATOR_ENABLE = false;
    private static final int MAX_NAME_COUNTS_TODISPLAY = 6;
    private static final int COLUMN_COUNTS_OF_CURSOR = 8;
    private static boolean MTK_VOICE_CONTACT_SEARCH_SUPPORT = SystemProperties.getBoolean("ro.mtk_voice_contact_support", false); //com.mediatek.common.featureoption.FeatureOption.MTK_VOICE_CONTACT_SEARCH_SUPPORT;
    private static final String KEY_ENABLE_VCS_BY_USER = "enable_vcs_by_user";
    private static final String PREFERENCE_NAME = "vcs_preference";

    private static final String[] VCS_CONTACT_PROJECTION = new String[] { Contacts._ID, // 0
            Contacts.DISPLAY_NAME_PRIMARY, // 1
            Contacts.CONTACT_PRESENCE, // 2
            Contacts.CONTACT_STATUS, // 3
            Contacts.PHOTO_ID, // 4
            Contacts.PHOTO_URI, // 5
            Contacts.PHOTO_THUMBNAIL_URI, // 6
            Contacts.LOOKUP_KEY // 7
    };

    /**
     * [VCS] whether VCS feature enabled on this device
     *
     * @return ture if allowed to enable
     */
    public static boolean isVcsFeatureEnable() {
        return MTK_VOICE_CONTACT_SEARCH_SUPPORT;
    }

    /**
     *
     * @param context
     * @return true if vcs if enable by user,false else.default will return
     *         false.
     */
    public static boolean isVcsEnableByUser(Context context) {
        SharedPreferences sp = context.getSharedPreferences(VcsUtils.PREFERENCE_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ENABLE_VCS_BY_USER, false);
    }

    /**
     *
     * @param enable
     *            true to enable the vcs,false to disable.
     * @param context
     */
    public static void setVcsEnableByUser(boolean enable, Context context) {
        SharedPreferences sp = context.getSharedPreferences(VcsUtils.PREFERENCE_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_ENABLE_VCS_BY_USER, enable).commit();
    }

    public static boolean isAnimatorEnable() {
        return IS_ANIMATOR_ENABLE;
    }

    public static Cursor getCursorByAudioName(DefaultContactBrowseListFragment allFragment, ArrayList<String> audioNameList, CursorLoader loader) {
        if (allFragment.getActivity() == null) {
            Log.w(TAG, "doInBackground,The Fragment is not attach to Activity:" + allFragment);
            return null;
        }
        if (audioNameList.size() <= 0) {
            Log.w(TAG, "doInBackground,audioNameList is empty:" + audioNameList);
            return null;
        }
        int nameListSize = audioNameList.size();
        StringBuffer sbToLog = new StringBuffer();

        // 1.make name filter selection
        StringBuffer selection = new StringBuffer();
        ArrayList<String> selectionArgs = new ArrayList<String>();
        selection.append("(");
        for (int i = 0; i < nameListSize; i++) {
            selection.append("display_name like ? or ");
            selectionArgs.add("%" + audioNameList.get(i) + "%");
            sbToLog.append(audioNameList.get(i) + ",");
        }
        // 1==1 to handle nameListSize is null or empty
        selection.append("1=1) ");

        // 2.make account filter selection
        String accountFilter = "1=1";
        if (allFragment.getAdapter() == null) {
            Log.w(TAG, "doInBackground,adapter is null");
            return null;
        }
        if (allFragment.getAdapter() instanceof com.android.contacts.common.list.DefaultContactListAdapter) {
            allFragment.getAdapter().configureLoader(loader, Directory.DEFAULT);
            accountFilter = loader.getSelection();
        }
        selection.append("and (" + accountFilter + ")");

        // 3.make selection args
        final ContentResolver resolver = allFragment.getActivity().getContentResolver();
        Uri uri = loader.getUri();
        String[] args = loader.getSelectionArgs();
        if (args != null) {
            for (String s : args) {
                selectionArgs.add(s);
                sbToLog.append(s + ",");
            }
        }
        LogUtils.d(TAG, "[onQueryContactsInfo] uri:" + uri + ",selects:" + selection + ":args:" + sbToLog.toString());

        // 4.query contacts DB
        LogUtils.i(TAG, "[vcs][performance],start query ContactsProvider,time:" + System.currentTimeMillis());
        Cursor originalCursor = resolver.query(uri, VCS_CONTACT_PROJECTION, selection.toString(),
                selectionArgs.toArray(new String[0]), "sort_key");
        LogUtils.i(TAG, "[vcs][performance],end query ContactsProvider,time:" + System.currentTimeMillis());

        LogUtils.i(TAG, "[onQueryContactsInfo] [vcs] originalCursor counts:" + originalCursor.getCount());
        Cursor cursor = orderVcsCursor(audioNameList, originalCursor);
        return cursor;
    }

    private static Cursor orderVcsCursor(ArrayList<String> audioNameList, Cursor originalCursor) {
        if (originalCursor == null) {
            LogUtils.w(TAG, "[orderVcsCursor] cusur is null.");
            return null;
        }
        String preAudioItemName = new String();
        String currAudioItemName = new String();
        String cursorItemName = new String();
        int itemCounts = 0;
        MatrixCursor audioOrderedCursor = new MatrixCursor(originalCursor.getColumnNames());
        for (int i = 0; i < audioNameList.size(); i++) {
            currAudioItemName = audioNameList.get(i);
            if (currAudioItemName.equals(preAudioItemName)) {
                LogUtils.i(TAG, "[orderVcsCursor] skip preAudioItemName:" + preAudioItemName);
                continue;
            }

            while (originalCursor.moveToNext()) {
                cursorItemName = originalCursor.getString(originalCursor.getColumnIndex(Contacts.DISPLAY_NAME_PRIMARY));
                if (currAudioItemName.equals(cursorItemName)) {
                    String[] columnValArray = new String[COLUMN_COUNTS_OF_CURSOR];
                    columnValArray[0] = String.valueOf(originalCursor.getLong(originalCursor.getColumnIndex(Contacts._ID)));
                    columnValArray[1] = originalCursor.getString(originalCursor
                            .getColumnIndex(Contacts.DISPLAY_NAME_PRIMARY));
                    columnValArray[2] = originalCursor.getString(originalCursor.getColumnIndex(Contacts.CONTACT_PRESENCE));
                    columnValArray[3] = originalCursor.getString(originalCursor.getColumnIndex(Contacts.CONTACT_STATUS));
                    columnValArray[4] = originalCursor.getString(originalCursor.getColumnIndex(Contacts.PHOTO_ID));
                    columnValArray[5] = originalCursor.getString(originalCursor.getColumnIndex(Contacts.PHOTO_URI));
                    columnValArray[6] = originalCursor
                            .getString(originalCursor.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI));
                    columnValArray[7] = originalCursor.getString(originalCursor.getColumnIndex(Contacts.LOOKUP_KEY));
                    try {
                        itemCounts++;
                        if (itemCounts > MAX_NAME_COUNTS_TODISPLAY) {
                            LogUtils.i(TAG, "[makeOrderedCursor] [vcs] mounts to max list counts!");
                            break;
                        }
                        audioOrderedCursor.addRow(columnValArray);
                    } catch (Exception e) {
                        // TODO: handle exception
                        LogUtils.i(TAG, "[makeOrderedCursor] [vcs] columnValStrings.length!=columnnames.length");
                    }
                }
            }
            // back to original position for ordering next time
            originalCursor.moveToPosition(-1);
            // set previous audio name item
            preAudioItemName = currAudioItemName;
        }

        // close the cursor
        if (originalCursor != null) {
            originalCursor.close();
        }
        LogUtils.i(TAG, "[makeOrderedCursor] [vcs] orderedCursor counts:" + audioOrderedCursor.getCount());
        audioOrderedCursor.moveToPosition(-1);
        return audioOrderedCursor;
    }
}

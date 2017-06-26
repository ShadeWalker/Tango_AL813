package com.mediatek.contacts.list;

import android.accounts.Account;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.util.WeakAsyncTask;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.util.ContactsGroupUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.ContactsGroupUtils.ContactsGroupArrayData;
import com.mediatek.contacts.util.ContactsGroupUtils.ParcelableHashMap;
import com.mediatek.contacts.util.PhbStateHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class ContactsGroupMultiPickerFragment extends MultiContactsPickerBaseFragment
        implements PhbStateHandler.Listener {

    private static final String TAG = "ContactsGroupMultiPickerFragment";
    private static final String SELECTED_ITEMS = "selected_items";
    public static final boolean DEBUG = true;
    private static HashMap<Long, ContactsGroupUtils.ContactsGroupArrayData> sSelectedContactsMap =
         new HashMap<Long, ContactsGroupUtils.ContactsGroupArrayData>();

    private String mFromUgroupName;
    private int mSubId;
    private long mFromPgroupId;
    private String mAccountName;
    private Account mAccount = null;
    private Context mContext;

    private static final int MAX_OP_COUNT_IN_ONE_BATCH = 150;
    private	List<String> mFamily = new ArrayList<String>();
   	private	List<String> mColleague = new ArrayList<String>();
   	private	List<String> mFriend = new ArrayList<String>();
   	private	List<String> mSchoolmate = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mContext = this.getActivity();
        Intent intent = this.getArguments().getParcelable(FRAGMENT_ARGS);
        mFromUgroupName = intent.getStringExtra("mGroupName");
        mAccountName = intent.getStringExtra("mAccountName");
        mSubId = intent.getIntExtra("mSubId", -1);
        mFromPgroupId = intent.getLongExtra("mGroupId", -1);
        mAccount = intent.getParcelableExtra("account");
        if (DEBUG) {
            LogUtils.i(TAG, "[onCreate]mFromUgroupName:" + mFromUgroupName
                    + "|mFromPgroupId:" + mFromPgroupId
                    + "|mSubId:" + mSubId + "|mAccountName:" + mAccountName);
        }
        ///M:fix ALPS01466297 finish Activity if phb not ready@{
        if (mSubId > 0 && !SimCardUtils.isPhoneBookReady(mSubId)) {
            LogUtils.w(TAG, "[onCreate] phone book is not ready. mSubID:" + mSubId);
            getActivity().finish();
            return;
        }
        ///@}
        initGroupTitle();
        showFilterHeader(false);
        /// M: add for sim contact
        PhbStateHandler.getInstance().register(this);
   }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /// M: add for sim contact
        PhbStateHandler.getInstance().unRegister(this);
        if (mMoveGroupTask != null) {
        ///For ALPS01956960, Async move group thread timing order.
            if (mIsMoveContactsInProcessing) {
                Toast.makeText(this.getContext(), R.string.moving_group_members_fail, Toast.LENGTH_LONG).show();
            }
            boolean result = mMoveGroupTask.cancel(true);
            mMoveGroupTask.setMoveSwitcher(true);
            LogUtils.d(TAG, "onDestory(), Cancel result: " + result);
        }
    }

    private void initGroupTitle() {

		mFamily.add("家人");
		mFamily.add("family");
		mFamily.add("خانواده");
		mFamily.add("العائلة");
		mFamily.add("Семья");
		mFamily.add("Famille");

		mColleague.add("同事");
		mColleague.add("colleague");
		mColleague.add("همکار");
		mColleague.add("زملاء العمل");
		mColleague.add("Коллеги");
		mColleague.add("Collègue");

		mFriend.add("朋友");
		mFriend.add("friend");
		mFriend.add("دوست");
		mFriend.add("صديق");
		mFriend.add("Друзья");
		mFriend.add("Ami");

		mSchoolmate.add("同学");
		mSchoolmate.add("schoolmate");
		mSchoolmate.add("دوست");
		mSchoolmate.add("زملاء الدراسة");
		mSchoolmate.add("Одноклассники");
		mSchoolmate.add("Camarade de classe");
	}
    
    @Override
    public void onOptionAction() {
        final long[] checkedIds = getCheckedItemIds();
        if (checkedIds.length == 0) {
            Toast.makeText(this.getContext(), R.string.multichoice_no_select_alert, Toast.LENGTH_SHORT).show();
            return;
        }
        sSelectedContactsMap.clear();

        final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();
        int simIndex = 0;
        int indexSimOrPhone = 0;
        for (long id : checkedIds) {
            if (DEBUG) {
                LogUtils.d(TAG, "contactId = " + id);
            }

            /**
             * M: fix CR ALPS00932398 when we tap "select all" twice then tap "ok",JE happend.@{
             */
            if (adapter.getListItemCache().getItemData(id) == null) {
                Toast.makeText(this.getContext(), R.string.phone_book_busy, Toast.LENGTH_SHORT).show();
                LogUtils.d(TAG, " onOptionAction() getItemData(id) = null,return!");
                return;
            }
            /** @} */

            simIndex = adapter.getListItemCache().getItemData(id).simIndex;
            indexSimOrPhone = adapter.getListItemCache().getItemData(id).contactIndicator;
            sSelectedContactsMap.put(id, new ContactsGroupUtils
                .ContactsGroupArrayData(simIndex, indexSimOrPhone));
        }
        if (DEBUG) {
            LogUtils.i(TAG, "[onOptionAction]selectedContactsMap size" + sSelectedContactsMap.size());
        }

        startTargetGroupQuery();
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        ContactsGroupMultiPickerAdapter adapter = new ContactsGroupMultiPickerAdapter(
                getActivity(), getListView());
        adapter.setFilter(ContactListFilter
                .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(false);
        adapter.setEmptyListEnabled(true);

        adapter.setGroupTitle(mFromUgroupName);

        adapter.setGroupAccount(mAccount);

        return adapter;
    }

    private  MoveGroupTask mMoveGroupTask;
    private  class MoveGroupTask extends
            WeakAsyncTask<String, Void, Integer, Activity> {
        private WeakReference<ProgressDialog> mProgress;
        private boolean mCancel = false;
        /** M: Add switcher for MoveGroupTask @{ */
        public void setMoveSwitcher(boolean flag) {
            mCancel = flag;
        }
        /** @} */
        public MoveGroupTask(Activity target) {
            super(target);
        }

        @Override
        protected void onPreExecute(final Activity target) {
            if (target != null && target.isFinishing()) {
                return;
            }
            ProgressDialog progressDialog = ProgressDialog.show(target, null, target
                    .getText(R.string.moving_group_members), false, true);
            progressDialog
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            cancel(true);
                            target.finish();
                        }
                    });
            progressDialog
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {

                        public void onCancel(DialogInterface dialog) {
                            mCancel = true;
                            boolean cancel = cancel(true);
                            LogUtils.i(TAG, cancel + "------------cancel");
                            target.finish();

                        }
                    });
            mProgress = new WeakReference<ProgressDialog>(progressDialog);
            super.onPreExecute(target);
        }

        @Override
        protected Integer doInBackground(Activity target, String... params) {

            String fromGroupName = params[0];
            String toGroupName = params[1];
            long fromGroupId = Long.parseLong(params[2]);
            long toGroupId = Long.parseLong(params[3]);
            int subId = Integer.parseInt(params[4]);

            if (DEBUG) {
                LogUtils.i(TAG, "[doInBackground]fromGroupName:" + fromGroupName
                        + "|toGroupName:" + toGroupName + "|fromGroupId:"
                        + fromGroupId + "|toGroupId:" + toGroupId + "|subId:"
                        + subId + "|selectedContactsMap size:"
                        + sSelectedContactsMap.size());
            }
            ///fix cr ALPS00567939
            mIsMoveContactsInProcessing = true;
            int ret = doMove(target.getContentResolver(), fromGroupName, subId,
                    toGroupName, sSelectedContactsMap, fromGroupId, toGroupId);
            ///fix cr ALPS00567939
            mIsMoveContactsInProcessing = false;
            if (DEBUG) {
                LogUtils.i(TAG, "[doInBackground]result:" + ret);
            }
            return ret;
        }

        @Override
        protected void onCancelled() {
            mCancel = true;
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(final Activity target, Integer error) {
            final ProgressDialog progress = mProgress.get();
            int toast;
            try {
                if (!target.isFinishing() && progress != null
                        && progress.isShowing()) {
                    progress.dismiss();
                }
            } catch (java.lang.IllegalArgumentException e) {
                //In some special cases, progress view will be dismissed
                //due to its window is gone, so calling dismiss here will cause exception.
                LogUtils.d(TAG, "progress.dismiss() cause IllegalArgumentException");
            }
            super.onPostExecute(target, error);
            if (error == 0) {
                toast = R.string.moving_group_members_sucess;
            } else if (error == -1) {
                toast = R.string.moving_group_members_fail;
            } else {
                toast = R.string.moving_group_members_sucess;
            }
            if (error == 0) {
                Toast.makeText(target, toast, Toast.LENGTH_LONG).show();
            }
            sSelectedContactsMap.clear();
            target.finish();
        }

        private int doMove(ContentResolver resolver, String fromUgroupName,
                int subId, String toUgroupName,
                HashMap<Long, ContactsGroupArrayData> selectedContactsMap,
                long fromPgroupId, long toPgroupId) {

            int failCount = 0;
            int fromUgroupId = -1;
            int toUgroupId = -1;
            if (subId > 0) {
                try {
                    fromUgroupId = ContactsGroupUtils.USIMGroup.hasExistGroup(
                            subId, fromUgroupName);
                    toUgroupId = ContactsGroupUtils.USIMGroup.hasExistGroup(subId,
                            toUgroupName);
                } catch (RemoteException e) {
                    // log
                    fromPgroupId = -1;
                    toUgroupId = -1;
                }
            }

            Cursor c = resolver.query(Data.CONTENT_URI,
                    new String[] { Data.CONTACT_ID }, Data.MIMETYPE + "=? AND "
                            + GroupMembership.GROUP_ROW_ID + "=?", new String[] {
                            GroupMembership.CONTENT_ITEM_TYPE,
                            String.valueOf(toPgroupId) }, null);

            HashSet<Long> set = new HashSet<Long>();
            while (c != null && c.moveToNext()) {
                long contactId = c.getLong(0);
                LogUtils.i(TAG, contactId + "--------contactId");
                set.add(contactId);
            }
            if (c != null) {
                c.close();
            }

            ContentValues values = new ContentValues();
            StringBuilder idBuilder = new StringBuilder();
            StringBuilder idBuilderDel = new StringBuilder();
            // USIM group begin
            boolean isInTargetGroup = false;
            // USIM group end
            Iterator<Entry<Long, ContactsGroupArrayData>> iter = new
                    HashMap<Long, ContactsGroupUtils.ContactsGroupArrayData>(selectedContactsMap)
                            .entrySet().iterator();
            int moveCount = 0;

            while (iter.hasNext() && !mCancel) {
                LogUtils.i(TAG, mCancel + "----------mCancel---------");
                Entry<Long, ContactsGroupArrayData> entry = iter.next();
                long id = entry.getKey();
                LogUtils.i(TAG, id + "--------entry.getKey()");
                // USIM Group begin
                isInTargetGroup = set.contains(id);

                int tsimId = entry.getValue().getmSimIndexPhoneOrSim();

                if (DEBUG) {
                    LogUtils.i(TAG, "contactsId--------------" + id);
                    LogUtils.i(TAG, "mSimIndexPhoneOrSim--------------" + tsimId);
                    LogUtils.i(TAG, "mSimIndex--------------"
                            + entry.getValue().getmSimIndex());
                }
                if (tsimId > 0
                        && !ContactsGroupUtils.moveUSIMGroupMember(
                                entry.getValue(), subId, isInTargetGroup,
                                fromUgroupId, toUgroupId)) {
                    // failed to move USIM contacts from one group to another
                    LogUtils.d(TAG, "Failed to move USIM contacts from one group to another");
                    failCount++;
                    continue;
                }

                if (isInTargetGroup) { // mark as need to be delete later
                    if (idBuilderDel.length() > 0) {
                        idBuilderDel.append(",");
                    }
                    idBuilderDel.append(id);
                } else { // mark as need to be update later
                    if (idBuilder.length() > 0) {
                        idBuilder.append(",");
                    }
                    idBuilder.append(id);
                }
                // USIM Group end
                moveCount++;
                if (moveCount > MAX_OP_COUNT_IN_ONE_BATCH) {
                    int count = 0;
                    if (idBuilder.length() > 0) {
                        String where = ContactsGroupUtils.SELECTION_MOVE_GROUP_DATA
                                .replace("%1", idBuilder.toString()).replace("%2",
                                        String.valueOf(fromPgroupId));
                        LogUtils.i(TAG, "[doMove]where: " + where);
                        values.put(CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                                toPgroupId);
                        count = resolver.update(ContactsContract.Data.CONTENT_URI,
                                values, where, null);
                        idBuilder.setLength(0);
                    }
                    LogUtils.i(TAG, "[doMove]move data count:" + count);
                    count = 0;
                    if (idBuilderDel.length() > 0) {
                        String whereDel = ContactsGroupUtils.SELECTION_MOVE_GROUP_DATA
                                .replace("%1", idBuilderDel.toString());
                        whereDel = whereDel.replace("%2", String
                                .valueOf(fromPgroupId));
                        LogUtils.i(TAG, "[doMove]whereDel: " + whereDel);
                        count = resolver.delete(ContactsContract.Data.CONTENT_URI,
                                whereDel, null);
                        LogUtils.i(TAG, "[doMove]delete repeat contact:"
                                + whereDel.toString());
                        idBuilderDel.setLength(0);
                    }
                    LogUtils.i(TAG, "[doMove]delete repeat data count:" + count);
                    moveCount = 0;
                }
            }
            int count = 0;
            if (idBuilder.length() > 0) {
                String where = ContactsGroupUtils.SELECTION_MOVE_GROUP_DATA
                        .replace("%1", idBuilder.toString()).replace("%2",
                                String.valueOf(fromPgroupId));
                LogUtils.i(TAG, "[doMove]End where: " + where);
                values
                        .put(CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                                toPgroupId);
                count = resolver.update(ContactsContract.Data.CONTENT_URI, values,
                        where, null);
                idBuilder.setLength(0);
            }
            ///fix cr ALPS00567939
            if (sSelectedContactsMap.size() == count) {
                mIsMoveContactsInProcessing = false;
            }
            LogUtils.i(TAG, "[doMove]End move data count:" + count);
            count = 0;
            if (idBuilderDel.length() > 0) {
                String whereDel = ContactsGroupUtils.SELECTION_MOVE_GROUP_DATA
                        .replace("%1", idBuilderDel.toString());
                whereDel = whereDel.replace("%2", String.valueOf(fromPgroupId));
                LogUtils.i(TAG, "[doMove]End whereDel: " + whereDel);
                count = resolver.delete(ContactsContract.Data.CONTENT_URI,
                        whereDel, null);
                idBuilderDel.setLength(0);
            }
            LogUtils.i(TAG, "[doMove]End delete repeat data count:" + count);
            int totalCount = selectedContactsMap.entrySet().size();
            return failCount == 0 ? 0 : (failCount == totalCount ? -1 : failCount);
        }
    }

    /**
     * Start the action of move group.
     */
    private void startMoveGroupTask(long targetGroupId, String originalGroupName,
            String targetGroupName, long originalGroupId, int subId) {
        if (targetGroupId > 0) {
            mMoveGroupTask = new MoveGroupTask(getActivity());
            mMoveGroupTask.execute(originalGroupName, targetGroupName,
                    String.valueOf(originalGroupId),
                    String.valueOf(targetGroupId), String.valueOf(subId));
        } else {
            if (getActivity() != null) {
                Toast.makeText(getActivity(), R.string.multichoice_no_select_alert,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class MoveDialog extends DialogFragment {

        private Activity mContext = null;
        private String mAccountName = null;
        private int mSubId = -1;
        private long mOriginalGroupId = -1;
        private String mOriginalGroupName = null;
        private long mTargetGroupId = -1;
        private String mTargetGroupName = null;
        private long [] mIdArray = null;
        private String [] mTitleArray = null;

        public MoveDialog() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mContext = activity;
        }

        public static MoveDialog newInstance(Context context, ContactsGroupMultiPickerFragment target) {
            final MoveDialog fragment = new MoveDialog();
            fragment.setTargetFragment(target, 0);
            return fragment;
        }

        public void onCreate(Bundle savedState) {
            super.onCreate(savedState);
            Intent intent = this.getArguments().getParcelable(FRAGMENT_ARGS);

            mOriginalGroupName = intent.getStringExtra("mGroupName");
            mAccountName = intent.getStringExtra("mAccountName");
            mSubId = intent.getIntExtra("mSubId", -1);
            mOriginalGroupId = intent.getLongExtra("mGroupId", -1);

            mIdArray = intent.getLongArrayExtra("IdArray");
            mTitleArray = intent.getStringArrayExtra("TitleArray");

            LogUtils.d(TAG, "[MoveDialog#onCreate]originalGroupName:"
                    + mOriginalGroupName + "|originalGroupId:" + mOriginalGroupId
                    + "|accountName:" + mAccountName + "|subId:" + mSubId
                    + "|mIdArray" + (mIdArray == null ? "null" : mIdArray.toString())
                    + "|mTitleArray:" + (mTitleArray == null ? "null" : mTitleArray.toString()));

            if (savedState != null) {
                mTargetGroupName = savedState.getString("target_group_name");
                mTargetGroupId = savedState.getLong("target_group_id", -1);
                LogUtils.d(TAG, "[MoveDialog#onCreate]targetGroupName:"
                        + mTargetGroupName + "|targetGroupId:"
                        + String.valueOf(mTargetGroupId));
                // add for ALPS01889745.
                LogUtils.d(TAG, "restore selected map");
                ParcelableHashMap parcelMap = (ParcelableHashMap) savedState
                        .getParcelable(SELECTED_ITEMS);
                if (parcelMap != null) {
                    sSelectedContactsMap = (HashMap<Long, ContactsGroupUtils
                        .ContactsGroupArrayData>) parcelMap.getHashMap();
                }

            }

        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (!TextUtils.isEmpty(mTargetGroupName)) {
                outState.putString("target_group_name", mTargetGroupName);
            }
            outState.putLong("target_group_id", mTargetGroupId);
            outState.putString("mGroupName", mOriginalGroupName);
            outState.putString("mAccountName", mAccountName);
            outState.putInt("mSubId", mSubId);
            outState.putLong("mGroupId", mOriginalGroupId);
            // add for ALPS01889745.
            outState.putParcelable(SELECTED_ITEMS, new ParcelableHashMap(sSelectedContactsMap));
            LogUtils.d(TAG, "save selected map: " + sSelectedContactsMap.size());
            super.onSaveInstanceState(outState);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
            builder.setTitle(R.string.move_contacts_to);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            int checkedPositon = -1;
            if (mTitleArray != null && mTitleArray.length > 0) {
                checkedPositon = 0;
                mTargetGroupId = mIdArray[0];
                mTargetGroupName = mTitleArray[0];
            }
            builder.setSingleChoiceItems(mTitleArray, checkedPositon,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {

                    long id = mIdArray[which];
                    String title = mTitleArray[which];
                    LogUtils.d(TAG, "[onClick]Move to title:" + title + " ||id: " + id);
                    mTargetGroupId = id;
                    mTargetGroupName = title;
                }
            });

            final ContactsGroupMultiPickerFragment target = (ContactsGroupMultiPickerFragment) getTargetFragment();
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    // Call ContactsGroupMultiPickerFragment to start move group.
                    target.startMoveGroupTask(mTargetGroupId, mOriginalGroupName,
                            mTargetGroupName, mOriginalGroupId, mSubId);
                }
            });
            builder.setNegativeButton(android.R.string.no, null);
            return builder.create();
        }
    }

    private MoveDialog mMoveDialog = null;
    private GroupQueryHandler mQueryHandler = null;
    private int mGroupQueryToken = 0;
    private class GroupQueryHandler extends AsyncQueryHandler {

        public GroupQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (token != mGroupQueryToken) {
                return;
            }
            if (mMoveDialog != null && mMoveDialog.getDialog() != null
                    && mMoveDialog.getDialog().isShowing()) {
                return;
            }
            Intent intent = getArguments().getParcelable(FRAGMENT_ARGS);
            if (cursor != null) {
                int count = cursor.getCount();
                long[] idArray = new long[count];
                String [] titleArray = new String[count];
                int i = 0;
                while (cursor.moveToNext()) {
                    String title = cursor.getString(1);
                    if (null != title) {
                        idArray[i] = cursor.getLong(0);
                        if (mFamily.contains(title)) {
                        	title= mContext.getResources().getString(R.string.family_group);
                		} else if (mColleague.contains(title)) {
                			title= mContext.getResources().getString(R.string.colleague_group);
                		} else if (mFriend.contains(title)) {
                			title= mContext.getResources().getString(R.string.friend_group);
                		} else if (mSchoolmate.contains(title)) {
                			title= mContext.getResources().getString(R.string.schoolmate_group);
                		} 
                        titleArray[i] = title;
                        i++;
                    } else {
                        LogUtils.i(TAG, "Error: group title is NULL!!");
                    }
                }
                intent.putExtra("TitleArray", titleArray);
                intent.putExtra("IdArray", idArray);

                cursor.close();
            }

            if (getActivity() != null && !getActivity().isFinishing()) {
                showMoveDialog();
            }
        }
    }

    private void showMoveDialog() {
        mMoveDialog = MoveDialog.newInstance(getActivity(), this);
        mMoveDialog.setArguments(getArguments());
        mMoveDialog.show(getFragmentManager(), "moveGroup");
    }

    public void startTargetGroupQuery() {
        if (mQueryHandler == null) {
            mQueryHandler = new GroupQueryHandler(this.getActivity()
                    .getContentResolver());
        }
        Intent intent = getArguments().getParcelable(FRAGMENT_ARGS);
        String accountName = intent.getStringExtra("mAccountName");
        long originalGroupId = intent.getLongExtra("mGroupId", -1);
        Uri viewGroupUri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI,
                "groups");
        mQueryHandler.startQuery(++mGroupQueryToken, null, viewGroupUri,
                new String[] { Groups._ID, Groups.TITLE }, Groups.ACCOUNT_NAME
                        + "= '" + accountName + "' AND " + Groups._ID + " !="
                        + originalGroupId + " AND " + Groups.AUTO_ADD
                        + "=0 AND " + Groups.FAVORITES + "=0 AND "
                        + Groups.DELETED + "=0 ", null, Groups.TITLE);
    }

    /// M: add for sim contact
    @Override
    public void onPhbStateChange(int subId) {
        if (subId == mSubId) {
            LogUtils.d(TAG , "onReceive,subId:" + subId + ",finish Group EditorActivity.");
            getActivity().finish();
            return;
        }
    }

    /**
     *M: fixed cr ALPS00567939 @ {
     */
    private static boolean mIsMoveContactsInProcessing = false;
    public static synchronized boolean isMoveContactsInProcessing() {
        return mIsMoveContactsInProcessing;
    }
    /** @} */
}

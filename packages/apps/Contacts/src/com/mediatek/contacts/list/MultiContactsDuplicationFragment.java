
package com.mediatek.contacts.list;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.ExportVCardActivity;
import com.android.contacts.common.vcard.VCardCommonArguments;
import com.android.internal.telephony.PhoneConstants;

import com.mediatek.contacts.activities.ContactImportExportActivity;
import com.mediatek.contacts.list.MultiContactsBasePickerAdapter.PickListItemCache;
import com.mediatek.contacts.list.MultiContactsBasePickerAdapter.PickListItemCache.PickListItemData;
import com.mediatek.contacts.list.service.MultiChoiceHandlerListener;
import com.mediatek.contacts.list.service.MultiChoiceRequest;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.model.AccountWithDataSetEx;
import com.mediatek.contacts.simservice.SIMServiceUtils;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.MtkToast;
import com.mediatek.contacts.util.ProgressHandler;

import java.util.ArrayList;
import java.util.List;

public class MultiContactsDuplicationFragment extends MultiContactsPickerBaseFragment {

    public static final String TAG = "CopyMultiContacts";
    public static final boolean DEBUG = true;

    private static final String FROMACCOUNT = "fromaccount";
    private static final String TOACCOUNT = "toaccount";

    private static final int DST_STORE_TYPE_NONE = 0;
    private static final int DST_STORE_TYPE_PHONE = 1;
    private static final int DST_STORE_TYPE_SIM = 2;
    private static final int DST_STORE_TYPE_USIM = 3;
    private static final int DST_STORE_TYPE_STORAGE = 4;
    private static final int DST_STORE_TYPE_ACCOUNT = 5;
    //UIM
    private static final int DST_STORE_TYPE_UIM = 6;
    private static final int DST_STORE_TYPE_CSIM = 7;
    //UIM
    private int mDstStoreType = DST_STORE_TYPE_NONE;
    //private int mSrcStoreType = DST_STORE_TYPE_NONE;
    private Account mAccountSrc;
    private Account mAccountDst;

    private SendRequestHandler mRequestHandler;
    private HandlerThread mHandlerThread;

    private CopyRequestConnection mConnection;

    private List<MultiChoiceRequest> mRequests = new ArrayList<MultiChoiceRequest>();

    private int mRetryCount = 20;

    private int mClickCounter = 1;

    private ProgressHandler mProgressHandler = new ProgressHandler();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Intent intent = this.getArguments().getParcelable(FRAGMENT_ARGS);

        /** M : bug fixed for CR ALPS00562872 @{*/
        if (intent.getExtras() != null && intent.getExtras().getClassLoader() == null) {
            LogUtils.w(TAG, "The ClassLoader of bundle is null, will reset it");
            intent.setExtrasClassLoader(getClass().getClassLoader());
        }
        /** }*/

        mAccountSrc = (Account) intent.getParcelableExtra(FROMACCOUNT);
        mAccountDst = (Account) intent.getParcelableExtra(TOACCOUNT);

        mDstStoreType = getStoreType(mAccountDst);
        //mSrcStoreType = getStoreType(mAccountDst);

        LogUtils.d(TAG, "Destination store type is " + storeTypeToString(mDstStoreType));

    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        ContactListFilter filter = ContactListFilter.createAccountFilter(mAccountSrc.type,
                mAccountSrc.name, null, null, null);
        super.setListFilter(filter);
    }

    @Override
    public boolean isAccountFilterEnable() {
        return false;
    }

    @Override
    public void onDestroyView() {
        LogUtils.d(TAG, "onDestroyView");
        super.onDestroyView();

        mProgressHandler.dismissDialog(getFragmentManager());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        LogUtils.d(TAG, "onConfigurationChanged " + newConfig.toString());
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onOptionAction() {
        // if not item checked, we will do nothing.
        if (getCheckedItemIds().length == 0) {
            Toast.makeText(getContext(), R.string.multichoice_no_select_alert,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (mDstStoreType != DST_STORE_TYPE_STORAGE) {
            if (mClickCounter > 0) {
                mClickCounter--;
            } else {
                LogUtils.d(TAG, "Avoid re-entrence");
                return;
            }
        }

        //setDataSetChangedNotifyEnable(false);
        if (mDstStoreType == DST_STORE_TYPE_STORAGE) {
            doExportVCardToSDCard();
        } else {
            startCopyService();

            if (mHandlerThread == null) {
                mHandlerThread = new HandlerThread(TAG);
                mHandlerThread.start();
                mRequestHandler = new SendRequestHandler(mHandlerThread.getLooper());
            }

            final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();
            final PickListItemCache listItemCacher = adapter.getListItemCache();
            // Bug fix ALPS01651069, if listItemCacher is empty, just return.
            if (listItemCacher.isEmpty()) {
                return;
            }
            final long[] checkedIds = getCheckedItemIds();
            for (long id : checkedIds) {
                PickListItemData item = listItemCacher.getItemData(id);
                mRequests.add(new MultiChoiceRequest(item.contactIndicator, item.simIndex, (int) id, item.displayName));
            }

            // UIM
            if (mDstStoreType == DST_STORE_TYPE_SIM || mDstStoreType == DST_STORE_TYPE_USIM
                    || mDstStoreType == DST_STORE_TYPE_UIM
                    || mDstStoreType == DST_STORE_TYPE_CSIM) {
                // Check Radio state
                int subId = ((AccountWithDataSetEx) mAccountDst).getSubId();
                /** M: change for PHB Status Refactoring. @{ */
                if (SimCardUtils.isPhoneBookReady(subId)) {
                    boolean serviceRunning = SIMServiceUtils.isServiceRunning(subId);
                    Log.i(TAG, "AbstractService state is running? " + serviceRunning);
                    if (serviceRunning) {
                        Log.i(TAG, "service is running, we would wait the service finished.");
                        MtkToast.toast(getActivity(), R.string.notifier_fail_copy_title,
                                Toast.LENGTH_SHORT);
                        destroyMyself();
                        // registerReceiver();
                    } else {
                        Log.i(TAG, "service is finished.");
                        mRequestHandler.sendMessage(mRequestHandler.obtainMessage(
                                SendRequestHandler.MSG_REQUEST, mRequests));
                    }
                    /** @} */
                } else {
                  Log.i(TAG, "[onOptionAction] isPhoneBookReady return false.");
                  MtkToast.toast(getActivity(), R.string.notifier_fail_copy_title, Toast.LENGTH_SHORT);
                  destroyMyself();
                }
                /**@}*/
            } else {
                mRequestHandler.sendMessage(mRequestHandler.obtainMessage(
                        SendRequestHandler.MSG_REQUEST, mRequests));
            }
        }
    }

    private static int getStoreType(Account account) {
        if (account == null) {
            return DST_STORE_TYPE_NONE;
        }

        if (ContactImportExportActivity.STORAGE_ACCOUNT_TYPE.equals(account.type)) {
            return DST_STORE_TYPE_STORAGE;
        } else if (AccountTypeUtils.ACCOUNT_TYPE_LOCAL_PHONE.equals(account.type)) {
            return DST_STORE_TYPE_PHONE;
        } else if (AccountTypeUtils.ACCOUNT_TYPE_SIM.equals(account.type)) {
            return DST_STORE_TYPE_SIM;
        } else if (AccountTypeUtils.ACCOUNT_TYPE_USIM.equals(account.type)) {
            return DST_STORE_TYPE_USIM;
        } else if (AccountTypeUtils.ACCOUNT_TYPE_UIM.equals(account.type)) { /// M: UIM
            return DST_STORE_TYPE_UIM;
        } else if (AccountTypeUtils.ACCOUNT_TYPE_CSIM.equals(account.type)) {
            return DST_STORE_TYPE_CSIM;
        }
        /// M: UIM

        return DST_STORE_TYPE_ACCOUNT;
    }

    private static String storeTypeToString(int type) {
        switch (type) {
            case DST_STORE_TYPE_NONE:
                return "DST_STORE_TYPE_NONE";
            case DST_STORE_TYPE_PHONE:
                return "DST_STORE_TYPE_PHONE";
            case DST_STORE_TYPE_SIM:
                return "DST_STORE_TYPE_SIM";
            case DST_STORE_TYPE_USIM:
                return "DST_STORE_TYPE_USIM";
            case DST_STORE_TYPE_STORAGE:
                return "DST_STORE_TYPE_STORAGE";
            case DST_STORE_TYPE_ACCOUNT:
                return "DST_STORE_TYPE_ACCOUNT";

            //UIM
            case DST_STORE_TYPE_UIM:
                return "DST_STORE_TYPE_UIM";
            //UIM
            case DST_STORE_TYPE_CSIM:
                return "DST_STORE_TYPE_CSIM";
            default:
                return "DST_STORE_TYPE_UNKNOWN";
        }
    }

    private void doExportVCardToSDCard() {
        final MultiContactsBasePickerAdapter adapter = (MultiContactsBasePickerAdapter) getAdapter();
        final long[] checkedIds = getCheckedItemIds();
        StringBuilder exportSelection = new StringBuilder();
        exportSelection.append(Contacts._ID + " IN (");

        int curIndex = 0;
        for (long id : checkedIds) {
            LogUtils.d(TAG, "contactId = " + id);
            if (curIndex++ != 0) {
                exportSelection.append("," + id);
            } else {
                exportSelection.append(id);
            }
        }

        exportSelection.append(")");

        LogUtils.d(TAG, "doExportVCardToSDCard exportSelection is " + exportSelection.toString());

        Intent exportIntent = new Intent(getActivity(), ExportVCardActivity.class);
        ///M:fix ALPS00997877,The ExportVCardActivity need ARG_CALLING_ACTIVITY
        ///So when user click on the notification after export finished,it will jump to PeopleActivity.same as JB.
        exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY, PeopleActivity.class.getName());
        ///@}
        exportIntent.putExtra("exportselection", exportSelection.toString());
        if (mAccountDst instanceof AccountWithDataSet) {
            AccountWithDataSet account = (AccountWithDataSet) mAccountDst;
            exportIntent.putExtra("dest_path", account.dataSet);
        }

        getActivity().startActivityForResult(exportIntent, ContactImportExportActivity.REQUEST_CODE);
    }

    private class CopyRequestConnection implements ServiceConnection {
        private MultiChoiceService mService;

        public boolean sendCopyRequest(final List<MultiChoiceRequest> requests) {
            LogUtils.d(TAG, "Send an copy request");
            if (mService == null) {
                LogUtils.i(TAG, "mService is not ready");
                return false;
            }
            mService.handleCopyRequest(requests, new MultiChoiceHandlerListener(mService), mAccountSrc, mAccountDst);
            return true;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            LogUtils.d(TAG, "onServiceConnected");
            mService = ((MultiChoiceService.MyBinder) binder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtils.d(TAG, "Disconnected from MultiChoiceService");
        }
    }

    private class SendRequestHandler extends Handler {

        public static final int MSG_REQUEST = 100;
        public static final int MSG_PBH_LOAD_FINISH = 200;
        public static final int MSG_END = 300;
        public static final int MSG_WAIT_CURSOR_START = 400;
        public static final int MSG_WAIT_CURSOR_END = 500;

        public SendRequestHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_REQUEST) {
                if (!mConnection.sendCopyRequest((List<MultiChoiceRequest>) msg.obj)) {
                    if (mRetryCount-- > 0) {
                        sendMessageDelayed(obtainMessage(msg.what, msg.obj), 500);
                    } else {
                        sendMessage(obtainMessage(MSG_END));
                    }
                } else {
                    sendMessage(obtainMessage(MSG_END));
                }
                return;
            } else if (msg.what == MSG_END) {
                destroyMyself();
                return;
            } else if (msg.what == MSG_PBH_LOAD_FINISH) {
                unRegisterReceiver();
                sendMessage(obtainMessage(SendRequestHandler.MSG_REQUEST, mRequests));
                return;
            } else if (msg.what == MSG_WAIT_CURSOR_START) {
                LogUtils.d(TAG, "Show waiting dialog");
                // Show waiting progress dialog
                mProgressHandler.showDialog(getFragmentManager());
            } else if (msg.what == MSG_WAIT_CURSOR_END) {
                LogUtils.d(TAG, "Dismiss waiting dialog");
                // Dismiss waiting progress dialog
                mProgressHandler.dismissDialog(getFragmentManager());
            }
            super.handleMessage(msg);
        }

    }

    void startCopyService() {
        mConnection = new CopyRequestConnection();

        LogUtils.i(TAG, "Bind to MultiChoiceService.");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this.getActivity(), MultiChoiceService.class);
        getActivity().getApplicationContext().startService(intent);
        getActivity().getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    void destroyMyself() {
        LogUtils.d(TAG, "destroyMyself");
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        if (getActivity() != null) {
            getActivity().getApplicationContext().unbindService(mConnection);
            getActivity().finish();
        }
    }


    private class PBHLoadFinishReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtils.d(TAG, "onReceive intent is " + action);

            /// M: change for SIM Service refactoring
            if (action.equals(SIMServiceUtils.ACTION_PHB_LOAD_FINISHED)) {
                int subId = ((AccountWithDataSetEx) mAccountDst).getSubId();
                if (intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1) == subId) {
                    LogUtils.d(TAG, "onReceive intent subId is "
                            + intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1));
                    mRequestHandler.sendMessage(mRequestHandler
                            .obtainMessage(SendRequestHandler.MSG_PBH_LOAD_FINISH));
                }
            }
        }
    }

    private PBHLoadFinishReceiver mPHBLoadFinishReceiver = new PBHLoadFinishReceiver();

    private void registerReceiver() {
        LogUtils.i(TAG, "registerReceiver");

        /// M: change for SIM Service refactoring
        IntentFilter phbLoadIntentFilter = new IntentFilter(
                (SIMServiceUtils.ACTION_PHB_LOAD_FINISHED));
        getActivity().registerReceiver(mPHBLoadFinishReceiver, phbLoadIntentFilter);
    }

    private void unRegisterReceiver() {
        LogUtils.i(TAG, "unRegisterReceiver");
        getActivity().unregisterReceiver(mPHBLoadFinishReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

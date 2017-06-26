package com.mediatek.contacts.activities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract.Intents.Insert;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.ServiceManager;
import android.os.Build;
import android.provider.ContactsContract.ProviderStatus;
import android.support.v4.view.ViewPager;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.VCardService;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.util.PhoneCapabilityTester;
import com.mediatek.contacts.ContactsApplicationEx;
import com.mediatek.contacts.ExtensionManager;
import com.mediatek.contacts.list.ContactsGroupMultiPickerFragment;
import com.mediatek.contacts.list.service.MultiChoiceService;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.simservice.SIMEditProcessor;
import com.mediatek.contacts.util.AccountTypeUtils;
import com.mediatek.contacts.util.ContactsSettingsUtils;
import com.mediatek.contacts.util.LogUtils;
import com.mediatek.contacts.util.MtkToast;
import com.mediatek.contacts.vcs.VcsController;

public class ActivitiesUtils {

    private static final String TAG = "ActivitiesUtils";
	public static int doImportExportRequestCode=98;

    /**  For CR ALPS01541210 */
    public static boolean checkContactsProcessIsBusy(final Activity activity) {
        // Since busy return directly no receiver is registered
        boolean isProcessBusy = ContactsApplicationEx.isContactsApplicationBusy();
        LogUtils.d(TAG, "isProcessBusy = " + isProcessBusy);
        if (isProcessBusy) {
            LogUtils.d(TAG, "[onCreate]contacts phone book busy.");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MtkToast.toast(activity.getApplicationContext(), R.string.phone_book_busy);
                }
            });
            activity.finish();
            return true;
        }
        return false;
    }

    public static boolean checkPhoneBookReady(final Activity activity, Bundle savedState, int subId) {
        if (subId > 0 && !SimCardUtils.isPhoneBookReady(subId)) {
            LogUtils.w(TAG, "[onCreate] phone book is not ready. mSubId:" + subId);
            activity.finish();
            return true;
        }

        if ((MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE)
                || MultiChoiceService.isProcessing(MultiChoiceService.TYPE_COPY)
                || VCardService.isProcessing(VCardService.TYPE_IMPORT)
                || ContactsGroupMultiPickerFragment.isMoveContactsInProcessing() /// M: Fixed cr ALPS00567939
            ) && (savedState == null)) {
            LogUtils.d(TAG, "delete or copy is processing ");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity.getApplicationContext(), R.string.phone_book_busy, Toast.LENGTH_SHORT).show();
                }
            });
            activity.finish();
            return true;
        }
        return false;
    }

    public static boolean isDeleteingContact(final Activity activity) {
        if (MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE)
                || MultiChoiceService.isProcessing(MultiChoiceService.TYPE_COPY)
                || VCardService.isProcessing(VCardService.TYPE_IMPORT)
                || ContactsGroupMultiPickerFragment.isMoveContactsInProcessing() /// M: Fixed cr ALPS00567939
                || ContactSaveService.isGroupTransactionProcessing()) { /// M: Fixed CR ALPS00542175
            LogUtils.d(TAG, "delete or copy is processing ");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity.getApplicationContext(), R.string.phone_book_busy,
                            Toast.LENGTH_SHORT).show();
                }
            });
            activity.finish();
            return true;
        }

        return false;
    }

    public static Handler initHandler(final Activity activity) {
        SIMEditProcessor.Listener l = (SIMEditProcessor.Listener) activity;
        Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                String content = null;
                int contentId = msg.arg1;
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    content = bundle.getString("content");
                }
                onShowToast(activity, content, contentId);
            }
        };
        SIMEditProcessor.registerListener(l, handler);

        return handler;
    }

    /** Add for SIM Service refactory */
    public static void onShowToast(Activity activity, String msg, int resId) {
        LogUtils.d(TAG, "msg: " + msg + " ,resId: " + resId);
        if (msg != null) {
            MtkToast.toast(activity, msg);
        } else if (resId != -1) {
            MtkToast.toast(activity, resId);
        }
    }


    public static void setPickerFragmentAccountType(Activity activity, ContactEntryListFragment<?> listFragment) {
        if (listFragment instanceof ContactPickerFragment) {
            ContactPickerFragment fragment = (ContactPickerFragment) listFragment;
            int accountTypeShow = activity.getIntent().getIntExtra(ContactsSettingsUtils.ACCOUNT_TYPE,
                    ContactsSettingsUtils.ALL_TYPE_ACCOUNT);
            LogUtils.d(TAG, "[configureListFragment]accountTypeShow:" + accountTypeShow);
            fragment.setAccountType(accountTypeShow);
        }
    }

    /** [For op09] */
    public static void formatPhoneNumber(Activity activity) {
        Bundle extras = activity.getIntent().getExtras();
        if (extras != null) {
            CharSequence value = extras.getCharSequence(Insert.PHONE);
	    /*zhongshengbin modified for HQ01894076  */
	    if(Build.TYPE.equals("eng")){
            LogUtils.d(TAG, " phone number == " + value);
	    }
            //LogUtils.d(TAG, " phone number == " + value);
            if (!TextUtils.isEmpty(value)) {
                String number = value.toString();
                String numberFormat = ExtensionManager.getInstance().getCtExtension().formatPhoneNumber(number);

                LogUtils.d(TAG, "[onCreate] number:" + number + ",numberFormat:" + numberFormat);
                extras.putCharSequence(Insert.PHONE, numberFormat);
            }
        }
    }

    /** New Feature */
    public static boolean checkSimNumberValid(Activity activity, String ssp) {
        if (ssp != null && !PhoneNumberUtils.isGlobalPhoneNumber(ssp)) {
            Toast.makeText(activity.getApplicationContext(), R.string.sim_invalid_number,
                    Toast.LENGTH_SHORT).show();
            activity.finish();
            return true;
        }
        return false;
    }


    public static void prepareVcsMenu(Menu menu, VcsController vcsController) {
        if (vcsController != null) {
            vcsController.onPrepareOptionsMenuVcs(menu);
        } else {
            MenuItem item = menu.findItem(com.android.contacts.R.id.menu_vcs);
            if (item != null) {
                item.setVisible(false);
            }
        }
    }

    public static boolean doImportExport(Activity activity) {
        if (MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE)) {
            Toast.makeText(activity, R.string.contact_delete_all_tips,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        final Intent intent = new Intent(activity, ContactImportExportActivity.class);
        activity.startActivityForResult(intent, doImportExportRequestCode);
        return true;
    }

    public static boolean deleteContact(Activity activity) {
        if (MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE)) {
            Toast.makeText(activity, R.string.contact_delete_all_tips,
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (VCardService.isProcessing(VCardService.TYPE_IMPORT)
                || VCardService.isProcessing(VCardService.TYPE_EXPORT)) {
            Toast.makeText(activity, R.string.contact_import_export_tips,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        activity.startActivity(new Intent().setClassName(activity.getApplicationContext(),
                "com.mediatek.contacts.list.ContactListMultiChoiceActivity").setAction(
                com.mediatek.contacts.util.ContactsIntent.LIST.ACTION_DELETE_MULTICONTACTS));
        return true;
    }

    public static boolean conferenceCall(Activity activity) {
        final Intent intent = new Intent();
        intent.setClassName(activity,
                "com.mediatek.contacts.list.ContactListMultiChoiceActivity").setAction(
                com.mediatek.contacts.util.ContactsIntent.LIST
                .ACTION_PICK_MULTIPLE_PHONEANDIMSANDSIPCONTACTS);
        intent.putExtra(com.mediatek.contacts.util.ContactsIntent.CONFERENCE_SENDER,
                com.mediatek.contacts.util.ContactsIntent.CONFERENCE_CONTACTS);
        activity.startActivity(intent);

        return true;
    }

    public static void setAllFramgmentShow(View contactsUnavailableView,
            DefaultContactBrowseListFragment allFragment,
            Activity activity, ViewPager tabPager,
            ContactsUnavailableFragment contactsUnavailableFragment,
            ProviderStatusWatcher.Status providerStatus) {

        boolean isNeedShow = showContactsUnavailableView(contactsUnavailableView,
                contactsUnavailableFragment, providerStatus);

        boolean isUsingTwoPanes = PhoneCapabilityTester.isUsingTwoPanes(activity);
        if (isNeedShow) {
            if (null != allFragment) {
                // mTabPager only exists while 1-pane thus the code should be modified for 2-panes
                if (isUsingTwoPanes && tabPager != null) {
                    tabPager.setVisibility(View.VISIBLE);
                }
                LogUtils.d(TAG, "close wait cursor");
                allFragment.setEnabled(true);
                allFragment.closeWaitCursor();
                if (!isUsingTwoPanes && tabPager != null) {
                    allFragment.setProfileHeader();
                }
            } else {
                LogUtils.e(TAG, "mAllFragment is null");
            }
            isNeedShow = false;
        } else {
            if (!isUsingTwoPanes && tabPager != null) {
                tabPager.setVisibility(View.GONE);
            }
        }
    }

    private static boolean showContactsUnavailableView(View contactsUnavailableView,
            ContactsUnavailableFragment contactsUnavailableFragment,
            ProviderStatusWatcher.Status providerStatus) {
        boolean mDestroyed = contactsUnavailableFragment.mDestroyed;
        boolean isNeedShow = false;
        LogUtils.d(TAG, " mContactsUnavailableFragment.mDestroyed : " + mDestroyed
                + " | mProviderStatus.status : " + providerStatus.status
                + " | ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS : "
                + ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS);
        if (providerStatus.status == ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS
                || mDestroyed) {
            contactsUnavailableView.setVisibility(View.GONE);
            isNeedShow = true;
            if (mDestroyed) {
                contactsUnavailableFragment.mDestroyed = false;
            }
        } else {
            contactsUnavailableView.setVisibility(View.VISIBLE);
        }
        return isNeedShow;
    }


    private static int getAvailableStorageCount(Activity activity) {
        int storageCount = 0;
        final StorageManager sm = (StorageManager) activity.getApplicationContext()
            .getSystemService(activity.STORAGE_SERVICE);
        if (null == sm) {
            return 0;
        }

        final IMountService mountService = IMountService.Stub.asInterface(ServiceManager
                .getService("mount"));

        try {
            StorageVolume volumes[] = sm.getVolumeList();
            if (volumes != null) {
                Log.d(TAG, "volumes are " + volumes);
                for (StorageVolume volume : volumes) {
                    LogUtils.d(TAG, "volume is " + volume);
                    if (volume.getPath().startsWith(Environment.DIRECTORY_USBOTG)
                            || !Environment.MEDIA_MOUNTED.equals(mountService.getVolumeState(volume
                                    .getPath()))) {
                        continue;
                    }
                    storageCount++;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return storageCount;
    }

    public static boolean showImportExportMenu(Activity activity) {
        return !((getAvailableStorageCount(activity) == 0)
                && (AccountTypeManager.getInstance(activity)
                .getAccounts(false).size() <= 1));
    }
    
    public static Integer checkSelectedDeleted(Integer result, final ArrayList<ContentProviderOperation> diff,
            ContentProviderResult[] results, final int failed) {
        if (results != null && results.length > 0 && !diff.isEmpty()) {
            // Version asserts failure if there is no contact item.
            if (diff.get(0).getType() == ContentProviderOperation.TYPE_ASSERT
                    && results[0].count != null && results[0].count <= 0) {
                result = failed;
                LogUtils.e(TAG, "[doInBackground]the selected contact has been deleted!");
            }
        }
        return result;
    }
    
    /** Fix CR ALPS00839693,the "Phone" should be translated into Chinese */
    public static void setAccountName(final TextView textView, final AccountWithDataSet account, Activity activity) {
        if (AccountTypeUtils.ACCOUNT_NAME_LOCAL_PHONE.equals(account.name)) {
            textView.setText(activity.getString(R.string.contact_editor_prompt_one_account,
                    activity.getString(R.string.account_phone_only)));
        } else {
            textView.setText(activity.getString(R.string.contact_editor_prompt_one_account, account.name));
        }
        // set ellip size for extra large font size
        textView.setSingleLine();
        textView.setEllipsize(TruncateAt.END);
    }


    /// This function is to customize the account list for differenct account type
    public static void customAccountsList(List<AccountWithDataSet> accountList, Activity activity) {
        if (accountList != null) {
            int type = activity.getIntent().getIntExtra(ContactsSettingsUtils.ACCOUNT_TYPE, 
                    ContactsSettingsUtils.ALL_TYPE_ACCOUNT);
            switch (type) {
                case ContactsSettingsUtils.PHONE_TYPE_ACCOUNT:
                    Iterator<AccountWithDataSet> iterator = accountList.iterator();
                    while (iterator.hasNext()) {
                        AccountWithDataSet data = iterator.next();
                        //Only sim account is AccountWithDataSetEx, so remove any type is AccountWithDataSetEx
                        if (isSimType(data.type)) {
                            iterator.remove();
                        }
                    }
                break;
                default:
                Log.i(TAG, "default all type account");
                break;
            }
        }
    }

    private static boolean isSimType(String type) {
        Log.i(TAG, "type = " + type);
        if (AccountTypeUtils.isAccountTypeIccCard(type)) {
            return true;
        }
        return false;
    }
}

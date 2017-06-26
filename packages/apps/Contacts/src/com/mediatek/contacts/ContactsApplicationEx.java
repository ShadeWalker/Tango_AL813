package com.mediatek.contacts;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsApplication;
import com.android.contacts.common.vcard.VCardService;

import com.mediatek.contacts.list.ContactsGroupMultiPickerFragment;
import com.mediatek.contacts.list.service.MultiChoiceService;

/**
 * An extension of ContactsApplication, init some environment variable and
 * there is two static util method.
 */
public class ContactsApplicationEx {
    private static String TAG = "ContactsApplicationEx";
    private static ContactsApplication sContactsApplication = null;

    /**
     * Extension for ContactsApplication.onCreate().
     * @param contactsApplication ContactsApplication.
     */
    public static void onCreateEx(ContactsApplication contactsApplication) {
        sContactsApplication = contactsApplication;
        ExtensionManager.registerApplicationContext(contactsApplication);
        // retrieve the application context for ContactsCommon
        GlobalEnv.setApplicationContext(contactsApplication);
        // fix ALPS00286964:Remove all contacts notifications
        NotificationManager notificationManager = (NotificationManager) contactsApplication
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    /**
     * When Contacts app is busy, some operations should be forbidden.
     *
     * @return true if there are time consuming operation running, such as:
     *         Multi-delete, import/export, SIMService running, group batch
     *         operation
     */
    public static boolean isContactsApplicationBusy() {
        boolean isMultiDeleting = MultiChoiceService.isProcessing(MultiChoiceService.TYPE_DELETE);
        boolean isMultiCopying = MultiChoiceService.isProcessing(MultiChoiceService.TYPE_COPY);
        boolean isVcardProcessing = VCardService.isProcessing(VCardService.TYPE_IMPORT);
        boolean isGroupMoving = ContactsGroupMultiPickerFragment.isMoveContactsInProcessing();
        boolean isGroupSavingInTransaction = ContactSaveService.isGroupTransactionProcessing();
        Log.d(TAG, "[isContactsApplicationBusy] multi-del: " + isMultiDeleting + ", multi-copy: "
                + isMultiCopying + ", vcard: " + isVcardProcessing + ", group-move: "
                + isGroupMoving + ", group-trans: " + isGroupSavingInTransaction);
        return (isMultiDeleting || isMultiCopying || isVcardProcessing
                || isGroupMoving || isGroupSavingInTransaction);
    }

    /**
     * Get ContactsApplication instance.
     * @return ContactsApplication
     */
    public static ContactsApplication getContactsApplication() {
        return sContactsApplication;
    }
}

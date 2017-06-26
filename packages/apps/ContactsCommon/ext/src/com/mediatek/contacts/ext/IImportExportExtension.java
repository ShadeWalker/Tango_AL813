package com.mediatek.contacts.ext;

import com.android.vcard.VCardComposer;
import com.android.vcard.VCardEntryConstructor;

import android.accounts.Account;
import android.content.Context;

public interface IImportExportExtension {

    /**
     * for op09
     * @param estimatedVCardType
     * @param account
     * @param estimatedCharset
     * @return
     */
    public VCardEntryConstructor getVCardEntryConstructorExt(int estimatedVCardType, Account account, String estimatedCharset);

    /**
     * for op09
     * @param context the context
     * @param vcardType
     * @param careHandlerErrors
     * @return VCardComposer
     */
    public VCardComposer getVCardComposerExt(final Context context, final int vcardType, final boolean careHandlerErrors);
}

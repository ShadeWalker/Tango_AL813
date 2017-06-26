package com.mediatek.contacts.ext;

import com.android.vcard.VCardComposer;
import com.android.vcard.VCardEntryConstructor;

import android.accounts.Account;
import android.content.Context;

public class DefaultImportExportExtension implements IImportExportExtension {
    @Override
    public VCardEntryConstructor getVCardEntryConstructorExt(int estimatedVCardType, Account account, String estimatedCharset) {
        return new VCardEntryConstructor(estimatedVCardType, account, estimatedCharset);
    }

    @Override
    public VCardComposer getVCardComposerExt(final Context context, final int vcardType, final boolean careHandlerErrors) {
        return new VCardComposer(context, vcardType, careHandlerErrors);
    }
}

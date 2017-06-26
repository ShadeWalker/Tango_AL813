package com.mediatek.contacts.ext;

import android.content.ContentResolver;
import android.os.Bundle;

public class DefaultSimServiceExtension implements ISimServiceExtension {
    @Override
    public void importViaReadonlyContact(Bundle bundle, ContentResolver cr) {
        //default do-nothing
    }
}

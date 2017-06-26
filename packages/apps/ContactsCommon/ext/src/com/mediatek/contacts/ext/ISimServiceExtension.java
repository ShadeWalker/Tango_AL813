package com.mediatek.contacts.ext;

import android.content.ContentResolver;
import android.os.Bundle;

public interface ISimServiceExtension {
    /**
     * import read only contact
     * @param bundle The Bundle contains values
     * @param cr the ContentResolver
     */
    public void importViaReadonlyContact(Bundle bundle, ContentResolver cr);
}

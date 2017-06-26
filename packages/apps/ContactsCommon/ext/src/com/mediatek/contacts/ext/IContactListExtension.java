package com.mediatek.contacts.ext;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public interface IContactListExtension {
    /**
     * for OP09
     * @param menu The menu to be add options
     * @param args
     */
    public void addOptionsMenu(Menu menu, Bundle args);

    /**
     *  for OP09,register host context to plugin
     *  @deprecated 
     * @param context Host context
     * @param args
     */
    public void registerHostContext(Context context, Bundle args);
}

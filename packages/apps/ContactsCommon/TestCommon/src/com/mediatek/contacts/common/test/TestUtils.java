package com.mediatek.contacts.common.test;

import android.content.Context;
import android.content.res.Resources;

public class TestUtils {

    /**
     * get the text from target package means get String resource value like
     * R.string.account_phone_only from Contacts package
     *
     * @param targetContext
     *            the test target context
     * @param key
     *            the R.string.key, e.g. "account_phone_only" for
     *            R.string.account_phone_only
     * @return the string text
     */
    public static String getTargetString(Context targetContext, String key) {
        int id = getResId(targetContext, key, "string");
        return targetContext.getResources().getString(id);
    }

    /**
     * get the resId of layout file:
     * R.layout.xxx
     * @param targetContext
     * @param key
     * @return
     */
    public static int getLayoutResId(Context targetContext, String key) {
        return getResId(targetContext, key, "layout");
    }

    public static int getIdResId(Context targetContext, String key) {
        return getResId(targetContext, key, "id");
    }

    /**
     * R.id.xxx: id --> key, xxx -> type
     *
     * @param targetContext
     * @param key
     * @param type
     * @return the id retrieved
     */
    public static int getResId(Context targetContext, String key, String type) {
        Resources res = targetContext.getResources();
        return res.getIdentifier(key, type, targetContext.getPackageName());
    }
}

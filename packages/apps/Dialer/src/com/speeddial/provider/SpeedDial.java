package com.speeddial.provider;


import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Modified by guofeiyao
 */
public class SpeedDial {
    public static final String AUTHORITY = "hq_speed_dial";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);


    public static class Numbers implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://hq_speed_dial/numbers");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/numbers";
        public static final String NUMBER = "number";
    }
}

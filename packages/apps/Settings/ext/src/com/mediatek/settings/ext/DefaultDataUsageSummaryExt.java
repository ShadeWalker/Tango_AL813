package com.mediatek.settings.ext;

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Switch;
import android.widget.TabWidget;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

public class DefaultDataUsageSummaryExt implements IDataUsageSummaryExt {

    public DefaultDataUsageSummaryExt(Context context) {
    }

    public String customizeBackgroundString(String defStr, String tag) {
        return defStr;
    }

    public void customizeTextViewBackgroundResource(int simColor,
        TextView title) {
        return;
    }

    public TabSpec customizeTabInfo(Activity activity, String tag,
        TabSpec tab, TabWidget tabWidget, String title) {
        return tab;
    }


    @Override
    public void customizeMobileDataSummary(View container, View titleView,
        int slotId) {
    }

    @Override
    public void customizeDataConnectionObserver(Activity activity,
            ContentObserver mDataConnectionObserver) {
    }

    @Override
    public void customizeUnregisterDataConnectionObserver(Activity activity,
            ContentObserver mDataConnectionObserver) {
    }

    @Override
    public boolean setDataEnableClickListener(Activity activity, View dataEnabledView,
            Switch dataEnabled, DialogInterface.OnClickListener dataEnabledDialogListerner) {
            return false;
    }

    @Override
    public boolean needToShowDialog() {
            return true;
    }

    @Override
    public boolean setDataEnableClickListener(Activity activity, View dataEnabledView,
            Switch dataEnabled, OnClickListener dataEnabledDialogListerner) {
        return false;
    }

    @Override
    public void resume(Context context, IDataUsage datausage, Map<String, Boolean> mMobileDataEnabled) {
    
	}
	
    @Override
    public void pause(Context context) {
    
	}

}

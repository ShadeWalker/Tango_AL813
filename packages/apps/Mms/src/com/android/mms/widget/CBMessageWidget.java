package com.android.mms.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import com.android.mms.R;
import com.android.mms.util.MmsLog;
import static android.content.Intent.ACTION_BOOT_COMPLETED;
import com.android.internal.telephony.TelephonyIntents;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.android.mms.transaction.CBMessageReceiverService;

public class CBMessageWidget extends AppWidgetProvider {
    /** Called when the activity is first created. */
    private static final String TAG = "Mms/CBMWidget";

    public static final String CB_MESSAGE_WIDGET = "com.android.mms.CB_MESSAGE_WIDGET";
    public static final String CLEAR_WIDGET_VIEW = "com.android.mms.CLEAR_WIDGET_VIEW";
    public static final String CB_MESSAGE_VALUE = "cb_message_txt";
    public static final String CB_MESSAGE_BODY = "cb_message_body";
    public static final String CB_MESSAGE_SIM = "cb_message_sim";
    public static final int CB_MESSAGE_SIM1 = 0;
    public static final int CB_MESSAGE_SIM2 = 1;
//    public static final String CB_MESSAGE_SIM2 = "cb_message_sim2";
    private static  RemoteViews rv;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // TODO Auto-generated method stub
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {

		if(intent.getAction()==null)
			return;
	
	Log.d(TAG, "receive intent" + intent);
        if (rv == null) {
	    Log.d(TAG, "creat a new widget");
            rv = new RemoteViews(context.getPackageName(), R.layout.cb_message_widget);
        }
        if (intent != null && intent.getAction().equals(CB_MESSAGE_WIDGET)) {
            int simID = intent.getIntExtra(CB_MESSAGE_SIM, CB_MESSAGE_SIM1);
            String msg = intent.getStringExtra(CB_MESSAGE_BODY);
            Log.e(TAG, "recive cb message is simID: " + simID);
            if (simID == CB_MESSAGE_SIM1 && msg != null) {
                rv.setTextViewText(R.id.cb_message_sim1, msg);
            } else if (simID == CB_MESSAGE_SIM2 && msg != null) {
                rv.setTextViewText(R.id.cb_message_sim2, msg);
            }
        }
	if(intent != null && ACTION_BOOT_COMPLETED.equals(intent.getAction())){
		Log.e(TAG, "Device reboot,clear widget view");
		if(rv != null){
			rv.setTextViewText(R.id.cb_message_sim1, "");
			rv.setTextViewText(R.id.cb_message_sim2, "");
		}
	}
	if(intent != null && (ACTION_BOOT_COMPLETED.equals(intent.getAction())||CLEAR_WIDGET_VIEW.equals(intent.getAction())
			)){
			Log.e(TAG, "networks type change: " + intent.getAction());
			//if(CBMessageReceiverService.getNetWorkType(context)!= CBMessageReceiverService.NETWORKTYPE_2G){
				Log.e(TAG, "not 2G networks right now!");
				if(rv != null){
				    //rv.setTextViewText(R.id.cb_message_sim1, "");
			    	    //rv.setTextViewText(R.id.cb_message_sim2, "");
				}
			//}
		}
        AppWidgetManager appWidgetManger = AppWidgetManager.getInstance(context);
        int[] appIds = appWidgetManger.getAppWidgetIds(new ComponentName(context,
                CBMessageWidget.class));
        appWidgetManger.updateAppWidget(appIds, rv);
        super.onReceive(context, intent);
    }

}


package com.mediatek.nfc.gsmahandset;

import android.os.Bundle;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.TextView;
import android.util.Log;
import android.view.View;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.nfc.R;

public class NfcControllerConfirmActivity extends AlertActivity {
    private static final String TAG = "NfcControllerConfirmActivity";

    private static final String REQUEST_ENABLE_NFC = "com.gsma.services.nfc.REQUEST_ENABLE_NFC";

    public static final String EXTRA_TITLE = "com.mediatek.nfc.gsmahandset.confirm.title";
    public static final String EXTRA_MESSAGE = "com.mediatek.nfc.gsmahandset.confirm.message";
    public static final String EXTRA_PKGNAME = "com.mediatek.nfc.gsmahandset.packagename";
    public static final String EXTRA_ENABLE = "com.mediatek.nfc.gsmahandset.enable";

    private String mPackagename;
    private boolean isClick = false;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mPackagename = intent.getStringExtra(EXTRA_PKGNAME);

        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_alert;
        p.mMessage = getString(R.string.mtk_gsma_nfc_enable);
        p.mPositiveButtonText = getString(android.R.string.yes);
        p.mNegativeButtonText = getString(android.R.string.no);
        p.mPositiveButtonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                isClick = true;
                Intent intent = new Intent(REQUEST_ENABLE_NFC);
                intent.putExtra(NfcControllerConfirmActivity.EXTRA_PKGNAME, mPackagename);
                Log.d(TAG, "Enable NFC");
                intent.putExtra(NfcControllerConfirmActivity.EXTRA_ENABLE, "YES");
                
			    sendBroadcast(intent);                
                finish();
            }
        };
        p.mNegativeButtonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                isClick = true;
                Intent intent = new Intent(REQUEST_ENABLE_NFC);
                intent.putExtra(NfcControllerConfirmActivity.EXTRA_PKGNAME, mPackagename);
                Log.d(TAG, "Don't enable NFC");
                intent.putExtra(NfcControllerConfirmActivity.EXTRA_ENABLE, "NO");
                
			    sendBroadcast(intent);                
                finish();
            }
        }; 

        setupAlert();
        
        TextView mMessageTextView = (TextView) getWindow().findViewById(android.R.id.message);
        mMessageTextView.setTextDirection(TextView.TEXT_DIRECTION_LOCALE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (isClick == false) {
            Intent intent = new Intent(REQUEST_ENABLE_NFC);
            intent.putExtra(NfcControllerConfirmActivity.EXTRA_PKGNAME, mPackagename);
            intent.putExtra(NfcControllerConfirmActivity.EXTRA_ENABLE, "NO");        
            sendBroadcast(intent);
        }
        super.onDestroy();
    }    
}

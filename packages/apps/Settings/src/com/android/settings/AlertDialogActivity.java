
package com.android.settings;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.widget.CheckBox;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.app.Dialog;
import android.provider.Settings;
import android.content.Intent;

public class AlertDialogActivity extends Activity {

    private AlertDialog mAlertDialog;
    private CheckBox mCheckbox;
    private TextView mTipText;
    private Intent mIntent;
    private String mMcc;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIntent = getIntent();
        mMcc = mIntent.getStringExtra("mcc");
        AlertDialog.Builder builder = new  AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.accept_button, new AcceptButtonListener());
        mAlertDialog = builder.create();
        View  dialogView = mAlertDialog.getLayoutInflater().inflate(R.layout.data_cost_tip_dialog, null);
        mCheckbox = (CheckBox) dialogView.findViewById(R.id.remind);
        mTipText = (TextView) dialogView.findViewById(R.id.data_cost_tip);
        if (mMcc != null) {
            if (mMcc.equals("740")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"https://m.miclaro.com.ec\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("714")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://m.mobilewebsiteserver.com/site/claropanama/precio-y-promociones-im\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("330")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://contenidos.clarotodo.com/wps/portal/pr/pc/personas/movil/banda_ancha_4g/pr.personas.movil.banda_ancha_4g.planes/paquete-internet-movil\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("370")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.claro.com.do/wps/portal/do/sc/personas/movil/planes-de-voz-datos\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("716")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.internetclaro.com.pe\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("722")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.claro.com.ar/wps/portal/ar/pc/personas/internet/pospago#info-02\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("730")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://miportal.clarochile.cl\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("712")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://internet.claro.cr/\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("706")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"https://internet.claro.com.sv/\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("704")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"https://internet.claro.com.gt/\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("708")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"https://internet.claro.com.hn/\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("710")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"https://internet.claro.com.ni/\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("744")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.claro.com.py/packsdeinternet\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("748")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.claro.com.uy/packsdeinternet\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else if (mMcc.equals("334")) {
                mTipText.setText(Html.fromHtml(getString(R.string.data_cost_tip_start) + " " + "<a href=\"http://www.internet.telcel.com\">" + getString(R.string.data_cost_tip_link) + "</a>" + getString(R.string.data_cost_tip_end)));
            } else {
                mTipText.setText(getString(R.string.data_cost_tip_full));
            }
        } else {
            mTipText.setText(getString(R.string.data_cost_tip_full));
        }
        mTipText.setMovementMethod(LinkMovementMethod.getInstance());
        mAlertDialog.setView(dialogView);
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.setCancelable(false);
        mAlertDialog.show();
    }

    private class AcceptButtonListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            Log.d("AlertDialogActivity", "in onClick");
            Settings.System.putInt(getContentResolver(), Settings.System.DATA_COST_TIP_SWITCH, mCheckbox.isChecked() ? 0 : 1);
            mAlertDialog.dismiss();
            finish();
        }
    }
}

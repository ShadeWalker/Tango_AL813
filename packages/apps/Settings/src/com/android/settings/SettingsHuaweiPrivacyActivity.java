/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Configuration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 * The "dialog" that shows from "License" in the Settings app.
 */
public class SettingsHuaweiPrivacyActivity extends Activity {

    private static final String TAG = "SettingsLicenseActivity";
    private static final boolean LOGV = false || false;

    private Handler mHandler;
    private WebView mWebView;
    private ProgressDialog mSpinnerDlg;
    private AlertDialog mTextDlg;

    private class LicenseFileLoader implements Runnable {

        private static final String INNER_TAG = "SettingsHuaweiPrivacyActivity.PrivacyFileLoader";
        public static final int STATUS_OK = 0;
        public static final int STATUS_NOT_FOUND = 1;
        public static final int STATUS_READ_ERROR = 2;
        public static final int STATUS_EMPTY_FILE = 3;


        private Handler mHandler;

        public LicenseFileLoader(Handler handler) {
            mHandler = handler;
        }

        public void run() {

            int status = STATUS_OK;

            InputStreamReader inputReader = null;
            StringBuilder data = new StringBuilder(2048);
            try {
                char[] tmp = new char[2048];
                int numRead;
                inputReader = new InputStreamReader(getResources().openRawResource(R.raw.huawei_privacy_policy));
                while ((numRead = inputReader.read(tmp)) >= 0) {
                    data.append(tmp, 0, numRead);
                }
            } catch (FileNotFoundException e) {
                Log.e(INNER_TAG, "License HTML file not found", e);
                status = STATUS_NOT_FOUND;
            } catch (IOException e) {
                Log.e(INNER_TAG, "Error reading license HTML file ", e);
                status = STATUS_READ_ERROR;
            } finally {
                try {
                    if (inputReader != null) {
                        inputReader.close();
                    }
                } catch (IOException e) {
                }
            }

            if ((status == STATUS_OK) && TextUtils.isEmpty(data)) {
                Log.e(INNER_TAG, "License HTML is empty!");
                status = STATUS_EMPTY_FILE;
            }

            // Tell the UI thread that we are finished.
            Message msg = mHandler.obtainMessage(status, null);
            if (status == STATUS_OK) {
                msg.obj = data.toString();
            }
            mHandler.sendMessage(msg);
        }
    }

    public SettingsHuaweiPrivacyActivity() {
        super();
        mHandler = null;
        mWebView = null;
        mSpinnerDlg = null;
        mTextDlg = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CookieSyncManager.createInstance(this);

        // The activity does not have any view itself,
        // so set it invisible to avoid displaying the title text in the background.
        setVisible(false);

        mWebView = new WebView(this);
        /**M: disable to over scroll @{ */
        mWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        /*@}*/
        
        // Configure the webview
        WebSettings s = mWebView.getSettings();
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        s.setUseWideViewPort(false);
        s.setSavePassword(false);
        s.setSaveFormData(false);
        s.setBlockNetworkLoads(true);

        mWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                    return true;
            }
        });

        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == LicenseFileLoader.STATUS_OK) {
                    String text = (String) msg.obj;
                    showPageOfText(text);
                } else {
                    showErrorAndFinish();
                }
            }
        };

        CharSequence title = getText(R.string.settings_huawei_privacy_activity_title);
        CharSequence msg = getText(R.string.settings_huawei_privacy_activity_loading);

        ProgressDialog pd = ProgressDialog.show(this, title, msg, true, false);
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mSpinnerDlg = pd;

        // Start separate thread to do the actual loading.
        Thread thread = new Thread(new LicenseFileLoader(mHandler));
        thread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CookieSyncManager.getInstance().startSync(); 
    }
    
    @Override
    protected void onStop() {
        super.onStop();        
        CookieSyncManager.getInstance().stopSync(); 
        mWebView.stopLoading();       
    }

    @Override
    protected void onDestroy() {
        if (mTextDlg != null && mTextDlg.isShowing()) {
            mTextDlg.dismiss();
        }
        if (mSpinnerDlg != null && mSpinnerDlg.isShowing()) {
            mSpinnerDlg.dismiss();
        }
        mWebView.freeMemory();
        mWebView.destroy();
        super.onDestroy();
    }

    private void showPageOfText(String text) {
        mWebView.loadDataWithBaseURL(null, text, "text/html", "utf-8", null);
        mWebView.setWebChromeClient(new WebChrome());
    }

    class WebChrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            //getWindow().setFeatureInt(Window.FEATURE_PROGRESS, newProgress*100);
            Log.d(TAG, "onProgressChanged " + newProgress);
            if (newProgress == 100) {
                if(mSpinnerDlg != null) {
                    mSpinnerDlg.dismiss();
                }
                CookieSyncManager.getInstance().sync();
                setContentView(mWebView);
            }
        }
    }

    private void showErrorAndFinish() {
        mSpinnerDlg.dismiss();
        mSpinnerDlg = null;
        Toast.makeText(this, R.string.settings_huawei_privacy_activity_unavailable, Toast.LENGTH_LONG)
                .show();
        finish();
    }
}

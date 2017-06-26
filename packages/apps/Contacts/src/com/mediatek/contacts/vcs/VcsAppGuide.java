package com.mediatek.contacts.vcs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import com.android.contacts.R;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class VcsAppGuide {
    private static final String TAG = "SwitchSimContactsExt";

    private static final String SHARED_PREFERENCE_NAME = "application_guide";
    private static final String KEY_VCS_GUIDE = "vcs_guide";
    private SharedPreferences mSharedPrefs;
    private Context mContext;
    private Dialog mAppGuideDialog;
    private OnGuideFinishListener mFinishListener;

    public VcsAppGuide(Context context) {
        mContext = context;
    }

    /**
     * Called when the app want to show VCS application guide
     *
     * @param activity
     *            The parent activity
     * @param commd
     *            The commd fotrwhich Plugin Implements will run
     */
    public boolean setVcsAppGuideVisibility(Activity activity, boolean isShow,
            OnGuideFinishListener onFinishListener) {
            
        // /added & annotated by guofeiyao for avoid showing the VCS guide
        return false;
        
		/*	
        if (isShow) {
            mFinishListener = onFinishListener;
            mSharedPrefs = activity.getSharedPreferences(SHARED_PREFERENCE_NAME,
                    Context.MODE_WORLD_WRITEABLE);
            if (mSharedPrefs.getBoolean(KEY_VCS_GUIDE, false)) {
                Log.d(TAG, "already show VCS guide, return");
                return false;
            }
            Log.d(TAG, "showVcsAppGuide");
            if (mAppGuideDialog == null) {
                Log.d(TAG, "mAppGuideDialog == null");
                mAppGuideDialog = new AppGuideDialog(activity);
                mAppGuideDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
            }
            mAppGuideDialog.show();
            return true;
        } else {
            dismissVcsAppGuide();
            return false;
        }
        */

		// /end
    }

    private void dismissVcsAppGuide() {
        if (mAppGuideDialog != null) {
            Log.d(TAG, "dismissVcsAppGuide");
            mAppGuideDialog.dismiss();
            mAppGuideDialog = null;
        }
    }

    private void onGuideFinish() {
        if (mFinishListener != null) {
            Log.d(TAG, "onGuideFinish");
            mFinishListener.onGuideFinish();
        }
    }

    class AppGuideDialog extends Dialog {

        private Activity mActivity;
        private Button mOkBtn;

        /**
         * ok button listner, finish app guide.
         */
        private View.OnClickListener mOkListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSharedPrefs.edit().putBoolean(KEY_VCS_GUIDE, true).commit();
                onGuideFinish();
                onBackPressed();
            }
        };

        public AppGuideDialog(Activity activity) {
            super(activity, android.R.style.Theme_Translucent_NoTitleBar);
            mActivity = activity;
        }

        @Override
        public void onBackPressed() {
            dismissVcsAppGuide();
            onGuideFinish();
            super.onBackPressed();
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // PluginLayoutInflater inflater = new
            // PluginLayoutInflater(mContext);
            LayoutInflater inflater = LayoutInflater.from(mContext);
            View view = inflater.inflate(R.layout.vcs_guide_full_bg_layout, null);
            mOkBtn = (Button) view.findViewById(R.id.vcs_ok_btn);
            mOkBtn.setText(android.R.string.ok);
            mOkBtn.setOnClickListener(mOkListener);
            setContentView(view);
        }
    }

    public static interface OnGuideFinishListener {
        void onGuideFinish();
    }
}

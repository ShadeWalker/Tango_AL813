package com.mediatek.incallui.volte;

import android.content.Context;
import android.content.Intent;

import com.android.incallui.Log;

public class AddMemberScreenController {

    private static final String LOG_TAG = "AddMemberScreenController";
    /**
     * This is the max caller count of the conference, including the host.
     */
    public static final int MAX_CALLERS_IN_CONFERENCE = 6;
    private static AddMemberScreenController sInstance = new AddMemberScreenController();
    private AddMemberScreen mAddMemberDialog;
    private String mConferenceCallId;

    public static synchronized AddMemberScreenController getInstance() {
        if (sInstance == null) {
            sInstance = new AddMemberScreenController();
        }
        return sInstance;
    }

    public void showAddMemberDialog(Context context) {
        Log.d(this, "showAddMemberDialog...");
        Intent intent = new Intent(context, AddMemberScreen.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public void setAddMemberScreen(AddMemberScreen screen) {
        // If there has one "Dialog" already, dismiss it first. quick-click may cause this.
        if (mAddMemberDialog != null) {
            mAddMemberDialog.finish();
        }
        mAddMemberDialog = screen;
    }

    public void clearAddMemberScreen() {
        if (mAddMemberDialog != null && mAddMemberDialog.isFinishing()) {
            mAddMemberDialog = null;
        }
    }

    public void updateConferenceCallId(String conferenceCallId) {
        mConferenceCallId = conferenceCallId;
    }

    public void dismissAddMemberDialog() {
        Log.d(this, "dismissAddMemberDialog...");
        if (mAddMemberDialog != null) {
            mAddMemberDialog.finish();
            mAddMemberDialog = null;
        }
    }

    public String getConferenceCallId() {
        return mConferenceCallId;
    }
}

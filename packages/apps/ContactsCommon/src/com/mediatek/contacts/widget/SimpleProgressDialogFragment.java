package com.mediatek.contacts.widget;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.contacts.common.R;
import com.mediatek.contacts.util.LogUtils;

public class SimpleProgressDialogFragment extends DialogFragment {
    private static String TAG = "SimpleProgressDialogFragment";

    private static final String DIALOG_TAG = "progress_dialog";

    private static SimpleProgressDialogFragment getInstance(FragmentManager fm) {
        SimpleProgressDialogFragment dialog = getExistDialogFragment(fm);
        if (dialog == null) {
            dialog = new SimpleProgressDialogFragment();
            LogUtils.i(TAG, "[getInstance]create new dialog " + dialog + " in " + fm);
        }
        return dialog;
    }

    private static SimpleProgressDialogFragment getExistDialogFragment(FragmentManager fm) {
        return (SimpleProgressDialogFragment) fm.findFragmentByTag(DIALOG_TAG);
    }

    public static void show(FragmentManager fm) {
        LogUtils.d(TAG, "[show]show dialog for " + fm);
        SimpleProgressDialogFragment dialog = getInstance(fm);
        if (dialog.isAdded()) {
            LogUtils.d(TAG, "[show]dialog is already shown: " + dialog);
        } else {
            LogUtils.d(TAG, "[show]dialog created and shown: " + dialog);
            dialog.show(fm, DIALOG_TAG);
            dialog.setCancelable(false);
        }
    }

    public static void dismiss(FragmentManager fm) {
        LogUtils.d(TAG, "[dismiss]dismiss dialog for " + fm);

        if (fm == null) {
            return;
        }

        SimpleProgressDialogFragment dialog = getExistDialogFragment(fm);
        if (dialog == null) {
            LogUtils.d(TAG, "dialog never shown before, no need dismiss");
            return;
        }
        if (dialog.isAdded()) {
            LogUtils.d(TAG, "force dismiss dialog: " + dialog);
            dialog.dismissAllowingStateLoss();
        } else {
            LogUtils.d(TAG, "dialog not added, dismiss failed: " + dialog);
        }
    }

    public SimpleProgressDialogFragment() {
    }

    @Override
    public String toString() {
        return String.valueOf(hashCode());
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setMessage(getActivity().getString(R.string.please_wait));
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
}

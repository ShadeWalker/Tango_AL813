package com.mediatek.galleryframework.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.gallery3d.R;

/** 
transparent progress dialog, alreay transparent, but not yet finished
 */
public class ProgressFragment extends DialogFragment {
    private int mTitleID = -1;
    private String mTitle = null; 
    
    public ProgressFragment(int titleID) {
        mTitleID = titleID;
    }
    
    public ProgressFragment(String title) {
        mTitle = title;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Window wind = getDialog().getWindow();
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        wind.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View view = inflater.inflate(R.layout.video_generating_progress, container);
        TextView rotateDialogText = (TextView) (view.findViewById(R.id.rotate_dialog_text));
        if (mTitleID != -1) {
            rotateDialogText.setText(mTitleID);
        } else {
            rotateDialogText.setText(mTitle);
        }

        return view;
    }
}
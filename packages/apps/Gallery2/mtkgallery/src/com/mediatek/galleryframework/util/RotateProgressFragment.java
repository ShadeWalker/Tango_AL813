package com.mediatek.galleryframework.util;

import android.app.DialogFragment;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import com.android.gallery3d.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

/**
transparent progress dialog, alreay transparent, but not yet finished
 */
public class RotateProgressFragment extends DialogFragment {
    private String mTitle;
    /// M: fix fragment InstantiationException: no empty constructor. @{
    private final static String TITLE = "title";
    /*
    public RotateProgressFragment(String title) {
        mTitle = title;
    }
    */
    public static RotateProgressFragment newInstance(String title) {
        RotateProgressFragment dialog = new RotateProgressFragment();
        Bundle args = new Bundle();
        args.putString(TITLE, title);
        dialog.setArguments(args);
        return dialog;
    }
    /// @}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        /// M: fix fragment InstantiationException: no empty constructor. @{
        mTitle = getArguments().getString(TITLE);
        /// @}
        Window wind = getDialog().getWindow();
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);

        wind.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LayoutInflater flater = LayoutInflater.from(wind.getContext());
        View view = (View) flater.inflate(R.layout.m_video_generating_progress,
                container);
        TextView rotateDialogText = (TextView) view.findViewById(
                R.id.m_rotate_dialog_text);
        rotateDialogText.setText(mTitle);

        return view;
    }
}
package com.mediatek.galleryfeature.animshare;

import android.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Bundle;
import com.mediatek.galleryframework.util.MtkLog;

public class SelectDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener, OnMultiChoiceClickListener {

    private static final String TAG = "MtkGallery2/SelectDialogFragment";
    private static final String KEY_ITEM_ARRAY = "itemArray";
    private static final String KEY_SUFFIX_ARRAY = "suffixArray";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DEFAULT_SELECT = "nowSelect";
    private static final String KEY_DEFAULT_SELECTARRAY = "nowSelectArray";
    private static final String KEY_SINGLE_CHOICE = "singleChoice";
    private DialogInterface.OnClickListener mClickListener = null;
    private DialogInterface.OnMultiChoiceClickListener mMultiChoiceClickListener = null;

    /**
     * M: create a instance of SelectDialogFragment
     *
     * @param itemArrayID
     *            the resource id array of strings that show in list
     * @param sufffixArray
     *            the suffix array at the right of list item
     * @param titleID
     *            the resource id of title string
     * @param nowSelect
     *            the current select item index
     * @return the instance of SelectDialogFragment
     */
    public static SelectDialogFragment newInstance(String[] itemArray, CharSequence[] sufffixArray,
            String title, boolean singleChoice, int nowSelect, boolean[] nowSelectArray) {
        SelectDialogFragment frag = new SelectDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray(KEY_ITEM_ARRAY, itemArray);
        args.putCharSequenceArray(KEY_SUFFIX_ARRAY, sufffixArray);
        args.putString(KEY_TITLE, title);
        args.putBoolean(KEY_SINGLE_CHOICE, singleChoice);
        if (singleChoice) {
            args.putInt(KEY_DEFAULT_SELECT, nowSelect);
        } else {
            args.putBooleanArray(KEY_DEFAULT_SELECTARRAY, nowSelectArray.clone());
        }
        frag.setArguments(args);
        return frag;
    }

    @Override
    /**
     * M: create a select dialog
     */
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MtkLog.i(TAG, "<onCreateDialog>");
        Bundle args = getArguments();
        final String title = args.getString(KEY_TITLE);
        final String[] itemArray = args.getStringArray(KEY_ITEM_ARRAY);
        int arraySize = itemArray.length;
        CharSequence[] itemArrayRes = new CharSequence[arraySize];
        CharSequence[] suffixArray = args.getCharSequenceArray(KEY_SUFFIX_ARRAY);
        if (null == suffixArray) {
            for (int i = 0; i < arraySize; i++) {
                itemArrayRes[i] = itemArray[i];
            }
        } else {
            for (int i = 0; i < arraySize; i++) {
                itemArrayRes[i] = itemArray[i] + suffixArray[i];
            }
        }

        final boolean singleChoice = args.getBoolean(KEY_SINGLE_CHOICE);
        AlertDialog.Builder builder = null;
        if (singleChoice) {
            int nowSelect = args.getInt(KEY_DEFAULT_SELECT);
            builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(title).setSingleChoiceItems(itemArrayRes, nowSelect, this)
                    .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(
                            getString(R.string.ok), this);
        } else {
            boolean[] nowSelectArray = args.getBooleanArray(KEY_DEFAULT_SELECTARRAY);
            builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(title).setMultiChoiceItems(itemArrayRes, nowSelectArray, this)
                    .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(
                            getString(R.string.ok), this);
        }
        return builder.create();
    }

    @Override
    /**
     * M: the process of select an item
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (null != mClickListener) {
            mClickListener.onClick(arg0, arg1);
        }
    }

    @Override
    public void onClick(DialogInterface arg0, int arg1, boolean arg2) {
        if (null != mMultiChoiceClickListener) {
            mMultiChoiceClickListener.onClick(arg0, arg1, arg2);
        }
    }

    /**
     * M: set listener of click items
     *
     * @param listener
     *            the listener to be set
     */
    public void setOnClickListener(DialogInterface.OnClickListener listener) {
        mClickListener = listener;
    }

    public void setOnMultiChoiceListener(DialogInterface.OnMultiChoiceClickListener listener) {
        mMultiChoiceClickListener = listener;
    }
}
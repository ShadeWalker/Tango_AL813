/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2013. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.gallery3d.video;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.mediatek.galleryframework.util.MtkLog;

import com.android.gallery3d.R;

/**
 * This class is used to set the color,font an so on for text subtile
 * @author MTK40641
 *
 */
public class SubtitleSettingDialog extends AlertDialog implements
        DialogInterface.OnClickListener {
    private static final String TAG = "Gallery3d/SubtitleSettingDialog";
    private Context mContext;
    private SubTitleView mSubTitleView;
    private View mView;
    private TextView mTitleView;
    private TabHost mTabHost;

    private static final int FONT_COLOR_WHITE = 4;
    private static final int TEXT_GRAVITY_CENTER = 1;

    public SubtitleSettingDialog(Context context, SubTitleView subTitleView) {
        super(context);
        mContext = context;
        mSubTitleView = subTitleView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MtkLog.v(TAG, "SubtitleSettingDialog  oncreate");
        mView = getLayoutInflater().inflate(R.layout.m_subtitle_setting_dialog,
                null);
        if (mView != null) {
            setView(mView);
        }
        TabWidget tabWidget;
        mTabHost = (TabHost) mView.findViewById(R.id.tabhost01);
        mTabHost.setup();
        LayoutInflater inflater_tab = LayoutInflater.from(mContext);
        inflater_tab.inflate(R.layout.m_subtitle_setting_text, mTabHost
                .getTabContentView());
        getLayoutInflater().inflate(R.layout.m_subtitle_setting_layout,
                mTabHost.getTabContentView());

        mTabHost.addTab(mTabHost.newTabSpec("tabb4Text").setIndicator(
                mContext.getString(R.string.SubtitleSetting_text)).setContent(
                R.id.textsetting));
        mTabHost.addTab(mTabHost.newTabSpec("tabb4Layout").setIndicator(
                mContext.getString(R.string.SubtitleSetting_layout))
                .setContent(R.id.layoutsetting));

        // set tab Title
        tabWidget = mTabHost.getTabWidget();
        int count = tabWidget.getChildCount();
        for (int i = 0; i < count; i++) {
            TextView tv = (TextView) tabWidget.getChildAt(i).findViewById(
                    android.R.id.title);
            tv.setTextSize(20);
        }

        initLayoutSettingAlign(mTabHost.getTabContentView());
        initLayoutSettingMargins(mTabHost.getTabContentView());
        initTextSettingFont(mTabHost.getTabContentView());
        initTextSettingSize(mTabHost.getTabContentView());
        initTextSettingColor(mTabHost.getTabContentView());
        initTextSettingEncoding(mTabHost.getTabContentView());

        // setOnDismissListener(this);
        super.onCreate(savedInstanceState);
    }

    private void initLayoutSettingAlign(View view) {
        Spinner spAlign = (Spinner) view.findViewById(R.id.alignment_type);
        ArrayAdapter<CharSequence> adapterAlign = ArrayAdapter
                .createFromResource(mContext, R.array.alignment,
                        android.R.layout.simple_spinner_item);
        adapterAlign.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAlign.setAdapter(adapterAlign);
        spAlign.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (null == mSubTitleView) {
                    return;
                }
                mSubTitleView.setTextGravity(id);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        if (null != mSubTitleView) {
            spAlign.setSelection((int) mSubTitleView.getTextGravity());
        } else {
            spAlign.setSelection(TEXT_GRAVITY_CENTER);
        }

    }

    //
    private void initLayoutSettingMargins(View view) {
        SeekBar sbMargins = (SeekBar) view.findViewById(R.id.bottom_margins_seek);
        final TextView textViewFontSize = (TextView) view.findViewById(R.id.bottom_margins_num);
        sbMargins.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromTouch) {
                if (mSubTitleView != null) {
                    mSubTitleView.setTextTranslationY(progress);
                }
                textViewFontSize.setText(" " + progress);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        if (mSubTitleView != null) {
            textViewFontSize.setText(" " + mSubTitleView.getTextTranslationY());
            sbMargins.setProgress(mSubTitleView.getTextTranslationY());
        } else {
            textViewFontSize.setText("0");
        }
    }

    //
    private void initTextSettingFont(View view) {
        Spinner spFont = (Spinner) view.findViewById(R.id.font_type);
        ArrayAdapter<CharSequence> adapterFont = ArrayAdapter
                .createFromResource(mContext, R.array.text_font,
                        android.R.layout.simple_spinner_item);
        adapterFont.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFont.setAdapter(adapterFont);
        spFont.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (null == mSubTitleView) {
                    return;
                }
                mSubTitleView.setTextFont(id);
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        if (null != mSubTitleView) {
            spFont.setSelection((int) mSubTitleView.getTextFont());
        } else {
            spFont.setSelection(0);
        }
    }

    //
    private void initTextSettingSize(View view) {
        SeekBar sbSize = (SeekBar) view.findViewById(R.id.text_size_seek);
        final TextView textViewFontSize = (TextView) view
                .findViewById(R.id.text_size_disp_num);
        sbSize.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromTouch) {
                if (mSubTitleView != null) {
                    mSubTitleView.setTextSizeSet(progress);
                }
                textViewFontSize.setText(" " + progress);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }

        });
        if (mSubTitleView != null) {
            textViewFontSize.setText(" " + mSubTitleView.getTextSizeSet());
            sbSize.setProgress(mSubTitleView.getTextSizeSet());
        } else {
            textViewFontSize.setText("25");
        }
    }

    //
    private void initTextSettingColor(View view) {
        Spinner spColor = (Spinner) view.findViewById(R.id.color_type);
        ArrayAdapter<CharSequence> adapterColor = ArrayAdapter
                .createFromResource(mContext, R.array.text_color,
                        android.R.layout.simple_spinner_item);
        adapterColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spColor.setAdapter(adapterColor);
        spColor.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (null == mSubTitleView) {
                    return;
                }
                MtkLog.v(TAG,
                        "SubtitleSettingDialog.initTextSettingColor  id = "
                                + id);
                mSubTitleView.setTextColorSelect(id);
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        if (null != mSubTitleView) {
            spColor.setSelection((int) mSubTitleView.getTextColorSelect());
        } else {
            spColor.setSelection(FONT_COLOR_WHITE);
        }
    }

    private void initTextSettingEncoding(View view) {
        Spinner spEncoding = (Spinner) view.findViewById(R.id.encoding_type);
        ArrayAdapter<CharSequence> adapterEncoding = ArrayAdapter
                .createFromResource(mContext, R.array.text_encoding,
                        android.R.layout.simple_spinner_item);
        adapterEncoding.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEncoding.setAdapter(adapterEncoding);
        spEncoding.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (null == mSubTitleView) {
                    return;
                }
                mSubTitleView.setTextEncodingSelect(id);
            }

            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        if (null != mSubTitleView) {
            spEncoding.setSelection((int) mSubTitleView
                    .getTextEncodingSelectIdx());
        } else {
            spEncoding.setSelection(0);
        }

    }

    public void onClick(DialogInterface dialogInterface, int button) {

    }

}

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


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.media.TimedText;
import android.widget.TextView;
import android.widget.ImageView;

import com.mediatek.media.TimedTextEx;
import com.mediatek.galleryframework.util.MtkLog;

/**
 * the class is used for subtitle view ,
 *
 * it supply the method to set the text cntent, type, size, font and so on.
 *
 * @author MTK40641
 *
 */
public class SubTitleView extends TextView {

    private static final String TAG = "Gallery3D/SubtitleView";
    private int mTextSize = 30;
    private float mTextAlpha;
    private long mTextGravity = TEXT_GRAVITY_CENTER;
    private int mTextTranslationY = 0;
    private long mTextFonttypes = FONT_TYPE_DEFAULT;
    private long mTextColortypes = FONT_COLOR_WHITE;
    private Rect mTarget;

    private ImageView mSubTitleViewBm;

    private String mEncodingSelect = null;
    private long mEncodingSelectIdx = 0;
    private Context mContext;
    //font type
    private static final int FONT_TYPE_DEFAULT = 0;
    private static final int FONT_TYPE_MONO = 1;
    private static final int FONT_TYPE_SANS_SERIF = 2;
    private static final int FONT_TYPE_SERIF = 3;
    //for TextGravity
    private static final int TEXT_GRAVITY_LEFT = 0;
    private static final int TEXT_GRAVITY_CENTER = 1;
    private static final int TEXT_GRAVITY_RIGHT = 2;
    //font color
    private static final int FONT_COLOR_BLACK = 0;
    private static final int FONT_COLOR_DKGRAY = 1;
    private static final int FONT_COLOR_GRAY = 2;
    private static final int FONT_COLOR_LTGRAY = 3;
    private static final int FONT_COLOR_WHITE = 4;
    private static final int FONT_COLOR_RED = 5;
    private static final int FONT_COLOR_GREEN = 6;
    private static final int FONT_COLOR_BLUE = 7;
    private static final int FONT_COLOR_YELLOW = 8;
    private static final int FONT_COLOR_CYAN = 9;
    private static final int FONT_COLOR_MAGENTA = 10;
    private static final int FONT_COLOR_TRANSPARENT = 11;
    //font encoding type
    private static final String FONT_ENCODE_DEFAULT = "";
    private static final String FONT_ENCODE_ARABIC_ISO8859_6 = "ISO-8859-6";
    private static final String FONT_ENCODE_ARABIC_CP1256 = "Cp1256";
    private static final String FONT_ENCODE_BALTIC_CP1257 = "Cp1257";
    private static final String FONT_ENCODE_BALTIC_ISO8859_13 = "ISO-8859-13";
    private static final String FONT_ENCODE_BALTIC_SCANDINAVIA_ISO8859_4 = "ISO-8859-4";
    private static final String FONT_ENCODE_CELTIC_ISO8859_14 = "ISO-8859-14";
    private static final String FONT_ENCODE_CENTRAL_EUROPEAN_ISO8859_2 = "ISO-8859-2";
    private static final String FONT_ENCODE_CHINESE_SIMPLIFIED_MS936 = "MS936";
    private static final String FONT_ENCODE_CHINESE_SIMPLIFIED_GB18030 = "GB18030";
    private static final String FONT_ENCODE_CHINESE_SIMPLIFIED_EUC_CN = "EUC_CN";
    private static final String FONT_ENCODE_CHINESE_SIMPLIFIED_GBK = "GBK";
    private static final String FONT_ENCODE_CHINESE_SIMPLIFIED_ISO2022_CN = "ISO-2022-CN";
    private static final String FONT_ENCODE_CHINESE_TRADITIONAL_BIG5 = "BIG5";
    private static final String FONT_ENCODE_CHINESE_TRADITIONAL_HONGKONG = "BIG5-HKSCS";
    private static final String FONT_ENCODE_CYRILLIC_CP1251 = "Cp1251";
    private static final String FONT_ENCODE_CYRILLIC_ISO8859_5 = "ISO-8859-5";
    private static final String FONT_ENCODE_EASTERN_EUROPEAN_CP1250 = "Cp1250";
    private static final String FONT_ENCODE_GREEK_ISO8859_7 = "ISO-8859-7";
    private static final String FONT_ENCODE_HEBREW_ISO8859_8 = "ISO-8859-8";
    private static final String FONT_ENCODE_HEBREW_CP1255 = "Cp1255";
    private static final String FONT_ENCODE_JAPANESE_MS932 = "MS932";
    private static final String FONT_ENCODE_JAPANESE_ECU_JP = "ECU_JP";
    private static final String FONT_ENCODE_JAPANESE_SHIFT_JIS = "Shift_JIS";
    private static final String FONT_ENCODE_JAPANESE_ISO2022_JP = "ISO-2022-JP";
    private static final String FONT_ENCODE_KOREAN_MS949 = "MS949";
    private static final String FONT_ENCODE_KOREAN_ECU_KR = "ECU_KR";
    private static final String FONT_ENCODE_KOREAN_ISO_2022_KR = "ISO-2022-KR";
    private static final String FONT_ENCODE_NORDIC_ISO8859_10 = "ISO-8859-10";
    private static final String FONT_ENCODE_ROMANIAN_ISO8859_16 = "ISO-8859-16";
    private static final String FONT_ENCODE_RUSSIAN_KOI8_R = "KOI8-R";
    private static final String FONT_ENCODE_SOUTH_EUROPEAN_ISO8859_3 = "ISO-8859-3";
    private static final String FONT_ENCODE_THAI_TIS_620 = "TIS-620";
    private static final String FONT_ENCODE_THAI_ISO8859_11 = "ISO-8859-11";
    private static final String FONT_ENCODE_TURKISH_CP1254 = "Cp1254";
    private static final String FONT_ENCODE_TURKISH_ISO8859_9 = "ISO-8859-9";
    private static final String FONT_ENCODE_UNICODE_UTF_8 = "UTF-8";
    private static final String FONT_ENCODE_UNICODE_UTF_16 = "UTF-16";
    private static final String FONT_ENCODE_UNICODE_UTF_16BE = "UTF-16BE";
    private static final String FONT_ENCODE_UNICODE_UTF_32 = "UTF-32";
    private static final String FONT_ENCODE_UNICODE_UTF_32BE = "UTF-32BE";
    private static final String FONT_ENCODE_UNICODE_UTF_32LE = "UTF-32LE";
    private static final String FONT_ENCODE_US_ASCII = "US-ASCII";
    private static final String FONT_ENCODE_VIETNAMESE_SP1258 = "Cp1258";
    private static final String FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_1 = "ISO-8859-1";
    private static final String FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_15 = "ISO-8859-15";
    private static final String FONT_ENCODE_WESTERN_EUROPEAN_CP1252 = "Cp1252";

    //font encoding type id index
    private static final int FONT_ENCODE_DEFAULT_IDX = 0;
    private static final int FONT_ENCODE_ARABIC_ISO8859_6_IDX = 1;
    private static final int FONT_ENCODE_ARABIC_CP1256_IDX = 2;
    private static final int FONT_ENCODE_BALTIC_CP1257_IDX = 3;
    private static final int FONT_ENCODE_BALTIC_ISO8859_13_IDX = 4;
    private static final int FONT_ENCODE_BALTIC_SCANDINAVIA_ISO8859_4_IDX = 5;
    private static final int FONT_ENCODE_CELTIC_ISO8859_14_IDX = 6;
    private static final int FONT_ENCODE_CENTRAL_EUROPEAN_ISO8859_2_IDX = 7;
    private static final int FONT_ENCODE_CHINESE_SIMPLIFIED_MS936_IDX = 8;
    private static final int FONT_ENCODE_CHINESE_SIMPLIFIED_GB18030_IDX = 9;
    private static final int FONT_ENCODE_CHINESE_SIMPLIFIED_EUC_CN_IDX = 10;
    private static final int FONT_ENCODE_CHINESE_SIMPLIFIED_GBK_IDX = 11;
    private static final int FONT_ENCODE_CHINESE_SIMPLIFIED_ISO2022_CN_IDX = 12;
    private static final int FONT_ENCODE_CHINESE_TRADITIONAL_BIG5_IDX = 13;
    private static final int FONT_ENCODE_CHINESE_TRADITIONAL_HONGKONG_IDX = 14;
    private static final int FONT_ENCODE_CYRILLIC_CP1251_IDX = 15;
    private static final int FONT_ENCODE_CYRILLIC_ISO8859_5_IDX = 16;
    private static final int FONT_ENCODE_EASTERN_EUROPEAN_CP1250_IDX = 17;
    private static final int FONT_ENCODE_GREEK_ISO8859_7_IDX = 18;
    private static final int FONT_ENCODE_HEBREW_ISO8859_8_IDX = 19;
    private static final int FONT_ENCODE_HEBREW_CP1255_IDX = 20;
    private static final int FONT_ENCODE_JAPANESE_MS932_IDX = 21;
    private static final int FONT_ENCODE_JAPANESE_ECU_JP_IDX = 22;
    private static final int FONT_ENCODE_JAPANESE_SHIFT_JIS_IDX = 23;
    private static final int FONT_ENCODE_JAPANESE_ISO2022_JP_IDX = 24;
    private static final int FONT_ENCODE_KOREAN_MS949_IDX = 25;
    private static final int FONT_ENCODE_KOREAN_ECU_KR_IDX = 26;
    private static final int FONT_ENCODE_KOREAN_ISO_2022_KR_IDX = 27;
    private static final int FONT_ENCODE_NORDIC_ISO8859_10_IDX = 28;
    private static final int FONT_ENCODE_ROMANIAN_ISO8859_16_IDX = 29;
    private static final int FONT_ENCODE_RUSSIAN_KOI8_R_IDX = 30;
    private static final int FONT_ENCODE_SOUTH_EUROPEAN_ISO8859_3_IDX = 31;
    private static final int FONT_ENCODE_THAI_TIS_620_IDX = 32;
    private static final int FONT_ENCODE_THAI_ISO8859_11_IDX = 33;
    private static final int FONT_ENCODE_TURKISH_CP1254_IDX = 34;
    private static final int FONT_ENCODE_TURKISH_ISO8859_9_IDX = 35;
    private static final int FONT_ENCODE_UNICODE_UTF_8_IDX = 36;
    private static final int FONT_ENCODE_UNICODE_UTF_16_IDX = 37;
    private static final int FONT_ENCODE_UNICODE_UTF_16BE_IDX = 38;
    private static final int FONT_ENCODE_UNICODE_UTF_32_IDX = 39;
    private static final int FONT_ENCODE_UNICODE_UTF_32BE_IDX = 40;
    private static final int FONT_ENCODE_UNICODE_UTF_32LE_IDX = 41;
    private static final int FONT_ENCODE_US_ASCII_IDX = 42;
    private static final int FONT_ENCODE_VIETNAMESE_SP1258_IDX = 43;
    private static final int FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_1_IDX = 44;
    private static final int FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_15_IDX = 45;
    private static final int FONT_ENCODE_WESTERN_EUROPEAN_CP1252_IDX = 46;

    public SubTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    /**
     * save the setting parameter by user
     */
    public void saveSettingPara() {
        SharedPreferences sp = mContext.getSharedPreferences("com.android.gallery3d.SubtitleViewSetInfoSP",
                Context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putInt("KeymTextSize", mTextSize);
        editor.putLong("KeymTextGravity", mTextGravity);
        editor.putLong("KeymTextFonttypes", mTextFonttypes);
        editor.putLong("KeymTextColortypes", mTextColortypes);
        editor.putLong("KeymEncodingSelectIdx", mEncodingSelectIdx);
        editor.putInt("KeymTextTranslationY", mTextTranslationY);
        editor.commit();
    }

    /**
     * initialize the saved setting parameter
     *
     * @param context the content from the main MoveActivity
     *
     * @param subTitleViewBm imageview for bitmap type
     */
    public void InitFirst(Context context, ImageView subTitleViewBm) {
        mSubTitleViewBm = subTitleViewBm;
        mContext = context;
        SharedPreferences sp = mContext.getSharedPreferences("com.android.gallery3d.SubtitleViewSetInfoSP",
                Context.MODE_PRIVATE);
        mTextSize = sp.getInt("KeymTextSize", 30);
        mTextGravity = sp.getLong("KeymTextGravity", TEXT_GRAVITY_CENTER);
        mTextFonttypes = sp.getLong("KeymTextFonttypes", FONT_TYPE_DEFAULT);
        mTextColortypes = sp.getLong("KeymTextColortypes", FONT_COLOR_WHITE);
        mEncodingSelectIdx = sp.getLong("KeymEncodingSelectIdx", 0);
        mTextTranslationY = sp.getInt("KeymTextTranslationY", 0);

        setTextAlpha(0.9f);
        setTextGravity(mTextGravity);
        setTextSizeSet(mTextSize);
        setTextColorSelect(mTextColortypes); // 4 is the white index in spinner
        setTextFont(mTextFonttypes); // 0 is the bold index in spinner
        setTextEncodingSelect(mEncodingSelectIdx);
        setTextTranslationY(mTextTranslationY);
    }

    /**
     * set the subtitle text Alpha
     *
     * @param alpha
     */
    public void setTextAlpha(float alpha) {
        setAlpha(alpha);
    }

    /**
     * set the text location
     *
     * @param gravity
     */
    public void setTextGravity(long types) {
            switch ((int) types) {
            case SubTitleView.TEXT_GRAVITY_LEFT:
                setGravity(Gravity.LEFT);
                break;
            case SubTitleView.TEXT_GRAVITY_CENTER:
                setGravity(Gravity.CENTER);
                break;
            case SubTitleView.TEXT_GRAVITY_RIGHT:
                setGravity(Gravity.RIGHT);
                break;
            default:
                setGravity(Gravity.CENTER);
            }
        mTextGravity = types;
    }

    /**
     * get the text location
     *
     * @return
     */
    public long getTextGravity() {
        return mTextGravity;
    }

    /**
     * set the text size
     *
     * @param size
     */
    public void setTextSizeSet(int size) {
        setTextSize(size);
        mTextSize = size;
    }

    /**
     * get the text size
     *
     * @return
     */
    public int getTextSizeSet() {
        return mTextSize;
    }

    /**
     * get the text translation on Y
     *
     * @return
     */
    public int getTextTranslationY() {
        return mTextTranslationY;
    }

    /**
     * set the text translation on Y
     *
     */
    public void setTextTranslationY(int translation) {
        setTranslationY(0 - translation);
        mTextTranslationY = translation;
    }

    /**
     * set the text font
     *
     * @param types
     *            can be :FONT_TYPE_DEFAULT FONT_TYPE_MONO,
     *            FONT_TYPE_SANS_SERIF, FONT_TYPE_SERIF
     */
    public void setTextFont(long types) {
        switch ((int) types) {
        case SubTitleView.FONT_TYPE_DEFAULT:
            setTypeface(Typeface.DEFAULT_BOLD);
            break;
        case SubTitleView.FONT_TYPE_MONO:
            setTypeface(Typeface.MONOSPACE);
            break;
        case SubTitleView.FONT_TYPE_SANS_SERIF:
            setTypeface(Typeface.SANS_SERIF);
            break;
        case SubTitleView.FONT_TYPE_SERIF:
            setTypeface(Typeface.SERIF);
            break;
        default:
            setTypeface(Typeface.DEFAULT_BOLD);
        }
        mTextFonttypes = types;
    }

    public long getTextFont() {
        return mTextFonttypes;
    }

    /**
     * set text color
     *
     * @param types
     *            can be : FONT_COLOR_BLACK, FONT_COLOR_DKGRAY and so on
     */
    public void setTextColorSelect(long types) {
        switch ((int) types) {
        case SubTitleView.FONT_COLOR_BLACK:
            setTextColor(Color.BLACK);
            break;
        case SubTitleView.FONT_COLOR_DKGRAY:
            setTextColor(Color.DKGRAY);
            break;
        case SubTitleView.FONT_COLOR_GRAY:
            setTextColor(Color.GRAY);
            break;
        case SubTitleView.FONT_COLOR_LTGRAY:
            setTextColor(Color.LTGRAY);
            break;
        case SubTitleView.FONT_COLOR_WHITE:
            setTextColor(Color.WHITE);
            break;
        case SubTitleView.FONT_COLOR_RED:
            setTextColor(Color.RED);
            break;
        case SubTitleView.FONT_COLOR_GREEN:
            setTextColor(Color.GREEN);
            break;
        case SubTitleView.FONT_COLOR_BLUE:
            setTextColor(Color.BLUE);
            break;
        case SubTitleView.FONT_COLOR_YELLOW:
            setTextColor(Color.YELLOW);
            break;
        case SubTitleView.FONT_COLOR_CYAN:
            setTextColor(Color.CYAN);
            break;
        case SubTitleView.FONT_COLOR_MAGENTA:
            setTextColor(Color.MAGENTA);
            break;
        case SubTitleView.FONT_COLOR_TRANSPARENT:
            setTextColor(Color.TRANSPARENT + (0x55FFFFFF));
            break;
        default:
            setTextColor(Color.WHITE);
            mTextColortypes = FONT_COLOR_WHITE;
            break;
        }
        mTextColortypes = types;
    }

    public long getTextColorSelect() {
        return mTextColortypes;
    }

    /**
     * set the text Encoding type
     *
     * @param types
     *            can be :FONT_ENCODE_DEFAULT,FONT_ENCODE_ARABIC_ISO8859_6, and
     *            so on
     */
    public void setTextEncodingSelect(long types) {
        switch ((int) types) {

        case FONT_ENCODE_DEFAULT_IDX:
            mEncodingSelect = FONT_ENCODE_DEFAULT;
            break;
        case FONT_ENCODE_ARABIC_ISO8859_6_IDX:
            mEncodingSelect = FONT_ENCODE_ARABIC_ISO8859_6;
            break;
        case FONT_ENCODE_ARABIC_CP1256_IDX:
            mEncodingSelect = FONT_ENCODE_ARABIC_CP1256;
            break;
        case FONT_ENCODE_BALTIC_CP1257_IDX:
            mEncodingSelect = FONT_ENCODE_BALTIC_CP1257;
            break;
        case FONT_ENCODE_BALTIC_ISO8859_13_IDX:
            mEncodingSelect = FONT_ENCODE_BALTIC_ISO8859_13;
            break;
        case FONT_ENCODE_BALTIC_SCANDINAVIA_ISO8859_4_IDX:
            mEncodingSelect = FONT_ENCODE_BALTIC_SCANDINAVIA_ISO8859_4;
            break;
        case FONT_ENCODE_CELTIC_ISO8859_14_IDX:
            mEncodingSelect = FONT_ENCODE_CELTIC_ISO8859_14;
            break;
        case FONT_ENCODE_CENTRAL_EUROPEAN_ISO8859_2_IDX:
            mEncodingSelect = FONT_ENCODE_CENTRAL_EUROPEAN_ISO8859_2;
            break;
        case FONT_ENCODE_CHINESE_SIMPLIFIED_MS936_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_SIMPLIFIED_MS936;
            break;
        case FONT_ENCODE_CHINESE_SIMPLIFIED_GB18030_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_SIMPLIFIED_GB18030;
            break;
        case FONT_ENCODE_CHINESE_SIMPLIFIED_EUC_CN_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_SIMPLIFIED_EUC_CN;
            break;
        case FONT_ENCODE_CHINESE_SIMPLIFIED_GBK_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_SIMPLIFIED_GBK;
            break;
        case FONT_ENCODE_CHINESE_SIMPLIFIED_ISO2022_CN_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_SIMPLIFIED_ISO2022_CN;
            break;
        case FONT_ENCODE_CHINESE_TRADITIONAL_BIG5_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_TRADITIONAL_BIG5;
            break;
        case FONT_ENCODE_CHINESE_TRADITIONAL_HONGKONG_IDX:
            mEncodingSelect = FONT_ENCODE_CHINESE_TRADITIONAL_HONGKONG;
            break;
        case FONT_ENCODE_CYRILLIC_CP1251_IDX:
            mEncodingSelect = FONT_ENCODE_CYRILLIC_CP1251;
            break;
        case FONT_ENCODE_CYRILLIC_ISO8859_5_IDX:
            mEncodingSelect = FONT_ENCODE_CYRILLIC_ISO8859_5;
            break;
        case FONT_ENCODE_EASTERN_EUROPEAN_CP1250_IDX:
            mEncodingSelect = FONT_ENCODE_EASTERN_EUROPEAN_CP1250;
            break;
        case FONT_ENCODE_GREEK_ISO8859_7_IDX:
            mEncodingSelect = FONT_ENCODE_GREEK_ISO8859_7;
            break;
        case FONT_ENCODE_HEBREW_ISO8859_8_IDX:
            mEncodingSelect = FONT_ENCODE_HEBREW_ISO8859_8;
            break;
        case FONT_ENCODE_HEBREW_CP1255_IDX:
            mEncodingSelect = FONT_ENCODE_HEBREW_CP1255;
            break;
        case FONT_ENCODE_JAPANESE_MS932_IDX:
            mEncodingSelect = FONT_ENCODE_JAPANESE_MS932;
            break;
        case FONT_ENCODE_JAPANESE_ECU_JP_IDX:
            mEncodingSelect = FONT_ENCODE_JAPANESE_ECU_JP;
            break;
        case FONT_ENCODE_JAPANESE_SHIFT_JIS_IDX:
            mEncodingSelect = FONT_ENCODE_JAPANESE_SHIFT_JIS;
            break;
        case FONT_ENCODE_JAPANESE_ISO2022_JP_IDX:
            mEncodingSelect = FONT_ENCODE_JAPANESE_ISO2022_JP;
            break;
        case FONT_ENCODE_KOREAN_MS949_IDX:
            mEncodingSelect = FONT_ENCODE_KOREAN_MS949;
            break;
        case FONT_ENCODE_KOREAN_ECU_KR_IDX:
            mEncodingSelect = FONT_ENCODE_KOREAN_ECU_KR;
            break;
        case FONT_ENCODE_KOREAN_ISO_2022_KR_IDX:
            mEncodingSelect = FONT_ENCODE_KOREAN_ISO_2022_KR;
            break;
        case FONT_ENCODE_NORDIC_ISO8859_10_IDX:
            mEncodingSelect = FONT_ENCODE_NORDIC_ISO8859_10;
            break;
        case FONT_ENCODE_ROMANIAN_ISO8859_16_IDX:
            mEncodingSelect = FONT_ENCODE_ROMANIAN_ISO8859_16;
            break;
        case FONT_ENCODE_RUSSIAN_KOI8_R_IDX:
            mEncodingSelect = FONT_ENCODE_RUSSIAN_KOI8_R;
            break;
        case FONT_ENCODE_SOUTH_EUROPEAN_ISO8859_3_IDX:
            mEncodingSelect = FONT_ENCODE_SOUTH_EUROPEAN_ISO8859_3;
            break;
        case FONT_ENCODE_THAI_TIS_620_IDX:
            mEncodingSelect = FONT_ENCODE_THAI_TIS_620;
            break;
        case FONT_ENCODE_THAI_ISO8859_11_IDX:
            mEncodingSelect = FONT_ENCODE_THAI_ISO8859_11;
            break;
        case FONT_ENCODE_TURKISH_CP1254_IDX:
            mEncodingSelect = FONT_ENCODE_TURKISH_CP1254;
            break;
        case FONT_ENCODE_TURKISH_ISO8859_9_IDX:
            mEncodingSelect = FONT_ENCODE_TURKISH_ISO8859_9;
            break;
        case FONT_ENCODE_UNICODE_UTF_8_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_8;
            break;
        case FONT_ENCODE_UNICODE_UTF_16_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_16;
            break;
        case FONT_ENCODE_UNICODE_UTF_16BE_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_16BE;
            break;
        case FONT_ENCODE_UNICODE_UTF_32_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_32;
            break;
        case FONT_ENCODE_UNICODE_UTF_32BE_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_32BE;
            break;
        case FONT_ENCODE_UNICODE_UTF_32LE_IDX:
            mEncodingSelect = FONT_ENCODE_UNICODE_UTF_32LE;
            break;
        case FONT_ENCODE_US_ASCII_IDX:
            mEncodingSelect = FONT_ENCODE_US_ASCII;
            break;
        case FONT_ENCODE_VIETNAMESE_SP1258_IDX:
            mEncodingSelect = FONT_ENCODE_VIETNAMESE_SP1258;
            break;
        case FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_1_IDX:
            mEncodingSelect = FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_1;
            break;
        case FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_15_IDX:
            mEncodingSelect = FONT_ENCODE_WESTERN_EUROPEAN_ISO8859_15;
            break;
        case FONT_ENCODE_WESTERN_EUROPEAN_CP1252_IDX:
            mEncodingSelect = FONT_ENCODE_WESTERN_EUROPEAN_CP1252;
            break;

        default:
            mEncodingSelect = FONT_ENCODE_DEFAULT;
            break;
        }
        mEncodingSelectIdx = types;
        MtkLog.v(TAG, " audioAndSubtitle  mEncodingSelect =" + mEncodingSelect
                + " mEncodingSelectIdx = " + mEncodingSelectIdx);

    }

    public long getTextEncodingSelectIdx() {
        return mEncodingSelectIdx;
    }

    /**
     * set the text to be show as subtitle
     *
     * @param text  timedText
     */
    public boolean setTextOrBitmap(TimedText text) {
            MtkLog.v(TAG, "audioAndSubtitle setTextOrBitmap: text = " + text);
            if (null == text) {
                setText(null);
                setVisibility(GONE);
                mSubTitleViewBm.setImageBitmap(null);
                mSubTitleViewBm.setVisibility(GONE);
                return false;
            }
            boolean ret = false;
            try {
                Bitmap bm = null;
                if (mEncodingSelectIdx != 0) {
                    bm = getBitmapFromAddr(TimedTextEx.getBitmapFd(text),
                                           TimedTextEx.getBitmapWidth(text),
                                           TimedTextEx.getBitmapHeight(text));
                    mSubTitleViewBm.setImageBitmap(bm);

                    if (TimedTextEx.getTextByteChars(text) != null) {
                        setText(new String(TimedTextEx.getTextByteChars(text), mEncodingSelect).trim());
                        setVisibility(VISIBLE);
                        ret = true;
                    } else {
                        setVisibility(GONE);
                    }

               } else {
                   //judge weather has bitmap subtitle
                   bm = getBitmapFromAddr(TimedTextEx.getBitmapFd(text),
                                          TimedTextEx.getBitmapWidth(text),
                                          TimedTextEx.getBitmapHeight(text));
                   mSubTitleViewBm.setImageBitmap(bm);

                   if (text.getText() != null) {
                       setText(text.getText().trim());
                       setVisibility(VISIBLE);
                       ret = true;
                   } else {
                       setVisibility(GONE);
                   }
               }

               if (bm != null) {
                   mSubTitleViewBm.setVisibility(VISIBLE);
                   ret = true;
                } else {
                    mSubTitleViewBm.setVisibility(GONE);
                }

            } catch (Exception e) {
                setText(null);
                setVisibility(GONE);
                mSubTitleViewBm.setVisibility(GONE);
                ret = false;
            }
            return ret;
    }
    /**
     * copy the data from the given fd by mmap
     * @param fd the filedescribe
     * @param width bitmap width
     * @param height  bitmap heght
     * @param bitmapOut for subtitle display
     */
    native protected void nativeGetBmFromAddr(int fd, int width, int height,
            Bitmap bitmapOut);

    private Bitmap getBitmapFromAddr(int fd, int width, int height) {
        if (fd < 0 || width < 0 || height < 0) {
            MtkLog.v(TAG,
            "audioAndSubtitle getBitmapFromAddr: No bitmap subtitle!!!");
            return null;
        }
        int w = width;
        int h = height;
        Bitmap mBitmapOut = null;
        while (mBitmapOut == null) {
            try {
                mBitmapOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            } catch (java.lang.OutOfMemoryError e) {
                System.gc();
                w /= 2;
                h /= 2;
                MtkLog.v(TAG,
                        "getBitmapFromAddr No memory to create Full bitmap create half instead");
            }
        }
        MtkLog.v(TAG,
                "audioAndSubtitle getBitmapFromAddr: start nativeGetBm w = "
                        + w + ";h = " + h + " addr = " + fd);
        nativeGetBmFromAddr(fd, w, h, mBitmapOut);
        MtkLog.v(TAG, "audioAndSubtitle getBitmapFromAddr: end nativeGetBm");
        return mBitmapOut;
    }

    static {
         if (MtkVideoFeature.isSubTitleSupport()) {
        MtkLog.i(TAG, "audioAndSubtitle loadLibrary jni_subtitle_bitmap for bitmap subtitle");
        System.loadLibrary("jni_subtitle_bitmap");
        }
    }
}

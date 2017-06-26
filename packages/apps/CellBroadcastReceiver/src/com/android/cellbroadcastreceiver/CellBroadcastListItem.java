/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.telephony.CellBroadcastMessage;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

// add for gemini
//import com.mediatek.telephony.SimInfoManager;
//import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import com.mediatek.text.style.BackgroundImageSpan;
import android.text.style.ForegroundColorSpan;
import java.util.HashMap;
import java.util.Map;
import com.mediatek.xlog.Xlog;

/**
 * This class manages the list item view for a single alert.
 */
public class CellBroadcastListItem extends RelativeLayout {

    private static final String TAG = "[ETWS]CellBroadcastListItem";
    private CellBroadcastMessage mCbMessage;

    private TextView mChannelView;
    private TextView mMessageView;
    private TextView mDateView;
    private ImageView mPresenceView;
    // add for gemini
    private TextView mSimIndicator;

    public CellBroadcastListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    CellBroadcastMessage getMessage() {
        return mCbMessage;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mChannelView = (TextView) findViewById(R.id.channel);
        mDateView = (TextView) findViewById(R.id.date);
        mMessageView = (TextView) findViewById(R.id.message);
        // add for gemini
        mSimIndicator = (TextView) findViewById(R.id.sim_indicator);
        mPresenceView = (ImageView) findViewById(R.id.presence);
    }

    /**
     * Only used for header binding.
     * @param message the message contents to bind
     */
    public void bind(CellBroadcastMessage message, boolean bRead) {
        mCbMessage = message;

        Drawable background = message.isRead() ?
                getResources().getDrawable(R.drawable.list_item_background_read) :
                getResources().getDrawable(R.drawable.list_item_background_unread);

        setBackground(background);

        mChannelView.setText(CellBroadcastResources.getDialogTitleResource(message));
        //mDateView.setText(message.getDateString(getContext()));
        mDateView.setText(ETWSUtils.formatTimeStampStringExtend(getContext(), message.getDeliveryTime()));

        mMessageView.setText(formatMessage(message));
        // add for gemini
        if (SystemProperties.get("ro.mtk_gemini_support").equals("1")) {
            Xlog.d(TAG, "bind sim_id:"+mCbMessage.getSubId());
            CharSequence subString = getSubString(this.getContext(), mCbMessage.getSubId());
            mSimIndicator.setText(subString);
        }

        if (!bRead) {
            mPresenceView.setVisibility(View.VISIBLE);
        } else {
            mPresenceView.setVisibility(View.GONE);
        }
    }

    private static CharSequence formatMessage(CellBroadcastMessage message) {
        String body = message.getMessageBody();

        SpannableStringBuilder buf = new SpannableStringBuilder(body);

        // Unread messages are shown in bold
        if (!message.isRead()) {
            buf.setSpan(new StyleSpan(Typeface.BOLD), 0, buf.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Speak the date first, then channel name, then message body
        event.getText().add(mCbMessage.getSpokenDateString(getContext()));
        mChannelView.dispatchPopulateAccessibilityEvent(event);
        mMessageView.dispatchPopulateAccessibilityEvent(event);
        return true;
    }

    // add for gemini functions
    public static Map<Integer, CharSequence> simInfoMap = new HashMap<Integer, CharSequence>();
    // invoke this function in the UI thread.
    public static CharSequence getSubString(Context context, int subId){
        Xlog.d(TAG, "getSimInfo simId = " + subId);
        //add this code for more safty
        if (subId == -1) {
            return "";
        }
        if (simInfoMap.containsKey(subId)) {
            Xlog.d(TAG, "MessageUtils.getSimInfo(): getCache");
            return simInfoMap.get(subId);
        }
      //L migration modify start
        
        //get sim info
        //SimInfoRecord simInfo = SimInfoManager.getSimInfoById(context, simId);
        
        //TODO:  done      
        //error: getSubscriptionInfo not public
        SubscriptionInfo si = SubscriptionManager.from(context).getSubscriptionInfo(subId);      
        if(null != si){
            // error: incompatible types
            String displayName = si.getDisplayName().toString();

            Xlog.d(TAG, "SIMInfo simId=" + subId + " mDisplayName=" + displayName);

            if(null == displayName){
                simInfoMap.put(subId, "");
                return "";
            }

            SpannableStringBuilder buf = new SpannableStringBuilder();
            buf.append(" ");
            if (displayName.length() < 20) {
                buf.append(displayName);
            } else {
                buf.append(displayName.substring(0,8) + "..." + displayName.substring(displayName.length()-8, displayName.length()));
            }
            buf.append(" ");

            //set background image
            //int colorRes = (si.slotId >= 0)? si.simIconRes[0] : com.mediatek.internal.R.drawable.sim_background_locked;
            //TODO: done            
            /*int colorRes = si.simIconRes[0];
            Drawable drawable = context.getResources().getDrawable(colorRes);*/
            Bitmap bitmap = si.createIconBitmap(context);
            Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            buf.setSpan(new BackgroundImageSpan(-1/*colorRes*/, drawable), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            //set simInfo color
            int color = context.getResources().getColor(R.color.siminfo_color);
            buf.setSpan(new ForegroundColorSpan(color), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //buf.setSpan(new StyleSpan(Typeface.BOLD),0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            simInfoMap.put(subId, buf);
            return buf;
        }
        
      //L migration modify END
        simInfoMap.put(subId, "");
        return "";
    }
}

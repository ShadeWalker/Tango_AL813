/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2012. All rights reserved.
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

package com.android.cellbroadcastreceiver;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
//import com.mediatek.telephony.SimInfoManager;
//import com.mediatek.telephony.SimInfoManager.SimInfoRecord;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mediatek.xlog.Xlog;

public class SelectCardActivity extends ListActivity {
    private static final String TAG = "[ETWS]CB/SelectCardActivity";
    private ListView mListView;
    private SimpleAdapter mAdapter;
    public static final String KEY_SIM_ICON    = "icon";
    public static final String KEY_SIM_TITLE   = "title";
    //private int mSlot0SimId = -1;
    //private int mSlot1SimId = -1;
    private ArrayList<Integer> subIds = new ArrayList<Integer>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initList();
    }

    private void initList() {
        mListView = getListView();
        mAdapter = new SimpleAdapter(this, getSimInfoList(), R.layout.select_card_list_item,
                                    new String[]{KEY_SIM_ICON, KEY_SIM_TITLE},
                                    new int[]{R.id.icon, R.id.title});
        setListAdapter(mAdapter);
        // Added for L-MR1
        mAdapter.setViewBinder(new ViewBinder() {
            
            @Override
            public boolean setViewValue(View view, Object data,
                    String textRepresentation) {
                // TODO Auto-generated method stub
                if (view instanceof ImageView && data instanceof Bitmap){
                    ImageView imgView = (ImageView) view;
                    imgView.setImageBitmap((Bitmap) data);
                    return true;
                }
                return false;
            }
        });
        
    }

    private List<Map<String, Object>> getSimInfoList() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        
        //SimInfoRecord sim1Info = SimInfoManager.getSimInfoBySlot(this, 0);
        //TODO: START done       
        List<SubscriptionInfo> si = SubscriptionManager.from(this).getActiveSubscriptionInfoList();//getSubInfoUsingSlotId(0);
        int siCount = SubscriptionManager.from(this).getActiveSubscriptionInfoCount();
        for(int i=0; i < siCount; i++) {
	        if (si.get(i) != null) {
                    SubscriptionInfo currinfo = si.get(i);
	            Map<String, Object> map = new HashMap<String, Object>();
	            //map.put(KEY_SIM_ICON, (Integer)currinfo.simIconRes[i]);
	            map.put(KEY_SIM_ICON, (Bitmap)currinfo.createIconBitmap(this));
	            map.put(KEY_SIM_TITLE, currinfo.getDisplayName().toString());
	            list.add(map);
	            subIds.add(currinfo.getSubscriptionId());
	            //mSlot0SimId = (int)si.get(0).subId;
	            Xlog.d(TAG, "add first card");
	        }
        }
        //TODO: END
   /*     
        List<SubInfoRecord> si2 = SubscriptionManager.getSubInfoUsingSlotId(1);
        if (si2 != null) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(KEY_SIM_ICON, (Integer)(si2.get(0).mSimIconRes));
            map.put(KEY_SIM_TITLE, si2.get(0).mDisplayName);
            list.add(map);
            mSlot1SimId = (int)si2.get(0).mSubId;
            Xlog.d(TAG, "add first card");
        } */
        return list;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent it = new Intent(this, CellBroadcastSettings.class);
        /*if (isTwoCard()) {
            if (position == 0) {
                it.putExtra("sim_id", mSlot0SimId);
            } else if (position == 1){
                it.putExtra("sim_id", mSlot1SimId);
            } else {
                Xlog.e(TAG, "invalid position:" + position);
            }
        } else {
            //only one card
            it.putExtra("sim_id", mSlot0SimId == -1?mSlot1SimId:mSlot0SimId);
        }*/
        it.putExtra("subscription",subIds.get(position));
        finish();
        startActivity(it);
    }
/*
    private boolean isTwoCard() {
        return ((mSlot0SimId != -1) && (mSlot1SimId != -1));
    }*/
}

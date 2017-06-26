/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.settings;


import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.PatternMatcher;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;


public class RestoreRotationReceiver extends BroadcastReceiver {

    public static boolean sRestoreRetore = false;
    private Context mContext;
    private List<ResolveInfo> mList;
    private PackageManager packageManager;
    private SharedPreferences sharePre ;
    public static final String DEFUALT_BROWSER_SHARED= "defualt_browser";
    public static final String SET_DEFUALT_BROWSER= "set_defualt_browser";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        mContext = context;
        packageManager = mContext.getPackageManager();
        sharePre = mContext.getSharedPreferences(DEFUALT_BROWSER_SHARED, Activity.MODE_PRIVATE);  
		if(action==null){
			return;
			}
        Log.v("RestoreRotationReceiver_IPO", action);
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.ACTION_BOOT_IPO")) {
            sRestoreRetore = Settings.System.getInt(context
                    .getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION_RESTORE, 0) != 0;
            if (sRestoreRetore) {
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 1);
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION_RESTORE, 0);
            }
        }
        //add by wangmingyue for HQ01512387 begin
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
        	
        	if("1".equals(SystemProperties.get("ro.hq.claro.default.browser")) && !sharePre.getBoolean(SET_DEFUALT_BROWSER, false)) {
        		
        		Intent clearIntent = new Intent();
        		clearIntent.setAction("android.intent.action.VIEW");
        		clearIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        		clearIntent.setData(Uri.parse("http://"));
        		mList = packageManager.queryIntentActivities(clearIntent, (PackageManager.GET_INTENT_FILTERS
        				| PackageManager.MATCH_DEFAULT_ONLY
        				| PackageManager.GET_RESOLVED_FILTER
        				));
        		if( mList!= null && mList.size()>0) {
        			ResolveInfo selectedResolveInfo = mList.get(0);
        			String preferredPkgName = selectedResolveInfo.activityInfo.applicationInfo.packageName;
        			String prefrredPkgClsName = selectedResolveInfo.activityInfo.name;
        			
        			Intent browserIntent = new Intent();
        			browserIntent.setAction("android.intent.action.VIEW");
        			browserIntent.setData(Uri.parse("http://"));
        			browserIntent.setFlags(IntentFilter.MATCH_CATEGORY_HOST);
        			browserIntent.setComponent(new ComponentName(preferredPkgName, prefrredPkgClsName));
        			setAlwaysDefault(selectedResolveInfo, clearIntent, browserIntent);
        		}
        	}
        }
    }
    
    public void setAlwaysDefault(ResolveInfo ri, Intent clearIntent, Intent intent) {
        if (null == packageManager || ri == null || clearIntent == null || intent == null) {
            return;
        }

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                clearIntent, (PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER));

        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (null != resolveInfo) {
            	packageManager.clearPackagePreferredActivities(resolveInfo.activityInfo.packageName);
            }
        }
        // Build a reasonable intent filter, based on what matched.
        IntentFilter filter = new IntentFilter();

        if (intent.getAction() != null) {
            filter.addAction(intent.getAction());
        }
        Set<String> categories = intent.getCategories();
        if (categories != null) {
            for (String cat : categories) {
                filter.addCategory(cat);
            }
        }
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        int cat = ri.match&IntentFilter.MATCH_CATEGORY_MASK;
        Uri data = intent.getData();
        if (cat == IntentFilter.MATCH_CATEGORY_TYPE) {
            String mimeType = intent.resolveType(mContext);
            if (mimeType != null) {
                try {
                    filter.addDataType(mimeType);
                } catch (IntentFilter.MalformedMimeTypeException e) {
                    Log.w("ResolverActivity", e);
                    filter = null;
                }
            }
        }
        if (data != null && data.getScheme() != null) {
            // We need the data specification if there was no type,
            // OR if the scheme is not one of our magical "file:"
            // or "content:" schemes (see IntentFilter for the reason).
            if (cat != IntentFilter.MATCH_CATEGORY_TYPE
                    || (!"file".equals(data.getScheme())
                            && !"content".equals(data.getScheme()))) {
                filter.addDataScheme(data.getScheme());

                // Look through the resolved filter to determine which part
                // of it matched the original Intent.
                Iterator<PatternMatcher> pIt = ri.filter.schemeSpecificPartsIterator();
                if (pIt != null) {
                    String ssp = data.getSchemeSpecificPart();
                    while (ssp != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(ssp)) {
                            filter.addDataSchemeSpecificPart(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
                Iterator<IntentFilter.AuthorityEntry> aIt = ri.filter.authoritiesIterator();
                if (aIt != null) {
                    while (aIt.hasNext()) {
                        IntentFilter.AuthorityEntry a = aIt.next();
                        if (a.match(data) >= 0) {
                            int port = a.getPort();
                            filter.addDataAuthority(a.getHost(),
                                    port >= 0 ? Integer.toString(port) : null);
                            break;
                        }
                    }
                }
                pIt = ri.filter.pathsIterator();
                if (pIt != null) {
                    String path = data.getPath();
                    while (path != null && pIt.hasNext()) {
                        PatternMatcher p = pIt.next();
                        if (p.match(path)) {
                            filter.addDataPath(p.getPath(), p.getType());
                            break;
                        }
                    }
                }
            }
        }

        if (filter != null) {
            final int N = resolveInfos.size();
            ComponentName[] set = new ComponentName[N];
            int bestMatch = 0;
            for (int i=0; i<N; i++) {
                ResolveInfo r = resolveInfos.get(i);
                set[i] = new ComponentName(r.activityInfo.packageName,
                        r.activityInfo.name);
                if (r.match > bestMatch) bestMatch = r.match;
            }
            packageManager.addPreferredActivity(filter, bestMatch, set,intent.getComponent());
        }
    }
  //add by wangmingyue for HQ01512387 end
    
}

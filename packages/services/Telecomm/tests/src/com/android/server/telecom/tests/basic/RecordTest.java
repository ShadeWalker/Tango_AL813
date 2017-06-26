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

package com.android.server.telecom.tests.basic;

import android.content.Context;
import android.test.InstrumentationTestCase;

import com.android.server.telecom.AutotestEngine;
import com.android.server.telecom.AutotestEngineUtils;
import com.android.server.telecom.ICommand;
import com.android.server.telecom.AddCallCommand;
import com.android.server.telecom.Utils;
import com.android.server.telecom.tests.annotation.InternalApiAnnotation;
import com.mediatek.storage.StorageManagerEx;

import java.io.File;

public class RecordTest extends InstrumentationTestCase {

    private static final String TAG = "RecordTest";
    AutotestEngine mAutotestEngine;
    private File mPhoneRecordDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        log("setUp");
        mAutotestEngine = AutotestEngine.makeInstance(getInstrumentation());
        getInstrumentation().waitForIdleSync();

        String path = StorageManagerEx.getDefaultPath();
        File file = new File(path);
        if (!file.canWrite()) {
            log(path + " can not write");
            path = "/sdcard/sdcard";
        }
        path += "/PhoneRecord";
        log("path:" + path);
        mPhoneRecordDir = new File(path);
        deleteFiles();

        String handleId = AutotestEngineUtils.getFirstInServiceSubAccountId(
                getInstrumentation().getTargetContext());
        if (handleId != null) {
            int result = mAutotestEngine.execute(AddCallCommand.FIRST_CALL + handleId);
            AutotestEngineUtils.assertAndWaitSync(result, true);
        }
    };

    @Override
    protected void tearDown() throws Exception {
        log("tearDown");
        super.tearDown();
        // End the call.
        AutotestEngineUtils.assertAndWaitSync(mAutotestEngine.execute("End 3"));
        deleteFiles();
    }

    @InternalApiAnnotation
    public void test01_recording() throws Exception {
        log("test01_recording");
        int result = mAutotestEngine.execute("Record");
        Thread.sleep(3000);
        log("record result=" + result);
        if (result == ICommand.RESULT_OK) {
            result = ICommand.RESULT_FAIL;
            File[] files = mPhoneRecordDir.listFiles();
            if (files.length == 1) {
                String fileName = files[0].getName();
                String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
                if (extension.equalsIgnoreCase("3gpp") && files[0].length() > 0) {
                    result = ICommand.RESULT_OK;
                }
            }
        }
        log("final result=" + result);
        AutotestEngineUtils.assertAndWaitSync(result);
    }

    private void deleteFiles() {
        if (mPhoneRecordDir == null) {
            return;
        }
        File[] files = mPhoneRecordDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        for (File delFile : files) {
            log("delete file:" + delFile.getName());
            delFile.delete();
        }
    }

    void log(String msg) {
        Utils.log(TAG, msg);
    }
}

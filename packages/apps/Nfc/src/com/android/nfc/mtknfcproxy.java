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

package com.android.nfc;

import android.content.Context;

public class mtknfcproxy {

    private static mtknfcproxy mStaticInstance = null;

    Context mContext;

    static P2pLinkManager mP2pLinkManager;

    //constructor
    private mtknfcproxy(Context context, P2pLinkManager p2pLinkManager) {
        mContext = context;
        mP2pLinkManager = p2pLinkManager;
    }

    public static void createSingleton(Context context, P2pLinkManager p2pLinkManager) {
        if (mStaticInstance == null) {
            mStaticInstance = new mtknfcproxy(context, p2pLinkManager);
        }
    }

    public static int getP2pSendState() {
        return mP2pLinkManager.getP2pState();
    }



}


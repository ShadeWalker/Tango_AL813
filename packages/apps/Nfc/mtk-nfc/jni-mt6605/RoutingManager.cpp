/*
 * Copyright (C) 2010 The Android Open Source Project
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

#include <errno.h>
#include <semaphore.h>
#include <string.h>

#include "com_android_nfc.h"
#include "RoutingManager.h"

int gDefaultRoute = 0x01; //route to SIM
int gDefaultOffHostRoute = 0x01; // route to SIM

#define AID_MATCHING_EXACT_ONLY_V 0x00
#define AID_MATCHING_EXACT_OR_PREFIX_V 0x01
#define AID_MATCHING_PREFIX_ONLY_V 0x02

int gAidMatchingMode = AID_MATCHING_PREFIX_ONLY_V;

// Every routing table entry is matched exact
int AID_MATCHING_EXACT_ONLY = 0x00;
// Every routing table entry can be matched either exact or prefix
int AID_MATCHING_EXACT_OR_PREFIX = 0x01;
// Every routing table entry is matched as a prefix
int AID_MATCHING_PREFIX_ONLY = 0x02;


namespace android {

/*
KK
    route = onHost ? 0 : DEFAULT_OFFHOST_ROUTE;
    static final int DEFAULT_OFFHOST_ROUTE = 0xF4;
L:
    mDefaultOffHostRoute = doGetDefaultOffHostRouteDestination();
    route = onHost ? 0 : mDefaultOffHostRoute;
*/

int com_android_nfc_cardemulation_doGetDefaultRouteDestination (JNIEnv*)
{
    // return getInstance().mDefaultEe;
    // TODO
    ALOGD("%s: return gDefaultRoute, %d", __FUNCTION__, gDefaultRoute);      
    return gDefaultRoute;
}

int com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination (JNIEnv*)
{
    // return getInstance().mOffHostEe;
    // TODO
    // default return 0x01
    ALOGD("%s: return gDefaultOffHostRoute, %d", __FUNCTION__, gDefaultOffHostRoute);  
    return gDefaultOffHostRoute;
}

int com_android_nfc_cardemulation_doGetAidMatchingMode (JNIEnv*)
{
    ALOGD("%s: return %d", __FUNCTION__, gAidMatchingMode); 
    //temp solution
    return gAidMatchingMode;
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] =
{
    {"doGetDefaultRouteDestination", "()I", (void*) com_android_nfc_cardemulation_doGetDefaultRouteDestination},
    {"doGetDefaultOffHostRouteDestination", "()I", (void*) com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination},
    {"doGetAidMatchingMode", "()I", (void*) com_android_nfc_cardemulation_doGetAidMatchingMode}
};

int register_com_android_nfc_RoutingManager(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/android/nfc/cardemulation/AidRoutingManager",
      gMethods, NELEM(gMethods));
}

} // namespace android

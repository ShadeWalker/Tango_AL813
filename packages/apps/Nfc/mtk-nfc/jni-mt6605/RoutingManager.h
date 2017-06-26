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
#ifndef __ROUTING_MANAGER_H__
#define __ROUTING_MANAGER_H__

#include <errno.h>
#include <semaphore.h>
#include <string.h>

#include "com_android_nfc.h"

namespace android {


int com_android_nfc_cardemulation_doGetDefaultRouteDestination (JNIEnv*);
int com_android_nfc_cardemulation_doGetDefaultOffHostRouteDestination (JNIEnv*);
int com_android_nfc_cardemulation_doGetAidMatchingMode (JNIEnv*);

int register_com_android_nfc_RoutingManager(JNIEnv *e);

} // namespace android

#endif

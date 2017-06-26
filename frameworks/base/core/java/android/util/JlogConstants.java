/*
 * Copyright (C) 2014 Huawei Technologies Co., Ltd.
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

package android.util;

/**
 * @hide
 */
public class JlogConstants {
    public static final int JLID_POWERKEY_PRESS                                  =0;
    public static final int JLID_USBCHARGING_START                               =1;
    public static final int JLID_USBCHARGING_END                                 =2;
    public static final int JLID_NEWRINGING_CONNECTION                           =3;
    public static final int JLID_INCOMINGCALL_RINGING                            =4;
    public static final int JLID_PMS_WAKEFULNESS_ASLEEP                          =5;
    public static final int JLID_PMS_WAKEFULNESS_DREAMING                        =6;
    public static final int JLID_PMS_WAKEFULNESS_NAPPING                         =7;
    public static final int JLID_PMS_WAKEUP_FINISHED                             =8;
    public static final int JLID_POWERKEY_RELEASE                                =9;
    public static final int JLID_KERNEL_LCD_OPEN                                 =10;
    public static final int JLID_KERNEL_LCD_SUSPEND                              =11;
    public static final int JLID_KERNEL_LCD_POWER_ON                             =12;
    public static final int JLID_KERNEL_LCD_POWER_OFF                            =13;
    public static final int JLID_KERNEL_PM_SUSPEND_WAKEUP                        =14;
    public static final int JLID_GO_TO_SLEEP_REASON_USER                         =15;
    public static final int JLID_KERNEL_PM_SUSPEND_SLEEP                         =16;
    public static final int JLID_KERNEL_PM_DEEPSLEEP_WAKEUP                      =17;
    public static final int JLID_KERNEL_LCD_BACKLIGHT_ON                         =18;
    public static final int JLID_SYSPROC_INIT_POWERON_START                      =19;
    public static final int JLID_SYSPROC_INIT_POWERON_END                        =20;
    public static final int JLID_SERVICEMANAGER_POWERON_START                    =21;
    public static final int JLID_SERVICEMANAGER_STARTUP                          =22;
    public static final int JLID_START_SYSTEMSERVER                              =23;
    public static final int JLID_LAUNCHER_STARTUP                                =24;
    public static final int JLID_ZYGOTE_START                                    =25;
    public static final int JLID_FIRST_BOOT                                      =26;
    public static final int JLID_BOOT_PROGRESS_START                             =27;
    public static final int JLID_BOOT_PROGRESS_PRELOAD_START                     =28;
    public static final int JLID_BOOT_PROGRESS_PRELOAD_END                       =29;
    public static final int JLID_BOOT_PROGRESS_SYSTEM_RUN                        =30;
    public static final int JLID_BOOT_PROGRESS_PMS_START                         =31;
    public static final int JLID_BOOT_PROGRESS_PMS_READY                         =32;
    public static final int JLID_BOOT_PROGRESS_AMS_READY                         =33;
    public static final int JLID_BOOT_PROGRESS_ENABLE_SCREEN                     =34;
    public static final int JLID_PROXIMITY_SENSOR_FAR                            =35;
    public static final int JLID_PROXIMITY_SENSOR_NEAR                           =36;
    public static final int JLID_JANK_FRAME_SKIP                                 =37;
    public static final int JLID_JANK_FRAME_INFLATE_TIME                         =38;
    public static final int JLID_JANK_FRAME_OBTAIN_TIME                          =39;
    public static final int JLID_JANK_FRAME_SETUP_TIME                           =40;
    public static final int JLID_JANK_FRAME_COMPOSE_TIME                         =41;
    public static final int JLID_SLIDE_TO_CLICK                                  =42;
    public static final int JLID_APP_LAUNCHING_BEGIN                             =43;
    public static final int JLID_APP_LAUNCHING_END                               =44;
    public static final int JLID_COVER_WAKE_LOCK                                 =45;
    public static final int JLID_BOOT_PROGRESS_INITZYGOTE_START                  =46;
    public static final int JLID_RIL_RESPONSE_NEW_SMS                            =47;
    public static final int JLID_DISPATCH_NORMAL_SMS                             =48;
    public static final int JLID_SEND_BROADCAST_SMS                              =49;
    public static final int JLID_DISPATCH_SMS_FAILED                             =50;
    public static final int JLID_WAP_DISPATCH_PDU                                =51;
    public static final int JLID_TP_GESTURE_KEY                                  =52;
    public static final int JLID_WAKEUP_DBCLICK                                  =53;
    public static final int JLID_INPUTDISPATCH_FINGERPRINT                       =54;
    public static final int JLID_FINGER_INDENTIFY_OK                             =55;
    public static final int JLID_FINGER_INDENTIFY_FAILED                         =56;
    public static final int JLID_ROTATION_CHANGED                                =57;
    public static final int JLID_START_ROTATION_ANIM                             =58;
    public static final int JLID_END_ROTATION_ANIM                               =59;
    public static final int JLID_MONKEY_CTS_START                                =60;
    public static final int JLID_MONKEY_CTS_END                                  =61;
    public static final int JLID_SQLITE_INSERT_ET                                =62;
    public static final int JLID_SQLITE_UPDATE_ET                                =63;
    public static final int JLID_SQLITE_QUERY_ET                                 =64;
    public static final int JLID_SQLITE_DELETE_ET                                =65;
    public static final int JLID_SQLITE_EXECSQL_ET                               =66;
    public static final int JLID_JANK_FRAME_HANDLER_TIME                         =67;
    public static final int JLID_JANK_FRAME_TIMEOUT                              =68;
    public static final int JLID_SLIDE_TO_CLICK_TIMEOUT                          =69;
    public static final int JLID_BINDER_TRANSACT_TIMEOUT                         =70;
    public static final int JLID_KEYGUARD_DELEGATE_SCTURNON                      =71;
    public static final int JLID_KEYGUARD_MEDIA_SCTURNON                         =72;
    public static final int JLID_KEYGUARD_MEDIA_NTSCON                           =73;
    public static final int JLID_KEYGUARD_MEDIA_HDLSCON                          =74;
    public static final int JLID_KEYGUARD_MANAG_SCTURNON                         =75;
    public static final int JLID_KEYGUARD_DELEGATE_SHOWN                         =76;
    public static final int JLID_KERNEL_LCD_BACKLIGHT_OFF                        =77;
    public static final int JLID_GO_TO_SLEEP_REASON_PROX                         =78;
    public static final int JLID_GO_TO_SLEEP_REASON_OTHERS                       =79;
    public static final int JLID_STRICT_MODE                                     =80;
    public static final int JLID_JANK_FRAME_ENABLE                               =81;
    public static final int JLID_JANK_FRAME_DISABLE                              =82;
    public static final int JLID_83                                              =83;
    public static final int JLID_84                                              =84;
    public static final int JLID_85                                              =85;
    public static final int JLID_86                                              =86;
    public static final int JLID_87                                              =87;
    public static final int JLID_88                                              =88;
    public static final int JLID_89                                              =89;
    public static final int JLID_90                                              =90;
    public static final int JLID_91                                              =91;
    public static final int JLID_92                                              =92;
    public static final int JLID_93                                              =93;
    public static final int JLID_94                                              =94;
    public static final int JLID_95                                              =95;
    public static final int JLID_96                                              =96;
    public static final int JLID_97                                              =97;
    public static final int JLID_98                                              =98;
    public static final int JLID_99                                              =99;
    public static final int JLID_100                                             =100;
    public static final int JLID_101                                             =101;
    public static final int JLID_102                                             =102;
    public static final int JLID_103                                             =103;
    public static final int JLID_104                                             =104;
    public static final int JLID_105                                             =105;
    public static final int JLID_106                                             =106;
    public static final int JLID_107                                             =107;
    public static final int JLID_108                                             =108;
    public static final int JLID_109                                             =109;
    public static final int JLID_110                                             =110;
    public static final int JLID_111                                             =111;
    public static final int JLID_112                                             =112;
    public static final int JLID_113                                             =113;
    public static final int JLID_114                                             =114;
    public static final int JLID_115                                             =115;
    public static final int JLID_116                                             =116;
    public static final int JLID_117                                             =117;
    public static final int JLID_118                                             =118;
    public static final int JLID_119                                             =119;
    public static final int JLID_120                                             =120;
}

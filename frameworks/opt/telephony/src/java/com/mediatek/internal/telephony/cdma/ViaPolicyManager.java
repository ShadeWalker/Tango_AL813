/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.mediatek.internal.telephony.cdma;

import android.content.Context;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The VIA policy manager.
 * @hide
 */
public class ViaPolicyManager {

    private static final String LOG_TAG = "ViaPolicyManager";
    private static final String VIA_PLUS_CODE_IMPL_CLASS_NAME =
            "com.mediatek.internal.telephony.cdma.ViaPlusCodeUtils";
    private static final String VIA_SMS_INTERFACES_IMPL_CLASS_NAME =
            "com.mediatek.internal.telephony.cdma.ViaSmsInterfacesAdapter";
    private static final String VIA_UTKSERVICE_IMPL_CLASS_NAME =
            "com.android.internal.telephony.cdma.utk.UtkService";
    private static final String VIA_GPS_PROCESS_CLASS_NAME =
            "com.mediatek.internal.telephony.cdma.ViaGpsProcess";

    private static final Object mLock = new Object();

    private static IPlusCodeUtils sPlusCodeUtilsInstance;
    private static ISmsInterfaces sSmsInterfacesInstance;
    private static IUtkService sUtkServiceInstanceSim1;
    private static IUtkService sUtkServiceInstanceSim2;

    /**
     * Create IPlusCodeUtils. Will return DefaultPlusCodeUtils If
     * cannot find VIA proprietary implementation
     * @return The implementation of IPlusCodeUtils
     */
    public static IPlusCodeUtils getPlusCodeUtils() {
        if (sPlusCodeUtilsInstance == null) {
            synchronized (mLock) {
                if (sPlusCodeUtilsInstance == null) {
                    sPlusCodeUtilsInstance = makePlusCodeUtis();
                }
            }
        }
        log("getPlusCodeUtils sPlusCodeUtilsInstance=" + sPlusCodeUtilsInstance);
        return sPlusCodeUtilsInstance;
    }

    /**
     * Create ISmsInterfaces. Will return DefaultSmsInterfaces If
     * cannot find VIA proprietary implementation
     * @return The implementation of ISmsInterfaces
     */
    public static ISmsInterfaces getSmsInterfaces() {
        if (sSmsInterfacesInstance == null) {
            synchronized (mLock) {
                if (sSmsInterfacesInstance == null) {
                    sSmsInterfacesInstance = makeSmsInterfaces();
                }
            }
        }
        log("getPlusCodeUtils sSmsInterfacesInstance=" + sSmsInterfacesInstance);
        return sSmsInterfacesInstance;
    }

    /**
     * Create IUtkService. Will return DefaultUtkService If
     * cannot find VIA proprietary implementation.
     * @param ci The CommandsInterface to init UtkService.
     * @param context The context to init UtkService.
     * @param ic The UiccCard instantce to init UiccCard.
     * @return The implementation of IUtkService
     */
    public static IUtkService getUtkService(CommandsInterface ci,
            Context context, UiccCard ic) {
        if (null == ic) {
            log("getPlusCodeUtils getUtkService ic == null");
            return null;
        }
        int mPhoneId = ic.getPhoneId();
        log("getPlusCodeUtils getUtkService mPhoneId = " + mPhoneId);
        UiccCardApplication ca = ic.getApplication(UiccController.APP_FAM_3GPP2);
        if (ca == null) {
            log("getPlusCodeUtils getUtkService ca == null");
            return null;
        }
        if (mPhoneId == 0) {
            if (sUtkServiceInstanceSim1 == null) {
                synchronized (mLock) {
                    if (sUtkServiceInstanceSim1 == null) {
                        sUtkServiceInstanceSim1 = makeUtkService(ci, context, ic);
                    }
                }
            }
            log("getPlusCodeUtils sUtkServiceInstanceSim1 = " + sUtkServiceInstanceSim1);
            return sUtkServiceInstanceSim1;
        } else {
            if (sUtkServiceInstanceSim2 == null) {
                synchronized (mLock) {
                    if (sUtkServiceInstanceSim2 == null) {
                        sUtkServiceInstanceSim2 = makeUtkService(ci, context, ic);
                    }
                }
            }
            log("getPlusCodeUtils sUtkServiceInstanceSim2 = " + sUtkServiceInstanceSim2);
            return sUtkServiceInstanceSim2;
        }   
    }
    public static void disposeUtkService(int mPhoneId) {
        log("getPlusCodeUtils dispose UtkService " + mPhoneId);
        if ((sUtkServiceInstanceSim1 != null) && (mPhoneId == 0)) {
            sUtkServiceInstanceSim1.dispose();
            sUtkServiceInstanceSim1 = null;
        } else if ((sUtkServiceInstanceSim2 != null) && (mPhoneId == 1)) {
            sUtkServiceInstanceSim2.dispose();
            sUtkServiceInstanceSim2 = null;
        } else {
            log("getPlusCodeUtils no need to dispose UtkService " + mPhoneId);
        }
    }

    /**
     * Create IGpsProcess. Will return DefaultGpsProcess If
     * cannot find VIA proprietary implementation
     * @param context context instance
     * @param phone The phone instance
     * @param ci The command interface
     * @return The implementation of IGpsProcess
     */
    public static IGpsProcess getGpsProcess(Context context,
            CDMAPhone phone, CommandsInterface ci) {
        IGpsProcess gpsProcessInstance;
        gpsProcessInstance = makeGpsProcess(context, phone, ci);
        log("getGpsProcess gpsProcessInstance=" + gpsProcessInstance);
        return gpsProcessInstance;
    }

    private static IPlusCodeUtils makePlusCodeUtis() {
        try {
            Class policyClass = Class.forName(VIA_PLUS_CODE_IMPL_CLASS_NAME);
            return (IPlusCodeUtils) policyClass.newInstance();
        } catch (ClassNotFoundException ex) {
            log("makePlusCodeUtis ClassNotFoundException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        } catch (IllegalAccessException ex) {
            log("makePlusCodeUtis IllegalAccessException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        } catch (InstantiationException ex) {
            log("makePlusCodeUtis InstantiationException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        }
    }

    private static ISmsInterfaces makeSmsInterfaces() {
        try {
            Class policyClass = Class.forName(VIA_SMS_INTERFACES_IMPL_CLASS_NAME);
            return (ISmsInterfaces) policyClass.newInstance();
        } catch (ClassNotFoundException ex) {
            log("makeSmsInterfaces ClassNotFoundException, return default DefaultSmsInterfaces");
            return new DefaultSmsInterfaces();
        } catch (IllegalAccessException ex) {
            log("makeSmsInterfaces IllegalAccessException, return default DefaultSmsInterfaces");
            return new DefaultSmsInterfaces();
        } catch (InstantiationException ex) {
            log("makeSmsInterfaces InstantiationException, return default DefaultSmsInterfaces");
            return new DefaultSmsInterfaces();
        }
    }

    private static IUtkService makeUtkService(CommandsInterface ci,
            Context context, UiccCard ic) {
        try {
            Class policyClass = Class.forName(VIA_UTKSERVICE_IMPL_CLASS_NAME);
            Method mUtkMethod = policyClass.getMethod("getInstance", CommandsInterface.class,
                    Context.class, UiccCard.class);
            return (IUtkService) mUtkMethod.invoke(null, ci, context, ic);
        } catch (ClassNotFoundException ex) {
            log("makeUtkService ClassNotFoundException, return default DefaultUtkService");
            return new DefaultUtkService();
        } catch (IllegalAccessException ex) {
            log("makeUtkService IllegalAccessException, return default DefaultUtkService");
            return new DefaultUtkService();
        } catch (NoSuchMethodException ex) {
            log("makeUtkService NoSuchMethodException, return default DefaultUtkService");
            return new DefaultUtkService();
        } catch (InvocationTargetException ex) {
            log("makeUtkService InvocationTargetException, return default DefaultUtkService");
            return new DefaultUtkService();
        }
    }

    private static IGpsProcess makeGpsProcess(Context context,
            CDMAPhone phone, CommandsInterface ci) {
        try {
            Class gpsProcessClass = Class.forName(VIA_GPS_PROCESS_CLASS_NAME);
            Constructor c = gpsProcessClass.getDeclaredConstructor(
                 new Class[]{Context.class, CDMAPhone.class, CommandsInterface.class});
            c.setAccessible(true);
            return (IGpsProcess) c.newInstance(new Object[] {context, phone, ci});
        } catch (ClassNotFoundException ex) {
            log("createGpsProcess ClassNotFoundException, return default DefaultGpsProcess");
            return new DefaultGpsProcess(context, phone, ci);
        } catch (IllegalAccessException ex) {
            log("createGpsProcess IllegalAccessException, return default DefaultGpsProcess");
            return new DefaultGpsProcess(context, phone, ci);
        } catch (InstantiationException ex) {
            log("createGpsProcess InstantiationException, return default DefaultGpsProcess");
            return new DefaultGpsProcess(context, phone, ci);
        } catch (NoSuchMethodException ex) {
            log("createGpsProcess NoSuchMethodException, return default DefaultGpsProcess");
            return new DefaultGpsProcess(context, phone, ci);
        } catch (InvocationTargetException ex) {
            log("createGpsProcess InvocationTargetException, return default DefaultGpsProcess");
            return new DefaultGpsProcess(context, phone, ci);
        }
    }

    private static void log(String string) {
        Rlog.d(LOG_TAG, string);
    }
}

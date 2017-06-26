package com.huawei.android.telephony;

//////////////////////////zhouguanghui check LTE on or off
import android.util.Log;
import com.android.internal.telecom.ITelecomService;
import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyProperties;
import android.os.RemoteException;
import android.content.Context;
import android.os.ServiceManager;
import com.android.internal.telephony.Phone;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.RadioAccessFamily;
import com.android.internal.telephony.PhoneFactory;
////////////////////////////END zgh
/**
 * Addon for the class {@link android.telephony.TelephonyManager}
 * <p>
 *
 * @see android.telephony.TelephonyManager
 */
public class TelephonyManagerCustEx {
	private static final String LOG_TAG = "xionghaifeng";

    /*
     * Network mode.
     * This value can be either a state, an action can also be.
     *
     * MODE_LTE_OFF_EX: shut down the LTE mode.
     * MODE_LTETDD_ONLY_EX: LTE ONLY mode.
     * MODE_LTE_AND_AUTO_EX: shut down LTE ONLY mode, but LTE is open.
     * MODE_ERROR_EX: exception mode.
     *
     * @see #setDataSettingMode(DataSettingModeTypeEx)
     * @see #getDataSettingMode()
     */

    public enum DataSettingModeTypeEx {
        MODE_LTE_OFF_EX,
        MODE_LTETDD_ONLY_EX,
        MODE_LTE_AND_AUTO_EX,
        MODE_ERROR_EX,
    };

    /*
     * Set the network mode that modem will work in.
     *
     * @param dataMode Network mode.
     *
     * @see #DataSettingModeTypeEx
     */

    public static void setDataSettingMode(DataSettingModeTypeEx dataMode) {
        //throw new NoExtAPIException("method not supported.");
    }

    /*
     * Get the network mode that modem is working in.
     *
     * @see #DataSettingModeTypeEx
     */

    public static DataSettingModeTypeEx getDataSettingMode() {
        //throw new NoExtAPIException("method not supported.");
		return DataSettingModeTypeEx.MODE_LTE_AND_AUTO_EX;
    }

    /* BEGIN PN: DTS2014072606694, Added by liugaolong 00273199, 2014/8/5 */
    /**
     * Set LTE service enable or disable.
     *
     * @param sub  Card slot
     * @param ablitiy  Enable LTE service or not. 1: enable, 0: disable.
     *
     * @see com.huawei.telephony.HuaweiTelephonyManager#setLteServiceAbility(int)
     */
    public static void setLteServiceAbility(int sub, int ability) throws RemoteException{
				boolean isLteOnOrOff = (ability == 1) ? true : false;
				Log.i(LOG_TAG,"isLteOnOrOff >> " + isLteOnOrOff);
				//SubscriptionManager.setLteServiceAbilityEx(isLteOnOrOff); dianxin
				SubscriptionManager.setLteServiceAbilityExTwo(isLteOnOrOff,sub);
    }

    /////////////////////////////////////////////zhouguanghui check LTE on or off
    /** Network type is unknown */
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    /** Current network is GPRS */
    public static final int NETWORK_TYPE_GPRS = 1;
    /** Current network is EDGE */
    public static final int NETWORK_TYPE_EDGE = 2;
    /** Current network is UMTS */
    public static final int NETWORK_TYPE_UMTS = 3;
    /** Current network is CDMA: Either IS95A or IS95B*/
    public static final int NETWORK_TYPE_CDMA = 4;
    /** Current network is EVDO revision 0*/
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    /** Current network is EVDO revision A*/
    public static final int NETWORK_TYPE_EVDO_A = 6;
    /** Current network is 1xRTT*/
    public static final int NETWORK_TYPE_1xRTT = 7;
    /** Current network is HSDPA */
    public static final int NETWORK_TYPE_HSDPA = 8;
    /** Current network is HSUPA */
    public static final int NETWORK_TYPE_HSUPA = 9;
    /** Current network is HSPA */
    public static final int NETWORK_TYPE_HSPA = 10;
    /** Current network is iDen */
    public static final int NETWORK_TYPE_IDEN = 11;
    /** Current network is EVDO revision B*/
    public static final int NETWORK_TYPE_EVDO_B = 12;
    /** Current network is LTE */
    public static final int NETWORK_TYPE_LTE = 13;
    /** Current network is eHRPD */
    public static final int NETWORK_TYPE_EHRPD = 14;
    /** Current network is HSPA+ */
    public static final int NETWORK_TYPE_HSPAP = 15;
    /** Current network is GSM {@hide} */
    public static final int NETWORK_TYPE_GSM = 16;


    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for a subscription.
     * @return the network type
     *
     * @param subId for which network type is returned
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
     */
    /** {@hide} */
   public static int getNetworkType(int subId) {
       try {
           ITelephony telephony = getITelephony();
           if (telephony != null) {
				Log.i(LOG_TAG,"telephony is not null === >>> " + telephony.getNetworkTypeForSubscriber(subId));
				return telephony.getNetworkTypeForSubscriber(subId);
           } else {
				Log.i(LOG_TAG,"telephony is not null === >>> " + NETWORK_TYPE_UNKNOWN);
               // This can happen when the ITelephony interface is not up yet.
               return NETWORK_TYPE_UNKNOWN;
           }
       } catch(RemoteException ex) {
           // This shouldn't happen in the normal case
           return NETWORK_TYPE_UNKNOWN;
       } catch (NullPointerException ex) {
           // This could happen before phone restarts due to crashing
           return NETWORK_TYPE_UNKNOWN;
       }
   }

    /**
    * @hide
    */
    private static ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }
    ///////////////////////////////////////////////////END zgh

    /**
     * Check network mode is LTE on or not.
     *
     * @param sub  Card slot
     * @param nwMode  Network mode.
     *
     * @return int  Wether LTE service on or off.
     *  1: LTE service on
     *  0: LTE service off
     *
     * @see com.huawei.telephony.HuaweiTelephonyManager#checkLteServiceAbiltiy(int)
     * @see com.huawei.telephony.HuaweiTelephonyManager#getLteServiceAbility()
     */
    public static int checkLteServiceAbiltiy(int sub, int nwMode) {
		//write value to DB, first it is on;
		return SubscriptionManager.getLteServiceAbiltiy(sub);
		/*
		/////////////////////////////////////zhouguanghui for check LTE on or off
		int[] subId = SubscriptionManager.getSubId(sub);
		int networkType = getNetworkType(subId[0]);

		if(networkType == NETWORK_TYPE_LTE){
		   networkType = 1;
		}else{
		   networkType = 0;
		}
		Log.i("MingYue","check LTE on or off networkType = >> " + networkType);
		return networkType;
		////////////////////////////////END zgh
		//return 1;
		//throw new NoExtAPIException("method not supported.");*/
    }
    /* END   PN: DTS2014072606694, Added by liugaolong 00273199, 2014/8/5 */
}

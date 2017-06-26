package com.mediatek.phone.ext;

import android.content.Context;
import android.telephony.ServiceState;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SubscriptionController;
import android.util.Log;
import com.android.internal.telephony.Phone;
import com.mediatek.common.MPlugin;
import com.mediatek.common.PluginImpl;
import com.mediatek.common.telephony.ILteDataOnlyController;
/**
 * Telephony connection service extension plugin for op09.
*/
public class DefaultTelephonyConnectionServiceExt implements ITelephonyConnectionServiceExt {
    private ILteDataOnlyController mLteDataOnlyController;

    /**
     * Check courrent mode is 4G data only mode.
     *
     * @param context from telephony connection service.
     * @param phone is call via by user
     * @return true if in 4G data only mode.
     */
     public boolean isDataOnlyMode(Context context, Phone phone) {
        //Log.d("context : " + context + " phone : " + phone);
        if (null == context || null == phone) {
            return false;
        }
        int state = phone.getServiceState().getState();
        int slotId = SubscriptionController.getInstance().getSlotId(phone.getSubId());

        mLteDataOnlyController = MPlugin.createInstance(
                ILteDataOnlyController.class.getName(), context);
        if (slotId == 0 && ServiceState.STATE_OUT_OF_SERVICE == state
                && !mLteDataOnlyController.checkPermission()) {
            return true;
        }
        return false;
    }
}


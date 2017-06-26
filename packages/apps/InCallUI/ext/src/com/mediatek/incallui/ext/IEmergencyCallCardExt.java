package com.mediatek.incallui.ext;

public interface IEmergencyCallCardExt {

    /**
     * Get the call's address if the current call is emergency call.
     * 
     * @param address
     *            call's address(may phone number or other)
     * @param isEmergency
     *            mark the current call is emergency or not.
     * @return current call's address
     */
    String getEmergencyCallAddress(String address, boolean isEmergency);
}

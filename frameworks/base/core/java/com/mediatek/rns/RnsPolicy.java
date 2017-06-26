package com.mediatek.rns;

/**
 * Describe all type of policies for Rns used.
 */
public class RnsPolicy {
    public static final String POLICY_NAME_PREFERENCE = "UserPreference";
    public static final String POLICY_NAME_ROVE_THRESHOLD = "WifiRoveThreshold";

    private UserPreference mPreference;
    private WifiRoveThreshold mRoveThreshold;

    /**
     * Create the policy by user preference.
     * @param u for user prefernce
     */
    public RnsPolicy(UserPreference u) {
        mPreference = u;
    }

    /**
     * Create the policy by rove in/out rssi threshold.
     * @param w for rssi threshold
     */
    public RnsPolicy(WifiRoveThreshold w) {
        mRoveThreshold = w;
    }

    /**
     * get user preference.
     * @return user prefence
     */
    public UserPreference getUserPreference() {
        return mPreference;
    }

    /**
     * get wifi rove in/out threshold.
     * @return rssi threshold
     */
    public WifiRoveThreshold getWifiRoveThreshold() {
        return mRoveThreshold;
    }

    /**
     * Describe the policy of user preference.
     */
    public static class UserPreference {
        public static final int PREFERENCE_NONE = -1;
        public static final int PREFERENCE_CELLULAR_ONLY = 0;
        public static final int PREFERENCE_WIFI_PREFERRED = 1;
        public static final int PREFERENCE_CELLULAR_PREFERRED = 2;
        public static final int PREFERENCE_WIFI_ONLY = 3;

        private int mMode = PREFERENCE_NONE;

        /**
         * create user preference.
         * @param m mode selection
         */
        public UserPreference(int m) {
            mMode = m;
        }

        /**
         * create user preference.
         * @return mode
         */
        public int getMode() {
            return mMode;
        }

        /**
         * set preference mode.
         * @param m mode selection
         */
        public void setMode(int m) {
            mMode = m;
        }
    }

    /**
     * Describe the policy of wifi rove in/out threshold.
     */
    public static class WifiRoveThreshold {
        /* rove-in when a Wi-Fi RSSI level of the value(dBm) is met */
        private int mRoveIn;
        /* rove-out when a Wi-Fi RSSI level of the value(dBm) is met */
        private int mRoveOut;

        /**
         * create wifi rove in/out rssi.
         * @param in rove in rssi
         * @param out rove out rssi
         */
        public WifiRoveThreshold(int in, int out) {
            mRoveIn = in;
            mRoveOut = out;
        }

        /**
         * get rove in rssi.
         * @return rssi
         */
        public int getRssiRoveIn() {
            return mRoveIn;
        }

        /**
         * get rove out rssi.
         * @return rssi
         */
        public int getRssiRoveOut() {
            return mRoveOut;
        }

        /**
         * set rove in rssi.
         * @param v value of rssi
         */
        public void setRssiRoveIn(int v) {
            mRoveIn = v;
        }

        /**
         * set rove out rssi.
         * @param v value of rssi
         */
        public void setRssiRoveOut(int v) {
             mRoveOut = v;
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(getClass().getSimpleName()).append("[");
        buffer.append("UserPreference=" + (mPreference == null ?
            "null" : mPreference.getMode()));
        buffer.append(" WifiRoveThreshold=" + (mRoveThreshold == null ?
            "null" : (" in=" + mRoveThreshold.getRssiRoveIn() + "out=" +
            mRoveThreshold.getRssiRoveOut())));
        buffer.append("]");
        return buffer.toString();
    }

}


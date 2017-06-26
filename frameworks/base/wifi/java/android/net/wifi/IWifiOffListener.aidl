package android.net.wifi;

/** for IMS to know wifi is going to off {@hide} */
interface IWifiOffListener
{
    /**
     * When wifi off
     * @param reason of wifi off
     */
    void onWifiOff(int reason);
}

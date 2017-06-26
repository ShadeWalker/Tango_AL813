package com.mediatek.nfc.porting;

//import android.net.wifi.p2p.WifiP2pService;


public class WifiP2pFastConnectInfo {
    private static final String TAG = "WifiP2pFastConnectInfo";
    public int networkId = -1;
    public int venderId = -1;
    public String deviceAddress = "";
    public String ssid = "";
    public String authType = "";
    public String encrType = "";
    public String psk = "";
    public String goIpAddress = "blahblah"; /// WifiP2pService.SERVER_ADDRESS;
    public String gcIpAddress = "192.168.49.2";
}

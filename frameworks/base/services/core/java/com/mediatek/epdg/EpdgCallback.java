package com.mediatek.epdg;

/**
 *
 * @hide
 */

@SuppressWarnings("javadocmethod")
public interface EpdgCallback {

    public void onEpdgConnected(String apn, int statusCode, String nwInterface,
        String tunnelIpv4, String tunnelIpv6,
        String pcscfIpv4Addr, String pcscfIpv6Addr,
        String dnsIpv4Addr, String dnsIpv6Addr);

    public void onEpdgConnectFailed(String apn, int statusCode);

    public void onEpdgDisconnected(String apn);

    public void onEpdgSimAuthenticate(String apn, byte[] rand, byte[] autn);

}
package com.mediatek.epdg;


/**
 *
 * Constant class for EPDG SW module.
 * @hide
 */

class EpdgCommand {


    EpdgCommand() {

    }

    static String getCommandByType(EpdgConfig config, int cmdType) {
        StringBuilder builder = new StringBuilder("");

        if (EpdgConstants.ATTACH_COMMMAND == cmdType) {
            builder.append(EpdgConstants.ATTACH_DATA + "=\"")
                .append(config.apnName).append("\",\"")
                .append(config.imsi).append("\",\"").
                append(config.mnc).append("\",\"").append(config.mcc).append("\",\"")
                .append(config.wifiInterface).append("\",");

            if (config.wifiIpv6Address != null) {
                builder.append("\"").append(config.wifiIpv6Address.getHostAddress()).append("\",");
            } else {
                builder.append("\"\",");
            }

            if (config.wifiIpv4Address != null) {
                builder.append("\"").append(config.wifiIpv4Address.getHostAddress()).append("\",");
            } else {
                builder.append("\"\",");
            }

            builder.append("\"" + config.edpgServerAddress
                + "\",").append(config.accessIpType).append("," + config.authType
                + "," + config.mobilityProtocol);

            if (config.certPath != null && config.certPath.length() > 0) {
                builder.append(",\"" + config.certPath + "\"");
            } else {
                builder.append(",\"\"");
            }

            if (config.ikeaAlgo != null && config.ikeaAlgo.length() > 0) {
                builder.append(",\"" + config.ikeaAlgo + "\"");
            } else {
                builder.append(",\"\"");
            }

            if (config.espAlgo != null && config.espAlgo.length() > 0) {
                builder.append(",\"" + config.espAlgo + "\"");
            }
        } else if (EpdgConstants.DETACH_COMMMAND == cmdType) {
            builder.append(EpdgConstants.DETACH_DATA + "=\"").append(config.apnName).append("\"");
        } else if (EpdgConstants.HANDOVER_COMMMAND == cmdType) {
            builder.append(EpdgConstants.HANDOVER_DATA + "=\"")
                .append(config.apnName).append("\",\"")
                .append(config.imsi).append("\",\"").
                append(config.mnc).append("\",\"").append(config.mcc).append("\",\"")
                .append(config.wifiInterface).append("\",");

            if (config.wifiIpv6Address != null) {
                builder.append("\"").append(config.wifiIpv6Address.getHostAddress()).append("\",");
            } else {
                builder.append("\"\",");
            }

            if (config.wifiIpv4Address != null) {
                builder.append("\"").append(config.wifiIpv4Address.getHostAddress()).append("\",");
            } else {
                builder.append("\"\",");
            }

            builder.append("\"" + config.edpgServerAddress
                + "\",").append(config.accessIpType).append(",");

            if (config.epdgIpv6Address != null) {
                String ipAddr = config.epdgIpv6Address.getHostAddress();
                if (ipAddr.indexOf("%") != -1) {
                    ipAddr = ipAddr.substring(0, ipAddr.indexOf("%"));
                }
                builder.append("\"").append(ipAddr).append("\",");
            } else {
                builder.append("\"\",");
            }

            if (config.epdgIpv4Address != null) {
                builder.append("\"").append(config.epdgIpv4Address.getHostAddress()).append("\",");
            } else {
                builder.append("\"\",");
            }

            builder.append(config.authType
                + "," + config.mobilityProtocol);

            if (config.certPath != null && config.certPath.length() > 0) {
                builder.append(",\"" + config.certPath + "\"");
            } else {
                builder.append(",\"\"");
            }

            if (config.ikeaAlgo != null && config.ikeaAlgo.length() > 0) {
                builder.append(",\"" + config.ikeaAlgo + "\"");
            } else {
                builder.append(",\"\"");
            }

            if (config.espAlgo != null && config.espAlgo.length() > 0) {
                builder.append(",\"" + config.espAlgo + "\"");
            }
        } else if (EpdgConstants.KEEPALIVE_COMMMAND == cmdType) {
            builder.append(EpdgConstants.KEEPALIVER_DATA);
        }

        return builder.toString();
    }
}
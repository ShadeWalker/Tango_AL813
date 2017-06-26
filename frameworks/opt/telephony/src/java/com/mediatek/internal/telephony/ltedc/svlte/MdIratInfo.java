package com.mediatek.internal.telephony.ltedc.svlte;

/**
 * MD IRAT information.
 * @hide
 */
public class MdIratInfo {
    /**
     * IRAT type.
     */
    public enum IratType {
        IRAT_TYPE_UNKNOWN(0),
        IRAT_TYPE_LTE_EHRPD(1),
        IRAT_TYPE_LTE_HRPD(2),
        IRAT_TYPE_EHRPD_LTE(3),
        IRAT_TYPE_HRPD_LTE(4),
        IRAT_TYPE_FAILED(5);

        private static String[] sIratTypeStrings = {
            "Unknown type",
            "LTE -> EHRPD",
            "LTE -> HRPD",
            "EHRPD -> LTE",
            "HRPD -> LTE",
            "IRAT failed"
        };

        private int mCode;

        private IratType(int code) {
            mCode = code;
        }

        public boolean isIpContinuousCase() {
            return this == IRAT_TYPE_LTE_EHRPD || this == IRAT_TYPE_EHRPD_LTE;
        }

        public boolean isFallbackCase() {
            return this == IRAT_TYPE_LTE_HRPD || this == IRAT_TYPE_HRPD_LTE;
        }

        public boolean isFailCase() {
            return this == IRAT_TYPE_FAILED;
        }

        /**
         * Convert int value to IRAT type.
         * @param typeInt Int value of the type.
         * @return IRAT type of the int value.
         */
        public static IratType getIratTypeFromInt(int typeInt) {
            IratType type;

            switch (typeInt) {
                case 0:
                    type = IratType.IRAT_TYPE_UNKNOWN;
                    break;
                case 1:
                    type = IratType.IRAT_TYPE_LTE_EHRPD;
                    break;
                case 2:
                    type = IratType.IRAT_TYPE_LTE_HRPD;
                    break;
                case 3:
                    type = IratType.IRAT_TYPE_EHRPD_LTE;
                    break;
                case 4:
                    type = IratType.IRAT_TYPE_HRPD_LTE;
                    break;
                case 5:
                    type = IratType.IRAT_TYPE_FAILED;
                    break;
                default:
                    throw new RuntimeException("Unrecognized IratType: "
                            + typeInt);
            }
            return type;
        }

        @Override
        public String toString() {
            return sIratTypeStrings[mCode];
        }
    };

    public int sourceRat = 0;
    public int targetRat = 0;
    public int action = 0;
    public IratType type = IratType.IRAT_TYPE_UNKNOWN;

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("MdIratInfo: {").append("sourceRat=").append(sourceRat)
                .append(" targetRat=").append(targetRat).append(" action=")
                .append(action).append(" type=").append(type);
        sb.append("]}");
        return sb.toString();
    }

}
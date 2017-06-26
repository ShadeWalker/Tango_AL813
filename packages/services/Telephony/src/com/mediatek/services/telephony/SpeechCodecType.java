/*
* Copyright (C) 2011-2014 MediaTek Inc.
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
package com.mediatek.services.telephony;

import java.util.HashMap;

/**
 * Returned as the Voice codec Type definition.
 */
public enum SpeechCodecType {
    NONE(0),
    QCELP13K(0x0001),
    EVRC(0x0002),
    EVRC_B(0x0003),
    EVRC_WB(0x0004),
    EVRC_NW(0x0005),
    AMR_NB(0x0006),
    AMR_WB(0x0007),
    GSM_EFR(0x0008),
    GSM_FR(0x0009),
    GSM_HR(0x000A);

    private final int mValue;
    private static final HashMap<Integer, SpeechCodecType> sValueToSpeechCodecTypeMap;
    static {
        sValueToSpeechCodecTypeMap = new HashMap<Integer, SpeechCodecType>();
        for (SpeechCodecType sc : values()) {
            sValueToSpeechCodecTypeMap.put(sc.getValue(), sc);
        }
    }

    SpeechCodecType(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    public boolean isHighDefAudio() {
        return (this == EVRC_WB || this == AMR_WB);
    }


    /**
     *
     * fromInt: Transfers the type to SpeechCodecType Enum.
     *
     * @param value speech codec type
     * @return SpeechCodecType
     */
    public static SpeechCodecType fromInt(int value) {
        SpeechCodecType type = sValueToSpeechCodecTypeMap.get(value);
        if (type == null) {
            type = NONE;
        }
        return type;
    }
}

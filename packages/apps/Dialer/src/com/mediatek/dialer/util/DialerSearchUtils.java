package com.mediatek.dialer.util;

import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;


import java.util.ArrayList;
import java.util.HashSet;

import com.android.dialer.dialpad.LatinSmartDialMap;

public class DialerSearchUtils {

    private static final String TAG = "DialerSearchUtils";
    private static LatinSmartDialMap sMap = new LatinSmartDialMap();

    private static final HashSet<Character> HYPHON_CHARACTERS = new HashSet<Character>();
    static {
        HYPHON_CHARACTERS.add(' ');
        HYPHON_CHARACTERS.add('-');
        HYPHON_CHARACTERS.add('(');
        HYPHON_CHARACTERS.add(')');
    }


    public static String tripHyphen(String number) {
        if (TextUtils.isEmpty(number)) {
            return number;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (!HYPHON_CHARACTERS.contains(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String tripNonDigit(String number) {
        if (TextUtils.isEmpty(number)) {
            return number;
        }

        StringBuilder sb = new StringBuilder();
        int len = number.length();

        for (int i = 0; i < len; i++) {
            char c = number.charAt(i);
            if (PhoneNumberUtils.isNonSeparator(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static ArrayList<Integer> adjustHighlitePositionForHyphen(String number,
            String numberMatchedOffsets, String originNumber) {
        ArrayList<Integer> res = new ArrayList<Integer>();
        try {
            int highliteBegin = (int) numberMatchedOffsets.charAt(0);
            int highliteEnd = (int) numberMatchedOffsets.charAt(1);
            int originNumberBegin = 0;
            String targetTemp = "";
            for (int i = 0; i < number.length(); i++) {
                char c = number.charAt(i);
                if (HYPHON_CHARACTERS.contains(c)) {
                    continue;
                }
                targetTemp += c;
            }
            originNumberBegin = originNumber.indexOf(targetTemp);

            if (highliteBegin > highliteEnd) {
                return res;
            }

            if ((originNumberBegin >= highliteEnd) && highliteEnd >= 1) {
                highliteEnd = 0;
            }

            if (highliteEnd > originNumberBegin) {
                highliteEnd = highliteEnd - originNumberBegin;
            }

            if (highliteBegin >= originNumberBegin) {
                highliteBegin = highliteBegin - originNumberBegin;
            }

            for (int i = 0; i <= highliteBegin; i++) {
                char c = number.charAt(i);
                if (HYPHON_CHARACTERS.contains(c)) {
                    highliteBegin++;
                    highliteEnd++;
                }
            }

            for (int i = highliteBegin + 1; (i <= highliteEnd && i < number.length()); i++) {
                char c = number.charAt(i);
                if (HYPHON_CHARACTERS.contains(c)) {
                    highliteEnd++;
                }
            }

            if (highliteEnd >= number.length()) {
                highliteEnd = number.length() - 1;
            }
            res.add(highliteBegin);
            res.add(highliteEnd);
        } catch (Exception e) {
            Log.i(TAG, "number = " + number + " numberMatchedOffsets = " + numberMatchedOffsets
                    + " originNumber = " + originNumber);
            e.printStackTrace();
            return null;
        }
        return res;
    }

    public static boolean isValidDialpadCharacter(char ch) {
        return sMap.isValidDialpadCharacter(ch);
    }

    public static boolean isInValidDialpadString(String string) {
        if (string != null) {
            for (int i = 0; i < string.length(); i++) {
                char ch = string.charAt(i);

                if (!isValidDialpadCharacter(ch)) {
                    return true;
                }
            }
        }
        return false;
    }
}

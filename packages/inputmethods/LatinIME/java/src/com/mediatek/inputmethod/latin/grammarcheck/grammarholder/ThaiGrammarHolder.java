/*
 * Copyright (C) 2013 MediaTek Inc. All Rights Reserved.
 *
 * This is the grammar checker for Thai and grammar in detail
 * is listed below.
 */

package com.mediatek.inputmethod.latin.grammarcheck.grammarholder;

import android.util.Log;

public class ThaiGrammarHolder implements GrammarHolder {

  /**
   * Below is the grammar for thai:
   * All valid characters: [0E01, 0E3A], [0E3F, 0E4D], [0E50, 0E59]
   *       0E01  0E02  0E03  0E04  0E05  0E06  0E07  0E08  0E09  0E0A  0E0B  0E0C  0E0D  0E0E  0E0F
   * 0E10  0E11  0E12  0E13  0E14  0E15  0E16  0E17  0E18  0E19  0E1A  0E1B  0E1C  0E1D  0E1E  0E1F
   * 0E20  0E21  0E22  0E23  0E24  0E25  0E26  0E27  0E28  0E29  0E2A  0E2B  0E2C  0E2D  0E2E  0E2F
   * 0E30  0E31  0E32  0E33  0E34  0E35  0E36  0E37  0E38  0E39  0E3A                          0E3F
   * 0E40  0E41  0E42  0E43  0E44  0E45  0E46  0E47  0E48  0E49  0E4A  0E4B  0E4C  0E4D
   * 0E50  0E51  0E52  0E53  0E54  0E55  0E56  0E57  0E58  0E59
   *
   * All valid vowels:[0E01, 0E33], [0E3F, 0E46], [0E50, 0E59]
   *       0E01  0E02  0E03  0E04  0E05  0E06  0E07  0E08  0E09  0E0A  0E0B  0E0C  0E0D  0E0E  0E0F
   * 0E10  0E11  0E12  0E13  0E14  0E15  0E16  0E17  0E18  0E19  0E1A  0E1B  0E1C  0E1D  0E1E  0E1F
   * 0E20  0E21  0E22  0E23  0E24  0E25  0E26  0E27  0E28  0E29  0E2A  0E2B  0E2C  0E2D  0E2E  0E2F
   * 0E30        0E32                                                                          0E3F
   * 0E40  0E41  0E42  0E43  0E44  0E45  0E46
   * 0E50  0E51  0E52  0E53  0E54  0E55  0E56  0E57  0E58  0E59
   *
   * All valid Tone: [0E33, 0E37], [0E47, 0E4D]
   *
   *
   *
   *        0E31        0E33  0E34  0E35  0E36 0E37
   *                                           0E47  0E48  0E49  0E4A  0E4B  0E4C  0E4D
   *
   * Valid Tone Marks in pair:
   * 0E31 :                                           0E48  0E49  0E4A  0E4B
   * 0E33 :
   * 0E34 :                                           0E48  0E49  0E4A  0E4B  0E4C
   * 0E35 :                                     0E47  0E48  0E49  0E4A  0E4B
   * 0E36 :                                           0E48  0E49  0E4A  0E4B
   * 0E37 :                                     0E47  0E48  0E49  0E4A  0E4B
   * 0E47 :
   * 0E48 :       0E33
   * 0E49 :       0E33
   * 0E4A :       0E33
   * 0E4B :       0E33
   * 0E4C :
   * 0E4D :                                           0E48  0E49  0E4A  0E4B  0E4C
   *
   * All Valid Subscript
   *                                                 0E38  0E39  0E3A
   */
  private static final String TAG = "ThaiGrammarHolder";
  private static boolean DEBUG = true;

  private boolean isThaiSymbol(int charCode) {
    if (((charCode >= 0x0E01) && (charCode <= 0x0E3A))
      || ((charCode >= 0x0E3F) && (charCode <= 0x0E4D))
      /*|| ((charCode >= 0x0E50) && (charCode <= 0x0E59))*/) {
      if (DEBUG) Log.d(TAG, "Char : " + charCode + " is valid Thai symbol");
      return true;
    }
    return false;
  }

  private boolean isSubScript(int charCode) {
    if ((charCode >= 0x0E38) && (charCode <= 0x0E3A)) {
      if (DEBUG)
        Log.d(TAG, "Char : " + charCode + " is subscript");
      return true;
    }

    return false;
  }

  private boolean isTone(int charCode) {
    if ((charCode == 0x0E31)
      || ((charCode >= 0x0E33) && (charCode <= 0x0E37))
      || ((charCode >= 0x0E47) && (charCode <= 0x0E4D))) {

      if (DEBUG) Log.d(TAG, "Char : " + charCode + " is Tone");
        return true;
    }
    return false;
  }

  private boolean isStandaloneVowel(int charCode) {
    if (((charCode >= 0x0E40) && (charCode <= 0x0E46))
      || ((charCode >= 0x0E24) && (charCode <= 0x0E26))
      || (charCode == 0x0E30)
            || (charCode == 0x0E32)
            || (charCode == 0x0E56)
            || (charCode == 0x0E3F)
            || (charCode == 0x0E2F)) {

      if (DEBUG) Log.d(TAG, "Char : " + charCode + " is standalone vowel");
      return true;
    }

    return false;
  }

  private boolean isValidTonePair(int tone1, int tone2) {
    boolean ret = false;

    if (!(isTone(tone1) && isTone(tone2)))
      return true;

    if (isSubScript(tone1) && isTone(tone2))
      return isValidSubAndTonePair(tone1, tone2);

    switch(tone1) {
    case 0x0E31:
    case 0x0E36:
      if ((0x0E48 <= tone2) && (0x0E4B >= tone2))
        ret = true;
      break;
    //case 0x0E33:
    //  break;
    case 0x0E34:
    case 0x0E4D:
      if ((0x0E48 <= tone2) && (0x0E4C >= tone2))
        ret = true;
      break;
    case 0x0E35:
    case 0x0E37:
      if ((0x0E47 <= tone2) && (0x0E4B >= tone2))
        ret = true;
      break;
    //case 0x0E47:
    //  break;
    case 0x0E48:
    case 0x0E49:
    case 0x0E4A:
    case 0x0E4B:
      if (0x0E33 == tone2)
        ret = true;
      break;
    //case 0x0E4C:
    //  break;
    }

    return ret;
  }

  private boolean isValidSubAndTonePair(int code1, int code2) {
      boolean ret = false;

      if (isVowel(code1) || (isVowel(code2)))
        return true;

      // Only pair match [subscript, tone] is accepted
      if (isTone(code1)
          || !isTone(code2))
        return false;

      switch(code1) {
      case 0x0E38:
        if ((0x0E48 <= code2) && (0x0E4B >= code2))
            ret = true;
        break;
      case 0x0E39:
        if ((0x0E48 <= code2) && (0x0E4D >= code2))
            ret = true;
        break;
      //case 0x0E3A:
      //  break;
      }

      return ret;
    }

  public boolean isConsonant(int charCode) {
    return false;
  }

  public boolean isVowel(int charCode) {
    if (((charCode >= 0x0E01) && (charCode <= 0x0E30))
        || (charCode == 0x0E32)
        || ((charCode >= 0x0E3F) && (charCode <= 0x0E46))
        /*|| ((charCode >= 0x0E50) && (charCode <= 0x0E59))*/) {
      if (DEBUG)
        Log.d(TAG, "Char : " + charCode + " is vowel");
      return true;
      }

    return false;
  }

  public boolean isSuperscript(int charCode) {
    return isTone(charCode);
  }

  public boolean isValidForString(String str, char charCode, int position) {

    Log.d(TAG, "isValidForString : " + str + " with position : " + position);
    //Step1. Check input parameters
    if ((position < 0)
        || (position > str.length())) {
      Log.e(TAG, "Bad parameter for position : " + position);
      return false;
    }

    //Step2. Non-standalone vowel is always welcome
    if (isThaiSymbol(charCode)
        && isVowel(charCode)
        && !isStandaloneVowel(charCode))
      return true;

    //Step3. For non thai symbols can never be followed by tone mark
    if (!isThaiSymbol(charCode)) {
      if ((str.length() > position)
          && (isTone(str.charAt(position)))) {
        return false;
      }

      return true;
    }

    //Step4. First charactor can never be tone mark or subscript
    if ((position == 0)
        && (isTone(charCode) || isSubScript(charCode))) {
      if (DEBUG)
        Log.d(TAG, "The first char can not be tone mark");
      return false;
    } else if (str.length() > 0) {
      //Step5. For standalone vowel can not be followed by tong marks
      if (isStandaloneVowel(charCode)) {
        if ((str.length() > position)
            && (isTone(str.charAt(position)))) {
          return false;
        }
      } else if (isSubScript(charCode)) {
        //Step6. For subscript, can not either follow or followed
        //       by tone marks, also can not follow standalone vowels
        //       or non-thai symbols
        if (isTone(str.charAt(position - 1))
            || isStandaloneVowel(str.charAt(position - 1))
            || !isThaiSymbol(str.charAt(position - 1))
            || !isValidSubAndTonePair(str.charAt(position - 1), charCode)
            || ((str.length() > position)
                && (isTone(str.charAt(position)) || (!isValidSubAndTonePair(charCode, str.charAt(position)))))) {
          return false;
        }
      } else {
        //It`s tone mark now
        //Step7. Tone mark can not follow stand alone vowels or non-thai symbols
        if (isStandaloneVowel(str.charAt(position - 1))
            //|| (isSubScript(str.charAt(position - 1)))
            || !isThaiSymbol(str.charAt(position - 1))) {
          if (DEBUG)
            Log.d(TAG, "Char " + str.charAt(position - 1) + " is standalone vowel");
          return false;
        }

        if (isSubScript(str.charAt(position - 1))) {
          return isValidSubAndTonePair(str.charAt(position - 1), charCode);
        }

        if (position >= 2) {
          //Step8. Tone mark can not follow or added between 2 tone marks
          if (isTone(str.charAt(position - 2)) && isTone(str.charAt(position - 1))
              || ((str.length() > position)
                  && isTone(str.charAt(position)) && isTone(str.charAt(position - 1)))) {
              if (DEBUG)
                Log.d(TAG, "Char " + charCode
                    + " can not be added after " + str.substring(0, position - 1));

              return false;
            }

          //Step9. Tone marks should be paired follow some rules
          if (!isValidTonePair(str.charAt(position - 1), charCode)
              || ((str.length() > position)
                  && !isValidTonePair(charCode, str.charAt(position)))) {
            if (DEBUG)
              Log.d(TAG, "Invalid input for invalid tone pairs");

            return false;
          }
        }
      }
    }

    return true;
  }
}

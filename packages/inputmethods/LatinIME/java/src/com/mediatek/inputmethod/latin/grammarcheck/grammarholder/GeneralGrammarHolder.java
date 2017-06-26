/*
 * Copyright (C) 2013 MediaTek Inc. All Rights Reserved.
 */

package com.mediatek.inputmethod.latin.grammarcheck.grammarholder;

public class GeneralGrammarHolder implements GrammarHolder {

  public boolean isVowel(int charCode) {
    return true;
  }

  public boolean isConsonant(int charCode) {
    return true;
  }

  public boolean isSuperscript(int charCode) {
    return true;
  }

  public boolean isValidForString(String str, char charCode, int position) {
    return true;
  }
}

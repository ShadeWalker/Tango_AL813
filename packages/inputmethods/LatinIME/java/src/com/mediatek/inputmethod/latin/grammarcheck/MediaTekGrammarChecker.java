/*
 * Copyright (C) 2013 MediaTek Inc. All Rights Reserved.
 *
 * This is the grammar checker for android keyboard AOSP, this is
 * used to validate the user input code.
 */

package com.mediatek.inputmethod.latin.grammarcheck;

import java.util.Locale;
import android.util.Log;

import com.mediatek.inputmethod.latin.grammarcheck.grammarholder.GeneralGrammarHolder;
import com.mediatek.inputmethod.latin.grammarcheck.grammarholder.GrammarHolder;
import com.mediatek.inputmethod.latin.grammarcheck.grammarholder.ThaiGrammarHolder;

public class MediaTekGrammarChecker {

  private final static String TAG = "MediaTekGrammarChecker";

  GrammarHolder mGrammarHolderInstance;

  Locale thai = new Locale("th");

  public MediaTekGrammarChecker(Locale curLocale) {
    if (curLocale.equals(thai)) {
      mGrammarHolderInstance = new ThaiGrammarHolder();
    } else {
      mGrammarHolderInstance = new GeneralGrammarHolder();
    }
  }

  public boolean isValidInput(String str, char ch, int position) {
    if (mGrammarHolderInstance != null) {
      return mGrammarHolderInstance.isValidForString(str, ch, position);
    } else {
      Log.w(TAG, "GrammarHolderInstance is invalid");
      return true;
    }
  }
}

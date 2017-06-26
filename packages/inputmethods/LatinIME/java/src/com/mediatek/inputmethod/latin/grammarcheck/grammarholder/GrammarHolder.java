/*
 * Copyright (C) 2013 MediaTek Inc. All Rights Reserved.
 */

package com.mediatek.inputmethod.latin.grammarcheck.grammarholder;

public interface GrammarHolder {

  /**
   * This method is used to tell whether a character is a vowel.
   * @param charCode the code for the character to be checked.
   * @return true when <code>charCode</code> is vowel, otherwise false.
   */
  public boolean isVowel(int charCode);

  /**
   * This method is used to tell whether a character is a consonant.
   * @param charCode the code for the character to be checked.
   * @return true when <code>charCode</code> is consonant, otherwise false.
   */
  public boolean isConsonant(int charCode);

  /**
   * This method is used to tell whether a character is a superscript.
   * @param charCode the code for the character to be checked.
   * @return true when <code>charCode</code> is note in superscript, otherwise false.
   */
  public boolean isSuperscript(int charCode);

  /**
   * This method is used to tell whether a character can be added or inserted to a string.
   * e.g. There is a word "Hllo", and to determine whether letter "e" can be inserted in this string
   * to form word "hello", isValidForString("Hllo", 'e', 1) shoule be checked.
   * @param str      the string needs to be modified, maybe charCode will be inserted into certain position.
   * @param charCode the code for the character to be checked.
   * @param position position within str where charCode will be inserted.
   * @return true when <code>charCode</code> is valid to be inserted into
   *         <code>str</code> at <code>position</code>, otherwise false.
   */
  public boolean isValidForString(String str, char charCode, int position);
}

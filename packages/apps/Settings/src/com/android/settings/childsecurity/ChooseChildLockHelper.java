/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.childsecurity;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.ChildMode;
import android.util.Log;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
//add by lihaizhou 0831 by begin
import java.security.*;  
import javax.crypto.Cipher;  
import javax.crypto.SecretKey;  
import javax.crypto.SecretKeyFactory;  
import javax.crypto.spec.DESKeySpec; 
//add by lihaizhou 0831 by end
import java.util.List;

public final class ChooseChildLockHelper {

    public static final String TAG = "ChooseChildLock";
    static final String EXTRA_KEY_PASSWORD = "password";
    private static final String KEY_CHILD_LOCK_TYPE = "child_mode_lock_type";
    private static final String KEY_CHILD_PIN = "child_mode_pin";
    private static final String KEY_CHILD_PATTERN = "child_mode_pattern";
    private static final String KEY_CHILD_PASSWORD_TIP = "child_mode_password_tip";
    public static final int CHILD_SECURITY_MODE_NONE = 0;
    public static final int CHILD_SECURITY_MODE_PATTERN = 1;
    public static final int CHILD_SECURITY_MODE_PIN = 2;

    private Activity mActivity;
    private Fragment mFragment;
    private ContentResolver mResolver;
    //add by lihaizhou 0831 by begin
    private static final String PASSWORD_CRYPT_KEY = "kEHrDooxWHCWtfeSxvDvgqZq";  
    /** 加密算法,可用 DES,DESede,Blowfish. */  
    private final static String ALGORITHM = "DES"; 
    //add by lihaizhou 0831 by end
    public ChooseChildLockHelper(Activity activity) {
        mActivity = activity;
        mResolver = activity.getContentResolver();
    }

    public ChooseChildLockHelper(Activity activity, Fragment fragment) {
        this(activity);
        mFragment = fragment;
    }

    /**
     * If a pattern, password or PIN exists, prompt the user before allowing them to change it.
     * @param message optional message to display about the action about to be done
     * @param details optional detail message to display
     * @return true if one exists and we launched an activity to confirm it
     * @see #onActivityResult(int, int, android.content.Intent)
     */
    public boolean launchConfirmationActivity(int request, CharSequence message, CharSequence details) {
        boolean launched = false;
        Log.i(TAG, "launchConfirm " + request + "/" + message);
        switch (getStoredPasswordQuality()) {
            case CHILD_SECURITY_MODE_PATTERN:
                launched = confirmPattern(request, message, details);
                break;
            case CHILD_SECURITY_MODE_PIN:
                launched = confirmPassword(request);
                break;
        }
        return launched;
    }

    /**
     * Launch screen to confirm the existing lock pattern.
     * @param message shown in header of ConfirmChildPattern if not null
     * @param details shown in footer of ConfirmChildPattern if not null
     * @see #onActivityResult(int, int, android.content.Intent)
     * @return true if we launched an activity to confirm pattern
     */
    private boolean confirmPattern(int request, CharSequence message, CharSequence details) {
        if (!isChildLockEnabled() || !savedPatternExists()) {
            return false;
        }
        final Intent intent = new Intent();
        // supply header and footer text in the intent
        intent.putExtra(ConfirmChildPattern.HEADER_TEXT, message);
        intent.putExtra(ConfirmChildPattern.FOOTER_TEXT, details);
        intent.setClassName("com.android.settings", "com.android.settings.childsecurity.ConfirmChildPattern");
        if (mFragment != null) {
            mFragment.startActivityForResult(intent, request);
        } else {
            mActivity.startActivityForResult(intent, request);
        }
        return true;
    }

    /**
     * Launch screen to confirm the existing lock password.
     * @see #onActivityResult(int, int, android.content.Intent)
     * @return true if we launched an activity to confirm password
     */
    private boolean confirmPassword(int request) {
        if (!isChildLockEnabled()) return false;
        final Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.childsecurity.ConfirmChildPassword");
        if (mFragment != null) {
            mFragment.startActivityForResult(intent, request);
        } else {
            mActivity.startActivityForResult(intent, request);
        }
        return true;
    }

    public boolean isChildLockSet() {
        String type = ChildMode.getString(mResolver, KEY_CHILD_LOCK_TYPE);
        if (type == null || "-1".equals(type)) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isChildLockEnabled() {
        String type = ChildMode.getString(mResolver, KEY_CHILD_LOCK_TYPE);
        if (!isChildLockSet() || "0".equals(type)) {
            return false;
        } else {
            return true;
        }
    }

    public void setChildLockDisabled() {
        boolean success = putString(KEY_CHILD_LOCK_TYPE,
                String.valueOf(CHILD_SECURITY_MODE_NONE));
        Log.i(TAG, "setlock disalbed " + success);
        if (success) {
            clearLock();
        }
    }

    public boolean savedPatternExists() {
        String curPattern = ChildMode.getString(mResolver, KEY_CHILD_PATTERN);
        if (curPattern != null && curPattern.length() > 0) {
            return true;
        }
        return false;
    }

    public int getStoredPasswordQuality() {
        String type = ChildMode.getString(mResolver, KEY_CHILD_LOCK_TYPE);
        Log.i(TAG, "current mode " + type);
        if (type != null) {
             int mode = Integer.parseInt(type);
             if (mode >= 0 && mode < 3) {
                 return mode;
             }
        }
        return CHILD_SECURITY_MODE_NONE;
    }

    public boolean checkPassword(String input) {
        try{
          input = encrypt(input);
       }
        catch(Exception e)
 
       {
       
       }
        Log.i("lihaizhou", "checkPassword " + input);
         try{
          input = decrypt(input);
       }
        catch(Exception e)
 
       {
       
       }
        
        String curPin = ChildMode.getString(mResolver, KEY_CHILD_PIN);
        try{
          curPin=decrypt(curPin);
        }
        catch(Exception e){
        
        }
        if (curPin == null || curPin.equals(input)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean putString(String key, String value) {
        if (ChildMode.getString(mResolver, key) != null) {
            return ChildMode.putString(mResolver, key, value);
        } else {
            ContentValues cv = new ContentValues();
            cv.put(ChildMode.NAME, key);
            cv.put(ChildMode.VALUE, value);
            Uri result = mResolver.insert(ChildMode.COMMON_CONTENT_URI, cv);
            Log.i(TAG, "insert " + result);
            if (result != null) {
                return true;
            } else {
                return false;
            }
        }
    }

    public void saveLockPassword(String pin, int quality) { 
        try{
          pin = encrypt(pin);
       }
        catch(Exception e)
 
       {
       
       }
        boolean success = putString(KEY_CHILD_PIN, pin);
        Log.i("lihaizhou", "saveLockPassword " + pin + " " + success);
        if (success) {
            putString(KEY_CHILD_LOCK_TYPE, String.valueOf(quality));
        }
    }

    public boolean checkPattern(List<LockPatternView.Cell> pattern) {
        Log.i(TAG, "checkPattern " + pattern.size());
        String curPattern = ChildMode.getString(mResolver, KEY_CHILD_PATTERN);
        if (curPattern == null || curPattern.equals(LockPatternUtils.patternToString(pattern))) {
            return true;
        } else {
            return false;
        }
    }

    public void saveLockPattern(List<LockPatternView.Cell> pattern) {
        boolean success = putString(KEY_CHILD_PATTERN,
                LockPatternUtils.patternToString(pattern));
        Log.i(TAG, "saveLockPattern " + pattern.size() + " " + success);
        if (success) {
            putString(KEY_CHILD_LOCK_TYPE, String.valueOf(CHILD_SECURITY_MODE_PATTERN));
        }
    }
    public void clearLock() {
        Log.i(TAG, "clearLock");
        putString(KEY_CHILD_PIN, "");
        putString(KEY_CHILD_PATTERN, "");
    }

    /* add lihaizhou for child mode password tip  20150715 begin */
    public boolean savePasswordTip(String tip) {
        boolean success = putString(KEY_CHILD_PASSWORD_TIP, tip);
        Log.i(TAG, "save tip " + tip + " " + success);
        return success;
    }

    public String getPasswordTip() {
        String tip = ChildMode.getString(mResolver, KEY_CHILD_PASSWORD_TIP);
        Log.i(TAG, "Got tip " + tip);
        return tip;
    }
   /* add lihaizhou for child mode password tip  20150715 end */

/** 
 * create by lihaizhou for ChildMode database Des encrypt by begin      
 * Creation date: 2015-08-31
 */  

    public final static String decrypt(String data) throws Exception {  
        return new String(decrypt(hex2byte(data.getBytes()),  
                PASSWORD_CRYPT_KEY.getBytes()));  
    }    
    public final static String encrypt(String data) throws Exception  {  
        return byte2hex(encrypt(data.getBytes(), PASSWORD_CRYPT_KEY  
                .getBytes()));  
    }    
    private static byte[] encrypt(byte[] data, byte[] key) throws Exception {   
        SecureRandom sr = new SecureRandom();  
        DESKeySpec dks = new DESKeySpec(key);  
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);  
        SecretKey securekey = keyFactory.generateSecret(dks);   
        Cipher cipher = Cipher.getInstance(ALGORITHM);  
        cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);   
        return cipher.doFinal(data);  
    }    
    private static byte[] decrypt(byte[] data, byte[] key) throws Exception {   
        SecureRandom sr = new SecureRandom();   
        DESKeySpec dks = new DESKeySpec(key);   
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);  
        SecretKey securekey = keyFactory.generateSecret(dks);  
        Cipher cipher = Cipher.getInstance(ALGORITHM);   
        cipher.init(Cipher.DECRYPT_MODE, securekey, sr);  
        return cipher.doFinal(data);  
    }  
    public static byte[] hex2byte(byte[] b) {  
        if ((b.length % 2) != 0)  
            throw new IllegalArgumentException("长度不是偶数");  
        byte[] b2 = new byte[b.length / 2];  
        for (int n = 0; n < b.length; n += 2) {  
            String item = new String(b, n, 2);  
            b2[n / 2] = (byte) Integer.parseInt(item, 16);  
        }  
        return b2;  
    }  
    public static String byte2hex(byte[] b) {  
        String hs = "";  
        String stmp = "";  
        for (int n = 0; n < b.length; n++) {  
            stmp = (java.lang.Integer.toHexString(b[n] & 0XFF));  
            if (stmp.length() == 1)  
                hs = hs + "0" + stmp;  
            else  
                hs = hs + stmp;  
        }  
        return hs.toUpperCase();  
    }  
/** 
 * create by lihaizhou for ChildMode database Des encrypt by end      
 * Creation date: 2015-08-31
 */  
} 

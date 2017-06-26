package com.huawei.securitymgr; 
 
import android.os.IBinder; 
 
/** {@hide} */ 
interface IAuthenticationClient { 
   void onMessage(int what, int arg1, int arg2, in byte[] payload); 
} 
 

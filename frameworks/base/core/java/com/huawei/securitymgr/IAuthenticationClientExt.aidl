package com.huawei.securitymgr; 
 
import java.lang.Object;
import android.os.IBinder;
import android.graphics.Bitmap;
import android.os.Bundle;

/** {@hide} */ 
interface IAuthenticationClientExt { 
    void onBundleMessage(int what, int arg1, int arg2, in Bundle arr);
    void onBundleMessage2(int what, int arg1, in int[] lastTouch); 
} 
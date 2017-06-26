package com.huawei.securitymgr; 

import android.os.IBinder; 
import com.huawei.securitymgr.IAuthenticationClient;
import com.huawei.securitymgr.IAuthenticationClientExt;


/** {@hide} */ 
interface IAuthenticationService { 
	boolean open(in IAuthenticationClient client, int authenciationType);
	void setClientExt(in IAuthenticationClientExt clientExt); 
	int startIdentify(in IAuthenticationClient client, in int[] ids, in byte[] challenge);
	void startIdentifyExt(in IAuthenticationClient client, in int[] ids);  
	void abort(in IAuthenticationClient client); 
	void release(in IAuthenticationClient client); 
	int[] getIds(in IAuthenticationClient client); 

	int[] getAuthenticateSupportTypes(); 
	boolean getEnable(int authenciationType); 
	int getAuthenticationState(int stateType); 
	boolean getAssociation(String appPkgName); 
	String getDescription(IAuthenticationClient client, int id); 

	int[] getIdsByPrivacyMode(in IAuthenticationClient client, int mode); 
	int getPrivacyMode(in IAuthenticationClient client, int id); 
	boolean setVibratorSwitch(in IAuthenticationClient client, int switchType, boolean switchState); 
	long getVibratorTime(in IAuthenticationClient client); 
	int startIdentifyForSign(in IAuthenticationClient client, in int[] ids, in byte[] challenge, in int keyType); 
	byte[] getIdentifySignedData(in IAuthenticationClient client); 
	
	void startEnrol(in IAuthenticationClient client, in int id);
	void startGuidedEnrol(in IAuthenticationClient client, in int id);
	boolean removeData(in IAuthenticationClient client, in int id);
	int deadPixelTest(in IAuthenticationClient client);
	int sleepTest(in IAuthenticationClient client);
    int wakeupTest(in IAuthenticationClient client);
    int startUseCase(in IAuthenticationClient client);
    int stopUseCase(in IAuthenticationClient client);
    String getSensorIDName(in IAuthenticationClient client);
} 

package com.mediatek.perfservice;

/** @hide */
interface IPerfService {

    void boostEnable(int scenario);
    void boostDisable(int scenario);
    void boostEnableTimeout(int scenario, int timeout);
    void boostEnableTimeoutMs(int scenario, int timeout_ms);
    void notifyAppState(String packName, String className, int state);

    int  userReg(int scn_core, int scn_freq, int pid, int tid);
    int  userRegBigLittle(int scn_core_big, int scn_freq_big, int scn_core_little, int scn_freq_little, int pid, int tid);
    void userUnreg(int handle);

    int  userGetCapability(int cmd);

    int  userRegScn(int pid, int tid);
    void userRegScnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4);
    void userUnregScn(int handle);

    void userEnable(int handle);
    void userEnableTimeout(int handle, int timeout);
    void userEnableTimeoutMs(int handle, int timeout_ms);
    void userDisable(int handle);

    void userResetAll();
    void userDisableAll();
    void userRestoreAll();

    void dumpAll();

    void setFavorPid(int pid);
    oneway void notifyFrameUpdate(int level);
    oneway void notifyDisplayType(int type);
    oneway void notifyUserStatus(int type, int status);
}

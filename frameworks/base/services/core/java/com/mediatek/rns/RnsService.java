package com.mediatek.rns;

import android.content.Context;
import android.util.Log;

import com.android.server.SystemService;

/**
 * Radio Network Selection Service.
 */
public class RnsService extends SystemService {

    private final String TAG = "RnsService";
    private final boolean DEBUG = true;
    final RnsServiceImpl mImpl;
    /**
     * constructor of rns service.
     * @param context from system server
     */
    public RnsService(Context context) {
        super(context);
        mImpl = new RnsServiceImpl(context);
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Registering service " + Context.RNS_SERVICE);
        publishBinderService(Context.RNS_SERVICE, mImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mImpl.start();
        }
    }
}


package com.mediatek.datashaping;

import android.content.Context;
import android.util.Slog;

import com.android.server.SystemService;

public class DataShapingService extends SystemService {

    private final String TAG = "DataShapingService";
    private DataShapingServiceImpl mImpl;

    public DataShapingService(Context context) {
        super(context);
        mImpl = new DataShapingServiceImpl(context);
    }

    @Override
    public void onStart() {
        Slog.d(TAG, "Start DataShaping Service.");
        publishBinderService(Context.DATA_SHAPING_SERVICE, mImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mImpl.start();
        }
    }
}

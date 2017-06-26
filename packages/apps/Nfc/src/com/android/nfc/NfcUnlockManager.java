package com.android.nfc;

import android.nfc.INfcUnlockHandler;
import android.nfc.Tag;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Singleton for handling NFC Unlock related logic and state.
 */
class NfcUnlockManager {
    private static final String TAG = "NfcUnlockManager";

    private final HashMap<IBinder, UnlockHandlerWrapper> mUnlockHandlers;
    private int mLockscreenPollMask;

    private int registerCount = 0;

    private static class UnlockHandlerWrapper {
        final INfcUnlockHandler mUnlockHandler;
        final int mPollMask;


        private UnlockHandlerWrapper(INfcUnlockHandler unlockHandler, int pollMask) {
            mUnlockHandler = unlockHandler;
            mPollMask = pollMask;
        }
    }

    public static NfcUnlockManager getInstance() {
        return Singleton.INSTANCE;
    }


    synchronized int addUnlockHandler(INfcUnlockHandler unlockHandler, int pollMask) {

        
        Log.d(TAG, "  addUnlockHandler(..)  unlockHandler:"+unlockHandler+"pollMask:"+pollMask);

        if (mUnlockHandlers.containsKey(unlockHandler.asBinder())) {
            return mLockscreenPollMask;
        }

        Log.d(TAG, "++  mUnlockHandlers.put   unlockHandler.asBinder():"+unlockHandler.asBinder());

        registerCount++;
        Log.d(TAG, "**  addUnlockHandler(..)  registerCount:"+registerCount);

        mUnlockHandlers.put(unlockHandler.asBinder(),
                new UnlockHandlerWrapper(unlockHandler, pollMask));

        Log.d(TAG, "  before add: "+mLockscreenPollMask);
        Log.d(TAG, "  after  add: "+(mLockscreenPollMask |= pollMask));

        
        return (mLockscreenPollMask |= pollMask);
    }

    synchronized int removeUnlockHandler(IBinder unlockHandler) {
        
        Log.d(TAG, "  removeUnlockHandler() unlockHandler:"+unlockHandler+" mLockscreenPollMask:"+mLockscreenPollMask);

        Log.d(TAG, "  removeUnlockHandler(..)  registerCount:"+registerCount);
        
        if (mUnlockHandlers.containsKey(unlockHandler)) {
            Log.d(TAG, "++  mUnlockHandlers.remove() unlockHandler:"+unlockHandler);

            registerCount--;
            Log.d(TAG, "**  removeUnlockHandler(..)  registerCount:"+registerCount);

            
            mUnlockHandlers.remove(unlockHandler);
            mLockscreenPollMask = recomputePollMask();
        }

        Log.d(TAG, " removeUnlockHandler() return  mLockscreenPollMask: "+mLockscreenPollMask);

        return mLockscreenPollMask;
    }

    synchronized boolean tryUnlock(Tag tag) {
        Iterator<IBinder> iterator = mUnlockHandlers.keySet().iterator();

        Log.d(TAG, " tryUnlock()  mUnlockHandlers.size()"+mUnlockHandlers.size());
        int i=0;
        while (iterator.hasNext()) {
            try {
                IBinder binder = iterator.next();
                UnlockHandlerWrapper handlerWrapper = mUnlockHandlers.get(binder);
                
                Log.e(TAG, "  i:"+i+"  wrapper.mUnlockHandler   "+handlerWrapper.mUnlockHandler);
                if (handlerWrapper.mUnlockHandler.onUnlockAttempted(tag)) {
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "failed to communicate with unlock handler, removing", e);
                iterator.remove();
                mLockscreenPollMask = recomputePollMask();
            }
            i++;
        }

        return false;
    }

    private int recomputePollMask() {
        
        Log.d(TAG, "  recomputePollMask() remain mUnlockHandlers.size()"+mUnlockHandlers.size());
        
        int pollMask = 0;
        for (UnlockHandlerWrapper wrapper : mUnlockHandlers.values()) {
            Log.d(TAG, "  wrapper.mPollMask:"+wrapper.mPollMask);
            Log.d(TAG, "  wrapper.mUnlockHandler:"+wrapper.mUnlockHandler);
            pollMask |= wrapper.mPollMask;
        }
        Log.d(TAG, "  recomputePollMask() pollMask:"+pollMask);
        
        return pollMask;
    }

    synchronized int getLockscreenPollMask() {
        Log.d(TAG, "getLockscreenPollMask() mLockscreenPollMask:"+mLockscreenPollMask);
        
        return mLockscreenPollMask;
    }

    synchronized boolean isLockscreenPollingEnabled() {

        Log.d(TAG, "isLockscreenPollingEnabled() mLockscreenPollMask:"+mLockscreenPollMask);
        
        return mLockscreenPollMask != 0;
    }

    private static class Singleton {
        private static final NfcUnlockManager INSTANCE = new NfcUnlockManager();
    }

    private NfcUnlockManager() {
        mUnlockHandlers = new HashMap<IBinder, UnlockHandlerWrapper>();
        mLockscreenPollMask = 0;
    }
}


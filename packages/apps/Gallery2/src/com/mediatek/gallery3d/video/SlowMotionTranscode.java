

package com.mediatek.gallery3d.video;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;

import android.content.Context;

import com.mediatek.galleryframework.util.MtkLog;


public class SlowMotionTranscode {

    public interface OnInfoListener {
        boolean onInfo(int msg, int ext1, int ext2);
    }

    static {
        // The runtime will add "lib" on the front and ".o" on the end of
        // the name supplied to loadLibrary.
        System.loadLibrary("jni_slow_motion");
    }

    private static final String TAG = "SlowMotionTranscode";
    private FileDescriptor srcFd;
    private FileDescriptor dstFd;
    private Context mContext;

    private OnInfoListener mOnInfoListener;

    public SlowMotionTranscode(Context context) {
        MtkLog.i(TAG, "SlowMotionTranscode");
        mContext = context;
        native_setup(new WeakReference<SlowMotionTranscode>(this));
    }


    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
    }
    private void onInfo(int msg, int ext1, int ext2) {
        mOnInfoListener.onInfo(msg, ext1, ext2);
    }

    public int stopSaveSpeedEffect() {
        return native_stopSaveSpeedEffect();
    }


    public int setSpeedEffectParams(long startPos, long endPos, String params) {
        return native_setSpeedEffectParams(startPos, endPos, params);
    }

    public int startSaveSpeedEffect(String srcPath, String dstPath) throws IOException {
        RandomAccessFile src;
        RandomAccessFile dst;
        MtkLog.i(TAG, "startSaveSpeedEffect srcPath " + srcPath);
        MtkLog.i(TAG, "startSaveSpeedEffect dstPath " + dstPath);

        src = new RandomAccessFile(srcPath, "r");
        MtkLog.i(TAG, "startSaveSpeedEffect srcfd " +  src.getFD());
        dst = new RandomAccessFile(dstPath, "rw");
        MtkLog.i(TAG, "startSaveSpeedEffect dstfd " +  dst.getFD());
        native_startSaveSpeedEffect(src.getFD(), dst.getFD(), src.length());
        src.close();
        dst.close();
        return 0;
    }

    /**
     * Set speed effect parameters, such as slow motion interval, slow motion speed
     *               caller need call this interface before start speed effect handling
     * @param startPos   start position of speed effect (such as slow motion) interval
     * @param endPos     end position of speed effect (such as slow motion) interval
     * @param params     the speed effect parameters,such as "slow-motion-speed = 4;video-framerate = 30;mute-autio = 0"
     * @return      0 indicate successful, otherwise error type will return
     */
    private static native int native_setSpeedEffectParams(long startPos, long endPos, String params);


    /**
     * Start save the speed effect
     * @param srcFd  File Description of the src file
     * @param dstFd  File Description of the dst file
     * @param srcLength Length of the src file.
     * @return    0 indicate successful, otherwise error type will return
     */
    private static native int native_startSaveSpeedEffect(FileDescriptor srcFd, FileDescriptor dstFd, long srcLength);


    /**
     * Stop the speed effect opertion
     *                 Caller can call this interface if user has cancelled the speed effect.
     *                 Part of the video will be transfered, caller can delete the output video if user cancel the operation
     * @return     0 indicate successful, otherwise error type will return
     */
    private static native int native_stopSaveSpeedEffect();


    /**
     *  Post message from Native.
     */
    private static void postEventFromNative(Object ref, int what, int arg1, int arg2, Object obj) {
        SlowMotionTranscode sm = (SlowMotionTranscode) ((WeakReference) ref).get();
        if (sm == null) {
            MtkLog.e(TAG, "postEventFromNative: Null sm! what=" + what + ", arg1=" + arg1 + ", arg2="
                    + arg2);
            return;
        }
        sm.onInfo(what, arg1, arg2);
    }
    /**
     * Setup slow motion Native runtime.
     *           Should call this firstly before any other operation.
     * @param slowmotion_this
     */
    private native final void native_setup(Object slowmotion_this);


}

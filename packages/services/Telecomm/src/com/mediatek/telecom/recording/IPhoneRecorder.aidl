package com.mediatek.telecom.recording;

import com.mediatek.telecom.recording.IPhoneRecordStateListener;

interface IPhoneRecorder {
    void listen(IPhoneRecordStateListener callback);
    void remove();
    void startRecord();
    void stopRecord(boolean isMount);
    boolean isRecording();
}

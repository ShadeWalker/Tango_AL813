package com.mediatek.galleryfeature.btovgenerator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.mediatek.galleryframework.util.MtkLog;

public class FullBox extends Box {
    public static String TAG = "MtkGallery2/FullBox";
    public static int mCreateTime;
    public static int mFrameNumber = 0;
    public static float mFps = 0;
    public static int mWidth;
    public static int mHeight;
    public static int mMediaTimeScale;
    private static int mTimeScale = 1000;

    public int mTrackID = 1;
    public boolean mIsAudio = false;
    private short mVersion;
    private short mFlags;
    private ArrayList<Entries> mArray = new ArrayList<Entries>();

    public FullBox(String type, int version, int flags) {
        super(type);
        mVersion = (short) version;
        mFlags = (short) flags;
    }

    public void add(int...data) {
        mArray.add(new Entries(data));
    }

    public void write() {
        super.write();
        FileWriter.writeInt16(mVersion);
        FileWriter.writeInt16(mFlags);
        String name = mType.replaceFirst(mType.trim().substring(0, 1), mType.trim().substring(0, 1).toUpperCase());
        String methodName = "write" + name + "Box";

        try {
            Method method = this.getClass().getMethod(methodName);
            method.invoke(this);
        } catch (NoSuchMethodException e) {
            MtkLog.d(TAG, "not find method:" + methodName + ",type:" + mType);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void writeMvhdBox() {
        FileWriter.writeInt32(mCreateTime); //crate time
        FileWriter.writeInt32(mCreateTime); //modification time
        FileWriter.writeInt32(mTimeScale); //time scale
        FileWriter.writeInt32((int) (mFrameNumber * mTimeScale / mFps)); //duration
        FileWriter.writeInt32(0x00010000); //rate
        FileWriter.writeInt16((short) 0x0100); //volume
        FileWriter.writeInt16((short) 0); //reversed
        FileWriter.writeInt32(0); //reversed
        FileWriter.writeInt32(0); //reversed

        FileWriter.writeInt32(0x00010000); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0x00010000); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0x40000000); //matrix

        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined

        FileWriter.writeInt32(2); //next_track_ID
    }

    public void writeTkhdBox() {
        FileWriter.writeInt32(mCreateTime); //crate time
        FileWriter.writeInt32(mCreateTime); //modification time
        FileWriter.writeInt32(mTrackID); //track id
        FileWriter.writeInt32(0); //reversed
        FileWriter.writeInt32((int) (mFrameNumber * mTimeScale / mFps));
        FileWriter.writeInt32(0); //reversed
        FileWriter.writeInt32(0); //reversed
        FileWriter.writeInt16((short) 0); //layer
        FileWriter.writeInt16((short) 0); //alternate_group
        if (mIsAudio) {
            FileWriter.writeInt16((short) 0x0100); //volume
        } else {
            FileWriter.writeInt16((short) 0); //volume
        }
        FileWriter.writeInt16((short) 0); //reversed

        FileWriter.writeInt32(0x00010000); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0x00010000); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0); //matrix
        FileWriter.writeInt32(0x40000000); //matrix

        FileWriter.writeInt32(mWidth << 16); //width
        FileWriter.writeInt32(mHeight << 16); //height
    }

    public void writeMdhdBox() {
        FileWriter.writeInt32(mCreateTime); //crate time
        FileWriter.writeInt32(mCreateTime); //modification time
        FileWriter.writeInt32(mMediaTimeScale); //media time scale
        FileWriter.writeInt32((int) (mFrameNumber * mMediaTimeScale / mFps)); //duration
        FileWriter.writeInt32(0); //pad,language,pre_defined
    }

    public void writeHdlrBox() {
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeString(mIsAudio ? "soun" : "vide", 4); //handler_type
        FileWriter.writeInt32(0); //reserved
        FileWriter.writeInt32(0); //reserved
        FileWriter.writeInt32(0); //reserved
        FileWriter.writeString(mIsAudio ? "SoundHandle " : "VideoHandle ", 12); //handler_type
    }

    public void writeVmhdBox() {
        FileWriter.writeInt16((short) 0);
        FileWriter.writeInt16((short) 0);
        FileWriter.writeInt16((short) 0);
        FileWriter.writeInt16((short) 0);
    }

    public void writeDrefBox() {
        FileWriter.writeInt32(1); //entry_count
    }

    public void writeStsdBox() {
        FileWriter.writeInt32(1); //entry_count
    }

    public void writeSttsBox() {
        FileWriter.writeInt32(mArray.size()); //entry_count
        for (Entries entries : mArray) {
            entries.write();
        }
    }

    public void writeStssBox() {
        FileWriter.writeInt32(mArray.size()); //entry_count
        for (Entries entries : mArray) {
            entries.write();
        }
    }

    public void writeStszBox() {
        FileWriter.writeInt32(0); //sample_size
        FileWriter.writeInt32(mArray.size()); //entry_count
        for (Entries entries : mArray) {
            entries.write();
        }
    }

    public void writeStscBox() {
        FileWriter.writeInt32(mArray.size()); //entry_count
        for (Entries entries : mArray) {
            entries.write();
        }
    }

    public void writeStcoBox() {
        FileWriter.writeInt32(mArray.size()); //entry_count
        for (Entries entries : mArray) {
            entries.write();
        }
    }

    private class Entries {
        private int mdata[];
        public Entries(int...data) {
            mdata = data;
        }
        public void write() {
            for (int data : mdata) {
                FileWriter.writeInt32(data);
            }
        }
    }
}

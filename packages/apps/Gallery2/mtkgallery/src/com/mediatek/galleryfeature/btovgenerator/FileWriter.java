package com.mediatek.galleryfeature.btovgenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.mediatek.galleryframework.util.MtkLog;

public class FileWriter {
    public static String TAG = "MtkGallery2/FileWriter";

    private static FileChannel mFileChannel;
    private static ByteBuffer mHeaderBuf = ByteBuffer.allocate(2 * 1024);

    public static void openFile(String path) {
        MtkLog.d(TAG, "openFile: " + path);
        try {
            File file = new File(path.substring(0, path.lastIndexOf("/")));
            if (!file.exists()) {
                file.mkdirs();
            }
            mFileChannel = new RandomAccessFile(path, "rw").getChannel();
        } catch (FileNotFoundException e) {
            MtkLog.d(TAG, "openFile: file not found exception");
        }
        mHeaderBuf.clear();
    }

    public static int getCurBufPos() {
        return mHeaderBuf.position();
    }

    public static void setBufferData(int pos, int data) {
        int curPos = mHeaderBuf.position();
        mHeaderBuf.position(pos);
        mHeaderBuf.putInt(data);
        mHeaderBuf.position(curPos);
    }

    public static void setFileData(int pos, int data) {
        if (mFileChannel == null) {
            MtkLog.d(TAG, "setFileData, FileChannel is null");
            return;
        }

        mHeaderBuf.putInt(data);
        mHeaderBuf.flip();
        try {
            mFileChannel.write(mHeaderBuf, pos);
        } catch (IOException e) {
            MtkLog.d(TAG, "set file data error");
        }
        mHeaderBuf.clear();
    }

    public static void writeBufToFile() {
        if (mFileChannel == null) {
            MtkLog.d(TAG, "FileChannel is null");
            return;
        }

        MtkLog.d(TAG, "write buf to file,lenght:" + mHeaderBuf.position());
        mHeaderBuf.flip();
        try {
            mFileChannel.write(mHeaderBuf);
        } catch (IOException e) {
            MtkLog.d(TAG, "write buf to file error");
        }
        mHeaderBuf.clear();
    }

    public static void close() {
        MtkLog.d(TAG, "file writer close");
        if (mFileChannel == null) {
            MtkLog.d(TAG, "close, FileChannel is null");
            return;
        }
        try {
            mFileChannel.close();
        } catch (IOException e) {
            MtkLog.d(TAG, "file writer close error");
        }
    }

    public static void writeInt8(byte data) {
        mHeaderBuf.put(data);
    }
    public static void writeBytes(byte[] data) {
        mHeaderBuf.put(data);
    }
    public static void writeInt16(short data) {
        mHeaderBuf.putShort(data);
    }
    public static void writeInt32(int data) {
        mHeaderBuf.putInt(data);
    }
    public static void writeString(String str, int len) {
        if (str.length() != len) {
            throw new AssertionError();
        }
        mHeaderBuf.put(str.getBytes());
    }

    public static void writeBitStreamToFile(byte[] outData, int length) {
        if (mFileChannel == null) {
            MtkLog.d(TAG, "FileChannel is null");
            return;
        }
        if (outData.length != length) {
            throw new AssertionError();
        }

        MtkLog.d(TAG, "writeBitStream,length:" + outData.length);
        try {
            mFileChannel.write(ByteBuffer.wrap(outData));
        } catch (IOException e) {
            MtkLog.d(TAG, "write bit stream error");
        }
    }
}

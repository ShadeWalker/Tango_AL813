package com.mediatek.galleryfeature.btovgenerator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.mediatek.galleryframework.util.MtkLog;

public class DirectBox extends Box {
    public static String TAG = "MtkGallery2/DirectBox";
    public static final int OUT_FORMAT_MPEG_4 = 1;
    public static int mWidth;
    public static int mHeight;

    private int mFileType;
    private byte [] mCodecSpecifiData = new byte[1];

    public DirectBox(String type) {
        super(type);
    }

    public void setCodecSpecifiData(byte[] data) {
        mCodecSpecifiData = data;
    }
    public void setFileType(int fileType) {
        mFileType = fileType;
    }

    public void write() {
        super.write();
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

    public void writeFtypBox() {
        if (mFileType == OUT_FORMAT_MPEG_4) {
            FileWriter.writeString("isom", 4);
        } else {
            FileWriter.writeString("3gp4", 4);
        }
        FileWriter.writeInt32(0);
        FileWriter.writeString("isom", 4);
        FileWriter.writeString("3gp4", 4);
    }

    public void writeMp4vBox() {
        writeVisualSampleEntry();
    }
    public void writeAvc1Box() {
        writeVisualSampleEntry();
    }
    public void writeS263Box() {
        writeVisualSampleEntry();
    }
    private void writeVisualSampleEntry() {
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt8((byte) 0);
        FileWriter.writeInt16((short) 1); //DataReferenceIndex

        FileWriter.writeInt16((short) 0); //pre_defined
        FileWriter.writeInt16((short) 0); //reserved
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt32(0); //pre_defined
        FileWriter.writeInt16((short) mWidth);
        FileWriter.writeInt16((short) mHeight);
        FileWriter.writeInt32(0x00480000); //horizresolution
        FileWriter.writeInt32(0x00480000); //vertresolution
        FileWriter.writeInt32(0); //reserved
        FileWriter.writeInt16((short) 1); //frame_count
        FileWriter.writeString("                                ", 32); //compressorname
        FileWriter.writeInt16((short) 0x0018); //depth
        FileWriter.writeInt16((short) -1); //pre_defined
    }

    public void writePaspBox() {
        FileWriter.writeInt32(1 << 16); //hSpacing
        FileWriter.writeInt32(1 << 16); //vSpacing
    }
    public void writeEsdsBox() {
        FileWriter.writeInt32(0); //version
        FileWriter.writeInt8((byte) 0x03); //ES_DescrTag
        FileWriter.writeInt8((byte) (23 + mCodecSpecifiData.length));
        FileWriter.writeInt16((short) 0x0000); //ES_ID
        FileWriter.writeInt8((byte) 0x1f);
        FileWriter.writeInt8((byte) 0x04);
        FileWriter.writeInt8((byte) (15 + mCodecSpecifiData.length));
        FileWriter.writeInt8((byte) 0x20);
        FileWriter.writeInt8((byte) 0x11);
        FileWriter.writeInt8((byte) 0x01);
        FileWriter.writeInt8((byte) 0x77);
        FileWriter.writeInt8((byte) 0x00);
        FileWriter.writeInt8((byte) 0x00);
        FileWriter.writeInt8((byte) 0x03);
        FileWriter.writeInt8((byte) 0xe8);
        FileWriter.writeInt8((byte) 0x00);
        FileWriter.writeInt8((byte) 0x00);
        FileWriter.writeInt8((byte) 0x03);
        FileWriter.writeInt8((byte) 0xe8);
        FileWriter.writeInt8((byte) 0x00);
        FileWriter.writeInt8((byte) 0x05); //decoderSpecificInfoTag

        FileWriter.writeInt8((byte) mCodecSpecifiData.length); //specificDataSize
        FileWriter.writeBytes(mCodecSpecifiData);

        FileWriter.writeInt8((byte) 0x06);
        FileWriter.writeInt8((byte) 0x01);
        FileWriter.writeInt8((byte) 0x02);
    }

    public void writeD263Box() {
        FileWriter.writeInt32(0); //vendor
        FileWriter.writeInt8((byte) 0); //decoder version
        FileWriter.writeInt8((byte) 10); //level:10
        FileWriter.writeInt8((byte) 0); //profile:0
    }

    public void writeAvcCBox() {
        FileWriter.writeBytes(mCodecSpecifiData);
    }
}

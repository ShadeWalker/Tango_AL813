package com.mediatek.galleryfeature.refocus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.os.Environment;
import android.os.SystemProperties;
import android.util.Log;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.options.SerializeOptions;
import com.adobe.xmp.XMPSchemaRegistry;

public class XmpUtils {
    private final static String TAG = "Gallery2/Refocus/XmpUtils";

    private static boolean ENABLE_BUFFER_DUMP = false;
    private static final String DUMP_PATH = Environment
            .getExternalStorageDirectory().getPath() + "/";
    private static String FILE_NAME;
    private static final String DUMP_FOLDER_NAME = "dump_jps_buffer" + "/";
    private static final String CFG_FILE_NAME = "dumpjps";
    private static final String CFG_PROPERTY_NAME = "dumpjps";

    private static final String GOOGLE_REFOCUS_NAMESPACE = "http://ns.google.com/photos/1.0/focus/";
    private static final String GOOGLE_DEPTH_NAMESPACE = "http://ns.google.com/photos/1.0/depthmap/";
    private static final String GOOGLE_IMAGE_NAMESPACE = "http://ns.google.com/photos/1.0/image/";
    private static final String MEDIATEK_IMAGE_REFOCUS_NAMESPACE = "http://ns.mediatek.com/refocus/jpsconfig/";

    private static final String REFOCUS_BLUR_INFINITY = "BlurAtInfinity";
    private static final String REFOCUS_FOCALDISTANCE = "FocalDistance";
    private static final String REFOCUS_FOCALPOINTX = "FocalPointX";
    private static final String REFOCUS_FOCALPOINTY = "FocalPointY";

    private static final String DEPTH_PREFIX = "GDepth";
    private static final String DEPTH_FORMAT = "Format";
    private static final String DEPTH_NEAR = "Near";
    private static final String DEPTH_FAR = "Far";
    private static final String DEPTH_MIME = "Mime";
    private static final String DEPTH_DATA = "Data";
    private static final String IMAGE_PREFIX = "GImage";

    private static final String MTK_REFOCUS_PREFIX = "MRefocus";
    private static final String JPS_WIDTH = "JpsWidth";
    private static final String JPS_HEIGHT = "JpsHeight";
    private static final String MASK_WIDTH = "MaskWidth";
    private static final String MASK_HEIGHT = "MaskHeight";
    private static final String POS_X = "PosX";
    private static final String POS_Y = "PosY";
    private static final String VIEW_WIDTH = "ViewWidth";
    private static final String VIEW_HEIGHT = "ViewHeight";
    private static final String ORIENTATION = "Orientation";
    private static final String MAIN_CAM_POS = "MainCamPos";
    private static final String TOUCH_COORDX_1ST = "TouchCoordX1st";
    private static final String TOUCH_COORDY_1ST = "TouchCoordY1st";
    // app needed parameters
    private static final String DEPTH_BUFFER_WIDTH = "DepthBufferWidth";
    private static final String DEPTH_BUFFER_HEIGHT = "DepthBufferHeight";
    private static final String XMP_DEPTH_WIDTH = "XmpDepthWidth";
    private static final String XMP_DEPTH_HEIGHT = "XmpDepthHeight";
    private static final String TOUCH_COORDX_LAST = "TouchCoordXLast";
    private static final String TOUCH_COORDY_LAST = "TouchCoordYLast";
    private static final String DEPTH_OF_FIELD_LAST = "DepthOfFieldLast";
    private static final String DEPTH_BUFFER_FLAG = "DepthBufferFlag";
    private static final String XMP_DEPTH_FLAG = "XmpDepthFlag";

    private static final int SERIALIZE_EXTRA_HEAD_LEN = 0x36;
    private static final int XMP_EXTENSION_SIZE = 0xffb4 - 2;
    private static final int XMP_EXTENSION_INDEX_MAIN = 0;

    private static final int SOI = 0xFFD8;
    private static final int SOS = 0xFFDA;
    private static final int APP1 = 0xFFE1;
    private static final int APP15 = 0xFFEF;
    private static final int DQT = 0xFFDB;
    private static final int DHT = 0xFFC4;

    // app15 reserve 0xffff bytes
    private static final int APP15_RESERVE_LENGTH = 0xFFFF;

    private final static int WRITE_XMP_AFTER_SOI = 0;
    private final static int WRITE_XMP_BEFORE_FIRST_APP1 = 1;
    private final static int WRITE_XMP_AFTER_FIRST_APP1 = 2;
    private final static int FIXED_BUFFER_SIZE = 1024 * 10;// 10KB

    private static ArrayList<Section> sParsedSectionsForCamera;
    private static ArrayList<Section> sParsedSectionsForGallery;
    private static XMPSchemaRegistry sRegister = XMPMetaFactory.getSchemaRegistry();

    // add option to dump buffer
    static {
        File XmpCfg = new File(DUMP_PATH + CFG_FILE_NAME);
        if (XmpCfg.exists()) {
            ENABLE_BUFFER_DUMP = true;
        } else {
            ENABLE_BUFFER_DUMP = false;
        }
        if (!ENABLE_BUFFER_DUMP) {
            ENABLE_BUFFER_DUMP = SystemProperties.getInt(CFG_PROPERTY_NAME, 0) == 1 ? true
                    : false;
        }
        if (ENABLE_BUFFER_DUMP) {
            makeDir(DUMP_PATH + DUMP_FOLDER_NAME);
        }
        Log.i(TAG, "ENABLE_BUFFER_DUMP: " + ENABLE_BUFFER_DUMP
                + ", DUMP_PATH: " + DUMP_PATH);
    }

    private static class ByteArrayInputStreamExt extends ByteArrayInputStream {
        public ByteArrayInputStreamExt(byte[] buf) {
            super(buf);
            Log.i(TAG, "<ByteArrayInputStreamExt> new instance, buf count 0x"
                    + Integer.toHexString(buf.length));
        }

        public final int readUnsignedShort() {
            int hByte = read();
            int lByte = read();
            return hByte << 8 | lByte;
        }

        // high byte first int
        public final int readInt() {
            int firstByte = read();
            int secondByte = read();
            int thirdByte = read();
            int forthByte = read();
            return firstByte << 24 | secondByte << 16 | thirdByte << 8
                    | forthByte;
        }

        // low byte first int
        public final int readReverseInt() {
            int forthByte = read();
            int thirdByte = read();
            int secondByte = read();
            int firstByte = read();
            return firstByte << 24 | secondByte << 16 | thirdByte << 8
                    | forthByte;
        }

        public void seek(long offset) throws IOException {
            if (offset > count - 1)
                throw new IOException("offset out of buffer range: offset "
                        + offset + ", buffer count " + count);
            pos = (int) offset;
        }

        public long getFilePointer() {
            return pos;
        }

        public int read(byte[] buffer) {
            return read(buffer, 0, buffer.length);
        }
    }

    private static class ByteArrayOutputStreamExt extends ByteArrayOutputStream {
        public ByteArrayOutputStreamExt(int size) {
            super(size);
        }

        public final void writeShort(int val) {
            int hByte = val >> 8;
            int lByte = val & 0xff;
            write(hByte);
            write(lByte);
        }

        public final void writeInt(int val) {
            int firstByte = val >> 24;
            int secondByte = (val >> 16) & 0xff;
            int thirdByte = (val >> 8) & 0xff;
            int forthByte = val & 0xff;
            write(firstByte);
            write(secondByte);
            write(thirdByte);
            write(forthByte);
        }
    }

    private static class Section {
        int marker; // e.g. 0xffe1, exif
        long offset; // marker offset from start of file
        int length; // app length, follow spec, include 2 length bytes
        boolean isXmpMain;
        boolean isXmpExt;
        boolean isExif;
        boolean isJpsData;
        boolean isJpsMask;
        boolean isDepthData;
        boolean isXmpDepth;

        public Section(int marker, long offset, int length, boolean isXmpMain,
                boolean isXmpExt, boolean isExif, boolean isJpsData,
                boolean isJpsMask, boolean isDepthData, boolean isXmpDepth) {
            this.marker = marker;
            this.offset = offset;
            this.length = length;
            this.isXmpMain = isXmpMain;
            this.isXmpExt = isXmpExt;
            this.isExif = isExif;
            this.isJpsData = isJpsData;
            this.isJpsMask = isJpsMask;
            this.isDepthData = isDepthData;
            this.isXmpDepth = isXmpDepth;
        }
    }

    public static boolean initialize(String srcFilePath) {
        File srcFile = new File(srcFilePath);
        if (!srcFile.exists()) {
            Log.i(TAG, "<initialize> " + srcFilePath + " not exists!!!");
            return false;
        }
        String fileFormat = srcFilePath.substring(srcFilePath.length() - 3,
                srcFilePath.length());
        Log.i(TAG, "<initialize> fileFormat " + fileFormat);
        if (!"JPG".equalsIgnoreCase(fileFormat)) {
            Log.i(TAG, "<initialize> " + srcFilePath + " is not JPG!!!");
            return false;
        }
        FILE_NAME = getFileNameFromPath(srcFilePath) + "/";
        makeDir(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME);
        sParsedSectionsForGallery = parseAppInfo(srcFilePath);
        return true;
    }

    public static void deInitialize() {
        sParsedSectionsForGallery = null;
    }

    public static JpsConfigInfo getJpsConfigInfoFromFile(String filePath) {
        XMPMeta meta = getXmpMetaFromFile(filePath);
        JpsConfigInfo jpsConfigInfo = parseJpsConfigInfo(meta);
        Log.i(TAG, "<getJpsConfigInfoFromFile> " + jpsConfigInfo);
        return jpsConfigInfo;
    }

    private static int locateXmpDataEnd(byte[] buffer) {
        int i = buffer.length - 1;
        for (; i > 3; i--) {
            if (buffer[i] == '>' && buffer[i - 1] == 'a'
                    && buffer[i - 2] == 't' && buffer[i - 3] == 'e'
                    && buffer[i - 4] == 'm') {
                return i + 1;
            }
        }
        Log.i(TAG, "<locateXmpDataEnd> error, can not find XmpDataEnd!!!");
        return -1;
    }

    private static void copyFileWithFixBuffer(RandomAccessFile rafIn,
            RandomAccessFile rafOut) {
        byte[] readBuffer = new byte[FIXED_BUFFER_SIZE];
        int readCount = 0;
        long lastReadPosition = 0;
        try {
            while ((readCount = rafIn.read(readBuffer)) != -1) {
                if (readCount == FIXED_BUFFER_SIZE) {
                    rafOut.write(readBuffer);
                } else {
                    rafOut.write(readBuffer, 0, readCount);
                }
            }
        } catch (IOException e) {
            Log.i(TAG, "<copyFileWithFixBuffer> IOException", e);
        }
    }

    private static void copyToStreamWithFixBuffer(ByteArrayInputStreamExt is,
            ByteArrayOutputStreamExt os) {
        byte[] readBuffer = new byte[FIXED_BUFFER_SIZE];
        int readCount = 0;
        long lastReadPosition = 0;
        try {
            Log.i(TAG, "<copyToStreamWithFixBuffer> copy remain jpg data begin!!!");
            while ((readCount = is.read(readBuffer)) != -1) {
                if (readCount == FIXED_BUFFER_SIZE) {
                    os.write(readBuffer);
                } else {
                    os.write(readBuffer, 0, readCount);
                }
            }
            Log.i(TAG, "<copyToStreamWithFixBuffer> copy remain jpg data end!!!");
        } catch (Exception e) {
            Log.i(TAG, "<copyToStreamWithFixBuffer> Exception", e);
        }
    }

    private static void writeSectionToFile(RandomAccessFile rafIn,
            RandomAccessFile rafOut, Section sec) {
        try {
            rafOut.writeShort(sec.marker);
            rafOut.writeShort(sec.length);// already contain length bytes(2
                                          // bytes, no need +2)
            rafIn.seek(sec.offset + 4);
            byte[] buffer = null;
            // if (sec.marker == APP15) {
            // buffer = new byte[APP15_RESERVE_LENGTH - 2];
            // } else {
            buffer = new byte[sec.length - 2];
            // }
            rafIn.read(buffer, 0, buffer.length);
            rafOut.write(buffer);
        } catch (IOException e) {
            Log.i(TAG, "<writeSectionToFile> IOException", e);
        }
    }

    private static void writeSectionToStream(ByteArrayInputStreamExt is,
            ByteArrayOutputStreamExt os, Section sec) {
        try {
            Log.i(TAG, "<writeSectionToStream> write section 0x"
                    + Integer.toHexString(sec.marker));
            os.writeShort(sec.marker);
            os.writeShort(sec.length);// already contain length bytes(2 bytes,
                                      // no need +2)
            is.seek(sec.offset + 4);
            byte[] buffer = null;
            // if (sec.marker == APP15) {
            // buffer = new byte[APP15_RESERVE_LENGTH - 2];
            // } else {
            buffer = new byte[sec.length - 2];
            // }
            is.read(buffer, 0, buffer.length);
            os.write(buffer);
        } catch (IOException e) {
            Log.i(TAG, "<writeSectionToStream> IOException", e);
        }
    }

    // return marker: write xmp after this marker
    private static int findProperLocationForXmp(ArrayList<Section> sections) {
        for (int i = 0; i < sections.size(); i++) {
            Section sec = sections.get(i);
            if (sec.marker == APP1) {
                if (sec.isExif) {
                    return WRITE_XMP_AFTER_FIRST_APP1;
                } else {
                    return WRITE_XMP_BEFORE_FIRST_APP1;
                }
            }
        }
        // means no app1, write after SOI
        return WRITE_XMP_AFTER_SOI;
    }

    private static byte[] makeXmpMainData(JpsConfigInfo configInfo) {
        if (configInfo == null) {
            Log.i(TAG, "<makeXmpMainData> configInfo is null, so return null!!!");
            return null;
        }
        try {
            XMPMeta meta = XMPMetaFactory.create();
            meta = makeXmpMainDataInternal(meta, configInfo);
            byte[] bufferOutTmp = serialize(meta);

            byte[] bufferOut = new byte[bufferOutTmp.length
                    + XmpResource.XMP_HEADER_START.length()];
            System.arraycopy(XmpResource.XMP_HEADER_START.getBytes(), 0,
                    bufferOut, 0, XmpResource.XMP_HEADER_START.length());
            System.arraycopy(bufferOutTmp, 0, bufferOut,
                    XmpResource.XMP_HEADER_START.length(), bufferOutTmp.length);
            return bufferOut;
        }  catch (Exception e) {
            Log.i(TAG, "<writeXmpData> Exception", e);
            return null;
        }
    }

    // return buffer not include app1 tag and length
    private static byte[] updateXmpMainDataWithDepthBuffer(XMPMeta meta,
            DepthBufferInfo depthBufferInfo) {
        if (depthBufferInfo == null || meta == null) {
            Log.i(TAG,
                  "<updateXmpMainDataWithDepthBuffer> depthBufferInfo or meta is null, so return null!!!");
            return null;
        }
        try {
            registerNamespace(MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                    MTK_REFOCUS_PREFIX);
            if (depthBufferInfo.depthBufferWidth != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        DEPTH_BUFFER_WIDTH, depthBufferInfo.depthBufferWidth);
            }
            if (depthBufferInfo.depthBufferHeight != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        DEPTH_BUFFER_HEIGHT, depthBufferInfo.depthBufferHeight);
            }
            if (depthBufferInfo.xmpDepthWidth != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        XMP_DEPTH_WIDTH, depthBufferInfo.xmpDepthWidth);
            }
            if (depthBufferInfo.xmpDepthHeight != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        XMP_DEPTH_HEIGHT, depthBufferInfo.xmpDepthHeight);
            }
            if (depthBufferInfo.touchCoordXLast != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        TOUCH_COORDX_LAST, depthBufferInfo.touchCoordXLast);
            }
            if (depthBufferInfo.touchCoordYLast != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        TOUCH_COORDY_LAST, depthBufferInfo.touchCoordYLast);
            }
            if (depthBufferInfo.depthOfFieldLast != -1) {
                setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        DEPTH_OF_FIELD_LAST, depthBufferInfo.depthOfFieldLast);
            }
            if (depthBufferInfo.depthBufferFlag != getPropertyBoolean(meta,
                    MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_FLAG)) {
                setPropertyBoolean(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        DEPTH_BUFFER_FLAG, depthBufferInfo.depthBufferFlag);
            }
            if (depthBufferInfo.xmpDepthFlag != getPropertyBoolean(meta,
                    MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_FLAG)) {
                setPropertyBoolean(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                        XMP_DEPTH_FLAG, depthBufferInfo.xmpDepthFlag);
            }

            byte[] bufferOutTmp = serialize(meta);
            byte[] bufferOut = new byte[bufferOutTmp.length
                    + XmpResource.XMP_HEADER_START.length()];
            System.arraycopy(XmpResource.XMP_HEADER_START.getBytes(), 0,
                    bufferOut, 0, XmpResource.XMP_HEADER_START.length());
            System.arraycopy(bufferOutTmp, 0, bufferOut,
                    XmpResource.XMP_HEADER_START.length(), bufferOutTmp.length);
            return bufferOut;
        }  catch (Exception e) {
            Log.i(TAG, "<updateXmpMainDataWithDepthBuffer> Exception", e);
            return null;
        }
    }

    private static XMPMeta makeXmpMainDataInternal(XMPMeta meta,
            JpsConfigInfo configInfo) {
        if (configInfo == null || meta == null) {
            Log.i(TAG,
                   "<makeXmpMainDataInternal> error, please make sure meta or JpsConfigInfo not null");
            return null;
        }
        registerNamespace(MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MTK_REFOCUS_PREFIX);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, JPS_WIDTH,
                configInfo.jpsWidth);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, JPS_HEIGHT,
                configInfo.jpsHeight);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MASK_WIDTH,
                configInfo.maskWidth);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MASK_HEIGHT,
                configInfo.maskHeight);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, POS_X,
                configInfo.posX);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, POS_Y,
                configInfo.posY);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, VIEW_WIDTH,
                configInfo.viewWidth);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, VIEW_HEIGHT,
                configInfo.viewHeight);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, ORIENTATION,
                configInfo.orientation);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                MAIN_CAM_POS, configInfo.mainCamPos);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                TOUCH_COORDX_1ST, configInfo.touchCoordX1st);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE,
                TOUCH_COORDY_1ST, configInfo.touchCoordY1st);

        // add default value for DepthBufferInfo
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_WIDTH, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_HEIGHT, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_WIDTH, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_HEIGHT, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDX_LAST, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDY_LAST, -1);
        setPropertyInteger(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_OF_FIELD_LAST, -1);
        setPropertyBoolean(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_FLAG, false);
        setPropertyBoolean(meta, MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_FLAG, false);
        return meta;
    }

    private static void registerNamespace(String nameSpace, String prefix) {
        try {
            sRegister.registerNamespace(nameSpace, prefix);
        } catch (XMPException e) {
            Log.i(TAG, "<registerNamespace> XMPException", e);
        }
    }

    private static void setPropertyString(XMPMeta meta, String nameSpace,
            String propName, String value) {
        if (meta == null) {
            Log.i(TAG, "<setPropertyString> meta is null, return!!!");
        }
        try {
            meta.setProperty(nameSpace, propName, value);
        } catch (XMPException e) {
            Log.i(TAG, "<setPropertyString> XMPException", e);
        } catch (NullPointerException e) {
            // when jpg has this propName, it will throw NullPointerException
            Log.i(TAG, "<setPropertyString> NullPointerException!!!");
        }
    }

    private static void setPropertyInteger(XMPMeta meta, String nameSpace,
            String propName, int value) {
        if (meta == null) {
            Log.i(TAG, "<setPropertyInteger> meta is null, return!!!");
        }
        try {
            meta.setPropertyInteger(nameSpace, propName, value);
        } catch (XMPException e) {
            Log.i(TAG, "<setPropertyInteger> XMPException", e);
        } catch (NullPointerException e) {
            // when jpg has this propName, it will throw NullPointerException
            Log.i(TAG, "<setPropertyInteger> NullPointerException!!!");
        }
    }

    private static void setPropertyBoolean(XMPMeta meta, String nameSpace,
            String propName, boolean value) {
        if (meta == null) {
            Log.i(TAG, "<setPropertyBoolean> meta is null, return!!!");
        }
        try {
            meta.setPropertyBoolean(nameSpace, propName, value);
        } catch (XMPException e) {
            Log.i(TAG, "<setPropertyBoolean> XMPException", e);
        } catch (NullPointerException e) {
            // when jpg has this propName, it will throw NullPointerException
            Log.i(TAG, "<setPropertyBoolean> NullPointerException!!!");
        }
    }

    private static boolean getPropertyBoolean(XMPMeta meta, String nameSpace,
            String propName) {
        if (meta == null) {
            Log.i(TAG, "<getPropertyBoolean> meta is null, return false!!!");
            return false;
        }
        try {
            return meta.getPropertyBoolean(nameSpace, propName);
        } catch (XMPException e) {
            Log.i(TAG, "<getPropertyBoolean> XMPException", e);
            return false;
        } catch (NullPointerException e) {
            // when jpg has this propName, it will throw NullPointerException
            Log.i(TAG, "<getPropertyBoolean> NullPointerException!!!");
            return false;
        }
    }

    private static int getPropertyInteger(XMPMeta meta, String nameSpace,
            String propName) {
        if (meta == null) {
            Log.i(TAG, "<getPropertyInteger> meta is null, return -1!!!");
            return -1;
        }
        try {
            return meta.getPropertyInteger(nameSpace, propName);
        } catch (XMPException e) {
            Log.i(TAG, "<getPropertyInteger> XMPException", e);
            return -1;
        } catch (NullPointerException e) {
            // when jpg has this propName, it will throw NullPointerException
            Log.i(TAG, "<getPropertyInteger> NullPointerException!!!");
            return -1;
        }
    }

    private static byte[] serialize(XMPMeta meta) {
        try {
            return XMPMetaFactory.serializeToBuffer(meta,
                    new SerializeOptions().setUseCompactFormat(true)
                    .setOmitPacketWrapper(true));
        } catch (XMPException e) {
            Log.i(TAG, "<serialize> XMPException", e);
        }
        Log.i(TAG, "<serialize> return null!!!");
        return null;
    }

    public static boolean writeBufferToFile(String desFile, byte[] buffer) {
        if (buffer == null) {
            Log.i(TAG, "<writeBufferToFile> buffer is null");
            return false;
        }
        File out = new File(desFile);
        if (out.exists()) out.delete();
        FileOutputStream fops = null;
        try {
            if (!(out.createNewFile())) {
                Log.i(TAG, "<writeBufferToFile> createNewFile error");
                return false;
            }
            fops = new FileOutputStream(out);
            fops.write(buffer);
            return true;
        } catch (IOException e) {
            Log.i(TAG, "<writeBufferToFile> IOException", e);
            return false;
        } catch (Exception e) {
            Log.i(TAG, "<writeBufferToFile> Exception", e);
            return false;
        } finally {
            try {
                if (fops != null) {
                    fops.close();
                    fops = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<writeBufferToFile> close, IOException", e);
            }
        }
    }

    public static byte[] readFileToBuffer(String filePath) {
        File inFile = new File(filePath);
        if (!inFile.exists()) {
            Log.i(TAG, "<readFileToBuffer> " + filePath + " not exists!!!");
            return null;
        }

        RandomAccessFile rafIn = null;
        try {
            rafIn = new RandomAccessFile(inFile, "r");
            int len = (int) inFile.length();
            byte[] buffer = new byte[len];
            rafIn.read(buffer);
            return buffer;
        } catch (Exception e) {
            Log.i(TAG, "<readFileToBuffer> Exception ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<readFileToBuffer> close IOException ", e);
            }
        }
    }

    public static void writeStringToFile(String desFile, String value) {
        if (value == null) {
            Log.i(TAG, "<writeStringToFile> input string is null, return!!!");
            return;
        }
        File out = new File(desFile);
        PrintStream ps = null;
        try {
            if (out.exists()) out.delete();
            if (!(out.createNewFile())) {
                Log.i(TAG, "<writeStringToFile> createNewFile error");
                return;
            }
            ps = new PrintStream(out);
            ps.println(value);
            ps.flush();
        } catch (Exception e) {
            Log.i(TAG, "<writeStringToFile> Exception ", e);
        } finally {
            out = null;
            if (ps != null) {
                ps.close();
                ps = null;
            }
        }
    }

    private static ArrayList<Section> parseAppInfo(String filePath) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "r");
            int value = raf.readUnsignedShort();
            if (value != SOI) {
                Log.i(TAG, "<parseAppInfo> error, find no SOI");
            }
            int marker = -1;
            long offset = -1;
            int length = -1;
            ArrayList<Section> sections = new ArrayList<Section>();

            while ((value = raf.readUnsignedShort()) != -1 && value != SOS) {
                marker = value;
                offset = raf.getFilePointer() - 2;
                length = raf.readUnsignedShort();
                sections.add(new Section(marker, offset, length, false, false,
                        false, false, false, false, false));
                // if (marker == APP15) {
                // raf.skipBytes(APP15_RESERVE_LENGTH - 2);
                // } else {
                raf.skipBytes(length - 2);
                // }
            }
            // write exif/isXmp flag
            for (int i = 0; i < sections.size(); i++) {
                checkIfXmpOrExifOrJpsInApp1(raf, sections.get(i));
                Log.i(TAG, "<parseAppInfo> marker 0x"
                        + Integer.toHexString(sections.get(i).marker)
                        + ", offset 0x"
                        + Long.toHexString(sections.get(i).offset)
                        + ", length 0x"
                        + Integer.toHexString(sections.get(i).length)
                        + ", isExif " + sections.get(i).isExif
                        + ", isXmpMain " + sections.get(i).isXmpMain
                        + ", isXmpExt " + sections.get(i).isXmpExt
                        + ", isJPSData " + sections.get(i).isJpsData
                        + ", isJPSMask " + sections.get(i).isJpsMask
                        + ", isDepthData " + sections.get(i).isDepthData
                        + ", isXmpDepth " + sections.get(i).isXmpDepth);
            }
            return sections;
        } catch (IOException e) {
            Log.i(TAG, "<parseAppInfo> IOException, path " + filePath, e);
            return null;
        } catch (Exception e) {
            Log.i(TAG, "<parseAppInfo> Exception, path " + filePath, e);
            return null;
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                    raf = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<parseAppInfo> IOException, path " + filePath, e);
            }
        }
    }

    private static ArrayList<Section> parseAppInfoFromStream(
            ByteArrayInputStreamExt is) {
        if (is == null) {
            Log.i(TAG, "<parseAppInfoFromStream> input stream is null!!!");
            return null;
        }
        try {
            is.seek(0);// reset position at the file start
            int value = is.readUnsignedShort();
            if (value != SOI) {
                Log.i(TAG, "<parseAppInfoFromStream> error, find no SOI");
            }
            Log.i(TAG, "<parseAppInfoFromStream> parse begin!!!");
            int marker = -1;
            long offset = -1;
            int length = -1;
            ArrayList<Section> sections = new ArrayList<Section>();

            while ((value = is.readUnsignedShort()) != -1 && value != SOS) {
                marker = value;
                offset = is.getFilePointer() - 2;
                length = is.readUnsignedShort();
                sections.add(new Section(marker, offset, length, false, false,
                        false, false, false, false, false));
                // if (marker == APP15) {
                // is.skip(APP15_RESERVE_LENGTH - 2);
                // } else {
                is.skip(length - 2);
                // }
            }

            // write exif/isXmp flag
            for (int i = 0; i < sections.size(); i++) {
                checkIfXmpOrExifOrJpsInStream(is, sections.get(i));
                Log.i(TAG, "<parseAppInfoFromStream> marker 0x"
                        + Integer.toHexString(sections.get(i).marker)
                        + ", offset 0x"
                        + Long.toHexString(sections.get(i).offset)
                        + ", length 0x"
                        + Integer.toHexString(sections.get(i).length)
                        + ", isExif " + sections.get(i).isExif
                        + ", isXmpMain " + sections.get(i).isXmpMain
                        + ", isXmpExt " + sections.get(i).isXmpExt
                        + ", isJPSData " + sections.get(i).isJpsData
                        + ", isJPSMask " + sections.get(i).isJpsMask
                        + ", isDepthData " + sections.get(i).isDepthData
                        + ", isXmpDepth " + sections.get(i).isXmpDepth);
            }
            is.seek(0);// reset position at the file start
            Log.i(TAG, "<parseAppInfoFromStream> parse end!!!");
            return sections;
        } catch (IOException e) {
            Log.i(TAG, "<parseAppInfoFromStream> IOException ", e);
            return null;
        }
    }

    private static void checkIfXmpOrExifOrJpsInApp1(RandomAccessFile raf,
            Section section) {
        if (section == null) {
            Log.i(TAG, "<checkIfXmpOrExifOrJpsInApp1> section is null!!!");
            return;
        }
        byte[] buffer = null;
        String str = null;

        try {
            if (section.marker == APP15) {
                raf.seek(section.offset + 2 + 2 + 4);// marker 2 bytes, length 2
                                                     // bytes, total length 4 bytes
                buffer = new byte[7];
                raf.read(buffer, 0, buffer.length);
                str = new String(buffer);

                if (XmpResource.TYPE_JPS_DATA.equals(str)) {
                    section.isJpsData = true;
                    return;
                }

                if (XmpResource.TYPE_JPS_MASK.equals(str)) {
                    section.isJpsMask = true;
                    return;
                }

                if (XmpResource.TYPE_DEPTH_DATA.equals(str)) {
                    section.isDepthData = true;
                    return;
                }

                if (XmpResource.TYPE_XMP_DEPTH.equals(str)) {
                    section.isXmpDepth = true;
                    return;
                }
            } else if (section.marker == APP1) {
                raf.seek(section.offset + 4);// 4: marker 2 bytes, length 2
                                             // bytes
                // main: "http://ns.adobe.com/xap/1.0/\0"
                // extension main: "http://ns.adobe.com/xmp/extension/"
                // extension slave:"http://ns.adobe.com/xmp/extension/"
                // use longest string as buffer length
                buffer = new byte[XmpResource.XMP_EXT_MAIN_HEADER1.length()];
                raf.read(buffer, 0, buffer.length);
                str = new String(buffer);
                if (XmpResource.XMP_EXT_MAIN_HEADER1.equals(str)) {
                    // ext main header is same as ext slave header
                    section.isXmpExt = true;
                    return;
                }

                str = new String(buffer, 0, XmpResource.XMP_HEADER_START.length());
                if (XmpResource.XMP_HEADER_START.equals(str)) {
                    section.isXmpMain = true;
                    return;
                }

                str = new String(buffer, 0, XmpResource.EXIF_HEADER.length());
                if (XmpResource.EXIF_HEADER.equals(str)) {
                    section.isExif = true;
                    return;
                }
            }
        } catch (UnsupportedEncodingException e) {
            Log.i(TAG,
                    "<checkIfXmpOrExifOrJpsInApp1> UnsupportedEncodingException"
                            + e);
        } catch (IOException e) {
            Log.i(TAG, "<checkIfXmpOrExifOrJpsInApp1> IOException" + e);
        }
        // note, we need to close RandomAccessFile in the upper caller
    }

    private static void checkIfXmpOrExifOrJpsInStream(
            ByteArrayInputStreamExt is, Section section) {
        if (is == null || section == null) {
            Log.i(TAG,
                    "<checkIfXmpOrExifOrJpsInStream> input stream or section is null!!!");
            return;
        }
        byte[] buffer = null;
        String str = null;

        try {
            if (section.marker == APP15) {
                is.seek(section.offset + 2 + 2 + 4);// marker 2 bytes, length 2
                // bytes, total length 4 bytes
                buffer = new byte[7];
                is.read(buffer, 0, buffer.length);
                str = new String(buffer);

                if (XmpResource.TYPE_JPS_DATA.equals(str)) {
                    section.isJpsData = true;
                    return;
                }

                if (XmpResource.TYPE_JPS_MASK.equals(str)) {
                    section.isJpsMask = true;
                    return;
                }

                if (XmpResource.TYPE_DEPTH_DATA.equals(str)) {
                    section.isDepthData = true;
                    return;
                }

                if (XmpResource.TYPE_XMP_DEPTH.equals(str)) {
                    section.isXmpDepth = true;
                    return;
                }
            } else if (section.marker == APP1) {
                is.seek(section.offset + 4);// 4: marker 2 bytes, length 2 bytes
                // main: "http://ns.adobe.com/xap/1.0/\0"
                // extension main: "http://ns.adobe.com/xmp/extension/"
                // extension slave:"http://ns.adobe.com/xmp/extension/"
                // use longest string as buffer length
                buffer = new byte[XmpResource.XMP_EXT_MAIN_HEADER1.length()];
                is.read(buffer, 0, buffer.length);
                str = new String(buffer);
                if (XmpResource.XMP_EXT_MAIN_HEADER1.equals(str)) {
                    // ext main header is same as ext slave header
                    section.isXmpExt = true;
                    return;
                }

                str = new String(buffer, 0, XmpResource.XMP_HEADER_START.length());
                if (XmpResource.XMP_HEADER_START.equals(str)) {
                    section.isXmpMain = true;
                    return;
                }

                str = new String(buffer, 0, XmpResource.EXIF_HEADER.length());
                if (XmpResource.EXIF_HEADER.equals(str)) {
                    section.isExif = true;
                    return;
                }
            }
        } catch (UnsupportedEncodingException e) {
            Log.i(TAG,
                    "<checkIfXmpOrExifOrJpsInStream> UnsupportedEncodingException"
                            + e);
        } catch (IOException e) {
            Log.i(TAG, "<checkIfXmpOrExifOrJpsInStream> IOException" + e);
        }
        // note, we need to close RandomAccessFile in the upper caller
    }

    public static byte[] writeJpsAndMaskAndConfigToJpgBuffer(byte[] jpgBuffer,
            byte[] jpsData, byte[] jpsMask, byte[] jpsConfig) {
        if (jpgBuffer == null || jpsData == null
                || jpsMask == null || jpsConfig == null) {
            Log.i(TAG,
                  "<writeJpsAndMaskAndConfigToJpgBuffer> jpgBuffer or jpsData or jpsMask or jpsConfig is null!!!");
            return null;
        }
        Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> write begin!!!");
        if (ENABLE_BUFFER_DUMP) {
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + "In.jpg", jpgBuffer);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + "Jps_Written.jpg", jpsData);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + "Mask_Written.bin", jpsMask);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + "Config_Written.bin", jpsConfig);
        }
        ByteArrayInputStreamExt is = null;
        ByteArrayOutputStreamExt os = null;
        try {
            is = new ByteArrayInputStreamExt(jpgBuffer);
            //ArrayList<Section> sections = parseAppInfoFromStream(is);
            sParsedSectionsForCamera = parseAppInfoFromStream(is);
            os = new ByteArrayOutputStreamExt(calcJpgOutStreamSize(sParsedSectionsForCamera,
                    jpgBuffer.length, jpsData.length, jpsMask.length, jpsConfig.length));

            if (is.readUnsignedShort() != SOI) {
                Log.i(TAG,
                      "<writeJpsAndMaskAndConfigToJpgBuffer> image is not begin with 0xffd8!!!");
                return null;
            }

            os.writeShort(SOI);
            boolean hasWritenConfigInfo = false;
            boolean hasWritenJpsAndMask = false;
            int writenLocation = findProperLocationForXmp(sParsedSectionsForCamera);
            if (writenLocation == WRITE_XMP_AFTER_SOI) {
                // means no APP1
                writeConfigToStream(os, jpsConfig);
                hasWritenConfigInfo = true;
            }
            for (int i = 0; i < sParsedSectionsForCamera.size(); i++) {
                Section sec = sParsedSectionsForCamera.get(i);
                if (sec.isExif) {
                    writeSectionToStream(is, os, sec);
                    if (!hasWritenConfigInfo) {
                        writeConfigToStream(os, jpsConfig);
                        hasWritenConfigInfo = true;
                    }
                } else {
                    if (!hasWritenConfigInfo) {
                        writeConfigToStream(os, jpsConfig);
                        hasWritenConfigInfo = true;
                    }
                    // APPx must be before DQT/DHT
                    if (!hasWritenJpsAndMask && (sec.marker == DQT || sec.marker == DHT)) {
                        writeJpsAndMaskToStream(os, jpsData, jpsMask);
                        hasWritenJpsAndMask = true;
                    }
                    if (sec.isXmpMain || sec.isXmpExt || sec.isJpsData || sec.isJpsMask) {
                        // skip old jpsData and jpsMask
                        is.skip(sec.length + 2);
                    } else {
                        writeSectionToStream(is, os, sec);
                    }
                }
            }
            //writeJpsAndMaskAndConfigToStream(os, jpsData, jpsMask, jpsConfig);
            // write jps and mask to app15, before sos
            if (!hasWritenJpsAndMask) {
                writeJpsAndMaskToStream(os, jpsData, jpsMask);
                hasWritenJpsAndMask = true;
            }
            // write remain whole file (from SOS)
            copyToStreamWithFixBuffer(is, os);
            Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> write end!!!");
            byte[] out = os.toByteArray();
            if (ENABLE_BUFFER_DUMP) {
                writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + "Out.jpg", out);
            }
            return out;
        } catch (Exception e) {
            Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> Exception ", e);
            return null;
        } finally {
            try {
                if (is != null) {
                    is.close();
                    is = null;
                }
                if (os != null) {
                    os.close();
                    os = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<writeJpsAndMaskAndConfigToJpgBuffer> close IOException ", e);
            }
        }
    }

    public static byte[] writeJpsAndMaskAndConfigToJpgBuffer(String fileName,
            byte[] jpgBuffer, byte[] jpsData, byte[] jpsMask, byte[] jpsConfig) {
        if (jpgBuffer == null || jpsData == null
                || jpsMask == null || jpsConfig == null) {
            Log.i(TAG,
                  "<writeJpsAndMaskAndConfigToJpgBuffer> jpgBuffer or jpsData or jpsMask or jpsConfig is null!!!");
            return null;
        }
        Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> write begin!!!");
        if (ENABLE_BUFFER_DUMP && fileName != null) {
            FILE_NAME = fileName + "/";
            makeDir(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "In.raw", jpgBuffer);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "Jps_Written.raw", jpsData);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "Mask_Written.raw", jpsMask);
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "Config_Written.raw", jpsConfig);
        }
        ByteArrayInputStreamExt is = null;
        ByteArrayOutputStreamExt os = null;
        try {
            is = new ByteArrayInputStreamExt(jpgBuffer);
            //ArrayList<Section> sections = parseAppInfoFromStream(is);
            sParsedSectionsForCamera = parseAppInfoFromStream(is);
            os = new ByteArrayOutputStreamExt(calcJpgOutStreamSize(sParsedSectionsForCamera,
                    jpgBuffer.length, jpsData.length, jpsMask.length, jpsConfig.length));

            if (is.readUnsignedShort() != SOI) {
                Log.i(TAG,
                      "<writeJpsAndMaskAndConfigToJpgBuffer> image is not begin with 0xffd8!!!");
                return null;
            }

            os.writeShort(SOI);
            boolean hasWritenConfigInfo = false;
            boolean hasWritenJpsAndMask = false;
            int writenLocation = findProperLocationForXmp(sParsedSectionsForCamera);
            if (writenLocation == WRITE_XMP_AFTER_SOI) {
                // means no APP1
                writeConfigToStream(os, jpsConfig);
                hasWritenConfigInfo = true;
            }
            for (int i = 0; i < sParsedSectionsForCamera.size(); i++) {
                Section sec = sParsedSectionsForCamera.get(i);
                if (sec.isExif) {
                    writeSectionToStream(is, os, sec);
                    if (!hasWritenConfigInfo) {
                        writeConfigToStream(os, jpsConfig);
                        hasWritenConfigInfo = true;
                    }
                } else {
                    if (!hasWritenConfigInfo) {
                        writeConfigToStream(os, jpsConfig);
                        hasWritenConfigInfo = true;
                    }
                    // APPx must be before DQT/DHT
                    if (!hasWritenJpsAndMask && (sec.marker == DQT || sec.marker == DHT)) {
                        writeJpsAndMaskToStream(os, jpsData, jpsMask);
                        hasWritenJpsAndMask = true;
                    }
                    if (sec.isXmpMain || sec.isXmpExt || sec.isJpsData || sec.isJpsMask) {
                        // skip old jpsData and jpsMask
                        is.skip(sec.length + 2);
                    } else {
                        writeSectionToStream(is, os, sec);
                    }
                }
            }
            //writeJpsAndMaskAndConfigToStream(os, jpsData, jpsMask, jpsConfig);
            // write jps and mask to app15, before sos
            if (!hasWritenJpsAndMask) {
                writeJpsAndMaskToStream(os, jpsData, jpsMask);
                hasWritenJpsAndMask = true;
            }
            // write remain whole file (from SOS)
            copyToStreamWithFixBuffer(is, os);
            Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> write end!!!");
            byte[] out = os.toByteArray();
            if (ENABLE_BUFFER_DUMP && fileName != null) {
                writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                        + "Out.raw", out);
            }
            return out;
        } catch (Exception e) {
            Log.i(TAG, "<writeJpsAndMaskAndConfigToJpgBuffer> Exception ", e);
            return null;
        } finally {
            try {
                if (is != null) {
                    is.close();
                    is = null;
                }
                if (os != null) {
                    os.close();
                    os = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<writeJpsAndMaskAndConfigToJpgBuffer> close IOException ", e);
            }
        }
    }

    // write to app15
    private static void writeJpsAndMaskToStream(
            ByteArrayOutputStreamExt os, byte[] jpsData, byte[] jpsMask) {
        try {
            Log.i(TAG, "<writeJpsAndMaskToStream> write begin!!!");
            int totalCount = 0;
            ArrayList<byte[]> jpsAndMaskArray = makeJpsAndMaskData(jpsData, jpsMask);
            for (int i = 0; i < jpsAndMaskArray.size(); i++) {
                byte[] section = jpsAndMaskArray.get(i);
                if (section[0] == 'J' && section[1] == 'P' && section[2] == 'S'
                        && section[3] == 'D') {
                    // current section is jps data
                    totalCount = jpsData.length;
                } else if (section[0] == 'J' && section[1] == 'P'
                        && section[2] == 'S' && section[3] == 'M') {
                    // current section is jps mark
                    totalCount = jpsMask.length;
                }
                //os.writeShort(APP1);
                os.writeShort(APP15);
                os.writeShort(section.length + 2 + 4);// 2: length tag bytes, 4:
                                                      // total length bytes
                os.writeInt(totalCount);// 4 bytes
                os.write(section);
            }
            Log.i(TAG, "<writeJpsAndMaskToStream> write end!!!");
        } catch (Exception e) {
            Log.i(TAG, "<writeJpsAndMaskToStream> Exception", e);
        }
    }

    private static void writeConfigToStream(ByteArrayOutputStreamExt os,
            byte[] jpsConfig) {
        try {
            Log.i(TAG, "<writeConfigToStream> write begin!!!");
            JpsConfigInfo configInfo = parseJpsConfigBuffer(jpsConfig);
            Log.i(TAG, "<writeJpsAndMaskAndConfigToStream> jpsConfigInfo "
                    + configInfo);
            if (ENABLE_BUFFER_DUMP) {
                writeStringToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                        + "Config_Written.txt", configInfo.toString());
            }
            byte[] xmpMain = makeXmpMainData(configInfo);
            os.writeShort(APP1);
            os.writeShort(xmpMain.length + 2);// need length 2 bytes
            os.write(xmpMain);
            Log.i(TAG, "<writeConfigToStream> write end!!!");
        } catch (Exception e) {
            Log.i(TAG, "<writeConfigToStream> Exception", e);
        }
    }

    private static ArrayList<byte[]> makeJpsAndMaskData(byte[] jpsData,
            byte[] jpsMask) {
        if (jpsData == null || jpsMask == null) {
            Log.i(TAG,
                  "<makeJpsAndMaskData> jpsData or jpsMask buffer is null!!!");
            return null;
        }

        int arrayIndex = 0;
        ArrayList<byte[]> jpsAndMaskArray = new ArrayList<byte[]>();

        for (int i = 0; i < 2; i++) {
            byte[] data = (i == 0 ? jpsData : jpsMask);
            String header = (i == 0 ? XmpResource.TYPE_JPS_DATA
                    : XmpResource.TYPE_JPS_MASK);
            int dataRemain = data.length;
            int dataOffset = 0;
            int sectionCount = 0;

            while (header.length() + 1 + dataRemain >= XmpResource.JPS_PACKET_SIZE) {
                byte[] section = new byte[XmpResource.JPS_PACKET_SIZE];
                // copy type
                System.arraycopy(header.getBytes(), 0, section, 0, header.length());
                // write section number
                section[header.length()] = (byte) sectionCount;
                // copy data
                System.arraycopy(data, dataOffset, section,
                        header.length() + 1, section.length - header.length() - 1);
                jpsAndMaskArray.add(arrayIndex, section);

                dataOffset += section.length - header.length() - 1;
                dataRemain = data.length - dataOffset;
                sectionCount++;
                arrayIndex++;
            }
            if (header.length() + 1 + dataRemain < XmpResource.JPS_PACKET_SIZE) {
                byte[] section = new byte[header.length() + 1 + dataRemain];
                // copy type
                System.arraycopy(header.getBytes(), 0, section, 0, header.length());
                // write section number
                section[header.length()] = (byte) sectionCount;
                // write data
                System.arraycopy(data, dataOffset, section,
                        header.length() + 1, dataRemain);
                jpsAndMaskArray.add(arrayIndex, section);
                arrayIndex++;
                // sectionCount++;
            }
        }
        return jpsAndMaskArray;
    }

    public static byte[] getJpsDataFromJpgFile(String filePath) {
        RandomAccessFile rafIn = null;
        try {
            rafIn = new RandomAccessFile(filePath, "r");
            //ArrayList<Section> sections = parseAppInfo(filePath);
            Log.i(TAG, "<getJpsDataFromJpgFile> begin!!! ");
            byte[] out = getJpsInfoFromSections(rafIn, true);
            if (ENABLE_BUFFER_DUMP) {
                writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                        + "Jps_Read.raw", out);
            }
            Log.i(TAG, "<getJpsDataFromJpgFile> end!!! ");
            return out;
        } catch (IOException e) {
            Log.i(TAG, "<getJpsDataFromJpgFile> IOException ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<getJpsDataFromJpgFile> IOException when close ", e);
            }
        }
    }

    public static byte[] getJpsMaskFromJpgFile(String filePath) {
        RandomAccessFile rafIn = null;
        try {
            rafIn = new RandomAccessFile(filePath, "r");
            //ArrayList<Section> sections = parseAppInfo(filePath);
            Log.i(TAG, "<getJpsMaskFromJpgFile> begin!!! ");
            byte[] out = getJpsInfoFromSections(rafIn, false);
            if (ENABLE_BUFFER_DUMP) {
                writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                        + "Mask_Read.raw", out);
            }
            Log.i(TAG, "<getJpsMaskFromJpgFile> end!!! ");
            return out;
        } catch (IOException e) {
            Log.i(TAG, "<getJpsMaskFromJpgFile> IOException ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<getJpsMaskFromJpgFile> IOException when close ", e);
            }
        }
    }

    private static byte[] getJpsInfoFromSections(RandomAccessFile rafIn,
            boolean isJpsDataOrMask) {
        try {
            Section sec = null;
            int dataLen = 0;
            // parse JPS Data or Mask length
            int i = 0;
            for (; i < sParsedSectionsForGallery.size(); i++) {
                sec = sParsedSectionsForGallery.get(i);
                if (isJpsDataOrMask && sec.isJpsData) {
                    rafIn.seek(sec.offset + 2 + 2);
                    dataLen = rafIn.readInt();
                    break;
                }
                if (!isJpsDataOrMask && sec.isJpsMask) {
                    rafIn.seek(sec.offset + 2 + 2);
                    dataLen = rafIn.readInt();
                    break;
                }
            }
            if (i == sParsedSectionsForGallery.size()) {
                Log.i(TAG,
                      "<getJpsInfoFromSections> can not find JPS INFO, return null");
                return null;
            }
            int app1Len = 0;
            int copyLen = 0;
            int byteOffset = 0;
            byte[] data = new byte[dataLen];

            for (i = i - 1; i < sParsedSectionsForGallery.size(); i++) {
                sec = sParsedSectionsForGallery.get(i);
                if (isJpsDataOrMask && sec.isJpsData) {
                    rafIn.seek(sec.offset + 2);
                    app1Len = rafIn.readUnsignedShort();
                    copyLen = app1Len - 2 - XmpResource.TOTAL_LENGTH_TAG_BYTE
                            - XmpResource.TYPE_JPS_DATA.length()
                            - XmpResource.JPS_SERIAL_NUM_TAG_BYTE;
                    rafIn.skipBytes(XmpResource.TOTAL_LENGTH_TAG_BYTE
                            + XmpResource.TYPE_JPS_DATA.length()
                            + XmpResource.JPS_SERIAL_NUM_TAG_BYTE);
                    rafIn.read(data, byteOffset, copyLen);
                    byteOffset += copyLen;
                }
                if (!isJpsDataOrMask && sec.isJpsMask) {
                    rafIn.seek(sec.offset + 2);
                    app1Len = rafIn.readUnsignedShort();
                    copyLen = app1Len - 2 - XmpResource.TOTAL_LENGTH_TAG_BYTE
                            - XmpResource.TYPE_JPS_MASK.length()
                            - XmpResource.JPS_SERIAL_NUM_TAG_BYTE;
                    rafIn.skipBytes(XmpResource.TOTAL_LENGTH_TAG_BYTE
                            + XmpResource.TYPE_JPS_MASK.length()
                            + XmpResource.JPS_SERIAL_NUM_TAG_BYTE);
                    rafIn.read(data, byteOffset, copyLen);
                    byteOffset += copyLen;
                }
            }
            return data;
        } catch (IOException e) {
            Log.i(TAG, "<getJpsInfoFromSections> IOException ", e);
            return null;
        }
    }

    private static JpsConfigInfo parseJpsConfigInfo(XMPMeta xmpMeta) {
        if (xmpMeta == null) {
            Log.i(TAG, "<parseJpsConfigInfo> xmpMeta is null, return!!!");
            return null;
        }
        JpsConfigInfo configInfo = new JpsConfigInfo();
        configInfo.jpsWidth = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, JPS_WIDTH);
        configInfo.jpsHeight = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, JPS_HEIGHT);
        configInfo.maskWidth = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MASK_WIDTH);
        configInfo.maskHeight = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MASK_HEIGHT);
        configInfo.posX = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, POS_X);
        configInfo.posY = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, POS_Y);
        configInfo.viewWidth = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, VIEW_WIDTH);
        configInfo.viewHeight = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, VIEW_HEIGHT);
        configInfo.orientation = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, ORIENTATION);
        configInfo.mainCamPos = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, MAIN_CAM_POS);
        configInfo.touchCoordX1st = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDX_1ST);
        configInfo.touchCoordY1st = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDY_1ST);
        return configInfo;
    }

    private static int calcJpgOutStreamSize(ArrayList<Section> sections,
            int jpgBufferSize, int jpsDataSize, int jpsMaskSize,
            int jpsConfigSize) {
        /*
         * new outPutStream length is: jpgBufferSize - (old jps data and mask
         * sections length) + (new jps data and mask sections and config length)
         */

        // calc old jps data and mask data length
        int oldJpsDataAndMskLen = 0;
        Log.i(TAG, "<calcJpgOutStreamSize> calc begin!!!");
        for (int i = 0; i < sections.size(); i++) {
            Section sec = sections.get(i);
            if (sec.isXmpMain || sec.isXmpExt || sec.isJpsData || sec.isJpsMask) {
                oldJpsDataAndMskLen += (sec.length + 2);
            }
        }
        Log.i(TAG, "<calcJpgOutStreamSize> jpgBufferSize 0x"
                + Integer.toHexString(jpgBufferSize));
        Log.i(TAG, "<calcJpgOutStreamSize> oldJpsDataAndMskLen 0x"
                + Integer.toHexString(oldJpsDataAndMskLen));

        int newConfigSize = 2 + 2 + jpsConfigSize;// tag + length + data
        Log.i(TAG, "<calcJpgOutStreamSize> newConfigSize 0x"
                + Integer.toHexString(newConfigSize));

        int dataSizePerSection = XmpResource.JPS_PURE_DATA_SIZE_PER_PACKET;

        // calc new jps data length
        int jpsDataPacketNum = (int) Math.ceil((double) jpsDataSize
                / (double) dataSizePerSection);
        int newJpsDataLen = XmpResource.JPS_PACKET_HEAD_SIZE_EXCLUDE_DATA
                * jpsDataPacketNum + jpsDataSize;
        Log.i(TAG, "<calcJpgOutStreamSize> jpsDataPacketNum 0x"
                + Integer.toHexString(jpsDataPacketNum) + ", newJpsDataLen 0x"
                + Integer.toHexString(newJpsDataLen));

        // calc new jps mask length
        int jpsMaskPacketNum = (int) Math.ceil((double) jpsMaskSize
                / (double) dataSizePerSection);
        int newJpsMskLen = XmpResource.JPS_PACKET_HEAD_SIZE_EXCLUDE_DATA
                * jpsMaskPacketNum + jpsMaskSize;
        Log.i(TAG, "<calcJpgOutStreamSize> jpsMaskPacketNum 0x"
                + Integer.toHexString(jpsMaskPacketNum) + ", newJpsMskLen 0x"
                + Integer.toHexString(newJpsMskLen));
        Log.i(TAG, "<calcJpgOutStreamSize> calc end!!!");
        return jpgBufferSize - oldJpsDataAndMskLen + newConfigSize
                + newJpsDataLen + newJpsMskLen;
    }

    // Config data structure:
    // jpsWidth,jpsHeight,maskWidth,maskHeight,posX,posY,
    // viewWidth,viewHeight,orientation,mainCamPos,touchCoordX1st,touchCoordY1st
    public static class JpsConfigInfo {
        public int jpsWidth;
        public int jpsHeight;
        public int maskWidth;
        public int maskHeight;
        public int posX;
        public int posY;
        public int viewWidth;
        public int viewHeight;
        public int orientation;
        public int mainCamPos;
        public int touchCoordX1st;
        public int touchCoordY1st;

        public JpsConfigInfo() {
        }

        @Override
        public String toString() {
            return "ConfigInfo:"
                + "\n    jpsWidth = 0x" + Integer.toHexString(jpsWidth) + "(" + jpsWidth + ")"
                + "\n    jpsHeight = 0x" + Integer.toHexString(jpsHeight) + "(" + jpsHeight + ")"
                + "\n    maskWidth = 0x" + Integer.toHexString(maskWidth) + "(" + maskWidth + ")"
                + "\n    maskHeight = 0x" + Integer.toHexString(maskHeight) + "(" + maskHeight + ")"
                + "\n    posX = 0x" + Integer.toHexString(posX) + "(" + posX + ")"
                + "\n    posY = 0x" + Integer.toHexString(posY) + "(" + posY + ")"
                + "\n    viewWidth = 0x" + Integer.toHexString(viewWidth) + "(" + viewWidth + ")"
                + "\n    viewHeight = 0x" + Integer.toHexString(viewHeight) + "(" + viewHeight + ")"
                + "\n    orientation = 0x" + Integer.toHexString(orientation) + "(" + orientation + ")"
                + "\n    mainCamPos = 0x" + Integer.toHexString(mainCamPos) + "(" + mainCamPos + ")"
                + "\n    touchCoordX1st = 0x" + Integer.toHexString(touchCoordX1st) + "(" + touchCoordX1st + ")"
                + "\n    touchCoordY1st = 0x" + Integer.toHexString(touchCoordY1st) + "(" + touchCoordY1st + ")";
        }
    }

    private static JpsConfigInfo parseJpsConfigBuffer(byte[] configBuffer) {
        if (configBuffer == null) {
            Log.i(TAG, "<parseJpsConfigBuffer> configBuffer is null!!!");
            return null;
        }

        ByteArrayInputStreamExt is = null;
        try {
            // native call back buffer with low byte first
            // e.g. 00 01 02 03, then the true value is: 0x03020100
            is = new ByteArrayInputStreamExt(configBuffer);
            JpsConfigInfo configInfo = new JpsConfigInfo();
            configInfo.jpsWidth = is.readReverseInt();
            configInfo.jpsHeight = is.readReverseInt();
            configInfo.maskWidth = is.readReverseInt();
            configInfo.maskHeight = is.readReverseInt();
            configInfo.posX = is.readReverseInt();
            configInfo.posY = is.readReverseInt();
            configInfo.viewWidth = is.readReverseInt();
            configInfo.viewHeight = is.readReverseInt();
            configInfo.orientation = is.readReverseInt();
            configInfo.mainCamPos = is.readReverseInt();
            configInfo.touchCoordX1st = is.readReverseInt();
            configInfo.touchCoordY1st = is.readReverseInt();
            return configInfo;
        } catch (Exception e) {
            Log.i(TAG, "<parseJpsConfigBuffer> Exception ", e);
            return null;
        } finally {
            try {
                if (is != null) {
                    is.close();
                    is = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<parseJpsConfigBuffer> close IOException ", e);
            }
        }
    }

    public static class DepthBufferInfo {
        // write below params to xmp main
        public boolean depthBufferFlag;
        public int depthBufferWidth;
        public int depthBufferHeight;
        public boolean xmpDepthFlag;
        public int xmpDepthWidth;
        public int xmpDepthHeight;
        public int touchCoordXLast;
        public int touchCoordYLast;
        public int depthOfFieldLast;
        // write below buffer to app1
        public byte[] depthData;
        public byte[] xmpDepthData;
        public DepthBufferInfo() {
            depthBufferFlag = false;
            depthBufferWidth = -1;
            depthBufferHeight = -1;
            xmpDepthFlag = false;
            xmpDepthWidth = -1;
            xmpDepthHeight = -1;
            touchCoordXLast = -1;
            touchCoordYLast = -1;
            depthOfFieldLast = -1;
            depthData = null;
            xmpDepthData = null;
        }

        public DepthBufferInfo(boolean depthBufferFlag, byte[] depthData,
                int depthBufferWidth, int depthBufferHeight,
                boolean xmpDepthFlag, byte[] xmpDepthData, int xmpDepthWidth,
                int xmpDepthHeight, int touchCoordXLast, int touchCoordYLast,
                int depthOfFieldLast) {
            this.depthBufferFlag = depthBufferFlag;
            this.depthBufferWidth = depthBufferWidth;
            this.depthBufferHeight = depthBufferHeight;
            this.xmpDepthFlag = xmpDepthFlag;
            this.xmpDepthWidth = xmpDepthWidth;
            this.xmpDepthHeight = xmpDepthHeight;
            this.touchCoordXLast = touchCoordXLast;
            this.touchCoordYLast = touchCoordYLast;
            this.depthOfFieldLast = depthOfFieldLast;
            this.depthData = depthData;
            this.xmpDepthData = xmpDepthData;
        }

        @Override
        public String toString() {
            String str1 = "DepthBufferInfo:"
                + "\n    depthBufferFlag = " + depthBufferFlag
                + "\n    xmpDepthFlag = " + xmpDepthFlag
                + "\n    depthBufferWidth = 0x" + Integer.toHexString(depthBufferWidth) + "(" + depthBufferWidth + ")"
                + "\n    depthBufferHeight = 0x" + Integer.toHexString(depthBufferHeight) + "(" + depthBufferHeight + ")"
                + "\n    xmpDepthWidth = 0x" + Integer.toHexString(xmpDepthWidth) + "(" + xmpDepthWidth + ")"
                + "\n    xmpDepthHeight = 0x" + Integer.toHexString(xmpDepthHeight) + "(" + xmpDepthHeight + ")"
                + "\n    touchCoordXLast = 0x" + Integer.toHexString(touchCoordXLast) + "(" + touchCoordXLast + ")"
                + "\n    touchCoordYLast = 0x" + Integer.toHexString(touchCoordYLast) + "(" + touchCoordYLast + ")"
                + "\n    depthOfFieldLast = 0x" + Integer.toHexString(depthOfFieldLast) + "(" + depthOfFieldLast + ")";
            String str2 = null;
            if (depthData != null) {
                str2 = "\n    depthData length = 0x"
                        + Integer.toHexString(depthData.length) + "("
                        + depthData.length + ")";
            } else {
                str2 = "\n    depthData = null";
            }
            String str3 = null;
            if (xmpDepthData != null) {
                str3 = "\n    xmpDepthData length = 0x"
                        + Integer.toHexString(xmpDepthData.length) + "("
                        + xmpDepthData.length + ")";
            } else 
                str3 = "\n    xmpDepthData = null";
            return str1 + str2 + str3;
        }
    }

    public static boolean writeDepthBufferToJpg(String srcFilePath,
            DepthBufferInfo depthBufferInfo, boolean deleteJps) {
        String tempPath = srcFilePath + ".tmp";
        boolean result = writeDepthBufferToJpg(srcFilePath, tempPath,
                depthBufferInfo, !ENABLE_BUFFER_DUMP);
        // delete source file and rename new file to source file
        Log.i(TAG, "<writeDepthBufferToJpg> delete src file and rename back!!!");
        File srcFile = new File(srcFilePath);
        File outFile = new File(tempPath);
        srcFile.delete();
        outFile.renameTo(srcFile);
        Log.i(TAG, "<writeDepthBufferToJpg> refresh app sections!!!");
        sParsedSectionsForGallery = parseAppInfo(srcFilePath);
        return result;
    }

    private static boolean writeDepthBufferToJpg(String srcFilePath,
            String dstFilePath, DepthBufferInfo depthBufferInfo,
            boolean deleteJps) {
        if (depthBufferInfo == null) {
            Log.i(TAG, "<writeDepthBufferToJpg> depthBufferInfo is null!!!");
            return false;
        }
        if (ENABLE_BUFFER_DUMP && depthBufferInfo.depthData != null) {
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "DepthBuffer_Written.raw", depthBufferInfo.depthData);
        }
        if (ENABLE_BUFFER_DUMP && depthBufferInfo.xmpDepthData != null) {
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "XMPDepthMap_Written.raw", depthBufferInfo.xmpDepthData);
        }
        if (ENABLE_BUFFER_DUMP) {
            writeStringToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "DepthBufferInfo_Written.txt", depthBufferInfo.toString());
        }
        Log.i(TAG, "<writeDepthBufferToJpg> write begin!!!");
        //ArrayList<Section> sections = parseAppInfo(srcFilePath);

        // begin to copy or replace
        RandomAccessFile rafIn = null;
        RandomAccessFile rafOut = null;
        try {
            File outFile = new File(dstFilePath);
            if (outFile.exists()) {
                outFile.delete();
            }
            outFile.createNewFile();
            rafIn = new RandomAccessFile(srcFilePath, "r");
            // rafOut = new RandomAccessFile(dstFilePath, "rw");
            rafOut = new RandomAccessFile(outFile, "rw");

            if (rafIn.readUnsignedShort() != SOI) {
                Log.i(TAG,
                      "<writeDepthBufferToJpg> image is not begin with 0xffd8!!!");
                return false;
            }

            rafOut.writeShort(SOI);
            boolean hasUpdateXmpMain = false;
            boolean hasWritenDepthData = false;
            XMPMeta meta = getXmpMetaFromFile(srcFilePath);
            int writenLocation = findProperLocationForXmp(sParsedSectionsForGallery);
            if (writenLocation == WRITE_XMP_AFTER_SOI) {
                //writeOnlyDepthBuffer(rafOut, meta, depthBufferInfo);
                updateOnlyDepthInfoWoBuffer(rafOut, meta, depthBufferInfo);
                hasUpdateXmpMain = true;
            }
            boolean needUpdateDepthBuffer = depthBufferInfo.depthData != null ? true : false;
            boolean needUpdateXmpDepth = depthBufferInfo.xmpDepthData != null ? true : false;
            for (int i = 0; i < sParsedSectionsForGallery.size(); i++) {
                Section sec = sParsedSectionsForGallery.get(i);
                if (sec.isExif) {
                    writeSectionToFile(rafIn, rafOut, sec);
                    if (!hasUpdateXmpMain) {
                        //writeOnlyDepthBuffer(rafOut, meta, depthBufferInfo);
                        updateOnlyDepthInfoWoBuffer(rafOut, meta, depthBufferInfo);
                        hasUpdateXmpMain = true;
                    }
                } else {
                    if (!hasUpdateXmpMain) {
                        //writeOnlyDepthBuffer(rafOut, meta, depthBufferInfo);
                        updateOnlyDepthInfoWoBuffer(rafOut, meta, depthBufferInfo);
                        hasUpdateXmpMain = true;
                    }
                    // APPx must be before DQT/DHT
                    if (!hasWritenDepthData && (sec.marker == DQT || sec.marker == DHT)) {
                        writeOnlyDepthBuffer(rafOut, depthBufferInfo);
                        hasWritenDepthData = true;
                    }
                    if (sec.isXmpMain || sec.isXmpExt) {
                        // delete old xmp main and ext
                        rafIn.skipBytes(sec.length + 2);
                    } else if (deleteJps && (sec.isJpsData || sec.isJpsMask)) {
                        rafIn.skipBytes(sec.length + 2);
                    } else if (needUpdateDepthBuffer && sec.isDepthData) {
                        // delete depth data
                        rafIn.skipBytes(sec.length + 2);
                    } else if (needUpdateXmpDepth && sec.isXmpDepth) {
                        // delete xmp depth
                        rafIn.skipBytes(sec.length + 2);
                    }else {
                        writeSectionToFile(rafIn, rafOut, sec);
                    }
                }
            }
            // write buffer to app15
            if (!hasWritenDepthData) {
                writeOnlyDepthBuffer(rafOut, depthBufferInfo);
                hasWritenDepthData = true;
            }
            // write remain whole file (from SOS)
            copyFileWithFixBuffer(rafIn, rafOut);
            Log.i(TAG, "<writeDepthBufferToJpg> write end!!!");
            return true;
        } catch (Exception e) {
            Log.i(TAG, "<writeDepthBufferToJpg> Exception", e);
            return false;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
                if (rafOut != null) {
                    rafOut.close();
                    rafOut = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<writeDepthBufferToJpg> raf close, IOException", e);
            }
        }
    }

    // just update depthBufferInfo(exclude depthBufferInfo.depthData/xmpDepthData) to xmp main section
    private static boolean updateOnlyDepthInfoWoBuffer(RandomAccessFile rafOut, XMPMeta meta,
            DepthBufferInfo depthBufferInfo) {
        if (rafOut == null || meta == null || depthBufferInfo == null) {
            Log.i(TAG,
                  "<updateOnlyDepthInfoWoBuffer> input params are null, return false!!!");
            return false;
        }
        Log.i(TAG, "<updateOnlyDepthInfoWoBuffer> write begin!!!");
        // step 1: write property
        byte[] newXmpMainBuffer = updateXmpMainDataWithDepthBuffer(meta, depthBufferInfo);
        if (newXmpMainBuffer == null) {
            Log.i(TAG, "<updateOnlyDepthInfoWoBuffer> updated xmp main data is null, return false!!!");
            return false;
        }
        try {
            rafOut.writeShort(APP1);
            rafOut.writeShort(newXmpMainBuffer.length + 2);
            rafOut.write(newXmpMainBuffer);
            Log.i(TAG, "<updateOnlyDepthInfoWoBuffer> write end!!!");
            return true;
        } catch (IOException e) {
            Log.i(TAG, "<updateOnlyDepthInfoWoBuffer> IOException ", e);
            return false;
        }
    }

    // just write depthBufferInfo.depthData/xmpDepthData) to app15
    private static boolean writeOnlyDepthBuffer(RandomAccessFile rafOut,
            DepthBufferInfo depthBufferInfo) {
        if (rafOut == null || depthBufferInfo == null) {
            Log.i(TAG,
                  "<writeOnlyDepthBuffer> input params are null, return false!!!");
            return false;
        }
        Log.i(TAG, "<writeOnlyDepthBuffer> write begin!!!");
        if (depthBufferInfo.depthData == null
                && depthBufferInfo.xmpDepthData == null) {
            Log.i(TAG, "<writeOnlyDepthBuffer> 2 depth buffers are null, skip write depth buffer!!!");
            return true;
        }
        try {
            int totalCount = 0;
            ArrayList<byte[]> depthDataArray = makeDepthData(
                    depthBufferInfo.depthData, depthBufferInfo.xmpDepthData);
            if (depthDataArray == null) {
                Log.i(TAG, "<writeOnlyDepthBuffer> depthDataArray is null, skip write depth buffer!!!");
                return true;
            }
            for (int i = 0; i < depthDataArray.size(); i++) {
                byte[] section = depthDataArray.get(i);
                if (section[0] == 'D' && section[1] == 'E' && section[2] == 'P'
                        && section[3] == 'T') {
                    // current section is depth buffer
                    totalCount = depthBufferInfo.depthData.length;
                    Log.i(TAG,
                            "<writeOnlyDepthBuffer> write depthData total count: 0x"
                                    + Integer.toHexString(totalCount));
                } else if (section[0] == 'X' && section[1] == 'M'
                        && section[2] == 'P' && section[3] == 'D') {
                    // current section is xmp depth buffer
                    totalCount = depthBufferInfo.xmpDepthData.length;
                    Log.i(TAG,
                            "<writeOnlyDepthBuffer> write xmpDepthData total count: 0x"
                                    + Integer.toHexString(totalCount));
                }
                //rafOut.writeShort(APP1);
                rafOut.writeShort(APP15);
                rafOut.writeShort(section.length + 2 + 4);// 2: length tag bytes, 4:
                                                      // total length bytes
                rafOut.writeInt(totalCount);// 4 bytes
                rafOut.write(section);
            }
            Log.i(TAG, "<writeOnlyDepthBuffer> write end!!!");
            return true;
        } catch (IOException e) {
            Log.i(TAG, "<writeOnlyDepthBuffer> IOException ", e);
            return false;
        }
    }

    private static ArrayList<byte[]> makeDepthData(byte[] depthData,
            byte[] xmpDepthData) {
        if (depthData == null && xmpDepthData == null) {
            Log.i(TAG,
                  "<makeDepthData> depthData and xmpDepthData are null, skip!!!");
            return null;
        }

        int arrayIndex = 0;
        ArrayList<byte[]> depthDataArray = new ArrayList<byte[]>();

        for (int i = 0; i < 2; i++) {
            if (i == 0 && depthData == null) {
                Log.i(TAG, "<makeDepthData> depthData is null, skip!!!");
                continue;
            }
            if (i == 1 && xmpDepthData == null) {
                Log.i(TAG, "<makeDepthData> xmpDepthData is null, skip!!!");
                continue;
            }
            byte[] data = (i == 0 ? depthData : xmpDepthData);
            String header = (i == 0 ? XmpResource.TYPE_DEPTH_DATA
                    : XmpResource.TYPE_XMP_DEPTH);
            int dataRemain = data.length;
            int dataOffset = 0;
            int sectionCount = 0;

            while (header.length() + 1 + dataRemain >= XmpResource.DEPTH_PACKET_SIZE) {
                byte[] section = new byte[XmpResource.DEPTH_PACKET_SIZE];
                // copy type
                System.arraycopy(header.getBytes(), 0, section, 0, header
                        .length());
                // write section number
                section[header.length()] = (byte) sectionCount;
                // copy data
                System.arraycopy(data, dataOffset, section,
                        header.length() + 1, section.length - header.length()
                                - 1);
                depthDataArray.add(arrayIndex, section);
                dataOffset += section.length - header.length() - 1;
                dataRemain = data.length - dataOffset;
                sectionCount++;
                arrayIndex++;
            }
            if (header.length() + 1 + dataRemain < XmpResource.DEPTH_PACKET_SIZE) {
                byte[] section = new byte[header.length() + 1 + dataRemain];
                // copy type
                System.arraycopy(header.getBytes(), 0, section, 0, header
                        .length());
                // write section number
                section[header.length()] = (byte) sectionCount;
                // write data
                System.arraycopy(data, dataOffset, section,
                        header.length() + 1, dataRemain);
                depthDataArray.add(arrayIndex, section);
                arrayIndex++;
                // sectionCount++;
            }
        }
        return depthDataArray;
    }

    private static XMPMeta getXmpMetaFromFile(String filePath) {
        File srcFile = new File(filePath);
        if (!srcFile.exists()) {
            Log.i(TAG, "<getXmpMetaFromFile> " + filePath + " not exists!!!");
            return null;
        }

        RandomAccessFile rafIn = null;
        XMPMeta meta = null;
        try {
            rafIn = new RandomAccessFile(filePath, "r");
            // if (sections == null) {
            // sections = parseAppInfo(filePath);
            // }
            Section sec = null;
            for (int i = 0; i < sParsedSectionsForGallery.size(); i++) {
                sec = sParsedSectionsForGallery.get(i);
                if (sec.isXmpMain) {
                    rafIn.seek(sec.offset + 2);
                    int len = rafIn.readUnsignedShort() - 2;
                    int xmpLen = len - XmpResource.XMP_HEADER_START.length();
                    byte[] buffer = new byte[xmpLen];
                    rafIn.skipBytes(XmpResource.XMP_HEADER_START.length());
                    rafIn.read(buffer, 0, buffer.length);
                    meta = XMPMetaFactory.parseFromBuffer(buffer);
                    if (meta == null) {
                        Log.i(TAG, "<getXmpMetaFromFile> parsed XMPMeta is null, create one!!!");
                        meta  = XMPMetaFactory.create();
                    } else {
                        Log.i(TAG, "<getXmpMetaFromFile> return parsed XMPMeta");
                    }
                    return meta;
                }
            }
            meta  = XMPMetaFactory.create();
            Log.i(TAG, "<getXmpMetaFromFile> no xmp main, then create XMPMeta!!!");
            return meta;
        } catch (IOException e) {
            Log.i(TAG, "<getXmpMetaFromFile> IOException ", e);
            return null;
        } catch (XMPException e) {
            Log.i(TAG, "<getXmpMetaFromFile> XMPException ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG, "<getXmpMetaFromFile> IOException when close ", e);
            }
        }
    }

    private static byte[] getDepthDataFromJpgFile(String filePath) {
        RandomAccessFile rafIn = null;
        try {
            Log.i(TAG, "<getDepthDataFromJpgFile> run...");
            rafIn = new RandomAccessFile(filePath, "r");
            return getDepthDataFromSections(rafIn, true);
        } catch (IOException e) {
            Log.i(TAG, "<getDepthDataFromJpgFile> IOException ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<getDepthDataFromJpgFile> IOException when close ", e);
            }
        }
    }

    private static byte[] getXmpDepthDataFromJpgFile(String filePath) {
        RandomAccessFile rafIn = null;
        try {
            Log.i(TAG, "<getXmpDepthDataFromJpgFile> run...");
            rafIn = new RandomAccessFile(filePath, "r");
            return getDepthDataFromSections(rafIn, false);
        } catch (IOException e) {
            Log.i(TAG, "<getXmpDepthDataFromJpgFile> IOException ", e);
            return null;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.i(TAG,
                      "<getXmpDepthDataFromJpgFile> IOException when close ", e);
            }
        }
    }

    private static byte[] getDepthDataFromSections(RandomAccessFile rafIn,
            boolean isDepthOrXmpDepth) {
        try {
            Section sec = null;
            int dataLen = 0;
            int i = 0;
            for (; i < sParsedSectionsForGallery.size(); i++) {
                sec = sParsedSectionsForGallery.get(i);
                if (isDepthOrXmpDepth && sec.isDepthData) {
                    rafIn.seek(sec.offset + 2 + 2);
                    dataLen = rafIn.readInt();
                    Log.i(TAG,
                            "<getDepthDataFromSections> type DEPTH DATA, dataLen: 0x"
                                    + Integer.toHexString(dataLen));
                    break;
                }
                if (!isDepthOrXmpDepth && sec.isXmpDepth) {
                    rafIn.seek(sec.offset + 2 + 2);
                    dataLen = rafIn.readInt();
                    Log.i(TAG,
                            "<getDepthDataFromSections> type XMP DEPTH, dataLen: 0x"
                                    + Integer.toHexString(dataLen));
                    break;
                }
            }
            if (i == sParsedSectionsForGallery.size()) {
                Log.i(TAG,
                      "<getDepthDataFromSections> can not find DEPTH INFO, return null");
                return null;
            }
            int app1Len = 0;
            int copyLen = 0;
            int byteOffset = 0;
            byte[] data = new byte[dataLen];

            for (i = i - 1; i < sParsedSectionsForGallery.size(); i++) {
                sec = sParsedSectionsForGallery.get(i);
                if (isDepthOrXmpDepth && sec.isDepthData) {
                    rafIn.seek(sec.offset + 2);
                    app1Len = rafIn.readUnsignedShort();
                    copyLen = app1Len - 2 - XmpResource.TOTAL_LENGTH_TAG_BYTE
                            - XmpResource.TYPE_DEPTH_DATA.length()
                            - XmpResource.DEPTH_SERIAL_NUM_TAG_BYTE;
                    Log.i(TAG, "<getDepthDataFromSections> app1Len: 0x"
                            + Integer.toHexString(app1Len) + ", copyLen 0x"
                            + Integer.toHexString(copyLen));
                    rafIn.skipBytes(XmpResource.TOTAL_LENGTH_TAG_BYTE
                            + XmpResource.TYPE_DEPTH_DATA.length()
                            + XmpResource.DEPTH_SERIAL_NUM_TAG_BYTE);
                    rafIn.read(data, byteOffset, copyLen);
                    byteOffset += copyLen;
                }
                if (!isDepthOrXmpDepth && sec.isXmpDepth) {
                    rafIn.seek(sec.offset + 2);
                    app1Len = rafIn.readUnsignedShort();
                    copyLen = app1Len - 2 - XmpResource.TOTAL_LENGTH_TAG_BYTE
                            - XmpResource.TYPE_XMP_DEPTH.length()
                            - XmpResource.DEPTH_SERIAL_NUM_TAG_BYTE;
                    Log.i(TAG, "<getDepthDataFromSections> app1Len: 0x"
                            + Integer.toHexString(app1Len) + ", copyLen 0x"
                            + Integer.toHexString(copyLen));
                    rafIn.skipBytes(XmpResource.TOTAL_LENGTH_TAG_BYTE
                            + XmpResource.TYPE_XMP_DEPTH.length()
                            + XmpResource.DEPTH_SERIAL_NUM_TAG_BYTE);
                    rafIn.read(data, byteOffset, copyLen);
                    byteOffset += copyLen;
                }
            }
            return data;
        } catch (IOException e) {
            Log.i(TAG, "<getDepthDataFromSections> IOException ", e);
            return null;
        }
    }

    public static DepthBufferInfo getDepthBufferInfoFromFile(String filePath) {
        Log.i(TAG, "<getDepthBufferInfoFromFile> begin!!!");
        XMPMeta meta = getXmpMetaFromFile(filePath);
        DepthBufferInfo depthBufferInfo = parseDepthBufferInfo(meta);
        if (depthBufferInfo == null) {
            Log.i(TAG, "<getDepthBufferInfoFromFile> depthBufferInfo is null!!!");
            return null;
        }
        depthBufferInfo.depthData = getDepthDataFromJpgFile(filePath);
        depthBufferInfo.xmpDepthData = getXmpDepthDataFromJpgFile(filePath);
        Log.i(TAG, "<getDepthBufferInfoFromFile> " + depthBufferInfo);
        if (ENABLE_BUFFER_DUMP && depthBufferInfo.depthData != null) {
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "DepthBuffer_Read.raw", depthBufferInfo.depthData);
        }
        if (ENABLE_BUFFER_DUMP && depthBufferInfo.xmpDepthData != null) {
            writeBufferToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "XMPDepthMap_Read.raw", depthBufferInfo.xmpDepthData);
        }
        if (ENABLE_BUFFER_DUMP) {
            writeStringToFile(DUMP_PATH + DUMP_FOLDER_NAME + FILE_NAME
                    + "DepthBufferInfo_Read.txt", depthBufferInfo.toString());
        }
        Log.i(TAG, "<getDepthBufferInfoFromFile> end!!!");
        return depthBufferInfo;
    }

    private static DepthBufferInfo parseDepthBufferInfo(XMPMeta xmpMeta) {
        if (xmpMeta == null) {
            Log.i(TAG, "<parseDepthBufferInfo> xmpMeta is null, return!!!");
            return null;
        }
        DepthBufferInfo depthBufferInfo = new DepthBufferInfo();
        depthBufferInfo.depthBufferWidth = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_WIDTH);
        depthBufferInfo.depthBufferHeight = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_HEIGHT);
        depthBufferInfo.xmpDepthWidth = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_WIDTH);
        depthBufferInfo.xmpDepthHeight = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_HEIGHT);
        depthBufferInfo.touchCoordXLast = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDX_LAST);
        depthBufferInfo.touchCoordYLast = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, TOUCH_COORDY_LAST);
        depthBufferInfo.depthOfFieldLast = getPropertyInteger(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_OF_FIELD_LAST);
        depthBufferInfo.depthBufferFlag = getPropertyBoolean(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, DEPTH_BUFFER_FLAG);
        depthBufferInfo.xmpDepthFlag = getPropertyBoolean(xmpMeta,
                MEDIATEK_IMAGE_REFOCUS_NAMESPACE, XMP_DEPTH_FLAG);
        return depthBufferInfo;
    }

    private static String getFileNameFromPath(String filePath) {
        if (filePath == null) return null;
        int start = filePath.lastIndexOf("/");
        if (start < 0 || start > filePath.length()) return filePath;
        return filePath.substring(start);
    }

    private static void makeDir(String filePath) {
        if (filePath == null) return;
        File dir = new File(filePath);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }
}

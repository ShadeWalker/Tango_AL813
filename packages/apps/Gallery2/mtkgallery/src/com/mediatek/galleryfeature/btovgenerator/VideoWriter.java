package com.mediatek.galleryfeature.btovgenerator;

import com.mediatek.galleryframework.util.MtkLog;

public class VideoWriter {
    public static String TAG = "MtkGallery2/VideoWriter";
    public static final int KEY_FRAME_RATE = 0;
    public static final String MEDIA_MIMETYPE_VIDEO_AVC = "video/avc";
    public static final String MEDIA_MIMETYPE_VIDEO_MPEG4 = "video/mp4v-es";
    public static final String MEDIA_MIMETYPE_VIDEO_H263 = "video/3gpp";

    private float mFps = 15; //default 15fps
    private int mWidth;
    private int mHeight;

    private int mFrameNumber = 0;
    //private int mTimeScale = 1000;
    private int mMediaTimeScale = 90000;
    private int mChunkOffset = 0;
    private int mMdatOffset = 0;
    private int mMdatSize = 0;
    private String mEncoderType;

    private DirectBox mFtyp = new DirectBox("ftyp");
    private Box mMdat = new Box("mdat");
    private Box mMoov = new Box("moov");
    private FullBox mMvhd = new FullBox("mvhd", 0, 0);
    private Box mTrak = new Box("trak");
    private FullBox mTkhd = new FullBox("tkhd", 0, 7);
    private Box mMdia = new Box("mdia");
    private FullBox mMdhd = new FullBox("mdhd", 0, 0);
    private FullBox mHdlr = new FullBox("hdlr", 0, 0);
    private Box mMinf = new Box("minf");
    private FullBox mVmhd = new FullBox("vmhd", 0, 1);
    private Box mDinf = new Box("dinf");
    private FullBox mDref = new FullBox("dref", 0, 0);
    private Box mStbl = new Box("stbl");
    private FullBox mStsd = new FullBox("stsd", 0, 0);
    private FullBox mStts = new FullBox("stts", 0, 0);
    private FullBox mStss = new FullBox("stss", 0, 0);
    private FullBox mStsz = new FullBox("stsz", 0, 0);
    private FullBox mStsc = new FullBox("stsc", 0, 0);
    private FullBox mStco = new FullBox("stco", 0, 0);
    private FullBox mUrl = new FullBox("url ", 0, 1);
    private DirectBox mEsds;
    private DirectBox mAvcC;

    public VideoWriter(String path, int width, int height, int fileType, String encoderType) {
        mWidth = width;
        mHeight = height;
        FileWriter.openFile(path);
        mFtyp.setFileType(fileType);
        mEncoderType = encoderType;
        initBoxes();
    }

    public void initBoxes() {
        mMoov.addSubBox(mMvhd);
        mMoov.addSubBox(mTrak);
        mTrak.addSubBox(mTkhd);
        mTrak.addSubBox(mMdia);
        mMdia.addSubBox(mMdhd);
        mMdia.addSubBox(mHdlr);
        mMdia.addSubBox(mMinf);
        mMinf.addSubBox(mVmhd);
        mMinf.addSubBox(mDinf);
        mMinf.addSubBox(mStbl);
        mDinf.addSubBox(mDref);
        mDref.addSubBox(mUrl);
        mStbl.addSubBox(mStsd);
        if (mEncoderType == null || mEncoderType.equals(MEDIA_MIMETYPE_VIDEO_MPEG4)) {
            DirectBox mp4v = new DirectBox("mp4v");
            mEsds = new DirectBox("esds");
            DirectBox pasp = new DirectBox("pasp");
            mStsd.addSubBox(mp4v);
            mp4v.addSubBox(mEsds);
            mp4v.addSubBox(pasp);
        } else if (mEncoderType.equals(MEDIA_MIMETYPE_VIDEO_AVC)) {
            DirectBox avc1 = new DirectBox("avc1");
            mAvcC = new DirectBox("avcC");
            DirectBox pasp = new DirectBox("pasp");
            mStsd.addSubBox(avc1);
            avc1.addSubBox(mAvcC);
            avc1.addSubBox(pasp);
        } else if (mEncoderType.equals(MEDIA_MIMETYPE_VIDEO_H263)) {
            DirectBox s263 = new DirectBox("s263");
            DirectBox d263 = new DirectBox("d263");
            DirectBox pasp = new DirectBox("pasp");
            mStsd.addSubBox(s263);
            s263.addSubBox(d263);

        } else {
            throw new AssertionError();
        }
        mStbl.addSubBox(mStts);
        mStbl.addSubBox(mStss);
        mStbl.addSubBox(mStsz);
        mStbl.addSubBox(mStsc);
        mStbl.addSubBox(mStco);
    }

    public void setParameter(int type, float data) {
        if (type == KEY_FRAME_RATE) {
            mFps = data;
        }
    }

    public void start() {
        MtkLog.d(TAG, "video writer start");
        mFtyp.wholeWrite();
        mMdat.wholeWrite();

        mChunkOffset = mFtyp.getBoxSize() + mMdat.getBoxSize();
        mMdatOffset = mFtyp.getBoxSize();
        mMdatSize = mMdat.getBoxSize();
        FileWriter.writeBufToFile();
    }

    public void receiveFrameBuffer(byte[] outData, int bufferSize, boolean iFrame) {
        FileWriter.writeBitStreamToFile(outData, bufferSize);
        mStsz.add(bufferSize);
        mMdatSize += bufferSize;
        mFrameNumber++;
        if (iFrame) {
            mStss.add(mFrameNumber);
        }
    }

    public void setCodecSpecifiData(byte[] data) {
        if (mEncoderType == null || mEncoderType.equals(MEDIA_MIMETYPE_VIDEO_MPEG4)) {
            mEsds.setCodecSpecifiData(data);
        } else if (mEncoderType.equals(MEDIA_MIMETYPE_VIDEO_AVC)) {
            mAvcC.setCodecSpecifiData(data);
        }
    }

    public void close() {
        MtkLog.d(TAG, "video writer close");
        writeMoovBox();
        FileWriter.writeBufToFile();
        //set mdat size
        FileWriter.setFileData(mMdatOffset, mMdatSize);
        FileWriter.close();
    }

    private void writeMoovBox() {
        initBoxesData();
        mMoov.wholeWrite();
    }

    private void initBoxesData() {
        //mp4 file uses time counting seconds since midnight,Jan.1.1904
        //while time function returns Unix epoch values while starts
        //at 1907-01-01, lets add the number of seconds between them
        int now = (int) (System.currentTimeMillis() / 1000 + (66 * 365 + 17) * (24 * 60 * 60));
        FullBox.mCreateTime = now;
        FullBox.mFps = mFps;
        FullBox.mFrameNumber = mFrameNumber;
        FullBox.mMediaTimeScale = mMediaTimeScale;
        FullBox.mWidth = mWidth;
        FullBox.mHeight = mHeight;
        DirectBox.mWidth = mWidth;
        DirectBox.mHeight = mHeight;

        mStts.add(mFrameNumber, (int) (mMediaTimeScale / mFps));
        mStsc.add(1, mFrameNumber, 1);
        mStco.add(mChunkOffset);
    }
}

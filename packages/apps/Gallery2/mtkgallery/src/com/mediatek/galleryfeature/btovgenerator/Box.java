package com.mediatek.galleryfeature.btovgenerator;

import java.util.ArrayList;

public class Box {
    protected String mType;
    private int mBeginPos;
    private int mEndPos;
    private ArrayList<Box> mSubBox = new ArrayList<Box>();

    public Box(String type) {
        mType = type;
    }

    public void write() {
        FileWriter.writeInt32(0); //size
        FileWriter.writeString(mType, 4);
    }

    public void wholeWrite() {
        mBeginPos = FileWriter.getCurBufPos();
        write();
        for (Box box : mSubBox) {
            box.wholeWrite();
        }
        //end box
        mEndPos = FileWriter.getCurBufPos();
        FileWriter.setBufferData(mBeginPos, mEndPos - mBeginPos);
    }

    public void addSubBox(Box box) {
        mSubBox.add(box);
    }

    public int getBoxSize() {
        return (mEndPos - mBeginPos);
    }
}
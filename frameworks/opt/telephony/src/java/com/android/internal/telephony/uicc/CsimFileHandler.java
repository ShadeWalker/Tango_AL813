/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccFileHandler.LoadLinearFixedContext;

import java.util.ArrayList;
/**
 * {@hide}
 * This class should be used to access files in CSIM ADF
 */
public final class CsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "CsimFH";

    private int[] adnRecordSize = {-1, -1, -1, -1};
    int mMaxNameLength = 0;
    int maxnumberLength = 20; //see AdnRecord

    public CsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
    }

    @Override
    protected String getEFPath(int efid) {
        logd("GetEFPath : " + efid);
        switch(efid) {
        case EF_SMS:
            return MF_SIM + DF_CDMA;
        case EF_CST:
        case EF_FDN:
        case EF_MSISDN:
        case EF_RUIM_SPN:
        //case EF_CSIM_LI:
        case EF_CSIM_MDN:
        case EF_CSIM_IMSIM:
        case EF_CSIM_CDMAHOME:
        case EF_CSIM_EPRL:
        case EF_CSIM_MIPUPP:
            return MF_SIM + DF_ADF;
        }
        String path = getCommonIccEFPath(efid);
        if (path == null) {
            // The EFids in UICC phone book entries are decided by the card manufacturer.
            // So if we don't match any of the cases above and if its a UICC return
            // the global 3g phone book path.
            return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
        }
        return path;
    }

    /// M: MTK added. @{
    protected String getEFPath(int efid, boolean is7FFF) {
        return getEFPath(efid);
    }
    /// @}

    /// M: MTK added. @{
    /**
     * Add RuimFileHandler handlemessage to process different simio response,
     * for UICC card, the get/select command will return different response
     * with normal UIM/SIM card, so we must parse it differently.
     * @param msg SIM IO message
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        IccIoResult result;
        Message response = null;
        String str;
        LoadLinearFixedContext lc;

        byte data[];
        int size;
        int fileid;
        int recordNum;
        int recordSize[];

        try {
            switch (msg.what) {
            case EVENT_GET_BINARY_SIZE_DONE:
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (processException(response, (AsyncResult) msg.obj)) {
                    break;
                }

                data = result.payload;

                size = parseSizeInfoForTransparent(data);

                if (size >= 0) {
                    fileid = msg.arg1;
                    loge("response.obj = " + response.obj);
                    String efPath = getEFPath(fileid);

                    mCi.iccIOForApp(COMMAND_READ_BINARY, fileid, efPath,
                                    0, 0, size, null, null, mAid,
                                    obtainMessage(EVENT_READ_BINARY_DONE,
                                                  fileid, 0, response));
                } else {
                    super.handleMessage(msg);
                }
                break;
            case EVENT_GET_RECORD_SIZE_DONE:
                ar = (AsyncResult) msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;

                if (processException(response, (AsyncResult) msg.obj)) {
                    break;
                }

                data = result.payload;

                if (parseRecordsInfoForLinearFixed(data, lc)) {
                    if (lc.mLoadAll) {
                        lc.results = new ArrayList<byte[]>(lc.mCountRecords);
                    }

                    if (lc.mMode != -1) {
                        mCi.iccIOForApp(COMMAND_READ_RECORD, lc.mEfid, getSmsEFPath(lc.mMode),
                                lc.mRecordNum,
                                READ_RECORD_MODE_ABSOLUTE,
                                lc.mRecordSize, null, null, mAid,
                                obtainMessage(EVENT_READ_RECORD_DONE, lc));
                    } else {
                        mCi.iccIOForApp(COMMAND_READ_RECORD, lc.mEfid, getEFPath(lc.mEfid),
                                lc.mRecordNum,
                                READ_RECORD_MODE_ABSOLUTE,
                                lc.mRecordSize, null, null, mAid,
                                obtainMessage(EVENT_READ_RECORD_DONE, lc));
                   }
                } else {
                    super.handleMessage(msg);
                }
                break;

            case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
                ar = (AsyncResult) msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;

                if (processException(response, (AsyncResult) msg.obj)) {
                    break;
                }

                data = result.payload;

                if (parseRecordsInfoForLinearFixed(data, lc)) {
                    recordSize = new int[3];
                    recordSize[0] = lc.mRecordSize;
                    recordSize[1] = lc.mCountRecords * recordSize[0];
                    recordSize[2] = lc.mCountRecords;

                    mMaxNameLength = lc.mRecordSize - 14; //see adnRecord
                    sendResult(response, recordSize, null);
                } else {
                    super.handleMessage(msg);
                }
                break;

            case EVENT_GET_RECORD_SIZE_IMG_DONE:
                logd("get record size img done");
                ar = (AsyncResult) msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;

                if (processException(response, (AsyncResult) msg.obj)) {
                    break;
                }

                data = result.payload;

                if (parseRecordsInfoForLinearFixed(data, lc)) {
                    logd("read EF IMG");
                    mCi.iccIOForApp(COMMAND_READ_RECORD, lc.mEfid, getEFPath(lc.mEfid),
                            lc.mRecordNum,
                            READ_RECORD_MODE_ABSOLUTE,
                            lc.mRecordSize, null, null, mAid,
                            obtainMessage(EVENT_READ_IMG_DONE, IccConstants.EF_IMG, 0, response));
                } else {
                    super.handleMessage(msg);
                }
                break;

            default:
                // hand other events directly to parent to handle
                super.handleMessage(msg);
                break;
            }
        } catch (Exception exc) {
            if (response != null) {
                sendResult(response, null, exc);
            } else {
                loge("uncaught exception" + exc);
            }
        }
    }

    /**
     * parse size infos for transparent EF file structure.
     */
    private int parseSizeInfoForTransparent(byte data[])
        throws IccFileTypeMismatch {
        // The first byte of the UICC card select command response data is 0x62,
        // and the UIM`s is 0x00, which is defined by spec, we can distinguish
        // them by the firs byte, see 11.1.1 of ETSI TS 102 221 V11.0.0 (2012-06).
        if (0x62 == data[0]) {
            if (data.length < 6) {
                loge("error response data for uicc");
                throw new IccFileTypeMismatch();
            }
            logd("data.length = " + data.length);

            // to find file size bytes which use 0x80 as the tag.
            // the min index is 2, because the first is FCP response tag(0x62),
            // and the second byte is FCP length.
            int index;
            for (index = 2; index < data.length - 1; index++) {
                if ((data[index] & 0xff) == 0x80 && (data[index + 1] & 0xff) == 0x02) {
                    break;
                 }
            }

            if (index > (data.length - 2)) {
                loge("no 0x80 tag found in response data for uicc");
                throw new IccFileTypeMismatch();
            }
            // length of file size bytes
            int lengthOfFileSizeBytes = data[index + 1] & 0xff;
            if (lengthOfFileSizeBytes != 0x02) {
                loge("error file size bytes length for uicc response data");
                throw new IccFileTypeMismatch();
            }

            int fileSize = ((data[index + 2] & 0xff) << 8)
                            + (data[index + 3] & 0xff);

            return fileSize;
        } else {
            logd("not uicc response , hand it to parent to handle");
            return -1;
        }
    }

    /**
     * parse records infos for linear fixed EF file structure.
     *
     * The first byte of the UICC card select command response data is 0x62,
     * and the UIM`s is 0x00, which is defined by spec, we can distinguish
     * them by the firs byte, see 11.1.1 of ETSI TS 102 221 V11.0.0 (2012-06).
     *
     * return true if can parse by UICC response data
     * return false if not
     */
    private boolean parseRecordsInfoForLinearFixed(byte data[], LoadLinearFixedContext lc)
        throws IccFileTypeMismatch {
        // The first byte of the UICC card select command response data is 0x62,
        // and the UIM`s is 0x00, which is defined by spec, we can distinguish
        // them by the firs byte, see 11.1.1 of ETSI TS 102 221 V11.0.0 (2012-06).
        if (0x62 == data[0]) {
            if (data.length < 9) {
                loge("error response data for uicc");
                throw new IccFileTypeMismatch();
            }
            logd("data.length = " + data.length);

            // to find file descriptor bytes which use 0x82 as the tag.
            // the min index is 2, because the first is FCP response tag(0x62),
            // and the second byte is FCP length.
            int index;
            for (index = 2; index < data.length; index++) {
                if ((data[index] & 0xff) == 0x82) {
                    break;
                 }
            }
            // the whole file descriptor bytes` length is 7, which include the record size infos
            if (index > (data.length - 7)) {
                loge("no 0x82 tag found in response data for uicc");
                throw new IccFileTypeMismatch();
            }
            // length of file descriptor bytes
            // can be 0x02 or 0x05, but only the 0x05 length data can include the record size infos
            int lengthOfFileDescriptor = data[index + 1] & 0xff;
            if (lengthOfFileDescriptor != 0x05) {
                loge("error bytes length for uicc response data");
                throw new IccFileTypeMismatch();
            }
            // File descriptor byte (see table 11.5 in ETSI TS 102 221 V11.0.0 (2012-06))
            byte fdByte = data[index + 2];

            if (0x21 != (data[index + 3 & 0xff])) {
                loge("error coding type for uicc response data");
                throw new IccFileTypeMismatch();
            }

            lc.mRecordSize = ((data[index + 4] & 0xff) << 8)
                            + (data[index + 5] & 0xff);

            lc.mCountRecords = data[index + 6] & 0xff;

            logd("lc.mRecordSize = " + lc.mRecordSize + ", lc.mCountRecords = " + lc.mCountRecords);
            return true;
        } else {
            logd("not uicc response , hand it to parent to handle");
            return false;
        }
    }

    ///M: add end @}

    @Override
    protected String getCommonIccEFPath(int efid) {
        logd("getCommonIccEFPath : " + efid);
        switch(efid) {
        case EF_ADN:
        case EF_FDN:
        case EF_MSISDN:
        case EF_SDN:
        case EF_EXT1:
        case EF_EXT2:
        case EF_EXT3:
            return MF_SIM + DF_TELECOM;

        case EF_ICCID:
        case EF_PL:
            return MF_SIM;
        case EF_PBR:
            // we only support global phonebook.
            return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
        case EF_IMG:
            return  MF_SIM + DF_TELECOM + DF_GRAPHICS;
        default:
            break;
        }
        return null;
    }

    @Override
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void setPhbRecordStorageInfo(int totalSize, int usedRecord) {
        adnRecordSize[0] = usedRecord;
        adnRecordSize[1] = totalSize;
    }

    public void getPhbRecordInfo(Message response) {
        if (adnRecordSize[0] != -1) {
        adnRecordSize[2] = 20;
        adnRecordSize[3] = mMaxNameLength;
            logd("adnRecordSize[0] = " + adnRecordSize[0] + " adnRecordSize[1] = " + adnRecordSize[1] + " adnRecordSize[2] = " + adnRecordSize[2] + " adnRecordSize[3] = " + adnRecordSize[3]);
            AsyncResult.forMessage(response).result = adnRecordSize;

            response.sendToTarget();
        } else {
            super.getPhbRecordInfo(response);
        }
    }
}

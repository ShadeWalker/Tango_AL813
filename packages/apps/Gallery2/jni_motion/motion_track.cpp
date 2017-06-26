#include "motion_track.h"
#include <sys/time.h>

#define LOG_TAG "motion_track"

#define MY_LOGD(fmt, arg...)    ALOGD("(%s) "fmt, __FUNCTION__, ##arg)
#define MY_LOGI(fmt, arg...)    ALOGI("(%s) "fmt, __FUNCTION__, ##arg)
#define MY_LOGM(fmt, arg...)    ALOGI("(%s) MM_PROFILE "fmt, __FUNCTION__, ##arg)

#define MM_PROFILING
#define MY_DUMP_IMG

namespace android {

MotionTrack::MotionTrack(){
}

MotionTrack::MotionTrack(const char *workPath, const char *prefixName, int inImgWidth, int inImgHeight,
                        int inImgNum, int outImgWidth, int outImgHeight){
    FILE *imfp = NULL;
    MY_LOGD("begin");
    this->inImgWidth = ALIGN16(inImgWidth);
    this->inImgHeight = ALIGN16(inImgHeight);
    this->inImgNum = inImgNum;
    this->inImgSize = (this->inImgWidth*this->inImgHeight*3)>>1;
    this->outImgWidth = ALIGN16(outImgWidth);
    this->outImgHeight = ALIGN16(outImgHeight);
    this->outImgSize = (this->outImgWidth*this->outImgHeight*3)>>1;
    this->outImgNum = 4;

    sprintf(this->workPath, "%s", workPath);
    sprintf(this->prefixName, "%s", prefixName);
    sprintf(this->inImgFileName, "%s/.ConShots/%s/%s", workPath, prefixName, prefixName);
    sprintf(this->outImgFileName, "%s/.ConShots/%sTK/%sTK", workPath, prefixName, prefixName);
    sprintf(this->intermediateFileName, "%s/.ConShots/InterMedia/%sIT", workPath, prefixName);
    sprintf(this->dumpImgFolder, "%s/.ConShots/dump", workPath);

    MY_LOGI("workPath: %s", workPath);
    MY_LOGI("prefixName: %s", prefixName);
    MY_LOGI("MAX_IMAGE_NUM: %d", MAX_IMAGE_NUM);
    MY_LOGI("YUV buffer size:%d", MAX_IMAGE_NUM*this->inImgSize);
    this->imgYUV12Buffer = (unsigned char *) malloc(MAX_IMAGE_NUM*this->inImgSize);
    if(this->imgYUV12Buffer == NULL)
    {
        MY_LOGI("Can not allocate memory for imgYUV12Buffer");
        return;
    }

    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        bufferMatcher[i] = INVALID_BUFFER_ID;
        prevSelectList[i] = 0;
    }

    // is in debug mode
    imfp = fopen(dumpImgFolder, "r");
    if (imfp == NULL) {
        MY_LOGI("debug mode off");
        IsdebugMode = false;
    }else
    {
        IsdebugMode = true;
        MY_LOGI("debug mode on, will dump intermediate file");
        fclose(imfp);
    }

    MY_LOGD("workPath:%s", this->workPath);
    MY_LOGD("prefixName:%s", this->prefixName);
    MY_LOGD("inImgFileName:%s", this->inImgFileName);
    MY_LOGD("outImgFileName:%s", this->outImgFileName);
    MY_LOGD("intermediateFileName:%s", this->intermediateFileName);
    MY_LOGD("dumpImgFolder:%s", this->dumpImgFolder);
    initMfb();
    setWorkingBuffer();
    setInterMediateData();
    MY_LOGD("end");
}

MotionTrack::~MotionTrack(){
    MY_LOGD("end");
}

void MotionTrack::initMfb()
{
    MFBMM_INIT_PARAM_STRUCT initInfo;
    MFBMM_TUNING_STRUCT tuningInfo;

    MY_LOGD("begin");
    //create instance
    mfb = mfb->createInstance(DRV_MFBMM_OBJ_SW);
    //set image information
    initInfo.mode = MFBMM_USEMODE_MANUAL;
    initInfo.img_width = inImgWidth;
    initInfo.img_height = inImgHeight;
    initInfo.thread_num = MOTIONTRACK_DEFAULT_THREAD_NUM;

    MY_LOGI("img_width:%d img_height:%d", initInfo.img_width, initInfo.img_height);

    //set tuning parameter
    tuningInfo.mode = MOTIONTRACK_DEFAULT_MODE;
    tuningInfo.maxMoveRange = MOTIONTRACK_DEFAULT_MAX_MOVE_RANGE;  // 1 ==> 0.01
    initInfo.tuning_param = tuningInfo;
    //init
    mfb->MfbmmInit((void*)&initInfo, NULL);
    MY_LOGD("end");
}

void MotionTrack::setWorkingBuffer()
{
    //get working buffer size
    MFBMM_GET_PROC_INFO_STRUCT procInfo;
    MFBMM_SET_WORKBUF_INFO_STRUCT bufferInfo;

    MY_LOGD("begin");
    mfb->MfbmmFeatureCtrl(MFBMM_FTCTRL_GET_PROC_INFO,NULL,(void*)&procInfo);

    //allocate working buffer
    MY_LOGI("workbuf_size:%d", procInfo.workbuf_size);
    if(procInfo.workbuf_size !=0)
    {
        workingBuffer = new MUINT8[procInfo.workbuf_size];
        if(workingBuffer == NULL)
        {
            MY_LOGI("Can not allocate memory for workingBuffer");
            return;
        }
    }
    else
    {
        workingBuffer = NULL;
    }

    // set working buffer
    bufferInfo.workbuf_addr = workingBuffer;
    bufferInfo.workbuf_size = procInfo.workbuf_size;

    mfb->MfbmmFeatureCtrl(MFBMM_FTCTRL_SET_WORKBUF_INFO,(void*)&bufferInfo, NULL);
    MY_LOGD("end");
}

void MotionTrack::setInterMediateData()
{
    //Set intermediate
    FILE *imfp = NULL;

    MY_LOGD("begin");
    //Load intermediate data, get 1. auto mode candidate index 2. Total number of images 3. GMV
    MY_LOGI("Reading intermediate file: %s", intermediateFileName);

    //1 get auto mode index
    imfp = fopen(intermediateFileName, "r");
    if (imfp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", intermediateFileName);
        isIMFileExist = false;
    }else
    {
    	isIMFileExist = true;
        MY_LOGI("Read intermediate file Success");
    }

    for(int i = 0; i < 4; i++)
    {
        //fscanf(imfp, "%d", &(interMedia.auto_idx[i]));
        if(isIMFileExist)
        {
            fread(&(interMedia.auto_idx[i]),sizeof(int),1,imfp);
        }else
        {
            interMedia.auto_idx[i] = i;
        }
        MY_LOGI("auto_idx[%d]=%d", i, (int)interMedia.auto_idx[i]);
    }

    //2 get total number of index
    if(isIMFileExist)
    {
        fread(&(autoCandNum), sizeof(int), 1, imfp);
    }else
    {
        autoCandNum = MAX_IMAGE_NUM;
    }
    interMedia.img_num = autoCandNum;
    //fscanf(imfp, "%d", &(autoCandNum));
    MY_LOGI("img_num=%d", (int)interMedia.img_num);

    //3 get GMV of each image
    for(int i =0;i< autoCandNum;i++)
    {
        if(isIMFileExist)
        {
            fread(&(interMedia.se_data[i][0]), sizeof(int), 1, imfp);
            fread(&(interMedia.se_data[i][1]), sizeof(int), 1, imfp);
        }else
        {
            interMedia.se_data[i][0] = 0;
            interMedia.se_data[i][1] = 0;
        }

        MY_LOGD("interMedia.se_data[%d][0]=%d", i, interMedia.se_data[i][0]);
        MY_LOGD("interMedia.se_data[%d][1]=%d", i, interMedia.se_data[i][1]);
        //fscanf(imfp, "%d", &(interMedia.se_data[i][0]));
        //fscanf(imfp, "%d", &(interMedia.se_data[i][1]));
    }
    infoPosition = ftell(imfp);
    MY_LOGD("positon:%d", infoPosition);
    //4 read manual flag
    fread(&(manualFlag), sizeof(MUINT8), 1, imfp);
    MY_LOGD("manualFlag:%d", (int)manualFlag);
    if (1 == feof(imfp)) {
        manualFlag = 0;
        MY_LOGD("End of file, set manualFlag:%d", (int)manualFlag);
    }
    //5 get previous select
    if(manualFlag == 1)
    {
        for(int i=0; i<MAX_IMAGE_NUM; i++)
        {
            fread(&(prevSelectList[i]), sizeof(MBOOL), 1, imfp);
            fread(&(rcmdImgList[i]), sizeof(MBOOL), 1, imfp);
            MY_LOGD("read rcmdImgList%d:%d",i, (int)rcmdImgList[i]);
        }
    }else
    {
        for(int i=0; i<4; i++)
        {
            prevSelectList[interMedia.auto_idx[i]] = 1;
        }
    }

    if(isIMFileExist) fclose(imfp);

    #ifdef MM_PROFILING
    GetTime(&start_sec, &start_nsec);
    #endif
    mfb->MfbmmFeatureCtrl(MFBMM_FTCTRL_SET_INTERMEDIATE,(void*)&interMedia, NULL);
    #ifdef MM_PROFILING
    GetTime(&end_sec, &end_nsec);
    timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
    MY_LOGM("%10d ==> MfbmmFeatureCtrl(MFBMM_FTCTRL_SET_INTERMEDIATE):set inter mediate", timeDiff);
    #endif
    MY_LOGD("end");
}

void MotionTrack::saveInfoToIMFile()
{
    FILE *imfp = NULL;

    MY_LOGD("begin");

    if(!isIMFileExist) return;

    MY_LOGI("save info to intermediate file: %s", intermediateFileName);

    imfp = fopen(intermediateFileName, "r+");
    if (imfp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", intermediateFileName);
        return;
    }

    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        prevSelectList[i] = false;
    }

    for(MUINT8 i = 0; i < manualCandNum; i++)
    {
        prevSelectList[manualCandImg[i]] = true;
    }

    MY_LOGD("position:%d", infoPosition);
    fseek(imfp, infoPosition, SEEK_SET);
    manualFlag = 1;
    //4 read manual flag
    fwrite(&(manualFlag), sizeof(MUINT8), 1, imfp);

    for(int i=0; i<MAX_IMAGE_NUM; i++)
    {
        fwrite(&(prevSelectList[i]), sizeof(MBOOL), 1, imfp);
        fwrite(&(rcmdImgList[i]), sizeof(MBOOL), 1, imfp);
        MY_LOGD("save rcmdImgList%d:%d",i, (int)rcmdImgList[i]);
    }
    fclose(imfp);
    MY_LOGD("end");
}

void MotionTrack::doBlending()
{
    MY_LOGD("begin");
    doManualSelection();
    readImages();
    blending();
    MY_LOGD("end");
}

/*do manual image selection*/
void MotionTrack::doManualSelection()
{
    MY_LOGD("begin");
    proc1In.manual_num = manualCandNum;

    memcpy(proc1In.manual_idx, manualCandImg, sizeof(MUINT8)*MAX_BLD_NUM);
    #ifdef MM_PROFILING
    GetTime(&start_sec, &start_nsec);
    #endif

    MY_LOGI("manual_num:%d", proc1In.manual_num);
    for(int i=0; i<manualCandNum; i++)
    {
        MY_LOGI("manual_idx[%d]=%d", i, proc1In.manual_idx[i]);
    }

    mfb->MfbmmMain(MFBMM_PROC1,(void*)&proc1In, (void*)&proc1Out);

    MY_LOGI("can_num:%d", proc1Out.can_num);
    for(int i=0; i<(int)proc1Out.can_num; i++)
    {
        MY_LOGI("can_img_idx[%d]=%d", i, proc1Out.can_img_idx[i]);
    }
    #ifdef MM_PROFILING
    GetTime(&end_sec, &end_nsec);
    timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
    MY_LOGM("%10d ==> MfbmmFeatureCtrl(MFBMM_PROC1): get manual indexs", timeDiff);
    #endif
    MY_LOGD("end");
}

/*blending*/
void MotionTrack::blending()
{
    MFBMM_PROC2_INFO_STRUCT proc2Info;
    char saveFilename[PATH_NAME_LENGTH];

    MY_LOGD("begin");
    proc2Info.bld_num = manualCandNum;

    MY_LOGD("bld_num:%d", proc2Info.bld_num);
    for(MUINT8 i = 0; i < proc1Out.can_num; i++)
    {
        int bufferIndex;
        proc2Info.img_idx[i] = proc1Out.can_img_idx[i];
        MY_LOGI("proc2Info.img_idx[%d] = %d", i, proc2Info.img_idx[i]);
        bufferIndex = bufferMatcher[proc1Out.can_img_idx[i]];
        MY_LOGI("bufferIndex = %d", bufferIndex);
        proc2Info.srcImgYUV420[i]  = imgYUV12Buffer + bufferIndex * inImgSize;

    }

    #ifdef MM_PROFILING
    GetTime(&start_sec, &start_nsec);
    #endif
    //do proc2
    mfb->MfbmmMain(MFBMM_PROC2, (void*) &proc2Info, NULL);
    #ifdef MM_PROFILING
    GetTime(&end_sec, &end_nsec);
    timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
    MY_LOGM("%10d ==> MyMfbmm->MfbmmMain(MFBMM_PROC2): set images buffer", timeDiff);
    #endif

    //Blending
    MFBMM_PROC3_INFO_STRUCT proc3Info;
    MFBMM_PROC3_RESULT_STRUCT proc3Result;

    //allocate memory
    this->imgBlendBuffer = (MUINT8 *) malloc(this->outImgSize+16);
    if(this->imgBlendBuffer == NULL)
    {
        MY_LOGI("ERROR: Can not allocate memory for imgBlendBuffer");
        return;
    }

    this->blendVirtalAddr = (MUINT8 *)ALIGN16((MUINT32)this->imgBlendBuffer);

    this->jpgBufferSize = getDstSize(outImgWidth, outImgHeight, JPEG_OUT_FORMAT_RGB565);
    this->jpgBuffer = (unsigned char *) malloc(this->jpgBufferSize+16);
    if(this->jpgBuffer == NULL)
    {
        MY_LOGI("ERROR: Can not allocate memory for jpgBuffer");
        return;
    }
    //write result image
    for(MUINT8 index = 0; index < proc2Info.bld_num; index++)
    {
        MY_LOGI("blengding index:%d", index);
        proc3Info.outImgYUV420 = blendVirtalAddr;
        #ifdef MM_PROFILING
        GetTime(&start_sec, &start_nsec);
        #endif
        mfb->MfbmmMain(MFBMM_PROC3,(void*)&proc3Info,(void*)&proc3Result);
        #ifdef MM_PROFILING
        GetTime(&end_sec, &end_nsec);
        timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
        MY_LOGM("%10d ==> MfbmmMain(MFBMM_PROC3): blend image", timeDiff);
        #endif

#ifdef MY_DUMP_IMG
        if (IsdebugMode) {
            //dump buffer
            sprintf(saveFilename, "%s/%sTK%02d.bin", dumpImgFolder, prefixName,
                    index + 1);
            dumpBufferToFile(blendVirtalAddr, outImgSize, saveFilename);
        }
#endif
        MY_LOGI("width:%d height:%d", proc3Result.out_img_width, proc3Result.out_img_height);
        MY_LOGI("Write images...");
        sprintf(saveFilename, "%s%02d.jpg", outImgFileName, index+1);
        printf("save file:%s", saveFilename);

        #ifdef MM_PROFILING
        GetTime(&start_sec, &start_nsec);
        #endif
        YV12ToJpg(blendVirtalAddr, outImgSize,
                proc3Result.out_img_width, proc3Result.out_img_height, this->jpgBuffer, jpgBufferSize, jpgSize);
        #ifdef MM_PROFILING
        GetTime(&end_sec, &end_nsec);
        timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
        MY_LOGM("%10d ==> YV12ToJpg: YV12 to jpg", timeDiff);
        #endif

        dumpBufferToFile(this->jpgBuffer, jpgSize, saveFilename);

        MY_LOGI("Done!!");
        if(index == proc2Info.bld_num-1)
        {
            sprintf(saveFilename, "%s/%s.jpg", workPath, prefixName);
            printf("save file:%s", saveFilename);
            dumpBufferToFileWithExif(this->jpgBuffer, jpgSize, saveFilename);
        }
    }
    //remove file
    for(MUINT8 index = proc2Info.bld_num; index < MAX_IMAGE_SELECT_NUM; index++)
    {
        sprintf(saveFilename, "%s%02d.jpg", outImgFileName, index+1);
        printf("delete file:%s", saveFilename);
        remove(saveFilename);
    }
    //save info to inner mediate file
    saveInfoToIMFile();
    free(imgBlendBuffer);
    free(jpgBuffer);
    MY_LOGI("end");
}

/*read manual-mode input image based on manual selection result*/
bool MotionTrack::readImages()
{
     char  fileName[300];
     FILE *fptr;
     MUINT8 cand_idx;
     MUINT8 *dstBuffer;

     MY_LOGD("begin");
     for(MUINT8 i = 0; i < proc1Out.can_num; i++)
     {
         // Images are not loaded yet, need to load images
         sprintf(fileName, "%s%02d.jpg", inImgFileName, proc1Out.can_img_idx[i]+1);

         MY_LOGI("read file:%s",fileName);
         cand_idx = proc1Out.can_img_idx[i];
         MY_LOGI("cand_idx[%d]=%d",i, cand_idx);
         if(bufferMatcher[cand_idx] == INVALID_BUFFER_ID)
         {

             bufferMatcher[cand_idx] = cand_idx;
             dstBuffer = (MUINT8 *)(imgYUV12Buffer + (int)cand_idx * inImgSize);
             if(!jpgDecode(fileName, dstBuffer))
             {
                 MY_LOGI("ERROR: jpgToYUV12 false");
                 return false;
             }else{
#ifdef MY_DUMP_IMG
                 if (IsdebugMode) {
                    //dump buffer
                    sprintf(fileName, "%s/%s%02d.bin", dumpImgFolder,
                            prefixName, cand_idx);
                    dumpBufferToFile(dstBuffer, outImgSize, fileName);
                }
#endif
             }
             MY_LOGI("decode success!");
         }

     }
     MY_LOGD("end");
     return true;
}

bool MotionTrack::jpgDecode(char const *fileName, uint8_t *dstBuffer){
    MUINT32 file_size;
    unsigned char *file_buffer;
    unsigned char *padded_file_buffer;
    FILE *fp;
    MUINT32 ret;

    MY_LOGD("begin");
    //open a image file
    fp = fopen(fileName, "rb");
    if (fp == NULL) {
        MY_LOGI("[readImageFile]ERROR: Open file %s failed.", fileName);
        return 0;
    }
    MY_LOGI("open file %s success!", fileName);
    //get file size
    fseek(fp, SEEK_SET, SEEK_END);
    file_size=ftell(fp);
    MY_LOGI("[decodeOneImage]file_size is %d", file_size);

    if(file_size == 0) {
       MY_LOGI("ERROR: [readImageFile]file size is 0");
       //close image file
       fclose(fp);
       return 0;
    }

    //allocate buffer for the file
    //should free this memory when not use it !!!
    file_buffer = (unsigned char *) malloc(ALIGN128(file_size) + 512 + 127);
    padded_file_buffer = (unsigned char *)((((MUINT32)file_buffer + 127) >> 7) << 7);
    MY_LOGI("src va :0x%x", (UINT32)file_buffer );
    if (file_buffer == NULL) {
        MY_LOGI("Can not allocate memory");
        //close image file
        fclose(fp);
        return false;
    }

    //read image file
    fseek(fp, SEEK_SET, SEEK_SET);
    ret = fread(padded_file_buffer,1,file_size,fp);
    if(ret != file_size) {
        MY_LOGI("File read error ret[%d]",ret);
        //close image file
        fclose(fp);
        return false;
    }
    MY_LOGI("read file to buffer success!");
    #ifdef MM_PROFILING
    GetTime(&start_sec, &start_nsec);
    #endif
    //decode one image
    if (!jpgToYV12(padded_file_buffer, file_size, dstBuffer, outImgWidth, outImgHeight)) {
        MY_LOGI("[decodeOneImage]decode failed!!");
    }
    #ifdef MM_PROFILING
    GetTime(&end_sec, &end_nsec);
    timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
    MY_LOGM("%10d ==> jpgToYV12: jpg to yv12", timeDiff);
    #endif
    // release file buffer
    free(file_buffer);

    //close image file
    fclose(fp);
    MY_LOGD("end");
    return true;
}

bool MotionTrack::jpgToYV12(uint8_t* srcBuffer, uint32_t srcSize, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight) {
    MHAL_JPEG_DEC_INFO_OUT outInfo;
    MHAL_JPEG_DEC_START_IN inParams;
    MHAL_JPEG_DEC_SRC_IN    srcInfo;
    void *fSkJpegDecHandle;
    unsigned int cinfo_output_width, cinfo_output_height;
    int re_sampleSize ;
    //int preferSize = 0;
    // TODO: samplesize value
    int sampleSize = 8;
    int width, height;

    MY_LOGI("onDecode start");
    //2 step1: set sampleSize value
    sampleSize = roundToTwoPower(sampleSize);
    //2 step2: init fSkJpegDecHandle
    fSkJpegDecHandle = srcInfo.jpgDecHandle = NULL;
    //2 step3: init  inparam
    //memcpy(&inParams, param, sizeof(MHAL_JPEG_DEC_START_IN));
    inParams.dstFormat = (JPEG_OUT_FORMAT_ENUM)FORMAT_YV12;
    inParams.srcBuffer = srcBuffer;
    inParams.srcBufSize = (ALIGN128(srcSize) + 512);
    inParams.srcLength = srcSize;
    inParams.dstWidth = dstWidth;
    inParams.dstHeight = dstHeight;
    inParams.dstVirAddr = dstBuffer;
    inParams.dstPhysAddr = NULL;
    inParams.doDithering = 0;
    inParams.doRangeDecode = 0;
    inParams.doPostProcessing = 0;
    inParams.postProcessingParam = NULL;
    inParams.PreferQualityOverSpeed = 0;

    //2 step4: init srcInfo
    srcInfo.srcBuffer = srcBuffer;
    srcInfo.srcLength = srcSize;
    //2 step5 jpeg dec parser
    MY_LOGI("onDecode MHAL_IOCTL_JPEG_DEC_PARSER");
    if (MHAL_NO_ERROR != mHalJpeg(MHAL_IOCTL_JPEG_DEC_PARSER, (void *)&srcInfo, sizeof(srcInfo),
                                    NULL, 0, NULL)){
        MY_LOGI("[onDecode]parser file error");
        return false;
    }
    //2 step6 set jpgDecHandle value
    outInfo.jpgDecHandle = srcInfo.jpgDecHandle;
    MY_LOGD("outInfo.jpgDecHandle --> %d",outInfo.jpgDecHandle);
    //2 step7: get jpeg info
    if (MHAL_NO_ERROR != mHalJpeg(MHAL_IOCTL_JPEG_DEC_GET_INFO, NULL, 0,
                                   (void *)&outInfo, sizeof(outInfo), NULL))
    {
        MY_LOGI("[onDecode]get info error");
        return false;
    }
    MY_LOGD("outInfo.srcWidth --> %d",outInfo.srcWidth);
    MY_LOGD("outInfo.srcHeight -- > %d",outInfo.srcHeight);

    //2 step8: set inParams
    inParams.jpgDecHandle = srcInfo.jpgDecHandle;
    inParams.dstWidth = ALIGN16(inParams.dstWidth);
    inParams.dstHeight = ALIGN16(inParams.dstHeight);
    MY_LOGD("inParams.dstFormat --> %d",inParams.dstFormat);
    MY_LOGD("inParams.dstWidth -- > %d",inParams.dstWidth);
    MY_LOGD("inParams.dstHeight --> %d",inParams.dstHeight);
    //2 step9: start decode
    if (MHAL_NO_ERROR != mHalJpeg(MHAL_IOCTL_JPEG_DEC_START,
                                   (void *)&inParams, sizeof(inParams),
                                   NULL, 0, NULL)){
        MY_LOGI("JPEG HW not support this image");
        return false;
    }

    return true;
}


MBOOL MotionTrack::YV12ToJpg(unsigned char *srcBuffer, int srcSize,
        int srcWidth, int srcHeight, unsigned char *dstBuffer, int dstSize, MUINT32 &u4EncSize)
{
    MBOOL ret = true;
    int fIsAddSOI = true;//if set true, not need add exif
    int quality = 90;

    MUINT32 yuvAddr[3],yuvSize[3];

    yuvSize[0] =    getBufSize(
                        srcWidth,
                        srcHeight,
                        YUV_IMG_STRIDE_Y);
    yuvSize[1] =    getBufSize(
                        srcWidth/2,
                        srcHeight/2,
                        YUV_IMG_STRIDE_U);
    yuvSize[2] =    getBufSize(
                        srcWidth/2,
                        srcHeight/2,
                        YUV_IMG_STRIDE_V);
    //
    yuvAddr[0] =    (MUINT32)srcBuffer;
    yuvAddr[1] =    yuvAddr[0]+yuvSize[0];
    yuvAddr[2] =    yuvAddr[1]+yuvSize[1];

    //
    // (0). debug
    MY_LOGD("begin");
    MY_LOGI("Y tride:%d, U tride:%d, V tride:%d", YUV_IMG_STRIDE_Y, YUV_IMG_STRIDE_U, YUV_IMG_STRIDE_V);
    MY_LOGI("srcBuffer=0x%x", (MUINT32)srcBuffer);
    MY_LOGI("dstBuffer=0x%x", (MUINT32)dstBuffer);
    MY_LOGI("width=%d", srcWidth);
    MY_LOGI("height=%d", srcHeight);

    MY_LOGI("yuvSize[0]=0x%x", yuvSize[0]);
    MY_LOGI("yuvSize[1]=0x%x", yuvSize[1]);
    MY_LOGI("yuvSize[2]=0x%x", yuvSize[2]);

    MY_LOGI("yuvAddr[0]=0x%x", yuvAddr[0]);
    MY_LOGI("yuvAddr[1]=0x%x", yuvAddr[1]);
    MY_LOGI("yuvAddr[2]=0x%x", yuvAddr[2]);

    // (1). Create Instance
    JpgEncHal* pJpgEncoder = new JpgEncHal();

    // (1). Lock
    pJpgEncoder->unlock();
    if(!pJpgEncoder->lock())
    {
        MY_LOGI("can't lock jpeg resource");
        goto EXIT;
    }

    // (2). size, format, addr
    MY_LOGI("jpeg source YV12");
    pJpgEncoder->setEncSize(srcWidth, srcHeight, JpgEncHal::kENC_YV12_Format);//JpgEncHal:: kENC_NV21_Format);

    MY_LOGI("setSrcAddr");

    pJpgEncoder->setSrcAddr(
            (void*)ALIGN16(yuvAddr[0]),
            (void*)ALIGN16(yuvAddr[1]),
            (void*)ALIGN16(yuvAddr[2]));
    MY_LOGI("setSrcBufSize");
    pJpgEncoder->setSrcBufSize(
            srcWidth,
            yuvSize[0],
            yuvSize[1],
            yuvSize[2]);
    // (3). set quality
    MY_LOGI("setQuality");
    pJpgEncoder->setQuality(quality);
    // (4). dst addr, size
    MY_LOGI("setDstAddr");
    pJpgEncoder->setDstAddr((void *)dstBuffer);
    MY_LOGI("setDstSize");
    pJpgEncoder->setDstSize(dstSize);
    // (6). set SOI
    MY_LOGI("enableSOI");
    pJpgEncoder->enableSOI((fIsAddSOI > 0) ? 1 : 0);
    // (7). ION mode
    MY_LOGI("start");
    // (8).  Start
    if (pJpgEncoder->start(&u4EncSize))
    {
        //add head
        //dstBuffer[0] = 0xff;
        //dstBuffer[1] = 0xD8;
        MY_LOGI("Jpeg encode done, size = %d", u4EncSize);
        ret = true;
        MY_LOGI("encode success");
    }
    else
    {
        MY_LOGI("encode fail");
        pJpgEncoder->unlock();
        goto EXIT;
    }

    pJpgEncoder->unlock();

EXIT:
    delete pJpgEncoder;
    MY_LOGD("end ret:%d", ret);
    return ret;
}

void MotionTrack::dumpBufferToFile(MUINT8* buffer,int bufferSize, char* fileName){
    FILE* fp;
    int index;

    if (buffer == NULL) return;
    MY_LOGI("dump buffer to file:%s", fileName);

    fp = fopen(fileName, "w");
    if (fp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", fileName);
        return ;
    }

    for(index = 0 ; index < bufferSize ; index++) {
        fprintf(fp, "%c", buffer[index]);
    }
    MY_LOGD("dump buffer to file success!");
    fclose(fp);
}

void MotionTrack::dumpBufferToFileWithExif(MUINT8* buffer,int bufferSize, char* fileName){
    if (buffer == NULL) {
        MY_LOGI("ERROR: input buffer is null, return");
        return;
    }

    char tempFileName[PATH_NAME_LENGTH];
    sprintf(tempFileName, "%s_withExif.jpg", this->outImgFileName);
    MY_LOGD("tempFileName is %s", tempFileName);
    FILE* fpTemp;
    fpTemp = fopen(tempFileName, "w");
    if (fpTemp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", tempFileName);
        return;
    }

    FILE* fp;
    int index;
    unsigned char head[6];
    int begin;

    fp = fopen(fileName, "r");
    if (fp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", fileName);
        return;
    }

    fseek(fp, 0, 0);
    fread(head, 1, 6, fp);
    MY_LOGD("read head,0x%x 0x%x 0x%x 0x%x 0x%x 0x%x",
            head[0], head[1], head[2], head[3], head[4], head[5]);

   if (head[2] == 0xff && head[3] == 0xe1) {
        int offset = (int) head[4] * 256 + head[5] + 4;
        MY_LOGD("this file has exif, offset:%d", offset);
        fseek(fp, 0, 0);
        unsigned char exifHeadBuffer[offset];
        fread(exifHeadBuffer,1,offset,fp);
        MY_LOGD("write exif to %s", tempFileName);
        for (index = 0; index < offset; index++) {
            fprintf(fpTemp, "%c", exifHeadBuffer[index]);
        }
        begin = 2;
    } else {
        MY_LOGD("this file not exif");
        fseek(fp, 0, 0);
        begin = 0;
    }
    MY_LOGD("write jpeg data to %s", tempFileName);
    for (index = begin; index < bufferSize; index++) {
        int res = fprintf(fpTemp, "%c", buffer[index]);
    }
    fclose(fp);
    fclose(fpTemp);
    MY_LOGD("write jpeg data success!");

    int deleteRes = remove(fileName);
    MY_LOGD("delete %s, deleteRes = %d", fileName, deleteRes);

    int removeRes = rename(tempFileName, fileName);
    MY_LOGD("rename %s to %s, removeRes = %d", tempFileName, fileName, removeRes);
}

// for some reasons, mark saveFile function
// 1. saveFile is never been used
// 2. SkBitmap.setConfig & copyTo cannot compile pass
/* 
int MotionTrack::saveFile(unsigned char* buf,int bufSize,
                    int width, int height,
                    char* filename, int fmt, bool onlyRaw) {
     if (buf == NULL) return 0;

     MY_LOGI("saveFile begin");
     if ((fmt != JPEG_OUT_FORMAT_RGB565 &&
        fmt != JPEG_OUT_FORMAT_ARGB8888 &&
        fmt != JPEG_OUT_FORMAT_RGB888) ||
            onlyRaw)
    {
        //ulog("can not save as png! unsupport format");
        MY_LOGI("[UT]saving file as RAW in %s ... ", filename);
        //strcat(filename, ".bin");
        FILE* fp;
        int index;
        fp = fopen(filename, "w");
        for(index = 0 ; index < bufSize ; index++) {
            fprintf(fp, "%c", buf[index]);
        }
        fclose(fp);
        return 0;
    }

    SkBitmap bmp;              // use Skia to encode raw pixel data into image file
    SkBitmap bmp_argb;
    MY_LOGI("[UT]saving file as PNG in %s ... ", filename);
    strcat(filename, ".png");

    //bmp.setConfig(SkBitmap::kARGB_8888_Config, sc.getWidth(), sc.getHeight());
    if (fmt == JPEG_OUT_FORMAT_RGB565) {
        bmp.setConfig(SkBitmap::kRGB_565_Config, width, height);
        bmp.setPixels((void*)buf);


        if (!bmp.copyTo(&bmp_argb, SkBitmap::kARGB_8888_Config)) {
            MY_LOGI("[UT]save as png error!! @ tanslate to argb");
        }

        SkImageEncoder::EncodeFile(filename, bmp_argb,
                SkImageEncoder::kPNG_Type, SkImageEncoder::kDefaultQuality);
    } else if (fmt == JPEG_OUT_FORMAT_ARGB8888){
        bmp.setConfig(SkBitmap::kARGB_8888_Config, width, height);
        bmp.setPixels((void*)buf);

        SkImageEncoder::EncodeFile(filename, bmp,
                SkImageEncoder::kPNG_Type, SkImageEncoder::kDefaultQuality);
    } else if (fmt == JPEG_OUT_FORMAT_RGB888) {
        unsigned char* tmpARGB = (unsigned char*)malloc(width * height * 4);
        memset(tmpARGB, 0xff, width * height * 4);

        for (int i=0; i < width*height; i++) {
            tmpARGB[4*i + 2] = buf[3*i + 0];
            tmpARGB[4*i + 1] = buf[3*i + 1];
            tmpARGB[4*i + 0] = buf[3*i + 2];
        }
        bmp.setConfig(SkBitmap::kARGB_8888_Config, width, height);
        bmp.setPixels((void*)tmpARGB);

        SkImageEncoder::EncodeFile(filename, bmp,
                SkImageEncoder::kPNG_Type, SkImageEncoder::kDefaultQuality);

        if (tmpARGB) free(tmpARGB);
    }

    MY_LOGI("done !");
    return 0;
}
*/

MUINT32 MotionTrack::getBufSize(MUINT32 width, MUINT32 height, MUINT32 stride)
{
    MUINT32 bufSize = 0;
    MUINT32 w;
//    //Y size
//    if(width % stride)
//    {
//        bufSize = (width/stride+1)*stride*height;
//    }
//    else
//    {
//        bufSize = width*height;
//    }
    w = ALIGN16(width);
    bufSize = w*height;
    MY_LOGI(
        "W(%d)xH(%d),BS(%d)",
        w,
        height,
        bufSize);
    //
    return bufSize;
}

MBOOL* MotionTrack::getRefImage(MUINT8 query_img_id)
{
    MY_LOGI("getRefImage begin");
    MY_LOGI("query_img_id:%d",query_img_id);
    #ifdef MM_PROFILING
    GetTime(&start_sec, &start_nsec);
    #endif
    memset(rcmdImgList,0,MAX_IMAGE_NUM);
    mfb->MfbmmFeatureCtrl(MFBMM_FTCTRL_GET_REF_IMAGE, (void*)&query_img_id, (void*)(rcmdImgList));
    #ifdef MM_PROFILING
    GetTime(&end_sec, &end_nsec);
    timeDiff = GetTimeDiff(start_sec, start_nsec, end_sec, end_nsec);
    MY_LOGM("%10d ==> MfbmmFeatureCtrl(MFBMM_FTCTRL_GET_REF_IMAGE): get ref image", timeDiff);
    #endif

    MY_LOGI("Selectable images...");
    //MY_LOGI("Query image %2d:", query_img_id*interval + 1);
    for(MUINT8 i=0;i< autoCandNum; i++)
    {
        //MY_LOGI("%2d,", *(pList+i));
        if( *(rcmdImgList + i) == 1 )

            MY_LOGI("%2d,", i);  // map to bmp file squence number
    }
    MY_LOGI("getRefImage end");
    return rcmdImgList;
}

MBOOL* MotionTrack::getPrevRefImage()
{
    if(manualFlag == 1) {
        return rcmdImgList;
    } else {
        return getRefImage(interMedia.auto_idx[0]);
    }
}

MBOOL *MotionTrack::getPrevSelect(void)
{
    return prevSelectList;
}

unsigned int MotionTrack::getDstSize(unsigned int width, unsigned int height, int fmt)
{
    unsigned int size;
    switch (fmt) {
        case JPEG_OUT_FORMAT_RGB565: //JpgDecHal::kRGB_565_Format:
            size = width * height * 2;
            break;
        case JPEG_OUT_FORMAT_RGB888: //JpgDecHal::kRGB_888_Format:
            size = width * height * 3;
            break;
        case JPEG_OUT_FORMAT_ARGB8888: //JpgDecHal::kARGB_8888_Format:
            size = width * height * 4;
            break;
        case 5:
            size = width * height * 3 / 2;
            break;
        default:
            size = 0;
            break;
    }
    return size;
}

void MotionTrack::test() {

    char file[128] = "/data/test.jpg";
    char resultname[100] = "/data/matt/yuv_test";
    char jpgname[100] = "/data/matt/jpg_test.jpg";
    unsigned int src_size;
    unsigned int dst_size;
    unsigned int dstFormat = FORMAT_YV12;
    bool ret = false;
    FILE *fp;
    unsigned char *src_va = NULL;
    unsigned char *dst_va = NULL;
    MUINT32 u4EncSize;
    outImgWidth = 480;
    outImgHeight = 640;
    inImgWidth = 2160;
    inImgHeight = 3600;

    MY_LOGI("test file:%s", file);
    //Init
    //src_size = getFileSize(file);

    fp = fopen(file, "rb");
    if (fp == NULL) {
        MY_LOGI("[ULT]ERROR: Open file %s failed.", file);
        return ;
    }

    fseek(fp, SEEK_SET, SEEK_END);
    src_size=ftell(fp);

    fseek(fp, SEEK_SET, SEEK_SET);
    if(src_size == 0) {
       MY_LOGI("file size is 0");
       return;
    }

    MY_LOGI("%s  %d",file,src_size);

    //should free this memory when not use it !!!
    src_va = (unsigned char *) malloc(ALIGN128(src_size));
    MY_LOGI("src va :0x%x", (UINT32)src_va );

    if (src_va == NULL) {
        MY_LOGI("Can not allocate memory");
        return;
    }

    int tt = fread(src_va,1,src_size,fp);
    if(tt != src_size) {
        MY_LOGI("File read error[%d]",tt);
    }
    MY_LOGI("src_size:%d", src_size);
    fclose(fp);

    dst_size = getDstSize(outImgWidth, outImgHeight, dstFormat);
    MY_LOGI("dst_size:%d", dst_size);
    dst_va = (unsigned char *)malloc(dst_size);
    if (dst_va == NULL) {
        MY_LOGI("Can not allocate memory");
        return;
    }
    memset(dst_va , 255 , dst_size);

  if (!jpgToYV12(src_va, src_size, dst_va, outImgWidth, outImgHeight)) {
        MY_LOGI("[ULT]decode failed!!");
    } else {
        ret = true;
    }

    if (ret == true) MY_LOGI("Successful");

    dumpBufferToFile(dst_va, dst_size, resultname);

    memset(src_va , 255 , src_size);
    //encodeJpg(dst_va, dst_size, src_va);
//    YV12ToJpg(dst_va, dst_size,outImgWidth, outImgHeight, src_va, src_size);
//    saveFile(src_va, src_size, outImgWidth, outImgHeight, jpgname, dstFormat, true);

    if (src_va) free(src_va);
    if (dst_va) free(dst_va);
}

void MotionTrack::setManualIndexes(MUINT8 *candImg, int num)
{
    if(num > MAX_IMAGE_SELECT_NUM) return;

    manualCandNum = num;
    memcpy(manualCandImg, candImg, sizeof(MUINT8)*num);
}

void MotionTrack::setBufferMatcher(INT8 *matcher)
{
    memcpy(bufferMatcher, matcher, sizeof(MUINT8)*MAX_IMAGE_NUM);
}

void MotionTrack::reset()
{
    mfb->MfbmmReset();
}

void MotionTrack::release()
{
    free(imgYUV12Buffer);
    free(workingBuffer);

    mfb->destroyInstance();
}

int MotionTrack::roundToTwoPower(int a)
{
    int ans = 1;

    if(a>=8) return a;

    while (a > 0)
    {
        a = a >> 1;
        ans *= 2;
    }

    return (ans >> 1);
}

void MotionTrack::GetTime(int *sec, int *usec)
{
    timeval time;
    gettimeofday(&time, NULL);
    *sec = time.tv_sec;
    *usec = time.tv_usec;
}

int MotionTrack::GetTimeDiff(int startSec, int startNSec, int endSec, int endNSec)
{
    return ((endSec - startSec) * 1000000 + (endNSec - startNSec))/1000;
}
}



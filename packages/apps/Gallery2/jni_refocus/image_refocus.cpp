#include "image_refocus.h"
#include <sys/time.h>

#define LOG_TAG "Gallery2_Refocus_image_refocus"

#define MY_LOGD(fmt, arg...)    ALOGD("(%s) "fmt, __FUNCTION__, ##arg)
#define MY_LOGI(fmt, arg...)    ALOGI("(%s) "fmt, __FUNCTION__, ##arg)
#define MY_LOGM(fmt, arg...)    ALOGI("(%s) Refocus "fmt, __FUNCTION__, ##arg)

#define MM_PROFILING
//#define MM_BMP_DEBUG
//#define DUMP_REFOCUS_IMAGE
//#define DUMP_DECODE_IMAGE
//#define DUMP_REFOCUS_RGB_IMAGE

namespace android {

ImageRefocus::ImageRefocus(){
}

ImageRefocus::ImageRefocus(int jpsWidth, int jpsHeight, int maskWidth, int maskHeight, int posX, int posY, 
        int viewWidth, int viewHeight, int orientation, int mainCamPos, int touchCoordX1st, int touchCoordY1st){
    debugConfig();
    mRefocusTuningInfo = {8, 16, 4, 0, 4, 1, 3.4};
    mRefocusImageInfo.ImgNum = 1;
    mRefocusImageInfo.ImgFmt = (MTK_REFOCUS_IMAGE_FMT_ENUM)UTL_IMAGE_FORMAT_YUV420;
    mRefocusImageInfo.Mode = REFOCUS_MODE_FULL;
    
    //test number for generate depth map buffer, maybe need optimization in future 
    mRefocusImageInfo.TouchCoordX = touchCoordX1st;
    mRefocusImageInfo.TouchCoordY = touchCoordY1st;
    mRefocusImageInfo.DepthOfField = testDepthOfField;

    mRefocusImageInfo.Width = jpsWidth;
    mRefocusImageInfo.Height = jpsHeight;
    mRefocusImageInfo.MaskWidth = maskWidth;
    mRefocusImageInfo.MaskHeight = maskHeight;
    mRefocusImageInfo.PosX = posX;
    mRefocusImageInfo.PosY = posY;
    mRefocusImageInfo.ViewWidth = viewWidth;
    mRefocusImageInfo.ViewHeight = viewHeight;
    mRefocusImageInfo.JPSOrientation = REFOCUS_ORIENTATION_0;
    mRefocusImageInfo.JPGOrientation = REFOCUS_ORIENTATION_0;
    mRefocusImageInfo.MainCamPos = REFOCUS_MAINCAM_POS_ON_RIGHT;
    
    //default rectify info in dual cam refocus
    mRefocusImageInfo.RcfyError = DFT_RCFY_ERROR;
    mRefocusImageInfo.RcfyIterNo = DFT_RCFY_ITER_NO;
    mRefocusImageInfo.DisparityRange = DFT_DISPARITY_RANGE;
    mRefocusImageInfo.Theta[0] = DFT_THETA;
    mRefocusImageInfo.Theta[1] = DFT_THETA;
    mRefocusImageInfo.Theta[2] = DFT_THETA;
    mRefocusImageInfo.DepthBufferAddr = NULL;
    MY_LOGI("mRefocusImageInfo.RcfyError %d, ", mRefocusImageInfo.RcfyError);

    mRefocusTuningInfo.IterationTimes = 3;
    mRefocusTuningInfo.HorzDownSampleRatio = 8;
    mRefocusTuningInfo.VertDownSampleRatio = 8;
    mRefocusImageInfo.DRZ_WD = 960;
    mRefocusImageInfo.DRZ_HT = 540;
    
    mRefocusTuningInfo.Baseline = 2.0f;
    mRefocusTuningInfo.CoreNumber = 4;
}

bool ImageRefocus::initRefocusNoDepthMapTest(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
                const char *depthmapSourcePath, int inStereoImgWidth, int inStereoImgHeight, const char *maskFilePath)
{
    MY_LOGI("image refocus initRefocusNoDepthMap start inStereoImgWidth %d, inStereoImgHeight %d, outImgWidth %d, outImgHeight %d", inStereoImgWidth, inStereoImgHeight, outImgWidth, outImgHeight);
    /* Image Information */
    mStereoImgWidth = inStereoImgWidth;
    mSetreoImgHeight = inStereoImgHeight;
    mOutTargetImgWidth = outImgWidth;
    mOutTargetImgHeight = outImgHeight;
    
    MY_LOGI("image refocus initRefocusIMGSource ");
    initRefocusIMGSource(sourceFilePath, outImgWidth, outImgHeight, imgOrientation);
    initRefocusDepthInfo(depthmapSourcePath, inStereoImgWidth, inStereoImgHeight);

    char filename[255];
    //1. jpg parse start
    #ifdef MM_BMP_DEBUG
    BITMAP jpegBMP;
    sprintf(filename, "%s", sourceFilePath);
    if(bmp_parse(filename,&jpegBMP)!=0)
    {
        MY_LOGI("Reading jpeg image:%s......\n", filename);
        bmp_read(filename,&jpegBMP);
    }
    else
    {
        MY_LOGI("Fail to read jpeg image:%s!!!!!!\n", filename);
    }
    MY_LOGI("Jpeg image width = %d height = %d\n", jpegBMP.width, jpegBMP.height);
    MUINT8* jpegBuffer = new MUINT8 [jpegBMP.width * jpegBMP.height * 3 / 2];
    mRefocusImageInfo.TargetWidth = jpegBMP.width;
    mRefocusImageInfo.TargetHeight = jpegBMP.height;
    mRefocusImageInfo.TargetImgAddr = jpegBuffer;
    bmp_toYUV420(&jpegBMP,(unsigned char *)mRefocusImageInfo.TargetImgAddr);
    bmp_free(&jpegBMP);
    // jpg parse end
    #endif

    //2. jps parse start
    BITMAP jpsBMP;
    sprintf(filename, "%s", depthmapSourcePath);
    if(bmp_parse(filename,&jpsBMP)!=0)
    {
        MY_LOGI("Reading jps image:%s......\n", filename);
        bmp_read(filename,&jpsBMP);
    }
    else
    {
        MY_LOGI("Fail to read jps image:%s!!!!!!\n", filename);
    }
    MY_LOGI("Jps image width = %d height = %d\n", jpsBMP.width, jpsBMP.height);
    MUINT8* jpsBuffer = new MUINT8 [jpsBMP.width * jpsBMP.height * 3 / 2];
    mRefocusImageInfo.Width= jpsBMP.width;
    mRefocusImageInfo.Height= jpsBMP.height;
    mRefocusImageInfo.ImgAddr[0] = jpsBuffer;
    bmp_toYUV420(&jpsBMP,(unsigned char *)mRefocusImageInfo.ImgAddr[0]);
    bmp_free(&jpsBMP);

    //3. config parse start
    //parse_configuration("/sdcard/Pictures/Config.cfg");

    //4. mask parse start
    BITMAP maskBMP;
    sprintf(filename, "%s",  maskFilePath);
    if(bmp_parse(filename,&maskBMP)!=0)
    {
        printf("Reading mask image:%s......\n", filename);
        bmp_read(filename,&maskBMP);
    }
    MY_LOGI("Mask image width = %d height = %d\n", maskBMP.width, maskBMP.height);
    MUINT8* maskBuffer = new MUINT8 [maskBMP.width * maskBMP.height];
    mRefocusImageInfo.MaskWidth = maskBMP.width;
    mRefocusImageInfo.MaskHeight = maskBMP.height;
    mRefocusImageInfo.MaskImageAddr = maskBuffer;
    memcpy(maskBuffer, maskBMP.g, maskBMP.width * maskBMP.height);
    bmp_free(&maskBMP);
    // mask parse end

    if (createRefocusInstance() && setBufferAddr() && generate())
    {
        MY_LOGI("image refocus init end, success");
        return SUCCESS;
    }
    MY_LOGI("image refocus init end, fail");
    return FAIL;
}

bool ImageRefocus::initRefocusNoDepthMapRealFileTest(const char *testSourceFilePath, const char *sourceFilePath, const char *jpsFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
            MUINT8* jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, MUINT8* maskBuffer, int maskBufferSize, int maskWidth, int maskHeight)
{
    MY_LOGI("image refocus initRefocusNoDepthMap start inStereoImgWidth %d, inStereoImgHeight %d, outImgWidth %d, outImgHeight %d", inStereoImgWidth, inStereoImgHeight, outImgWidth, outImgHeight);
    /* Image Information */
    mStereoImgWidth = inStereoImgWidth;
    mSetreoImgHeight = inStereoImgHeight;
    
    MY_LOGI("image refocus initRefocusIMGSource ");
    initRefocusIMGSource(testSourceFilePath, outImgWidth, outImgHeight, imgOrientation);
    //2. jps parse start
    mRefocusImageInfo.Width= mStereoImgWidth;
    mRefocusImageInfo.Height= mSetreoImgHeight;
    mRefocusImageInfo.ImgAddr[0] = jpsBuffer;

    //initJPSIMGSource(jpsFilePath, mRefocusImageInfo.Width, mRefocusImageInfo.Height);
    initJPSBuffer(jpsBuffer, jpsBufferSize);
    //3. mask parse start
    mRefocusImageInfo.MaskWidth = maskWidth;
    mRefocusImageInfo.MaskHeight = maskHeight;
    mRefocusImageInfo.MaskImageAddr = maskBuffer;
    // mask parse end

    if (createRefocusInstance() && setBufferAddr() && generate())
    {
        MY_LOGI("image refocus init end, success");
        return SUCCESS;
    }
    MY_LOGI("image refocus init end, fail");
    return FAIL;
}

bool ImageRefocus::initRefocusNoDepthMap(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
            MUINT8* jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, MUINT8* maskBuffer, int maskBufferSize, int maskWidth, int maskHeight)
{
    MY_LOGI("image refocus initRefocusNoDepthMap start inStereoImgWidth %d, inStereoImgHeight %d, outImgWidth %d, outImgHeight %d", inStereoImgWidth, inStereoImgHeight, outImgWidth, outImgHeight);
    /* Image Information */
    mStereoImgWidth = inStereoImgWidth;
    mSetreoImgHeight = inStereoImgHeight;
    mOutTargetImgWidth = outImgWidth;
    mOutTargetImgHeight = outImgHeight;
    
    MY_LOGI("image refocus initRefocusIMGSource ");
    initRefocusIMGSource(sourceFilePath, outImgWidth, outImgHeight, imgOrientation);
    //2. jps parse start
    mRefocusImageInfo.Width= mStereoImgWidth;
    mRefocusImageInfo.Height= mSetreoImgHeight;
    mRefocusImageInfo.ImgAddr[0] = jpsBuffer;

    //initJPSIMGSource(jpsFilePath, mRefocusImageInfo.Width, mRefocusImageInfo.Height, imgOrientation);
    //initRefocusDepthInfo(jpsFilePath, mRefocusImageInfo.Width, mRefocusImageInfo.Height);
    initJPSBuffer(jpsBuffer, jpsBufferSize);
    //3. mask parse start
    mRefocusImageInfo.MaskWidth = maskWidth;
    mRefocusImageInfo.MaskHeight = maskHeight;
    mRefocusImageInfo.MaskImageAddr = maskBuffer;
    // mask parse end

    if (createRefocusInstance() && setBufferAddr() && generate())
    {
        MY_LOGI("image refocus init end, success");
        return SUCCESS;
    }
    MY_LOGI("image refocus init end, fail");
    return FAIL;
}

bool ImageRefocus::initRefocusWithDepthMap(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation, 
        MUINT8* depthMapBuffer, int depthBufferSize, int inStereoImgWidth, int inStereoImgHeight)
{
    MY_LOGI("image refocus initRefocusWithDepthMap start outImgWidth %d, outImgHeight %d", outImgWidth, outImgHeight);
    /* Image Information */
    mOutTargetImgWidth = outImgWidth;
    mOutTargetImgHeight = outImgHeight;

    initRefocusIMGSource(sourceFilePath, outImgWidth, outImgHeight, imgOrientation);
    
    MY_LOGI("image refocus set depthbuffer info start %d  size %d", depthMapBuffer, depthBufferSize);
    mRefocusImageInfo.Width = inStereoImgWidth;
    mRefocusImageInfo.Height = inStereoImgHeight;
    mRefocusImageInfo.DepthBufferAddr = depthMapBuffer;
    mRefocusImageInfo.DepthBufferSize = depthBufferSize;
    MY_LOGI("image refocus set depthbuffer info end   outImgHeight %d outImgWidth %d", outImgHeight, outImgWidth);
    p_jpsImgBuffer = NULL;
    //config parse start
    //parse_configuration("/sdcard/Pictures/Config.cfg");

    if (createRefocusInstance() && setBufferAddr() && generate())
    {
        MY_LOGI("image refocus init end, success");
        return SUCCESS;
    }
    MY_LOGI("image refocus init end, fail");
    return FAIL;
}

void ImageRefocus::initRefocusIMGSource(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation)
{
    MY_LOGI("image refocus initRefocusIMGSource start");
    sprintf(mSourceFileName, "%s", sourceFilePath);
    
    // assign orientation
    //mRefocusImageInfo.Orientation = REFOCUS_ORIENTATION_0;
     /*memory allocation*/ 
     MUINT32 TargetSize;

    mSimRefocusInput.TargetWidth = outImgWidth;
    mSimRefocusInput.TargetHeight = outImgHeight;

    TargetSize = mSimRefocusInput.TargetWidth * mSimRefocusInput.TargetHeight * 3;
    //unsigned char *p_targetImgBuffer = new unsigned char[TargetSize];
    p_targetImgBuffer = (unsigned char *) malloc (TargetSize);
    MY_LOGI("image refocus decode image p_targetImgBuffer %d ", p_targetImgBuffer);
    mSimRefocusInput.TargetImageAddr = (MUINT32)p_targetImgBuffer;

    MY_LOGI("image refocus decode image resource start w  %d, H %d ", outImgWidth, outImgHeight);
    jpgDecode(sourceFilePath, (uint8_t*)p_targetImgBuffer, outImgWidth, outImgHeight);
    MY_LOGI("image refocus decode image resource end");

    //for target image
    mRefocusImageInfo.TargetWidth = mSimRefocusInput.TargetWidth;
    mRefocusImageInfo.TargetHeight = mSimRefocusInput.TargetHeight;
    mRefocusImageInfo.TargetImgAddr = (MUINT8*)p_targetImgBuffer;
    MY_LOGI("image refocus initRefocusIMGSource end");
}

void ImageRefocus::initJPSIMGSource(const char *jpsFilePath, int jpsImgWidth, int jpsImgHeight, int jpsImgOrientation)
{
    MY_LOGI("image refocus initRefocusIMGSource start");
    
     /*memory allocation*/ 
     MUINT32 TargetSize;

    TargetSize = jpsImgWidth * (jpsImgHeight + 8) * 3 / 2;
    //unsigned char *p_targetImgBuffer = new unsigned char[TargetSize];
    p_jpsImgBuffer = (unsigned char *) malloc (TargetSize * 2);
    MY_LOGI("image refocus decode image p_jpsImgBuffer %d ", p_jpsImgBuffer);
    //char sourceTestFilePath[100] = "/storage/sdcard0/DCIM/IMG_20100101_000350_1.jpg";
    jpgDecode(jpsFilePath, (uint8_t*)p_jpsImgBuffer, jpsImgWidth, jpsImgHeight + 8);
    MY_LOGI("image refocus decode image resource end");

    //for target image
    //mRefocusImageInfo.Width = mSimRefocusInput.Width;
    //mRefocusImageInfo.Height = mSimRefocusInput.Height;
    mRefocusImageInfo.ImgAddr[0] = (MUINT8*)p_jpsImgBuffer;
    MY_LOGI("image refocus initJPSIMGSource end");
}

void ImageRefocus::initRefocusDepthInfo(const char *depthmapSourcePath, int inStereoImgWidth, int inStereoImgHeight)
{
    MUINT32 StereoSize;

    mSimRefocusInput.StereoWidth = inStereoImgWidth;
    mSimRefocusInput.StereoHeight = inStereoImgHeight;
    StereoSize = inStereoImgWidth * inStereoImgHeight * 3 / 2;
    //allocate memory for depth info image
    //unsigned char *p_pano3d_buffer = new unsigned char [StereoSize*2];
    unsigned char *p_pano3d_buffer = (unsigned char *) malloc (StereoSize*2);
    MY_LOGI("image refocus decode image p_targetImgBuffer %d ", p_pano3d_buffer);
    // store result in MyPano3DResultInfo
    mSimRefocusInput.LeftImageAddr = (MUINT32)p_pano3d_buffer;
    MY_LOGI("image refocus decode depthmap resource start mSimRefocusInput.LeftImageAddr %d", mSimRefocusInput.LeftImageAddr);
    jpgDecode(depthmapSourcePath, (uint8_t*)p_pano3d_buffer, inStereoImgWidth, inStereoImgHeight);
    MY_LOGI("image refocus decode depthmap resource end");

    //for stereo image
    mRefocusImageInfo.Width = mSimRefocusInput.StereoWidth;
    mRefocusImageInfo.Height = mSimRefocusInput.StereoHeight;
    mRefocusImageInfo.ImgAddr[0] = (MUINT8*)p_pano3d_buffer;
}

void ImageRefocus::initJPSBuffer(MUINT8* jpsBuffer, int jpsBufferSize)
{
    MUINT32 TargetSize;

    TargetSize = mRefocusImageInfo.Width * (mRefocusImageInfo.Height+8) * 3 / 2;
    p_jpsImgBuffer = (unsigned char *) malloc (TargetSize * 2);
    MY_LOGI("image refocus decode image p_jpsImgBuffer %d  TargetSize %d ", p_jpsImgBuffer, TargetSize);
    if (!jpgToYV12(jpsBuffer, jpsBufferSize, (uint8_t*)p_jpsImgBuffer, mRefocusImageInfo.Width, (mRefocusImageInfo.Height+8))) {
        MY_LOGI("[decodeOneImage]decode failed!!");
    }
    mRefocusImageInfo.ImgAddr[0] = (MUINT8*)p_jpsImgBuffer;

}

bool ImageRefocus::createRefocusInstance()
{
    MY_LOGI("image refocus createRefocusInstance start");
    // init
    mRefocusInitInfo.pTuningInfo = &mRefocusTuningInfo;
    
    getTime(&mStartSec, &mStartNsec);
    
    mRefocus = mRefocus->createInstance(DRV_REFOCUS_OBJ_SW);

    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->createInstance time %10d", mTimeDiff);
    
    MUINT32 initResult;

    getTime(&mStartSec, &mStartNsec);

    initResult = mRefocus->RefocusInit((MUINT32 *)&mRefocusInitInfo, 0);

    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->RefocusInit time %10d", mTimeDiff);

    if (initResult != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus createRefocusInstance fail ");
        return FAIL;
    }
    MY_LOGI("image refocus createRefocusInstance success ");
    return SUCCESS;
}

bool ImageRefocus::setBufferAddr()
{
    MY_LOGI("image refocus setBufferAddr ");
    // get buffer size
    MUINT32 result;
    MUINT32 buffer_size;
    
    getTime(&mStartSec, &mStartNsec);

    MY_LOGI("image refocus setBufferAddr mRefocusImageInfo  "
            "TargetWidth %d, TargetHieght %d, TargetImgAddr %d  ImgNum %d, Width %d Height %d ImgAddr %d "
            "DepthBufferAddr %d  DepthBufferSize %d Orientation %d  MainCamPos %d", 
            mRefocusImageInfo.TargetWidth, mRefocusImageInfo.TargetHeight, mRefocusImageInfo.TargetImgAddr
            , mRefocusImageInfo.ImgNum , mRefocusImageInfo.Width , mRefocusImageInfo.Height , mRefocusImageInfo.ImgAddr
            , mRefocusImageInfo.DepthBufferAddr
            , mRefocusImageInfo.DepthBufferSize , mRefocusImageInfo.JPSOrientation , mRefocusImageInfo.MainCamPos);

    result = mRefocus->RefocusFeatureCtrl(REFOCUS_FEATURE_GET_WORKBUF_SIZE, (void *)&mRefocusImageInfo, (void *)&buffer_size);

    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->get_workbuff_size time %10d", mTimeDiff);

    MY_LOGI("image refocus setBufferAddr REFOCUS_FEATURE_GET_WORKBUF_SIZE buffer size  %d, result %d ", buffer_size, result);
    if (result != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus GET_WORKBUF_SIZE fail ");
        return FAIL;
    }

    // set buffer address
    //unsigned char *pWorkingBuffer = new unsigned char[buffer_size];
    pWorkingBuffer = (unsigned char *) malloc (buffer_size);
    mRefocusInitInfo.WorkingBuffAddr = (MUINT8*)pWorkingBuffer;
    
    getTime(&mStartSec, &mStartNsec);

    MY_LOGM("image refocus setBufferAddr SET_WORKBUF_ADDR start");
    result = mRefocus->RefocusFeatureCtrl(REFOCUS_FEATURE_SET_WORKBUF_ADDR, (void *)&mRefocusInitInfo.WorkingBuffAddr, NULL);

    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->set_workbuff_size time %10d", mTimeDiff);
    
    if (result != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus SET_WORKBUF_ADDR fail ");
        return FAIL;
    }
    MY_LOGI("image refocus SET_WORKBUF_ADDR success ");
    return SUCCESS;
}

bool ImageRefocus::generate()
{
    MUINT32 result;
    // algorithm - gen depth map
    MY_LOGI("image refocus generate start");
    MY_LOGI("mRefocusImageInfo.RcfyError %d, ", mRefocusImageInfo.RcfyError);
    MY_LOGI("mRefocusImageInfo.JPSOrientation %d, ", mRefocusImageInfo.JPSOrientation);

    getTime(&mStartSec, &mStartNsec);

    result = mRefocus->RefocusFeatureCtrl(REFOCUS_FEATURE_ADD_IMG, (void *)&mRefocusImageInfo, NULL);
    
    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->add_image time %10d", mTimeDiff);
    
    MY_LOGI("image refocus get result add image  %d ", result);
    if (result != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus ADD_IMG fail ");
        return FAIL;
    }

    getTime(&mStartSec, &mStartNsec);

    result = mRefocus->RefocusMain();
    
    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->RefocusMain time %10d", mTimeDiff);
    
    MY_LOGI("image refocus get result mainResult %d ", result);
    if (result != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus RefocusMain fail ");
        return FAIL;
    }
    getTime(&mStartSec, &mStartNsec);

    result = mRefocus->RefocusFeatureCtrl(REFOCUS_FEATURE_GET_RESULT, NULL, (void *)&mRefocusResultInfo);
    
    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance mRefocus->get_result time %10d", mTimeDiff);

    MY_LOGI("image refocus get result get result %d ", result);
    MY_LOGI("image refocus generate w  %d  h  %d", mRefocusResultInfo.RefocusImageWidth, mRefocusResultInfo.RefocusImageHeight);
    MY_LOGI("image refocus generate DepthBufferWidth  %d  DepthBufferHeight  %d", mRefocusResultInfo.DepthBufferWidth, mRefocusResultInfo.DepthBufferHeight);
    if (result != S_REFOCUS_OK)
    {
        MY_LOGI("image refocus GET_RESULT fail ");
        return FAIL;
    }
    if (mRefocusImageInfo.DepthBufferAddr == NULL)
    {
        MUINT8* depthBuffer = new MUINT8 [mRefocusResultInfo.DepthBufferSize];
        memcpy(depthBuffer, mRefocusResultInfo.DepthBufferAddr, mRefocusResultInfo.DepthBufferSize);
        mRefocusImageInfo.DepthBufferAddr = depthBuffer;
        mRefocusImageInfo.DepthBufferSize = mRefocusResultInfo.DepthBufferSize;
        MY_LOGI("image refocus copy depthBuffer from %d to %d", mRefocusResultInfo.DepthBufferAddr, depthBuffer);
    }
    //#if defined(DUMP_REFOCUS_IMAGE)
    if (isDumpRefocusYUVImage)
    {
        saveRefocusResult(&mRefocusResultInfo, &mRefocusImageInfo);
    }
    //#endif
    return SUCCESS;
}

bool ImageRefocus::generateRefocusImage(MUINT8* array, int touchCoordX, int touchCoordY, int depthOfField)
{
    MY_LOGI("image refocus generateRefocusImage touchCoordX %d touchCoordY %d, depthOfField %d ", touchCoordX, touchCoordY, depthOfField);
    mRefocusImageInfo.TouchCoordX = touchCoordX;
    mRefocusImageInfo.TouchCoordY = touchCoordY;
    mRefocusImageInfo.DepthOfField = depthOfField;
    bool generateResult;
    generateResult = generate();
    if (!generateResult) return FAIL;

    getTime(&mStartSec, &mStartNsec);
    memcpy(array, (MUINT8*)mRefocusResultInfo.RefocusedRGBAImageAddr, mRefocusResultInfo.RefocusImageWidth*mRefocusResultInfo.RefocusImageHeight*4);
    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("performance generateRefocusImage memcpy time %10d", mTimeDiff);

    MY_LOGI("image refocus memcpy done");
    if (mRefocusResultInfo.RefocusImageWidth == 0 || mRefocusResultInfo.RefocusImageHeight == 0) 
    {
        return FAIL;
    } 
    //#if defined(DUMP_REFOCUS_RGB_IMAGE)
    if (isDumpRefocusRGBImage)
    {
        char file_dumpbuffer[FILE_NAME_LENGTH];
        sprintf(file_dumpbuffer,  "%s_%s_%d_%d_%d.raw", mSourceFileName, "_rgb_buffer", touchCoordX, touchCoordY, depthOfField);
        dumpBufferToFile((MUINT8*)mRefocusResultInfo.RefocusedRGBAImageAddr, mRefocusResultInfo.RefocusImageWidth*mRefocusResultInfo.RefocusImageHeight*4, file_dumpbuffer);
    }
    //#endif

    return SUCCESS;
}

void ImageRefocus::deinit()
{
    // uninit
    MY_LOGI("image refocus deinit start");
    if (pWorkingBuffer)free(pWorkingBuffer);
    MY_LOGI("image refocus deinit free pWorkingBuffer");
    if (p_targetImgBuffer)free(p_targetImgBuffer);
    MY_LOGI("image refocus deinit free p_targetImgBuffer %d ", p_jpsImgBuffer);
    if (p_jpsImgBuffer != NULL)free(p_jpsImgBuffer);
    MY_LOGI("image refocus deinit free p_jpsImgBuffer");
    mRefocus->RefocusReset();
    MY_LOGI("image refocus deinit RefocusReset");
    mRefocus->destroyInstance(mRefocus);
}

int ImageRefocus::getDepthBufferSize()
{
    return mRefocusResultInfo.DepthBufferSize;
}

int ImageRefocus::getDepthBufferWidth()
{
    return mRefocusResultInfo.DepthBufferWidth;
}

int ImageRefocus::getDepthBufferHeight()
{
    return mRefocusResultInfo.DepthBufferHeight;
}

int ImageRefocus::getXMPDepthBufferSize()
{
    return mRefocusResultInfo.XMPDepthWidth * mRefocusResultInfo.XMPDepthHeight;
}

int ImageRefocus::getXMPDepthBufferWidth()
{
    return mRefocusResultInfo.XMPDepthWidth;
}

int ImageRefocus::getXMPDepthBufferHeight()
{
    return mRefocusResultInfo.XMPDepthHeight;
}

void ImageRefocus::saveDepthMapInfo(MUINT8* depthBufferArray, MUINT8* xmpDepthBufferArray)
{
    MY_LOGI("saveDepthMapInfo DepthBufferSize %d ", mRefocusResultInfo.DepthBufferSize);
    memcpy(depthBufferArray, (MUINT8*)mRefocusResultInfo.DepthBufferAddr, mRefocusResultInfo.DepthBufferSize);
    memcpy(xmpDepthBufferArray, (MUINT8*)mRefocusResultInfo.XMPDepthMapAddr, mRefocusResultInfo.XMPDepthWidth * mRefocusResultInfo.XMPDepthHeight);
}

void ImageRefocus::saveRefocusImage(const char *saveFileName, int inSampleSize)
{
    mRefocusImageInfo.TouchCoordX = mRefocusImageInfo.TouchCoordX * inSampleSize;
    mRefocusImageInfo.TouchCoordY = mRefocusImageInfo.TouchCoordY * inSampleSize;
    initRefocusIMGSource(mSourceFileName, mRefocusImageInfo.TargetWidth * inSampleSize, mRefocusImageInfo.TargetHeight * inSampleSize, 0);
    if (createRefocusInstance() && setBufferAddr() && generate())
    {
        MY_LOGI("image refocus init end, success");
        //return SUCCESS;
    }
    
    char file[FILE_NAME_LENGTH];
    sprintf(file, "%s", saveFileName);
    FILE *fp;
    unsigned char *src_va = NULL;
    MY_LOGI("test file:%s", file);
    fp = fopen(file, "w");
    if (fp == NULL) {
        MY_LOGI("[ULT]ERROR: Open file %s failed.", file);
        return ;
    }

    //should free this memory when not use it !!!
    src_va = (unsigned char *) malloc(mRefocusResultInfo.RefocusImageWidth * mRefocusResultInfo.RefocusImageHeight);
    MY_LOGI("src va :0x%x", (UINT32)src_va );

    if (src_va == NULL) {
        MY_LOGI("Can not allocate memory");
        fclose(fp);
        return;
    }
    

    MY_LOGI("src va :0x%x", (UINT32)mRefocusResultInfo.RefocusedYUVImageAddr );

    //char file_dumpbuffer[FILE_NAME_LENGTH];
    //sprintf(file_dumpbuffer,  "%s_%s.yuv", mSourceFileName, "_yuv_buffer");
    //dumpBufferToFile((MUINT8*)mRefocusResultInfo.RefocusedYUVImageAddr, mRefocusResultInfo.RefocusImageWidth*mRefocusResultInfo.RefocusImageHeight, file_dumpbuffer);
    
    //dumpBuffer((unsigned char *)mRefocusResultInfo.RefocusedYUVImageAddr, mRefocusResultInfo.RefocusImageWidth * mRefocusResultInfo.RefocusImageHeight, false);
    
    yv12ToJpg((unsigned char *)mRefocusResultInfo.RefocusedYUVImageAddr, mRefocusResultInfo.RefocusImageWidth * mRefocusResultInfo.RefocusImageHeight,
                        mRefocusResultInfo.RefocusImageWidth, mRefocusResultInfo.RefocusImageHeight, src_va, mRefocusResultInfo.RefocusImageWidth * mRefocusResultInfo.RefocusImageHeight, mJpgSize);
    dumpBufferToFile(src_va, mJpgSize, file);
    free(src_va);
    fclose(fp);
}

ImageRefocus::~ImageRefocus(){
    if (mRefocusImageInfo.DepthBufferAddr != NULL)
    {	
        delete mRefocusImageInfo.DepthBufferAddr;
    }
    MY_LOGD("end");
}


bool ImageRefocus::jpgDecode(char const *fileName, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight){
    MUINT32 file_size;
    unsigned char *file_buffer;
    FILE *fp;
    MUINT32 ret;

    MY_LOGD("begin");
    //open a image file
    fp = fopen(fileName, "rb");
    if (fp == NULL) {
        MY_LOGI("[readImageFile]ERROR: Open file %s failed.", fileName);
        return false;
    }
    MY_LOGI("open file %s success!", fileName);
    //get file size
    fseek(fp, SEEK_SET, SEEK_END);
    file_size=ftell(fp);
    MY_LOGI("[decodeOneImage]file_size is %d", file_size);

    if(file_size == 0) {
       MY_LOGI("ERROR: [readImageFile]file size is 0");
       fclose(fp);
       return false;
    }

    //allocate buffer for the file
    //should free this memory when not use it !!!
    file_buffer = (unsigned char *) malloc(ALIGN128(file_size));
    MY_LOGI("src va :0x%x", (UINT32)file_buffer );
    if (file_buffer == NULL) {
        MY_LOGI("Can not allocate memory");
        fclose(fp);
        return false;
    }

    //read image file
    fseek(fp, SEEK_SET, SEEK_SET);
    ret = fread(file_buffer,1,file_size,fp);
    if(ret != file_size) {
        MY_LOGI("File read error ret[%d]",ret);
        fclose(fp);
        return false;
    }
    MY_LOGI("read file to buffer success!");
    #ifdef MM_PROFILING
        getTime(&mStartSec, &mStartNsec);
    #endif
        
    MY_LOGI("jpgDecode srcSize %d ", file_size);
    if (!jpgToYV12(file_buffer, file_size, dstBuffer, dstWidth, dstHeight)) {
        MY_LOGI("[decodeOneImage]decode failed!!");
    }

    //#ifdef DUMP_DECODE_IMAGE
    if (isDumpDecodeImage)
    {
        char jpgname[FILE_NAME_LENGTH];
        sprintf(jpgname,  "%s_%d.bmp", fileName, "_decode");
        unsigned int dstFormat = FORMAT_YV12;
        //saveFile(file_buffer, file_size, mOutTargetImgWidth, mOutTargetImgHeight, jpgname, dstFormat, true);
    }
    //#endif

    
    #ifdef MM_PROFILING
    getTime(&mEndSec, &mEndNsec);
    mTimeDiff = getTimeDiff(mStartSec, mStartNsec, mEndSec, mEndNsec);
    MY_LOGM("%10d ==> jpgToYV12: jpg to yv12", mTimeDiff);
    #endif
    // release file buffer
    free(file_buffer);

    //close image file
    fclose(fp);
    MY_LOGD("end");
    return true;
}

bool ImageRefocus::jpgToYV12(uint8_t* srcBuffer, uint32_t srcSize, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight) {
    MHAL_JPEG_DEC_INFO_OUT outInfo;
    MHAL_JPEG_DEC_START_IN inParams;
    MHAL_JPEG_DEC_SRC_IN    srcInfo;
    void *fSkJpegDecHandle;
    unsigned int cinfo_output_width, cinfo_output_height;
    int re_sampleSize ;
    //int preferSize = 0;
    // TODO: samplesize value
    //int sampleSize = 8;
    int width, height;

    MY_LOGI("onDecode start %d ", srcSize);
    //2 step1: set sampleSize value
    //sampleSize = roundToTwoPower(sampleSize);
    //2 step2: init fSkJpegDecHandle
    fSkJpegDecHandle = srcInfo.jpgDecHandle = NULL;
    //2 step3: init  inparam
    //memcpy(&inParams, param, sizeof(MHAL_JPEG_DEC_START_IN));
    inParams.dstFormat = (JPEG_OUT_FORMAT_ENUM)FORMAT_YV12;
    inParams.srcBuffer = srcBuffer;
    inParams.srcBufSize = ALIGN128(srcSize);
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
    MY_LOGD("inParams.srcLength --> %d",inParams.srcLength);
    //2 step9: start decode
    if (MHAL_NO_ERROR != mHalJpeg(MHAL_IOCTL_JPEG_DEC_START,
                                   (void *)&inParams, sizeof(inParams),
                                   NULL, 0, NULL)){
        MY_LOGI("JPEG HW not support this image");
        return false;
    }

    return true;
}

int ImageRefocus::roundToTwoPower(int a)
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


/* bool ImageRefocus::saveFile(unsigned char* buf,int bufSize,
                    int width, int height,
                    char* filename, int fmt, bool onlyRaw) {
     MY_LOGI("saveFile");
     if (buf == NULL) return false;
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
        return true;
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
    return true;
} */

void ImageRefocus::dumpBufferToFile(MUINT8* buffer,int bufferSize, char* fileName){
    FILE* fp;
    int index;

    MY_LOGI("dump buffer to file buffer address:0x%x", buffer);

    if (buffer == NULL) return;
    MY_LOGI("dump buffer to file:%s", fileName);

    fp = fopen(fileName, "w");
    if (fp == NULL) {
        MY_LOGI("ERROR: Open file %s failed.", fileName);
        return ;
    }

    MY_LOGI("bufferSize %d ", bufferSize);

    for(index = 0 ; index < bufferSize ; index++) {
        fprintf(fp, "%c", buffer[index]);
    }
    MY_LOGD("dump buffer to file success!");
    fclose(fp);
}

unsigned int ImageRefocus::getDstSize(unsigned int width, unsigned int height, int fmt)
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

void ImageRefocus::saveRefocusResult(RefocusResultInfo* pResultInfo, RefocusImageInfo* pImageInfo)
{
    BITMAP bmp;
    char filename[FILE_NAME_LENGTH];

    sprintf(filename, "%s_%d_%d_%d.bmp",mSourceFileName,
                        pImageInfo->TouchCoordX, 
                        pImageInfo->TouchCoordY, 
                        pImageInfo->DepthOfField);
    MY_LOGI("filename  %s", filename);
    UTIL_BASE_IMAGE_STRUCT img;
    char *ImageBuffer = new char [pResultInfo->RefocusImageWidth*pResultInfo->RefocusImageHeight*4];
    bmp_create(&bmp, pResultInfo->RefocusImageWidth, pResultInfo->RefocusImageHeight, 0);
    img.data = (MUINT32 *)(pResultInfo->RefocusedYUVImageAddr);
    img.width = pResultInfo->RefocusImageWidth;
    img.height = pResultInfo->RefocusImageHeight;
    YUV420_toBMP((kal_uint8 *)img.data, &bmp);
    bmp_write(filename, &bmp);
    bmp_free(&bmp);
}

bool ImageRefocus::yv12ToJpg(unsigned char *srcBuffer, int srcSize,
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

MUINT32 ImageRefocus::getBufSize(MUINT32 width, MUINT32 height, MUINT32 stride)
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

void ImageRefocus::getTime(int *sec, int *usec)
{
    timeval time;
    gettimeofday(&time, NULL);
    *sec = time.tv_sec;
    *usec = time.tv_usec;
}

int ImageRefocus::getTimeDiff(int startSec, int startNSec, int endSec, int endNSec)
{
    return ((endSec - startSec) * 1000000 + (endNSec - startNSec))/1000;
}

void ImageRefocus::debugConfig()
{
    FILE *fpYUVDump;
    FILE *fpRGBDump;
    FILE *fpDecodeDump;
    fpYUVDump = fopen(dumpRefocusYUVImageConfig, "r");
    if (fpYUVDump == NULL) {
        MY_LOGI("dump refocus yuv image off");
        isDumpRefocusYUVImage = false;
    } else {
        isDumpRefocusYUVImage = true;
        fclose(fpYUVDump);
    }
    fpRGBDump = fopen(dumpRefocusRGBImageConfig, "r");
    if (fpRGBDump == NULL) {
        MY_LOGI("dump refocus rgb image off");
        isDumpRefocusRGBImage = false;
    } else {
        isDumpRefocusRGBImage = true;
        fclose(fpRGBDump);
    }
    fpDecodeDump = fopen(dumpDecodeImageConfig, "r");
    if (fpDecodeDump == NULL) {
        MY_LOGI("dump refocus decode image off");
        isDumpDecodeImage = false;
    } else {
        isDumpDecodeImage = true;
        fclose(fpDecodeDump);
    }
}

// for debug information
void ImageRefocus::parse_configuration(char* configFilename)
{
    int maxline = 200;
    char ident[200];
    char oneline[200], *token;
    char seps = '=';
    FILE* config_fp = 0;

    ident[maxline-1] = 0;
  
    if((config_fp = fopen(configFilename, "r")) == NULL)
    {
        printf("The configuration file cannot be opened.\n");
        return;
    }

    while(!feof(config_fp) )
    {
        fgets(oneline, maxline, config_fp);
        first_arg( oneline, ident );    // Get the first argument
        switch ( ident[0] ){
        case '#':   // comment
            continue;
        break;

        default:    // words
            token = (char *)strchr(oneline, seps);
            if( !token )    
                break;

            strcpy(oneline, token+2);

            if ( !strcmp("C_ITERATION",ident)){ 
                sscanf(oneline, "%d", &mRefocusTuningInfo.IterationTimes);
                MY_LOGI("C_ITERATION     =%d\n",mRefocusTuningInfo.IterationTimes);
            }

            if ( !strcmp("DS_H", ident) ){
                sscanf(oneline, "%d", &mRefocusTuningInfo.HorzDownSampleRatio);
                MY_LOGI("DS_H            =%d\n",mRefocusTuningInfo.HorzDownSampleRatio);                
            }

            if ( !strcmp("DS_V", ident) ){
                sscanf(oneline, "%d", &mRefocusTuningInfo.VertDownSampleRatio);          
                MY_LOGI("DS_V            =%d\n",mRefocusTuningInfo.VertDownSampleRatio);
            }

            /*if ( !strcmp("ViewWidth", ident)){
                sscanf(oneline, "%d", &mRefocusImageInfo.ViewWidth);
                MY_LOGI("ViewWidth       =%d\n",mRefocusImageInfo.ViewWidth);
            }
            
            if ( !strcmp("ViewHeight", ident) ){
                sscanf(oneline, "%d", &mRefocusImageInfo.ViewHeight);
                MY_LOGI("ViewHeight      =%d\n",mRefocusImageInfo.ViewHeight);
            }
            
            if ( !strcmp("POS_X", ident) ){   
                sscanf(oneline, "%d", &mRefocusImageInfo.PosX);          
                MY_LOGI("POS_X           =%d\n",mRefocusImageInfo.PosX);
            }
            
            if ( !strcmp("POS_Y", ident) ){   
                sscanf(oneline, "%d", &mRefocusImageInfo.PosY);          
                MY_LOGI("POS_Y           =%d\n",mRefocusImageInfo.PosY);
            }*/

            /*if ( !strcmp("SR_H", ident) ){   
                sscanf(oneline, "%d", &mRefocusImageInfo.SRH);          
                MY_LOGI("SR_H           =%d\n",mRefocusImageInfo.SRH);
            }

            if ( !strcmp("SR_V", ident) ){   
                sscanf(oneline, "%d", &mRefocusImageInfo.SRV);          
                MY_LOGI("SR_V           =%d\n",mRefocusImageInfo.SRV);
            }                                                                                                                                                                             

            if ( !strcmp("LP_BOX_EN", ident) ){
                sscanf(oneline, "%d", &mRefocusImageInfo.LPBoxEn);
                MY_LOGI("LP_BOX_EN      =%d\n",mRefocusImageInfo.LPBoxEn);
            }   
            
            if ( !strcmp("C_INTPL_GAIN", ident) ){
                sscanf(oneline, "%d", &mRefocusImageInfo.c_gain_input);
                MY_LOGI("C_INTPL_GAIN   =%d\n",mRefocusImageInfo.c_gain_input);
            }
            
            if ( !strcmp("C_INTPL_MODE", ident) ){
                sscanf(oneline, "%d", &mRefocusImageInfo.c_intpl_mode);
                printf("C_INTPL_MODE  =%d\n",mRefocusImageInfo.c_intpl_mode);
            }
            
            if ( !strcmp("C_INTPL_WT", ident) ) {
                sscanf(oneline, "%d", &mRefocusImageInfo.c_intpl_WT);
                MY_LOGI("C_INTPL_WT   =%d\n",mRefocusImageInfo.c_intpl_WT);
            }
            
            if ( !strcmp("C_INTPL_PREF", ident) ){
                sscanf(oneline, "%d", &mRefocusImageInfo.c_intpl_pref);
                MY_LOGI("C_INTPL_PREF  =%d\n",mRefocusImageInfo.c_intpl_pref);
            }*/
            
            if ( !strcmp("DRZ_WD", ident) )     {
                sscanf(oneline, "%d", &mRefocusImageInfo.DRZ_WD);
                MY_LOGI("DRZ_WD        =%d\n",mRefocusImageInfo.DRZ_WD);
            }
            
            if ( !strcmp("DRZ_HT", ident) )     {
                sscanf(oneline, "%d", &mRefocusImageInfo.DRZ_HT);
                MY_LOGI("DRZ_HT        =%d\n",mRefocusImageInfo.DRZ_HT);
            }
            
            /*if ( !strcmp("PRINT_DATA", ident) ) {
                sscanf(oneline, "%d", &mRefocusImageInfo.PRINT_DATA);
                MY_LOGI("PRINT_DATA    =%d\n",mRefocusImageInfo.PRINT_DATA);
            }
*/
            break;
        }
    }

    fclose(config_fp);

  return;
}

char* ImageRefocus::first_arg( char *argument, char *arg_first )
{
  memset(arg_first, 0, strlen(arg_first));

  while ( *argument == ' ' )
    argument++;

  while ( *argument != '\0')    {
    if ( *argument == ' ' || *argument == '=' || *argument == '\t')
      break;
    sprintf(arg_first, "%s%c", arg_first, (char)*argument);
    argument++;
  }

  while ( *argument == ' ' )
    argument++;

  return argument;
}

/*bool ImageRefocus::dumpBuffer(unsigned char *SrcAddr, unsigned int size, bool is_out)
{

FILE *fp = NULL;
FILE *fpEn = NULL;
unsigned char* cptr ;
char filepath[128];

//sprintf(filepath, "/data/otis/dec_pipe_scaler_step_%04d.raw", fileNameIdx); 
if (is_out)
sprintf(filepath, "//data//otis//%s.jpg", "JpgEncPipe"); 
else
sprintf(filepath, "//data//otis//%s.yuv", "JpgEncPipe"); 

fp = fopen(filepath, "w");
if (fp == NULL)
{
    MY_LOGI("open Dump file fail: %s\n", filepath);
return false;
}

MY_LOGI("\nDumpRaw -> %s, addr %x, size 0x%x !!", filepath,(unsigned int)SrcAddr, size); 
cptr = (unsigned char*)SrcAddr ;
if (is_out)
{
// fill in SOI marker
fprintf(fp, "%c", 0xFF); 
fprintf(fp, "%c", 0xD8); 
}

for( unsigned int i=0;i<size;i++){  total size in comp 
fprintf(fp,"%c", *cptr ); 
cptr++;
} 

fclose(fp); 
return true ; 

}*/

}




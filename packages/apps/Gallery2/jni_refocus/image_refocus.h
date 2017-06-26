#include <utils/Log.h>

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#include <iostream>
#include <vector>
#include <string>
#include <pthread.h>
#include "SkBitmap.h"
#include "SkPaint.h"
#include "SkCanvas.h"
#include "SkStream.h"
#include "SkColorPriv.h"
#include "SkString.h"
#include "SkImageEncoder.h"
#include "SkImageDecoder.h"

#include "MediaHal.h"
#include "jpeg_hal.h"
#include "MTKRefocus.h"
#include "MTKRefocusErrCode.h"
#include "bmp_utility.h"

/*********************************************************************/
/* from libutility.h */

typedef struct UTIL_BASE_IMAGE_STRUCT
{
    MINT32 width;   ///< image width (column)
    MINT32 height;  ///< image height (row)
    void *data;     ///< data pointer    
} UTIL_BASE_IMAGE_STRUCT, *P_UTIL_BASE_IMAGE_STRUCT;

typedef enum UTL_IMAGE_FORMAT_ENUM
{
    UTL_IMAGE_FORMAT_RGB565=1,
    UTL_IMAGE_FORMAT_BGR565,
    UTL_IMAGE_FORMAT_RGB888,
    UTL_IMAGE_FORMAT_BGR888,
    UTL_IMAGE_FORMAT_ARGB888,
    UTL_IMAGE_FORMAT_ABGR888,
    UTL_IMAGE_FORMAT_BGRA8888,
    UTL_IMAGE_FORMAT_RGBA8888,
    UTL_IMAGE_FORMAT_YUV444,
    UTL_IMAGE_FORMAT_YUV422,
    UTL_IMAGE_FORMAT_YUV420,
    UTL_IMAGE_FORMAT_YUV411,
    UTL_IMAGE_FORMAT_YUV400,
    UTL_IMAGE_FORMAT_PACKET_UYVY422,
    UTL_IMAGE_FORMAT_PACKET_YUY2,
    UTL_IMAGE_FORMAT_PACKET_YVYU,
    UTL_IMAGE_FORMAT_NV21,
    UTL_IMAGE_FORMAT_YV12,

    UTL_IMAGE_FORMAT_RAW8=100,
    UTL_IMAGE_FORMAT_RAW10,
    UTL_IMAGE_FORMAT_EXT_RAW8,
    UTL_IMAGE_FORMAT_EXT_RAW10,
    UTL_IMAGE_FORMAT_JPEG=200
} UTL_IMAGE_FORMAT_ENUM;

/*********************************************************************/
typedef struct
{
     MUINT32                 StereoWidth;        //Width of stereo image
     MUINT32                 StereoHeight;       //Height of stereo image
     MUINT32                 LeftImageAddr;      //Address of Left image
     MUINT32                 RightImageAddr;     //Address of Right image

     MUINT32                 TargetWidth;        //Width of target image
     MUINT32                 TargetHeight;       //Height of target image
     MUINT32                 TargetImageAddr;    //Address of Target image
} SimRefocusInputStruct;

/*********************************************************************/
#define FORMAT_YV12 5
#define ALIGN16(x)  ((x + 15)&(~(16-1)))
#define ALIGN128(x)  ((x + 127)&(~(128-1)))
#define DFT_RCFY_ERROR       0
#define DFT_RCFY_ITER_NO    10
#define DFT_THETA            0
#define DFT_DISPARITY_RANGE 10
#define YUV_IMG_STRIDE_Y        (16)
#define YUV_IMG_STRIDE_U        (16)
#define YUV_IMG_STRIDE_V        (16)
/*********************************************************************/

#define FILE_NAME_LENGTH 100
#define PATH_NAME_LENGTH 300
#define INVALID_BUFFER_ID -1
#define SUCCESS true
#define FAIL false
#define dumpRefocusYUVImageConfig "/storage/sdcard0/dumpRefocusYUVImageConfig.bin"
#define dumpDecodeImageConfig "/storage/sdcard0/dumpRefocusDecodeImage.bin"
#define dumpRefocusRGBImageConfig "/storage/sdcard0/dumpRefocusRGBImageConfig.bin"
#define testTouchX 100
#define testTouchY 100
#define testDepthOfField 16

namespace android {

    class ImageRefocus
    {
        private:
            /*for performance profiling*/
            MINT32 mStartSec;
            MINT32 mStartNsec;
            MINT32 mEndSec;
            MINT32 mEndNsec;
            MINT32 mTimeDiff;
            MINT32 getTimeDiff(int startSec, int startNSec, int endSec, int endNSec);
            void getTime(int *sec, int *usec);
        
            /* Refocus global*/
            MUINT8 *pWorkingBuffer;
            MUINT8 *p_targetImgBuffer;
            MUINT8 *p_jpsImgBuffer;
            SimRefocusInputStruct mSimRefocusInput;
            MTKRefocus *mRefocus;
            RefocusInitInfo mRefocusInitInfo;
            RefocusTuningInfo mRefocusTuningInfo;
            RefocusImageInfo mRefocusImageInfo;
            RefocusResultInfo mRefocusResultInfo;
            MUINT32 mStereoImgWidth;
            MUINT32 mSetreoImgHeight;
            MUINT32 mOutTargetImgWidth; //output image width
            MUINT32 mOutTargetImgHeight; //output image height
            MUINT32 getBufSize(MUINT32 width, MUINT32 height, MUINT32 stride);
            MUINT32 mJpgSize; //jpg file size
            char mSourceFileName[FILE_NAME_LENGTH]; //prefix name

            /* Refocus debug*/
            bool isDumpRefocusYUVImage;
            bool isDumpDecodeImage;
            bool isDumpRefocusRGBImage;
            
            void debugConfig();
            bool jpgDecode(char const *fileName, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight);
            bool jpgToYV12(uint8_t* srcBuffer, uint32_t srcSize, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight);
            int roundToTwoPower(int a);
           // bool saveFile(unsigned char* buf,int bufSize,int width, int height,char* filename, int fmt, bool onlyRaw);
            void dumpBufferToFile(MUINT8* buffer,int bufferSize, char* filename);
            unsigned int getDstSize(unsigned int width, unsigned int height, int fmt);
            void saveRefocusResult(RefocusResultInfo* pResultInfo, RefocusImageInfo* pImageInfo);
            bool yv12ToJpg(unsigned char *srcBuffer, int srcSize,
                    int srcWidth, int srcHeight, unsigned char *dstBuffer, int dstSize, MUINT32 &u4EncSize);
            void initRefocusIMGSource(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation);
            void initJPSIMGSource(const char *jpsFilePath, int jpsImgWidth, int jpsImgHeight, int jpsImgOrientation);
            void initRefocusDepthInfo(const char *depthmapSourcePath, int inStereoImgWidth, int inStereoImgHeight);
            void initJPSBuffer(MUINT8* jpsBuffer, int jpsBufferSize);
            bool createRefocusInstance();
            bool setBufferAddr();
            bool generate();
            //debug
            void parse_configuration(char* configFilename);
            char *first_arg( char *argument, char *arg_first);
        public:
            ImageRefocus();
            ImageRefocus(int jpsWidth, int jpsHeight, int maskWidth, int maskHeight, int posX, int posY, 
                    int viewWidth, int viewHeight, int orientation, int mainCamPos, int touchCoordX1st, int touchCoordY1st);
            bool initRefocusNoDepthMapTest(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
                            const char *depthmapSourcePath, int inStereoImgWidth, int inStereoImgHeight, const char *maskFilePath);
            bool initRefocusNoDepthMap(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation,
                        MUINT8* jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, MUINT8* maskBuffer, 
                        int maskBufferSize, int maskWidth, int maskHeight);
            bool initRefocusNoDepthMapRealFileTest(const char *testSourceFilePath, const char *sourceFilePath, const char *jpsFilePath, 
                                    int outImgWidth, int outImgHeight, int imgOrientation,
                                    MUINT8* jpsBuffer, int jpsBufferSize, int inStereoImgWidth, int inStereoImgHeight, MUINT8* maskBuffer, 
                                    int maskBufferSize, int maskWidth, int maskHeight);
            bool initRefocusWithDepthMap(const char *sourceFilePath, int outImgWidth, int outImgHeight, int imgOrientation, 
                            MUINT8* depthMapBuffer, int depthBufferSize, int inStereoImgWidth, int inStereoImgHeight);
            bool generateRefocusImage(MUINT8* array, int touchCoordX, int touchCoordY, int depthOfField);
            void deinit();
            void saveDepthMapInfo(MUINT8* depthBufferArray, MUINT8* xmpDepthBufferArray);
            void saveRefocusImage(const char *saveFileName, int inSampleSize);
            int getDepthBufferSize();
            int getDepthBufferWidth();
            int getDepthBufferHeight();
            int getXMPDepthBufferSize();
            int getXMPDepthBufferWidth();
            int getXMPDepthBufferHeight();
            ~ImageRefocus();
    };
}

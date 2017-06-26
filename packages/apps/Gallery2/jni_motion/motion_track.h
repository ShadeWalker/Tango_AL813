#ifndef MOTION_TRACK_H
#define MOTION_TRACK_H

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

#include "mtkmfbmm.h"
#include "MediaHal.h"
#include "jpeg_hal.h"
#include "camera_custom_motiontrack.h"

#define FORMAT_YV12 5
#define ALIGN16(x)  ((x + 15)&(~(16-1)))
#define ALIGN128(x)  ((x + 127)&(~(128-1)))
#define MAX_IMAGE_SELECT_NUM 8
#define MAX_IMAGE_NUM 20

namespace android {

    class MotionTrack
    {
        private:
            #define FILE_NAME_LENGTH 50
            #define PATH_NAME_LENGTH 300
            #define INVALID_BUFFER_ID -1

            MTKMfbmm *mfb; //mfb object
            char workPath[PATH_NAME_LENGTH]; //work folder path
            char prefixName[FILE_NAME_LENGTH]; //prefix name
            int inImgWidth; //input image width
            int inImgHeight; //input image height
            int inImgNum;
            int inImgSize; //inImgSize
            char inImgFileName[PATH_NAME_LENGTH]; //input image file name

            int outImgWidth; //output image width
            int outImgHeight; //output image height
            int outImgSize; //output image size
            char outImgFileName[PATH_NAME_LENGTH]; //out image path name
            int outImgNum; //output image number

            char dumpImgFolder[PATH_NAME_LENGTH];

            char intermediateFileName[PATH_NAME_LENGTH]; //inter mediate file name
            MBOOL isIMFileExist; //is inter mediate file exist
            long infoPosition;
            MUINT8 manualFlag;
            MBOOL IsdebugMode;

            MUINT8 *imgYUV12Buffer; // input YUV12 image buffer
            MUINT8 *imgBlendBuffer; //blend output buffer
            MUINT8 *blendVirtalAddr; //need be align 16 for jpg encoder
            MUINT8 *workingBuffer; //working buffer
            MUINT8 *jpgBuffer; //jpg buffer for save jpg image
            MUINT32 jpgBufferSize; //jpg buffer size
            MUINT32 jpgSize; //jpg file size

            MINT32 autoCandImg[4]; //auto mode candidate image index
            int autoCandNum; //auto mode candidate image number

            MUINT8 manualCandImg[MAX_IMAGE_SELECT_NUM]; //manual mode candidate image index
            INT8 bufferMatcher[MAX_IMAGE_NUM]; //buffer matcher table
            int manualCandNum; //manual mode candidate image number
            MBOOL rcmdImgList[MAX_IMAGE_NUM]; //use for recommended image, true stand for can be used for blend
            MBOOL prevSelectList[MAX_IMAGE_NUM];//previous select list
            MFBMM_PROC1_INFO_STRUCT proc1In; // use for blending
            MFBMM_PROC1_RESULT_STRUCT proc1Out; // use for blending
            MFBMM_INTERMEDIATE_STRUCT interMedia;

            /*for mm profiling*/
            int start_sec;
            int start_nsec;
            int end_sec;
            int end_nsec;
            int timeDiff;

            void initMfb();
            void setWorkingBuffer();
            void setInterMediateData();
            void doManualSelection();
            void blending();
            /*read manual-mode input image based on manual selection result*/
            bool readImages();
            /*decode jpg image to YUV12*/
            bool jpgDecode(char const *fileName, uint8_t *dstBuffer);
            bool jpgToYV12(uint8_t* srcBuffer, uint32_t srcSize, uint8_t *dstBuffer, uint32_t dstWidth, uint32_t dstHeight);
            MBOOL YV12ToJpg(unsigned char *srcBuffer, int srcSize,
                    int srcWidth, int srcHeight, unsigned char *dstBuffer, int dstSize, MUINT32 &u4EncSize);
            // for some reasons, mark saveFile function
            // 1. saveFile is never been used
            // 2. SkBitmap.setConfig & copyTo cannot compile pass
            /*
            int saveFile(unsigned char* buf,int bufSize,
                                int width, int height,
                                char* filename, int fmt, bool onlyRaw);
            */
            int roundToTwoPower(int a);
            unsigned int getDstSize(unsigned int width, unsigned int height, int fmt);
            MUINT32 getBufSize(MUINT32 width, MUINT32 height, MUINT32 stride);
            void saveInfoToIMFile();
            void dumpBufferToFile(MUINT8* buffer,int bufferSize, char* filename);
            void dumpBufferToFileWithExif(MUINT8* buffer,int bufferSize, char* fileName);
            void GetTime(int *sec, int *usec);
            int GetTimeDiff(int startSec, int startNSec, int endSec, int endNSec);
            //define
            #define YUV_PRE_ALLOC_WIDTH     (1920)
            #define YUV_PRE_ALLOC_HEIGHT    (1088)
            #define JPG_COMPRESSION_RATIO   (2)
            #define JPG_EXIF_SIZE           (2*1024)
            #define JPG_LOCK_TIMEOUT_CNT    (10)
            #define JPG_LOCK_TIMEOUT_SLEEP  (1000) //us
            #define YUV_IMG_STRIDE_Y        (16)
            #define YUV_IMG_STRIDE_U        (16)
            #define YUV_IMG_STRIDE_V        (16)

        public:
            MotionTrack();
            MotionTrack(const char *workPath, const char *prefixName, int inImgWidth, int inImgHeight,
                    int inImgNum, int outImgWidth, int outImgHeight);
            /*get recommend image list*/
            MBOOL *getRefImage(MUINT8 query_img_id);
            MBOOL *getPrevRefImage(void);
            MBOOL *getPrevSelect(void);
            /*set manual select indexes*/
            void setManualIndexes(MUINT8 *candImg, int num);
            void setBufferMatcher(INT8 *matcher);
            /*do blending*/
            void doBlending();
            void reset();
            void release();
            void test();
            ~MotionTrack();
    };
}
#endif

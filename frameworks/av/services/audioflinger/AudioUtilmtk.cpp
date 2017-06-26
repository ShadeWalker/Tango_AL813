/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

#define LOG_TAG "AudioUtilmtk"
#include"AudioUtilmtk.h"

#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <signal.h>
#include <sys/time.h>
#include <sys/resource.h>

#include <cutils/xlog.h>
#include <cutils/properties.h>

#include <utils/Vector.h>
#include <utils/SortedVector.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>

namespace android {

// Audio Dump Thread B

struct WAVEFORMATEX { 
    uint16_t wFormatTag; 
    uint16_t nChannels; 
    uint32_t nSamplesPerSec; 
    uint32_t nAvgBytesPerSec; 
    uint16_t nBlockAlign; 
    uint16_t wBitsPerSample; 
    uint16_t cbSize;
};

struct WavFormatHeader
{    	
    char        ckID[5];  		// 4 : Chunk ID: "RIFF"
    uint32_t    cksize;			// 4: Chunk size: File length - 8
    char        WAVEID[5];		// 4: WAVE ID: "WAVE"
    // Format Chunk
    char        FormatckID[5];	// 4: "fmt "
    uint32_t    Formatcksize;     // 4: Chunk size: 16 or 18 or 40 ( We will use 18, no extensiable format. )            
	char        DataID[5];		// 4: "data"
	uint32_t    Datacksize;		// 4: Chunk size: Data Size
	WAVEFORMATEX WaveFormatEx;		

	WavFormatHeader(): 
			            cksize(0),						   
						Formatcksize(18),						   
						Datacksize(0)
	{
		strcpy( ckID,		"RIFF" );
		strcpy( WAVEID,		"WAVE" );
		strcpy( FormatckID, "fmt " );
		strcpy( DataID,		"data" );
	}
};

// This structure will write to .Header file
struct AudioDumpFileInfo 
{
    audio_format_t format;
    uint32_t sampleRate;
    uint32_t channelCount;
    int size;

    AudioDumpFileInfo() 
    {
        format = AUDIO_FORMAT_INVALID;
        sampleRate = 0;
        channelCount = 0;
        size = 0;
    }
};

struct AudioDumpBuffer
{
    void *pBufBase;
    int  changeCount; ///< This will deside the file name number.
    AudioDumpFileInfo fileInfo;

    AudioDumpBuffer()
    {
        changeCount = 0;
    }
};

pthread_t hAudioDumpThread = 0;
pthread_cond_t  AudioDataNotifyEvent;
pthread_mutex_t AudioDataNotifyMutex;

Mutex mAudioDumpMutex; 
int   mHeaderUpdated = 0;

KeyedVector<String8, Vector<AudioDumpBuffer *>* > mAudioDumpFileVector; ///< The queue buffer waiting for write
///< The first element of Vector<AudioDumpBuffer *> is the previous buffer info.

uint32_t mAudioDumpSleepTime = 2;

FILE* fopen_rb( String8 filePath )
{
    FILE * fp= fopen( filePath.string(), "rb+" );

    if( fp == NULL )
    {
        ALOGV("fopen_rb() file(%s) rb+ fail, open with ab+", filePath.string() );
        fp= fopen(filePath.string(), "ab+");
    }

    if( fp == NULL )
    {     
        ALOGE("fopen_rb() file(%s) fail", filePath.string() );
        return NULL;
    }

    return fp;
}

void UpdateWaveHeader( FILE *fp, AudioDumpBuffer audioBuffer )
{   
    WavFormatHeader wavHeader;

    fseek( fp, 0, SEEK_END );
    if ( ftell(fp) == 0 )
    {                
    	if ( audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_SUB_FLOAT )
    	{
    		wavHeader.WaveFormatEx.wFormatTag = 3; // IEEE Float
    		wavHeader.WaveFormatEx.wBitsPerSample = 32;
    	}else
    	{
    		wavHeader.WaveFormatEx.wFormatTag = 1; // PCM

    		if ( audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_SUB_8_BIT )
    		{
    			wavHeader.WaveFormatEx.wBitsPerSample = 8;
    		}
    		else if ( audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_16_BIT )
    		{
    			wavHeader.WaveFormatEx.wBitsPerSample = 16;
    		}
    		else if ( audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_SUB_24_BIT_PACKED )
    		{
    			wavHeader.WaveFormatEx.wBitsPerSample = 24;
    		}
    		else if ( audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_SUB_8_24_BIT ||
    		          audioBuffer.fileInfo.format == AUDIO_FORMAT_PCM_SUB_32_BIT )
    		{
    			wavHeader.WaveFormatEx.wBitsPerSample = 32;
    		}
    	}
    	wavHeader.cksize = audioBuffer.fileInfo.size + 38; // 46 - 8
    	wavHeader.Datacksize = audioBuffer.fileInfo.size;
    	wavHeader.WaveFormatEx.nChannels		= audioBuffer.fileInfo.channelCount;
    	wavHeader.WaveFormatEx.nSamplesPerSec	= audioBuffer.fileInfo.sampleRate;
    	wavHeader.WaveFormatEx.nAvgBytesPerSec	= wavHeader.WaveFormatEx.nSamplesPerSec * wavHeader.WaveFormatEx.nChannels * wavHeader.WaveFormatEx.wBitsPerSample / 8;
    	wavHeader.WaveFormatEx.nBlockAlign		= wavHeader.WaveFormatEx.nChannels * wavHeader.WaveFormatEx.wBitsPerSample / 8;
    	wavHeader.WaveFormatEx.cbSize			= 0;

        //ALOGD("WaveHeader wavHeader.WaveFormatEx(%d)", sizeof(WAVEFORMATEX) );    		
    	fwrite( &wavHeader.ckID,			4, 1, fp );
    	fwrite( &wavHeader.cksize,			4, 1, fp );
    	fwrite( &wavHeader.WAVEID,			4, 1, fp );
    	fwrite( &wavHeader.FormatckID,		4, 1, fp );
    	fwrite( &wavHeader.Formatcksize,	4, 1, fp );
    	//fwrite( &wavHeader.WaveFormatEx,	sizeof(WAVEFORMATEX), 1, fp );//18, sizeof(WAVEFORMATEX) = 20. So can't use this line.
    	fwrite( &wavHeader.WaveFormatEx,	18, 1, fp );//18
    	fwrite( &wavHeader.DataID,			4, 1, fp );
    	fwrite( &wavHeader.Datacksize,		4, 1, fp );
	}
	else
	{
	    //ALOGD("UpdateWaveHeader() wavHeader.cksize(%u), wavHeader.Datacksize(%u), audioBuffer.fileInfo.size(%u)",
	    //    wavHeader.cksize, wavHeader.Datacksize, audioBuffer.fileInfo.size);
        fseek( fp, 4, SEEK_SET);
        fread( &wavHeader.cksize, 4, 1, fp );
        wavHeader.cksize += audioBuffer.fileInfo.size;
        fseek( fp, 4, SEEK_SET);
        fwrite( &wavHeader.cksize, 4, 1, fp );

        fseek( fp, 42, SEEK_SET);
        fread( &wavHeader.Datacksize, 4, 1, fp );
        wavHeader.Datacksize += audioBuffer.fileInfo.size;
        fseek( fp, 42, SEEK_SET);
        fwrite( &wavHeader.Datacksize, 4, 1, fp );        
	}
}

void UpdateWaveHeader_f( String8 filePath, AudioDumpBuffer audioBuffer )
{
    FILE *fp = fopen_rb(filePath);
    if ( fp != NULL )
    {
        //ALOGD("UpdateWaveHeader_f START file(%s)", filePath.string());
        UpdateWaveHeader( fp, audioBuffer );
        fclose( fp );
    }
}

void WiteAudioBuffer( FILE *fp, AudioDumpBuffer audioBuffer )
{
    fwrite( audioBuffer.pBufBase, audioBuffer.fileInfo.size, 1, fp );
    free( audioBuffer.pBufBase );
    audioBuffer.pBufBase = NULL;
}

/**
 * @brief Update .pcm header file.
 *
 * @param updateSize true: Update the last header buffer size if the format is the same.
 *                                     If the data buffer is not written to .pcm, set this parameter to fasle.
 * @return return the times of format changing.
 */
void WiteAudioHeader( String8 filePath, AudioDumpBuffer audioBuffer )
{
    FILE * fp= fopen_rb(filePath);

    if( fp == NULL )
    {     
        return;
    }
    
    fseek ( fp, 0, SEEK_END);
    long fileLength = ftell (fp);
    
    if ( fileLength == 0 ) // Empty file
    {
        fwrite( &(audioBuffer.fileInfo), sizeof(AudioDumpFileInfo), 1, fp );        
        fclose( fp );
        // Write OK. return.      
        return;
    }

    if ( fileLength < sizeof(AudioDumpFileInfo) )
    {
        ALOGE("WiteAudioHeader() the Header file length is not correct!");
        fclose( fp );
        return;
    }
    
    // Get the last info
    AudioDumpFileInfo lastFileInfo;
    fseek (fp, fileLength - sizeof(AudioDumpFileInfo), SEEK_SET);
    fread ( &lastFileInfo, sizeof(AudioDumpFileInfo), 1, fp );

    //ALOGD("WiteAudioHeader() lastFileInfo(%d, %d, %d), audioBuffer.fileInfo(%d, %d, %d)",
    //    lastFileInfo.format, lastFileInfo.sampleRate, lastFileInfo.channelCount,
    //    audioBuffer.fileInfo.format, audioBuffer.fileInfo.sampleRate, audioBuffer.fileInfo.channelCount);

    if ( memcmp( &(audioBuffer.fileInfo), &lastFileInfo, 
         sizeof(AudioDumpFileInfo) - sizeof(int) ) == 0 ) // Compare all except 'size';
    //if ( audioBuffer.fileInfo.format == lastFileInfo.format &&
    //     audioBuffer.fileInfo.sampleRate == lastFileInfo.sampleRate &&
    //     audioBuffer.fileInfo.channelCount == lastFileInfo.channelCount )
    {
        // The format is the same, update the last info.
        fseek (fp, fileLength - sizeof(AudioDumpFileInfo), SEEK_SET);
        lastFileInfo.size += audioBuffer.fileInfo.size;
        fwrite( &(lastFileInfo), sizeof(AudioDumpFileInfo), 1, fp );     
    }
    else
    {
        // Add new info
        fwrite( &(audioBuffer.fileInfo), sizeof(AudioDumpFileInfo), 1, fp );
    }     
    fclose( fp );    
    return;
}

void UpdateAllWaveHeader()
{
    mAudioDumpMutex.lock();
    int FileVectorSize = mAudioDumpFileVector.size();
    for ( size_t i = 0; i < FileVectorSize; i ++ )        
    {
        Vector<AudioDumpBuffer *> *pDumpBufferVector = mAudioDumpFileVector.valueAt(i);

        int BufferVectorSize = (*pDumpBufferVector).size();

        if ( BufferVectorSize == 1 ) // Only Header info.
        {                
            String8 filePathPCM = mAudioDumpFileVector.keyAt(i);
            String8 filePathHeader = filePathPCM;   
            String8 filePathWav = filePathPCM; 
            filePathHeader.append( ".header" );

            AudioDumpBuffer *pLastBufferInfo = (*pDumpBufferVector)[0];
            if ( pLastBufferInfo->changeCount == 0 )
            {
                filePathWav = String8::format("%s.wav", filePathPCM.string() );
            }
            else
            {
                filePathWav = String8::format("%s.%d.wav", filePathPCM.string(), pLastBufferInfo->changeCount );
            }

            WiteAudioHeader( filePathHeader, *pLastBufferInfo ); 
            UpdateWaveHeader_f( filePathWav, *pLastBufferInfo );

            (*pDumpBufferVector).removeAt(0);
            delete pLastBufferInfo;
            pLastBufferInfo = NULL;

            mAudioDumpFileVector.removeItem(filePathPCM);
            delete pDumpBufferVector;
            pDumpBufferVector = NULL;

            break; // Break here to prevent mAudioDumpMutex lock too long.
        }
    }        
	mAudioDumpMutex.unlock();
}

void *AudioDumpThread(void *arg)
{    
    bool bHasdata = false;
    int iNoDataCount = 0;
    while ( 1 )
    {
        mAudioDumpMutex.lock();
        int FileVectorSize = mAudioDumpFileVector.size();
        mAudioDumpMutex.unlock();
        
        bHasdata = false;

        for (size_t i = 0; i < FileVectorSize; i++)
        {
            mAudioDumpMutex.lock();
            String8 filePathPCM = mAudioDumpFileVector.keyAt(i);
            Vector<AudioDumpBuffer *>* pvector = (mAudioDumpFileVector.valueAt(i));
            int BufferVectorSize = (*pvector).size();
            mAudioDumpMutex.unlock();
            
            if (BufferVectorSize > 1)            
            {
                bHasdata = true;
                FILE * fpWav = NULL;

                // Open wav and header file.                
                String8 filePathHeader = filePathPCM;   
                String8 filePathWav = filePathPCM; 
                filePathHeader.append( ".header" );

                AudioDumpBuffer *pLastBufferInfo = (*pvector)[0];
                if ( pLastBufferInfo->changeCount == 0 )
                {
                    filePathWav = String8::format("%s.wav", filePathPCM.string() );
                }
                else
                {
                    filePathWav = String8::format("%s.%d.wav", filePathPCM.string(), pLastBufferInfo->changeCount );
                }
                int ret = AudioDump::checkPath(filePathWav.string()); // Create folder
	            if(ret<0)
	            {
		            XLOGE("dump %s fail!!!", filePathWav.string());
		            continue;
	            }

                while( BufferVectorSize > 1 )
                {                
                    AudioDumpBuffer *pAudioBuffer = (*pvector)[1];     
                    if ( pAudioBuffer == NULL || pLastBufferInfo == NULL )
                    {
                        ALOGE("AudioDumpThread null buffer error!!!!");
                        break;
                    }
                                       
                    if ( memcmp( &(pAudioBuffer->fileInfo), &(pLastBufferInfo->fileInfo), 
                         sizeof(AudioDumpFileInfo) - sizeof(int) ) != 0 )
                    {                                        
                        // If the format is changed
                        WiteAudioHeader( filePathHeader, *pLastBufferInfo ); 
                        if ( fpWav != NULL )
                        {
                            UpdateWaveHeader( fpWav, *pLastBufferInfo );
                        }
                        else
                        {
                            UpdateWaveHeader_f( filePathWav, *pLastBufferInfo );
                        }
                        int changeCount = pLastBufferInfo->changeCount + 1;
                        memcpy( pLastBufferInfo, pAudioBuffer, sizeof( AudioDumpBuffer ) );
                        pLastBufferInfo->changeCount = changeCount;
                        pLastBufferInfo->fileInfo.size = 0;

                        filePathWav = String8::format("%s.%d.wav", filePathPCM.string(), pLastBufferInfo->changeCount );

                        if ( fpWav != NULL )
                        {
                            fclose( fpWav );
                            fpWav = NULL;
                        }
                    }

                    if ( fpWav == NULL )
                    {
                        fpWav = fopen(filePathWav.string(), "ab+");
                                              		    
            		    if ( fpWav != NULL )
            		    {
            		        if ( fseek ( fpWav, 0, SEEK_END ) == 0 )
            		        {
                		        if ( ftell (fpWav) == 0 )
                		        {
                		            // Write Header            		            
                		            UpdateWaveHeader( fpWav, *pLastBufferInfo );
                		        }
            		        }
            		        else
            		        {
            		            int error = ferror( fpWav );   
            		            ALOGE("Create New Header fpWav(%s) seek error(%d)",  filePathWav.string(), error );
            		        }    
            		    }
            		}   

            		if ( fpWav != NULL )
            		{
            		    WiteAudioBuffer( fpWav, *pAudioBuffer );
            		    pLastBufferInfo->fileInfo.size += pAudioBuffer->fileInfo.size;
                        mAudioDumpMutex.lock();
                        (*pvector).removeAt(1);
                        mAudioDumpMutex.unlock();
                        delete pAudioBuffer;
                        pAudioBuffer = NULL;
                        BufferVectorSize --;        	                        
                    }
                    else
                    {
                        ALOGE("AudioDumpThread no fpWav(%s) error", filePathWav.string() );
                    }
        	    }

        	    if ( fpWav != NULL )
                {
                    fclose(fpWav);
                    fpWav = NULL;
                }            	            	    
            }
        }
        
        if (!bHasdata)
        {
            iNoDataCount++;
            if (iNoDataCount >= 20) // wait 200ms
            {
                UpdateAllWaveHeader();
            }
            if (iNoDataCount >= 1000)
            {                
                mAudioDumpSleepTime = -1;
                //ALOGD("AudioDumpThread, wait for new data dump\n");
                pthread_mutex_lock(&AudioDataNotifyMutex);
                pthread_cond_wait(&AudioDataNotifyEvent, &AudioDataNotifyMutex);
                pthread_mutex_unlock(&AudioDataNotifyMutex);
                //ALOGD("AudioDumpThread, PCM data dump again\n");
            }
            else
            {
                mAudioDumpSleepTime = 10;
                usleep(mAudioDumpSleepTime * 1000);
            }
        }
        else
        {
            iNoDataCount = 0;
            mAudioDumpSleepTime = 2;
            usleep(mAudioDumpSleepTime * 1000);
        }
    }

    ALOGD("AudioDumpThread exit hAudioDumpThread=%u", hAudioDumpThread);
    hAudioDumpThread = 0;
    pthread_exit(NULL);
    return 0;
}
// Audio Dump Thread E

//class  AudioDump
void AudioDump::dump(const char * filepath, void * buffer, int count,const char * property)
{
	char value[PROPERTY_VALUE_MAX];
	int ret;
	property_get(property, value, "0");
	int bflag=atoi(value);
	if(bflag)
	{
	   ret = checkPath(filepath);
	   if(ret<0)
	   {
		   XLOGE("dump fail!!!");
	   }
	   else
	   {
		 FILE * fp= fopen(filepath, "ab+");
		 if(fp!=NULL)
		 {
			 fwrite(buffer,1,count,fp);
			 fclose(fp);
		 }
		 else
		 {
			 XLOGE("dump %s fail",property);
		 }
	   }
	}
}

void AudioDump::threadDump(const char * path, void * buffer, int count,const char * property,
		                         audio_format_t format, uint32_t sampleRate, uint32_t channelCount )
{
  	char value[PROPERTY_VALUE_MAX];
	property_get(property, value, "0");
	int bflag=atoi(value);
	if(!bflag)
	{
	    return;
	}
    

    if (hAudioDumpThread == NULL)
    {
        //create PCM data dump thread here
        int ret;
        ret = pthread_create(&hAudioDumpThread, NULL, AudioDumpThread, NULL);
        if (ret != 0)
        {
            ALOGE("hAudioDumpThread create fail!!!");
        }
        else
        {
            ALOGD("hAudioDumpThread=%p created", hAudioDumpThread);
        }
        ret = pthread_cond_init(&AudioDataNotifyEvent, NULL);
        if (ret != 0)
        {
            ALOGE("AudioDataNotifyEvent create fail!!!");
        }

        ret = pthread_mutex_init(&AudioDataNotifyMutex, NULL);
        if (ret != 0)
        {
            ALOGE("AudioDataNotifyMutex create fail!!!");
        }
    }

    ALOGD("threadDump() %s(%d)", path, count); // For dump analysis
    pushBufferInfo( path, buffer, count, format, sampleRate, channelCount );    
}

int AudioDump::checkPath(const char * path)
{
	char tmp[PATH_MAX];
	int i = 0;

	while(*path)
	{
		tmp[i] = *path;

		if(*path == '/' && i)
		{
			tmp[i] = '\0';
			if(access(tmp, F_OK) != 0)
			{
				if(mkdir(tmp, 0770) == -1)
				{
					XLOGE("mkdir error! %s",(char*)strerror(errno));
					return -1;
				}
			}
			tmp[i] = '/';
		}
		i++;
		path++;
	}
	return 0;
}

void AudioDump::pushBufferInfo(const char * path, void * buffer, int count,
                                    audio_format_t format, uint32_t sampleRate, uint32_t channelCount)
{
    if ( buffer != NULL && count > 0 )
    {
        AudioDumpBuffer *newInBuffer = new AudioDumpBuffer;
        newInBuffer->pBufBase = (short *) malloc(count);
        memcpy(newInBuffer->pBufBase, buffer, count);
        newInBuffer->fileInfo.format = format;
        newInBuffer->fileInfo.sampleRate = sampleRate;
        newInBuffer->fileInfo.channelCount = channelCount;
        newInBuffer->fileInfo.size = count; 
            
        Vector<AudioDumpBuffer *> *pDumpBufferVector = NULL;
        String8 filePath(path);

        mAudioDumpMutex.lock();    
        ssize_t index = mAudioDumpFileVector.indexOfKey( filePath );        
        if ( index < 0 ) // new add
        {
            pDumpBufferVector = new Vector<AudioDumpBuffer *>;
            mAudioDumpFileVector.add(filePath, pDumpBufferVector);
        }
        else
        {
            pDumpBufferVector = mAudioDumpFileVector.valueAt( index );
        }

        if ( pDumpBufferVector->size() == 0 ) // No previous buffer info
        {
            AudioDumpBuffer *lastInfoBuffer = new AudioDumpBuffer();
            
            // Try to read previous info from .header file
            String8 filePathHeader = filePath;    
            filePathHeader.append( ".header" );

            FILE * fp= fopen( filePathHeader.string(), "rb+" );
            if( fp != NULL )
            {
                fseek ( fp, 0, SEEK_END);
                long fileLength = ftell (fp);

                if ( fileLength >= sizeof(AudioDumpFileInfo) )
                {
                    // Get the last info                    
                    fseek (fp, fileLength - sizeof(AudioDumpFileInfo), SEEK_SET);
                    fread ( &(lastInfoBuffer->fileInfo), sizeof(AudioDumpFileInfo), 1, fp );
                    lastInfoBuffer->pBufBase = NULL;   // We don't care this parameter in last info.
                    lastInfoBuffer->fileInfo.size = 0; // The size that had writtn to file. Set to 0 here since we havn't write anything. 
                    lastInfoBuffer->changeCount = fileLength / sizeof(AudioDumpFileInfo) - 1; 
                    pDumpBufferVector->add( lastInfoBuffer );
                }
                fclose( fp );
                fp = NULL;
            }

            if ( pDumpBufferVector->size() == 0 ) // If we can't read previous info from .header file
            {
                // Create a new one.             
                memcpy( lastInfoBuffer, newInBuffer, sizeof( AudioDumpBuffer ) );
                lastInfoBuffer->pBufBase = NULL;   // We don't care this parameter in last info.
                lastInfoBuffer->fileInfo.size = 0; // The size that had writtn to file. Set to 0 here since we havn't write anything. 
                pDumpBufferVector->add( lastInfoBuffer );
            }      
        } 
        pDumpBufferVector->add( newInBuffer );
        mHeaderUpdated = 0;
        mAudioDumpMutex.unlock();

        if (mAudioDumpSleepTime == -1) //need to send event
        {
            pthread_mutex_lock(&AudioDataNotifyMutex);
            pthread_cond_signal(&AudioDataNotifyEvent);
            pthread_mutex_unlock(&AudioDataNotifyMutex);
        }                                
    }
} 
// class hw

bool HwFSync::mUnderflow =false;

HwFSync::HwFSync()
    :mFd(-1)
{
}

HwFSync::~HwFSync()
{
    if(mFd != -1)
    {
        ::close(mFd);
        mFd = -1;
    }
}

void HwFSync::setFsync()
{
    if(mFd == -1){
        mFd = ::open("/dev/eac", O_RDWR);
        XLOGW("mfd =%d",mFd);
       if(mFd < 0)
       {
           XLOGE("setFsync fail to open eac");
       }
    }
    if(mFd >= 0 )
    {
        XLOGD("callback hw setFSync");
        memset(&action, 0, sizeof(action));
        action.sa_handler = callback;
        action.sa_flags = 0;
        sigaction(SIGIO, &action, NULL); //set up async handler
        fcntl(mFd, F_SETOWN, gettid()); //enable async notification
        fcntl(mFd, F_SETFL, fcntl(mFd, F_GETFL) | FASYNC|FD_CLOEXEC);
    }
}

//do  not use mutex to protect this value, use atomic if needed.
bool HwFSync::underflow()
{
   return mUnderflow;
}

 void HwFSync::callback(int signal)
{
    XLOGD("callback");
    if (signal==SIGIO)
    {
        mUnderflow = true;
        XLOGD("callback hw is under flow");
    }
}

void HwFSync::reset()
{
   mUnderflow = false;
}

void setCPU_MIN_Freq(const char *pfreq)
{
    FILE *fp= fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq", "w");
    if(fp!=NULL)
    {
        fputs(pfreq,fp);
        fclose(fp);
    }
    else
    {
        XLOGE("Can't open /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq");
    }
}

}













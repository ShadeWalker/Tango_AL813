#ifndef SUB_PARSER_H_
#define SUB_PARSER_H_

#include <sys/mman.h>
#include <fcntl.h>

#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <utils/Compat.h>  // off64_t

#include "VOBSubtitleParser.h"
namespace android
{

class SUBParser
{
public:
    
    enum SUB_PARSE_STATE_E
    {
        SUB_PARSE_DONE,
        SUB_PARSE_NEXTLOOP,
        SUB_PARSE_FAIL,
    };

    struct SUBTITLE_PACKET_DATA
    {
        void * m_pvSubtitlePacketBuffer;         //Data Buffer
        int m_iLength;                           //Buffer Length
        int m_iCurrentOffset;                    //Current Offset in Buffer
    };

    
    static const int MPEG2_PACKET_START_CODE_LENGTH = 14;
    static const int MPEG2_PES_START_CODE_LENGTH = 3;
    static const int MPEG2_PES_STREAM_ID_LENGTH = 1;
    static const int MPEG2_PES_SIZE_FLAG_LENGTH = 2;
    static const int MPEG2_PES_STRM_LANG_FLAG_LENGTH = 1;
    
public:
    SUBParser();
    SUBParser(const char * uri);
    ~SUBParser();
    int parse(int offset);
    SUB_PARSE_STATE_E mParse(int & i4Offset);
    unsigned int readBigEndian(char *pByte, int bytesCount);
    SUBTITLE_PACKET_DATA m_rSpData;
    int iGetFileIdx();
    void incFileIdx();
    int iGetStartTime();
    int iGetSubtitleWidth();
    int iGetSubtitleHeight();
    int iGetBeginTime();
    int iGetEndTime();
    void vSetVOBPalette(const VOB_SUB_PALETTE palette);
    bool fgIsBufferReady();
private:
    FILE *mSUBFile;
    VOBSubtitleParser * m_SpParser;
};

};
#endif

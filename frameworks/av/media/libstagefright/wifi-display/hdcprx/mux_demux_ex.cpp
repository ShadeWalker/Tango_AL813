#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include "mux_demux.h"
#include "ts_ex.h"

//#include "hdcp2x_ext.h"
#define LOG_TAG "[HDCP2.X RX]"
#include <utils/Log.h>

//namespace android {

// to enable or disable the debug messages of this source file, put 1 or 0 below
#ifdef DEBUG_CRYPTO_SPU
#define LOCALDBG ENABLE
#else
#define LOCALDBG DISABLE
#endif

#define HDCP_DWORD	0x48444350 // 'HDCP'

#define min(a,b) (((a) < (b)) ? (a) : (b))

#define ENABLE_HDCP2X_RX 1
#define MTK_LARGE_BUF_DEC 1

#if MTK_LARGE_BUF_DEC
typedef struct
{
    //int used;
    long  dst_idx;
    unsigned long  work_idx;
    unsigned long  size;
    unsigned long  streamCtr;
    unsigned long long inputCtr;
} _blockInfo_t;
#endif

#ifdef __ANDROID__
#include "jni.h"
#include <utils/Log.h>

#define hdcp2x_parser_log(x...) \
    do { \
        if (1)\
        { \
            ALOGD(" " x); \
        } \
    } while(0)

#endif

#define TS_UNIT_NUM 7*10

typedef struct _ParserData
{
    unsigned long		ulHead;
    unsigned long		ulStreamCtrV1;
    unsigned long		ulStreamCtrA1;

	unsigned short      m_bHasHDCPDataV;
    unsigned short      m_bHasHDCPDataA;
	
    unsigned short      m_bHDCPV;
    unsigned short      m_bHDCPA;

    unsigned long long   ullInputCounterV;
    unsigned long long   ullInputCounterA;

    unsigned short      usVidPesID;
    unsigned short      usAudPesID;

    unsigned long long   ullInputCounter;
    unsigned long        ulStreamCtr;

    unsigned short       PMT_PID;
    unsigned short       PCR_PID;
    unsigned short       usVID_PID; // use default //(unsigned short)-1;
    unsigned short       usAUD_PID; // use default //(unsigned short)-1;
    
    int                 bInfoIdx;
    int                 workBufIdx;
    unsigned long       ulFrameSize;

    int                 bInfoIdxA;
    int                 workBufIdxA;
    unsigned long       ulFrameSizeA;
    
    unsigned long		ulEnd;

    _blockInfo_t        bInfo[TS_UNIT_NUM];
    _blockInfo_t        bInfoA[TS_UNIT_NUM];

    unsigned char       workBuf[TS_PACKET_SIZE*TS_UNIT_NUM]; // 4512*4
    unsigned char       workBufA[TS_PACKET_SIZE*TS_UNIT_NUM]; // 4512*4

    // for non-16 bytes align case
    int boundary_data;
    unsigned long long boundary_inputCtr;
    unsigned long boundary_streamCtr;
    int boundary_dataA;
    unsigned long long boundary_inputCtrA;
    unsigned long boundary_streamCtrA;

	DECRYPT_CB_T mDecryptCallBack=NULL;
    unsigned long		ulEnd2;
} ParserData;

ParserData gPData;


uint32_t demux_decrypt_ex(unsigned char ucSrcTs[TS_PACKET_SIZE], unsigned char ucDstTs[TS_PACKET_SIZE], unsigned char *pDst, int blockSize);

uint32_t hdcp2x_parser_reg_callback(DECRYPT_CB_T pFnDecrypt)
{
	gPData.mDecryptCallBack = pFnDecrypt;
	hdcp2x_parser_log("hdcp2x_parser_reg_callback %p=%p", gPData.mDecryptCallBack, pFnDecrypt);
	return 0;
}

/*
static void _DumpHex(const char *hint, const unsigned char *data, int len)
{
    int pos = 0;
    
    if (!data || !len)
    {
        return;
    }

	
	if (len==4) {
		ALOGD("Dumping %s: %02x %02x %02x %02x ", hint, data[0], data[1], data[2], data[3]);
	} else {
        
        while(pos < len)
        {
            
			ALOGD("Dumping %s: %02x %02x %02x %02x %02x %02x %02x %02x ", 
                                hint, 
                                data[pos+0], data[pos+1], data[pos+2], data[pos+3],
                                data[pos+4], data[pos+5], data[pos+6], data[pos+7]);
            
            pos += 8;
        }
        
    }	
}
*/
int hdcp2x_parser_init()
{
    gPData.ulHead=1111;
    gPData.ulStreamCtrV1=0;
    gPData.ulStreamCtrA1=0;
	gPData.m_bHasHDCPDataV=0;
	gPData.m_bHasHDCPDataA=0;
    gPData.m_bHDCPV=0;
    gPData.m_bHDCPA=0;
    gPData.usVidPesID=0;
    gPData.usAudPesID=0;
    gPData.ullInputCounterV=0;
    gPData.ullInputCounterA=0;

    gPData.ullInputCounter=0;
    gPData.ulStreamCtr=0;

    gPData.PMT_PID=0;
    gPData.PCR_PID=0;
    gPData.usVID_PID=0; // use default //(unsigned short)-1;
    gPData.usAUD_PID=0; // use default //(unsigned short)-1;
    

    gPData.boundary_data=-1;
    gPData.boundary_inputCtr=0;
    gPData.boundary_streamCtr=0;
    gPData.boundary_dataA=-1;
    gPData.boundary_inputCtrA=0;
    gPData.boundary_streamCtrA=0;
    
    gPData.ulEnd=2222;

    hdcp2x_parser_log("[DEBUG] enter %s TS_UNIT_NUM=%d \n",__FUNCTION__, TS_UNIT_NUM);
    hdcp2x_parser_log("[DEBUG] 0x%08x 0x%08x 0x%08x 0x%08x - 0x%08x\n", (int)&gPData.ulHead, (int)&gPData.ulStreamCtrV1, (int)&gPData.ulStreamCtrA1, (int)&gPData.ulEnd, (int)&gPData.ulEnd2);
    
    return 0;
}

int hdcp2x_parser_uninit()
{
    return 0;
}

int hdcp2x_boundary_chk_video()
{
    if (gPData.boundary_data != -1)
    {
        unsigned char dummy[16];
        memset(dummy, 0x00, 16);

        if (gPData.boundary_data>0)
            memcpy(&gPData.workBuf[0], dummy, gPData.boundary_data);

        /* update point information */
        gPData.bInfo[0].size = gPData.boundary_data; // maybe zero, it is 16 byte align case, only need deal with inputCtr.
        gPData.bInfo[0].work_idx = 0;
        gPData.bInfo[0].dst_idx = -1; // -1 for dummy, don't copy
        gPData.bInfo[0].streamCtr = gPData.boundary_streamCtr;
        gPData.bInfo[0].inputCtr = gPData.boundary_inputCtr;
        //bInfo[0].used = 1;
        
        gPData.workBufIdx = gPData.boundary_data;
        gPData.bInfoIdx = 1;

		
        gPData.ulFrameSize = gPData.boundary_data;
        
		
        //printf("[DEBUG] has previous dummy data, size:%d streamCtr:%lu inputCtr:%llu \n", boundary_data, boundary_streamCtr, boundary_inputCtr);

        // clear boundary information
        // mark it, some case, whole stream maybe audio, so this video information must keep to next video packet coming...
        gPData.boundary_data=-1;
    }
    return 0;
}

int hdcp2x_boundary_chk_audio()
{
    if (gPData.boundary_dataA != -1)
    {
        unsigned char dummy[16];
        memset(dummy, 0x00, 16);

        if (gPData.boundary_dataA>0)
            memcpy(&gPData.workBufA[0], dummy, gPData.boundary_dataA);

        /* update point information */
        gPData.bInfoA[0].size = gPData.boundary_dataA; // maybe zero, it is 16 byte align case, only need deal with inputCtr.
        gPData.bInfoA[0].work_idx = 0;
        gPData.bInfoA[0].dst_idx = -1; // -1 for dummy, don't copy
        gPData.bInfoA[0].streamCtr = gPData.boundary_streamCtrA;
        gPData.bInfoA[0].inputCtr = gPData.boundary_inputCtrA;
        //bInfoA[0].used = 1;
        
        gPData.workBufIdxA = gPData.boundary_dataA;
        gPData.bInfoIdxA = 1;

        gPData.ulFrameSizeA = gPData.boundary_dataA;
            
        //printf("[DEBUG] has previous dummy data, size:%d streamCtr:%lu inputCtr:%llu \n", boundary_data, boundary_streamCtr, boundary_inputCtr);

        // clear boundary information
        // mark it, some case, whole stream maybe audio, so this video information must keep to next video packet coming...
        gPData.boundary_dataA=-1;
    }
    return 0;
}


uint32_t hdcp2x_parser_ts_decrypt
    ( unsigned char *ucSrcTs ///< [in]  pointer to TS packet to decrypt
    , unsigned char *ucDstTs ///< [out] pointer to buffer to hold decrypted TS packet
    , int dataLen            ///<[in] data length of TS packet for input and output
    )
{
    int count=0;
    
    if (ucSrcTs==NULL ||
        ucDstTs==NULL ||
        dataLen%TS_PACKET_SIZE != 0 ||
        dataLen<TS_PACKET_SIZE )
    {
        printf("parameter has error \n");
        return 1;
    }

    //hdcp2x_parser_log("ts_decrypt enter, dataLen:%d \n", dataLen);

    count = 0;

    #if MTK_LARGE_BUF_DEC
    
    //printf("[HDCP2X] use large buffer(A/V) decrypt mechanism \n");

    for (int i=0;i<TS_UNIT_NUM;i++)
    {
        gPData.bInfo[i].dst_idx = 0;
        gPData.bInfo[i].work_idx = 0;
        gPData.bInfo[i].size = 0;
        gPData.bInfo[i].streamCtr = 0;
        gPData.bInfo[i].inputCtr = 0;
        
        gPData.bInfoA[i].dst_idx = 0;
        gPData.bInfoA[i].work_idx = 0;
        gPData.bInfoA[i].size = 0;
        gPData.bInfoA[i].streamCtr = 0;
        gPData.bInfoA[i].inputCtr = 0;
    }
    gPData.ulFrameSize = 0;
    gPData.bInfoIdx = 0;
    gPData.workBufIdx = 0;

    gPData.ulFrameSizeA = 0;
    gPData.bInfoIdxA = 0;
    gPData.workBufIdxA = 0;

    // to test structure head and end position in memory
    //hdcp2x_parser_log("[DEBUG] Head-> 0x%08x 0x%08x 0x%08x 0x%08x - 0x%08x \n", (int)&gPData.ulHead, (int)&gPData.ulStreamCtrV1, (int)&gPData.ulStreamCtrA1, (int)&gPData.ulEnd, (int)&gPData.ulEnd2);
    //hdcp2x_parser_log("[DEBUG] Video-> 0x%08x 0x%08x 0x%08x  \n", (int)&gPData.bInfoIdx, (int)&gPData.workBufIdx, (int)&gPData.ulFrameSize);
    //hdcp2x_parser_log("[DEBUG] Audio-> 0x%08x 0x%08x 0x%08x \n", (int)&gPData.bInfoIdxA, (int)&gPData.workBufIdxA, (int)&gPData.ulFrameSizeA);
    
    #else
    //printf("[HDCP2X] use ts unit to decrypt mechanism (SW) \n");
    #endif

    /* check video boundary */
    hdcp2x_boundary_chk_video();
    hdcp2x_boundary_chk_audio();

    //int n=0;
    do {
        //printf("n:%d \n", ++n);
        demux_decrypt_ex(&ucSrcTs[count], &ucDstTs[count], ucDstTs, count);
        count += TS_PACKET_SIZE;
    } while (count<dataLen);

    #if MTK_LARGE_BUF_DEC

    /* final block */
    if (gPData.ulFrameSize && gPData.workBufIdx)
    {   
        
        //hdcp2x_parser_log("[DEBUG] large buf decrypt(video), PES frame size:%d workBufIdx:%d bInfoIdx:%d bInfo[0].streamCtr:%ld bInfo[0].inputCtr:%lld remain:%d \n", 
        //            (int)gPData.ulFrameSize, gPData.workBufIdx, gPData.bInfoIdx, gPData.bInfo[0].streamCtr, gPData.bInfo[0].inputCtr, (gPData.workBufIdx)%16 );
        
        //hdcp2x_parser_log("PES frame size (video): ulFrameSizeV:%d last block(hw) \n", (int)ulFrameSizeV );
        //hdcp2x_parser_log("workBufIdx:%d bInfoIdx:%d bInfo[0].streamCtr:%ld bInfo[0].inputCtr:%lld remain:%d \n", workBufIdx, bInfoIdx, bInfo[0].streamCtr, bInfo[0].inputCtr, workBufIdx%16 );

        
        //SpuDecrypt(workBufIdx, (unsigned char*)&workBuf[0], (unsigned char*)&workBuf[0], bInfo[0].streamCtr, bInfo[0].inputCtr, &result);
        //hdcp2x_rx_decrypt(gPData.bInfo[0].streamCtr, gPData.bInfo[0].inputCtr,  (unsigned char*)&gPData.workBuf[0], gPData.workBufIdx, (unsigned char*)&gPData.workBuf[0]);
        #ifdef ENABLE_HDCP2X_RX
        //hdcp2x_rx_decrypt(gPData.boundary_streamCtr, gPData.boundary_inputCtr,  (unsigned char*)&gPData.workBuf[0], gPData.workBufIdx, (unsigned char*)&gPData.workBuf[0]);
		//_DumpHex("1:boundary_streamCtr", (unsigned char*)&gPData.boundary_streamCtr, 4);
		//_DumpHex("1:boundary_inputCtr", (unsigned char*)&gPData.boundary_inputCtr, 8);

		//hdcp2x_parser_log("[DEBUG] ready to call=%p", gPData.mDecryptCallBack);
		gPData.mDecryptCallBack(gPData.boundary_streamCtr, gPData.boundary_inputCtr,  (unsigned char*)&gPData.workBuf[0], gPData.workBufIdx, (unsigned char*)&gPData.workBuf[0]);
		//hdcp2x_parser_log("[DEBUG] go");
		#endif
        
        /* copy decrypted-data to dst buffer */
        for (int i=0;i<gPData.bInfoIdx;i++)
        {
            //#if MTK_LARGE_BUF_LOG
            //hdcp2x_parser_log("copy(%d) dstIdx:%d workIdx:%d size:%d \n", i, gPData.bInfo[i].dst_idx, gPData.bInfo[i].work_idx, gPData.bInfo[i].size);
            //#endif
            
            if (gPData.bInfo[i].dst_idx>=0) // for S3 case, -1 no copy, because it is previous dummy data...
            {
                memcpy(&ucDstTs[gPData.bInfo[i].dst_idx], &gPData.workBuf[gPData.bInfo[i].work_idx], gPData.bInfo[i].size);
            }
            else
            {
                //printf("[DEBUG] dummy data case, bInfo:%d data:%d \n", i, bInfo[i].size);
            }
        }

        /* for S3 case, keep padding length and iv for continuous PES payload */
        if (gPData.ulFrameSize%16 !=0 )
        {
            // remain data need as head for next data stream
            gPData.boundary_data = gPData.ulFrameSize%16;
        }
        
        // udpate
        //_DumpHex("3:boundary_inputCtr", (unsigned char*)&gPData.boundary_inputCtr, 8);
        gPData.boundary_inputCtr = gPData.boundary_inputCtr + gPData.ulFrameSize/16;
		//_DumpHex("4:boundary_inputCtr", (unsigned char*)&gPData.boundary_inputCtr, 8);
        //printf("[DEBUG] boundary_inputCtr:%lld boundary_data:%d \n", boundary_inputCtr, boundary_data);
        
    }

    /* for audio */
    if (gPData.ulFrameSizeA && gPData.workBufIdxA)
    {
        //hdcp2x_parser_log("[DEBUG] large buf decrypt(audio), PES frame size:%d workBufIdx:%d bInfoIdx:%d bInfo[0].streamCtr:%ld bInfo[0].inputCtr:%lld remain:%d \n", 
        //            (int)gPData.ulFrameSizeA, gPData.workBufIdxA, gPData.bInfoIdxA, gPData.bInfoA[0].streamCtr, gPData.bInfoA[0].inputCtr, (gPData.workBufIdxA)%16 );
        
        //hdcp2x_parser_log("PES frame size (audio): ulFrameSizeA:%d last block(hw) \n", (int)ulFrameSizeA );
        //hdcp2x_parser_log("workBufIdx:%d bInfoIdx:%d bInfo[0].streamCtr:%ld bInfo[0].inputCtr:%lld remain:%d \n", workBufIdxA, bInfoIdxA, bInfoA[0].streamCtr, bInfoA[0].inputCtr, workBufIdxA%16 );

                  
        //SpuDecrypt(workBufIdxA, (unsigned char*)&workBufA[0], (unsigned char*)&workBufA[0], bInfoA[0].streamCtr, bInfoA[0].inputCtr, &result);
        //hdcp2x_rx_decrypt(gPData.bInfoA[0].streamCtr, gPData.bInfoA[0].inputCtr,  (unsigned char*)&gPData.workBufA[0], gPData.workBufIdxA, (unsigned char*)&gPData.workBufA[0]);
        #ifdef ENABLE_HDCP2X_RX
        //hdcp2x_rx_decrypt(gPData.boundary_streamCtrA, gPData.boundary_inputCtrA,  (unsigned char*)&gPData.workBufA[0], gPData.workBufIdxA, (unsigned char*)&gPData.workBufA[0]);
        gPData.mDecryptCallBack(gPData.boundary_streamCtrA, gPData.boundary_inputCtrA,  (unsigned char*)&gPData.workBufA[0], gPData.workBufIdxA, (unsigned char*)&gPData.workBufA[0]);
        #endif
        
        /* copy decrypted-data to dst buffer */
        for (int i=0;i<gPData.bInfoIdxA;i++)
        {
            if (gPData.bInfoA[i].dst_idx>=0) // for S3 case, -1 no copy, because it is previous dummy data...
            {
                memcpy(&ucDstTs[gPData.bInfoA[i].dst_idx], &gPData.workBufA[gPData.bInfoA[i].work_idx], gPData.bInfoA[i].size); 
            }
            else
            {
                //printf("[DEBUG] dummy data case, bInfo:%d data:%d \n", i, bInfo[i].size);
            }
            
        }

        /* for S3 case, keep padding length and iv for continuous PES payload */
        if (gPData.ulFrameSizeA%16 !=0 )
        {
            // remain data need as head for next data stream
            gPData.boundary_dataA = gPData.ulFrameSizeA%16;
        }
        
        // update
        gPData.boundary_inputCtrA = gPData.boundary_inputCtrA + gPData.ulFrameSizeA/16;
    }
    
    #endif
    
    //hdcp2x_parser_log("ts_decrypt leave, count:%d \n", count);
    
    
    return 0;
}

/**
 * Type a brief description here describing what this function does. Requires foo() to be called before use.
 *
 * @returns None
 */
/*extern "C"*/ uint32_t demux_decrypt_ex
	( unsigned char ucSrcTs[TS_PACKET_SIZE] ///< [in]  pointer to TS packet to decrypt
	, unsigned char ucDstTs[TS_PACKET_SIZE] ///< [out] pointer to buffer to hold decrypted TS packet
	, unsigned char *pDst
	, int blockSize)
{
    
    
	uint32_t result=0;
	//bool bSend = false;
	//CTSPacket tsSrc(BIT_STREAM_SRC);
	//CTSPacket tsDst(BIT_STREAM_DST);
	TSPacket(BIT_STREAM_SRC);
	TSPacket(BIT_STREAM_DST);

	// now read the PES payload
//#define BUFFER_SIZE	1024

    int			nFrame=0;
    
	//unsigned long	ulCopied=0;

	//unsigned long	format_identifier=0;

	unsigned char	sync_byte=0;
	unsigned char	transport_error_indicator=0;
	unsigned char	payload_unit_start_indicator=0;
	unsigned char	transport_priority=0;
	unsigned short	PID=0;
	unsigned char	transport_scrambling_code=0;
	unsigned char	adaptation_field_control=0;
	unsigned char	continuity_counter=0;

	unsigned short	adaptation_field_length=0;
	unsigned char	discontinuity_indicator=0;
	unsigned char	random_access_indicator=0;
	unsigned char	elementary_stream_priority_indicator=0;
	unsigned char	PCR_flag=0;
	unsigned char	OPCR_flag=0;
	unsigned char	splicing_point_flag=0;
	unsigned char	transport_private_data_flag=0;
	unsigned char	adaptation_field_extension_flag=0;
	unsigned char	pointer_field=0;

	unsigned long long	program_clock_reference_base=0;
	//unsigned long long	original_program_clock_reference_base=0;
    static unsigned char tmpLogEnc=0;
    static unsigned char tmpLogClr=0;
    
//try 
    {
        
		nFrame++;
        //hdcp2x_parser_log("[DEBUG] blockSize:%d \n", blockSize);
            
		/*tsSrc.*/
        SetBuffer(BIT_STREAM_SRC, ucSrcTs, false);
		/*tsDst.*/
        SetBuffer(BIT_STREAM_DST, ucDstTs, false/*true*/);


		// parse TS header
		/*tsSrc.*/Parse_TS_Header(BIT_STREAM_SRC,
			sync_byte,
			transport_error_indicator,
			payload_unit_start_indicator,
			transport_priority,
			PID,
			transport_scrambling_code,
			adaptation_field_control,
			continuity_counter);


		int N=184;

		//hdcp2x_parser_log("[DEBUG] PID:%d \n", PID);
		
		if (adaptation_field_control & 0x2 /*'0x10 or 0x11'*/) {
            //hdcp2x_parser_log("[DEBUG] adaptation_field_control:%d 0x2 case\n", adaptation_field_control);
            
			// second bit of adaptation_field_control indicates the presence of an adaptation_field header

			/*tsSrc.*/
            Parse_adaptation_field(
			    BIT_STREAM_SRC,
				adaptation_field_length,
				discontinuity_indicator,
				random_access_indicator,
				elementary_stream_priority_indicator,
				PCR_flag,
				OPCR_flag,
				splicing_point_flag,
				transport_private_data_flag,
				adaptation_field_extension_flag);
					

            N -= adaptation_field_length+1;
            

			if (adaptation_field_length > 0) {
				if (PCR_flag == 0x01 /*'1'*/) {
					/*tsSrc.*/
                    Parse_PCR(BIT_STREAM_SRC, program_clock_reference_base);
				}
				if (OPCR_flag == 0x01 /*'1'*/) {
					// currently unsupported
					printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
                    while(1);
                    //throw -1;
				}
				if (splicing_point_flag == 0x01 /*'1'*/) {
					// currently unsupported
					printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
					while(1);
                    //throw -1;
				}
				if (transport_private_data_flag == 0x01 /*'1'*/) {
					// currently unsupported
					printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
					while(1);
                    //throw -1;
				}
				if (adaptation_field_extension_flag == 0x01 /*'1'*/) {
					// currently unsupported
					printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
					while(1);
                    //throw -1;
				}
			// advance pointer past adaptation field padding if any
			if (0 < TS_PACKET_SIZE-N)
			{	/*tsSrc.*/
                SetPos(BIT_STREAM_SRC, TS_PACKET_SIZE-N);
                //hdcp2x_parser_log("[DEBUG] Warning, move src:%d \n", TS_PACKET_SIZE-N);
            }
			}
		}
        


		if(adaptation_field_control & 0x1 /*'0x01 or 0x11'*/) {
            //hdcp2x_parser_log("[DEBUG] adaptation_field_control:%d 0x1 case\n", adaptation_field_control);
            
			// first bit of adaptation_field_control indicates the presence of a payload
			// N bytes of data

            //hdcp2x_parser_log("[DEBUG] PID:%d \n", PID);
			if (PAT_PID == PID) {
                //hdcp2x_parser_log("[DEBUG] this is a PAT \n");
				// this is a PAT, so we can parse for the audio and video PID's

				// pointer_field (see ISO-13818-1 sec 2.4.4.1)
				if (payload_unit_start_indicator) {
					pointer_field = /*tsSrc.*/Get(BIT_STREAM_SRC, 8);
					if (pointer_field) {
						/*tsSrc.*/SetPos(BIT_STREAM_SRC, /*tsSrc.*/ByteOffset(BIT_STREAM_SRC) + pointer_field);
					}
				}

				// parse PAT
				unsigned char		table_id;
				unsigned char		section_syntax_indicator;
				unsigned short		section_length;
				unsigned short		transport_stream_id;
				unsigned char		version_number;
				unsigned char		current_next_indicator;
				unsigned char		section_number;
				unsigned char		last_section_number;

				/*tsSrc.*/
                Parse_PAT(
                    BIT_STREAM_SRC,
					table_id,
					section_syntax_indicator,
					section_length,
					transport_stream_id,
					version_number,
					current_next_indicator,
					section_number,
					last_section_number
				);

				if (0 == table_id) {
					unsigned short program_number;

					/*tsSrc.*/
                    Parse_PAT_Program(BIT_STREAM_SRC, program_number, gPData.PMT_PID);
                    //hdcp2x_parser_log("[DEBUG] PMT_PID:%d \n", gPData.PMT_PID);

					/*tsSrc.*/Get(BIT_STREAM_SRC,32); // CRC_32
				}
			} else if (0 != gPData.PMT_PID && gPData.PMT_PID == PID) {
			
                //hdcp2x_parser_log("[DEBUG] this is a PMT \n");
                // this is a PMT, so we can parse for the programs
				// and alter the PMT if necessary to include HDCP registration descriptor

				unsigned char* ucTmp=NULL;

				// pointer_field (see ISO-13818-1 sec 2.4.4.1)
				if (payload_unit_start_indicator) {
					pointer_field = /*tsSrc.*/Get(BIT_STREAM_SRC,8);
					if (0 < pointer_field) {
						ucTmp = new unsigned char[pointer_field];

						for (int i=0; i < pointer_field; i++)
							ucTmp[i] = /*tsSrc.*/Get(BIT_STREAM_SRC,8);

						delete ucTmp;
					}
				}

				unsigned char		table_id;
				unsigned char		section_syntax_indicator;
				unsigned short		section_length;
				unsigned short		program_number;
				unsigned char		version_number;
				unsigned char		current_next_indicator;
				unsigned char		section_number;
				unsigned char		last_section_number;
				unsigned short		program_info_length;

				/*tsSrc.*/
                Parse_PMT(BIT_STREAM_SRC,
					table_id,
					section_syntax_indicator,
					section_length,
					program_number,
					version_number,
					current_next_indicator,
					section_number,
					last_section_number,
                    gPData.PCR_PID,
					program_info_length
				);

				unsigned char	descriptor_tag=0;
				unsigned char	descriptor_length=0;
				unsigned short	program_info_length_unused = program_info_length;
				bool		bHDCP20=false;
                unsigned char hdcp_version=0x00;
                static char bHDCP20Warning=0;
                    
                //hdcp2x_parser_log("[DEBUG] section_length:%d program_info_length_unused:%d \n", section_length, program_info_length_unused);
				while (program_info_length_unused) {
					if (0x05 == /*tsSrc.*/Peek(BIT_STREAM_SRC,8)) {
						descriptor_tag = /*tsSrc.*/Get(BIT_STREAM_SRC,8);
						descriptor_length = /*tsSrc.*/Get(BIT_STREAM_SRC,8);
						program_info_length_unused -= descriptor_length+2;

						unsigned long format_identifier = /*tsSrc.*/Get(BIT_STREAM_SRC,32);
						/*unsigned char*/ hdcp_version = /*tsSrc.*/Get(BIT_STREAM_SRC, 8);

						if (HDCP_DWORD == format_identifier && 0x20 == hdcp_version)
							bHDCP20 = true;
					} else {
						descriptor_tag = /*tsSrc.*/Get(BIT_STREAM_SRC, 8);
						descriptor_length = /*tsSrc.*/Get(BIT_STREAM_SRC, 8);
						program_info_length_unused -= descriptor_length+2;

						for (int j=0; j < descriptor_length; j++)
							/*tsSrc.*/Get(BIT_STREAM_SRC, 8);

					}
				}

				if (!bHDCP20) {
					//H2DBGLOG((LOCALDBG, "Stream doesn't have HDCP 2.0 descriptor in PMT\n"));
                    //if (bHDCP20Warning==0)
                        //hdcp2x_parser_log("[DEBUG] Warning, Stream doesn't have HDCP 2.0 descriptor in PMT 0x%2.2x \n", hdcp_version);
					//return H2_ERROR; /* test for wifi display */
					bHDCP20Warning++;
				}
                else
                {
                    //hdcp2x_parser_log("[DEBUG] Stream have HDCP 2.0 descriptor in PMT\n");
                }

                //hdcp2x_parser_log("[DEBUG] section_length:%d program_info_length:%d \n", section_length, program_info_length);
				for (int i=0; i < section_length-13-program_info_length;) {
					unsigned char	stream_type;
					unsigned short	elementary_PID;
					unsigned short	ES_info_length;

					descriptor_tag = /*tsSrc.*/Peek(BIT_STREAM_SRC, 8);

					if (MPG2_STREAM_TYPE == descriptor_tag || AVC_STREAM_TYPE == descriptor_tag) {
						/*tsSrc.*/
                        Parse_PMT_Program_elements(BIT_STREAM_SRC, 
							stream_type,
							elementary_PID,
							ES_info_length);

                        //if (gPData.usVID_PID==0)
                            //hdcp2x_parser_log( "[DEBUG] TS, video pid:%d stream_type:0x%02x (H.264) \n", elementary_PID, descriptor_tag);
                        
                        gPData.usVID_PID = elementary_PID;
                        
                        
						for (int j=0; j < ES_info_length; j++)
							/*tsSrc.*/Get(BIT_STREAM_SRC, 8);

						i += 5 + ES_info_length;

					} else if (ADTS_STREAM_TYPE == descriptor_tag) {
						/*tsSrc.*/
                        Parse_PMT_Program_elements(BIT_STREAM_SRC, 
							stream_type,
							elementary_PID,
							ES_info_length);

                        //if (gPData.usAUD_PID==0)
                            //hdcp2x_parser_log( "[DEBUG] TS, audio pid:%d stream_type:0x%02x (AAC) \n", elementary_PID, descriptor_tag);
                        
						gPData.usAUD_PID = elementary_PID;
                       
                        

						for (int j=0; j < ES_info_length; j++)
							/*tsSrc.*/Get(BIT_STREAM_SRC, 8);

						i += 5 + ES_info_length;

					}
                    
                    /* test for wifi display */
                    else if (LPCM_STREAM_TYPE == descriptor_tag) {
						/*tsSrc.*/
                        Parse_PMT_Program_elements(BIT_STREAM_SRC, 
							stream_type,
							elementary_PID,
							ES_info_length);

                        //if (gPData.usAUD_PID==0)
                            //hdcp2x_parser_log( "[DEBUG] TS, audio pid:%d stream_type:0x%02x (LPCM) \n", elementary_PID, descriptor_tag);
                        
						gPData.usAUD_PID = elementary_PID;
                        
                        
						for (int j=0; j < ES_info_length; j++)
							/*tsSrc.*/Get(BIT_STREAM_SRC, 8);

						i += 5 + ES_info_length;

					}
                    /* test for wifi display */
                    else if (AC3_STREAM_TYPE == descriptor_tag) {
						/*tsSrc.*/
                        Parse_PMT_Program_elements(BIT_STREAM_SRC, 
							stream_type,
							elementary_PID,
							ES_info_length);

                        //if (gPData.usAUD_PID==0)
                            //hdcp2x_parser_log( "[DEBUG] TS, audio pid:%d stream_type:0x%02x (AC-3) \n", elementary_PID, descriptor_tag);
                        
						gPData.usAUD_PID = elementary_PID;
                        
                        
						for (int j=0; j < ES_info_length; j++)
							/*tsSrc.*/Get(BIT_STREAM_SRC, 8);

						i += 5 + ES_info_length;

					}
                    
                    else
                    { 
                        hdcp2x_parser_log( "[DEBUG] descriptor_tag:%d , no support check it \n", descriptor_tag);
                        while(1);
                    }
                    
				}

                //printf("directly copy src to dst (%d) \n", ++pesCount );
				memcpy(ucDstTs, ucSrcTs, TS_PACKET_SIZE);

			} else if (gPData.usVID_PID == PID ||
			    gPData.usAUD_PID == PID /* test for wifi display */) {

                //hdcp2x_parser_log("[DEBUG] PID=%d gPData.usVID_PID=%d gPData.usAUD_PID=%d \n", PID, gPData.usVID_PID, gPData.usAUD_PID);
                
				unsigned long ul = /*tsSrc.*/Peek(BIT_STREAM_SRC, 32);
				unsigned char ucPesId = (unsigned char)ul;

                /* MTK_PART */
                if (PES_STARTCODE == /*tsSrc.*/Peek(BIT_STREAM_SRC, 24)) {
                    
                    if (!gPData.usVidPesID && (0xe0 == (0xf0 & ucPesId))) {
                        gPData.usVidPesID = ucPesId;
                        hdcp2x_parser_log( "[DEBUG] PES stream_type:0x%02x (H.264) \n", gPData.usVidPesID);
                    }

                    if (!gPData.usAudPesID && (0xc0 == (0xf0 & ucPesId))) {
                        gPData.usAudPesID = ucPesId;
                        hdcp2x_parser_log( "[DEBUG] PES stream_type:0x%02x (AAC) \n", gPData.usAudPesID);
                    }

                    /* test for wifi display, to support LPCM */
                    if (!gPData.usAudPesID && (0xbd == (0xff & ucPesId))) {
                        gPData.usAudPesID = ucPesId;
                        hdcp2x_parser_log( "[DEBUG] PES stream_type:0x%02x (LPCM or AC-3) \n", gPData.usAudPesID);
                    }
                }

                //hdcp2x_parser_log("[DEBUG] usVidPesID:%d usAudPesID:%d ucPesId:%d \n", usVidPesID, usAudPesID, ucPesId);
                
				// this is video, so we need to demux and decrypt (Intel widi only encrypt video; wifi display encrypt audio & video)
				if (PES_STARTCODE == /*tsSrc.*/Peek(BIT_STREAM_SRC, 24) && ((gPData.usVidPesID == ucPesId) || (gPData.usAudPesID == ucPesId)/* test for wifi display */)) {
					// we have a PES header

                    //hdcp2x_parser_log("[DEBUG] we have a PES header \n");
                    
					unsigned char	stream_id=0;
					unsigned short	PES_packet_length=0;

					unsigned char	PES_scrambling_control=0;
					unsigned char	PES_priority=0;
					unsigned char	data_alignment_indicator=0;
					unsigned char	copyright=0;
					unsigned char	original_or_copy=0;
					unsigned char	PTS_DTS_flag=0;
					unsigned char	ESCR_flag=0;
					unsigned char	ES_rate_flag=0;
					unsigned char	DSM_trick_mode_flag=0;
					unsigned char	additional_copy_info_flag=0;
					unsigned char	PES_CRC_flag=0;
					unsigned char	PES_extension_flag=0;
					unsigned char	PES_header_data_length=0;

					unsigned long long PTS=0;
					unsigned long long DTS=0;

					/*tsSrc.*/
                    Parse_PES_packet_top(BIT_STREAM_SRC, stream_id, PES_packet_length);
                    //printf("stream_id:0x%x PES_packet_length:0x%x \n", stream_id, PES_packet_length);

					/*tsSrc.*/
                    Parse_PES_header(BIT_STREAM_SRC, 
						PES_scrambling_control,
						PES_priority,
						data_alignment_indicator,
						copyright,
						original_or_copy,
						PTS_DTS_flag,
						ESCR_flag,
						ES_rate_flag,
						DSM_trick_mode_flag,
						additional_copy_info_flag,
						PES_CRC_flag,
						PES_extension_flag,
						PES_header_data_length);

                    //hdcp2x_parser_log("PES_extension_flag:0x%x PES_header_data_length:0x%x \n", PES_extension_flag, PES_header_data_length);

					unsigned char	_PES_extension_flag=PES_extension_flag;
					unsigned char	_PES_header_data_length=PES_header_data_length;

					// receiving & decrypting
					//_PES_extension_flag = 0;
					_PES_extension_flag = PES_extension_flag; /* test for wifi display */
					//_PES_header_data_length = PES_extension_flag?(PES_header_data_length-17):PES_header_data_length;
                    _PES_header_data_length=PES_header_data_length; /* test for wifi display */
                    
					//unsigned char PES_header_length = PES_HDR_LEN + _PES_header_data_length;
					unsigned char PCR_len = (PCR_flag == 0x01)?6:0;

					// on the receiver side, we just need to add to the adaptation_field_length to
					// compensate for the PES extension we're dropping

                    /* test for wifi display */
                    //adaptation_field_length += PES_header_data_length - _PES_header_data_length + 1;

					// first copy the TS header to a destination buffer
					/*tsDst.*/
                    Add_TS_Header(BIT_STREAM_DST, 
						transport_error_indicator,
						payload_unit_start_indicator,
						transport_priority,
						PID,
						transport_scrambling_code,
						(adaptation_field_length?0x2:0) | 0x1, //adaptation_field_control
						continuity_counter);

					if (adaptation_field_length > 0) {

						// fill adaptation field
						/*tsDst.*/
                        Add_adaptation_field(BIT_STREAM_DST, 
                            adaptation_field_length, /* test for wifi display */
                            //adaptation_field_length-1, // this computed length includes itself but len defined by spec does not, so sub 1
							discontinuity_indicator,
							random_access_indicator,
							elementary_stream_priority_indicator,
							PCR_flag,
							OPCR_flag,
							splicing_point_flag,
							transport_private_data_flag,
							adaptation_field_extension_flag);
						
						if (adaptation_field_length > 0) {
							if (PCR_flag == 0x01 /*'1'*/) {
								/*tsDst.*/
                                Add_PCR(BIT_STREAM_DST, program_clock_reference_base);
							}
							if (OPCR_flag == 0x01 /*'1'*/) {
								// currently unsupported
								printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
								while(1);
                                //throw -1;
							}
							if (splicing_point_flag == 0x01 /*'1'*/) {
								// currently unsupported
								printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
								while(1);
                                //throw -1;
							}
							if (transport_private_data_flag == 0x01 /*'1'*/) {
								// currently unsupported
								printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
								while(1);
                                //throw -1;
							}
							if (adaptation_field_extension_flag == 0x01 /*'1'*/) {
								// currently unsupported
								printf("[DEBUG] mFrame:%d line:%d\n", nFrame, __LINE__);
								while(1);
                                //throw -1;
							}
						}

                        
						// padd remainder of adaptation field with 0xff
						// this code will need to change if other headers are included besides PCR
						if (0 < adaptation_field_length-PCR_len-ADAP_FIELD_LEN) /* test for wifi display */
						//if (0 < adaptation_field_length-1-PCR_len-ADAP_FIELD_LEN)
						    /*tsDst.*/
                            Pad(BIT_STREAM_DST, 0xff, adaptation_field_length-PCR_len-ADAP_FIELD_LEN); /* test for wifi display */
							///*tsDst.*/Pad(BIT_STREAM_DST, 0xff, adaptation_field_length-1-PCR_len-ADAP_FIELD_LEN);
                        
					}

                    
					/*tsDst.*/
                    Add_PES_packet_top(BIT_STREAM_DST, stream_id, PES_packet_length);
					/*tsDst.*/
                    
                    Add_PES_header(BIT_STREAM_DST, 
						PES_scrambling_control,
						PES_priority,
						data_alignment_indicator,
						copyright,
						original_or_copy,
						PTS_DTS_flag,
						ESCR_flag,
						ES_rate_flag,
						DSM_trick_mode_flag,
						additional_copy_info_flag,
						PES_CRC_flag,
						_PES_extension_flag,
						_PES_header_data_length);

                    
					if (0x2 == PTS_DTS_flag) {
						// only PTS
						/*tsSrc.*/
                        Parse_PES_header_PTS(BIT_STREAM_SRC, PTS);
						/*tsDst.*/
                        Add_PES_header_PTS(BIT_STREAM_DST, PTS);
					}
					else if (0x3 == PTS_DTS_flag) {
						// PTS and DTS
						/*tsSrc.*/
                        Parse_PES_header_PTS_DTS(BIT_STREAM_SRC, PTS, DTS);
						/*tsDst.*/
                        Add_PES_header_PTS_DTS(BIT_STREAM_DST, PTS, DTS);
					}

                    //hdcp2x_parser_log("[DEBUG] 4 PES_extension_flag: %d\n", PES_extension_flag);
                        
					if (PES_extension_flag) {
						unsigned char PES_private_data_flag=0;
						unsigned char pack_header_field_flag=0;
						unsigned char program_packet_sequence_counter_flag=0;
						unsigned char P_STD_buffer_flag=0;
						unsigned char PES_extension_flag_2=0;

						// we have some private data for encryption
						/*tsSrc.*/
                        Parse_PES_header_PES_extension(BIT_STREAM_SRC,
							PES_private_data_flag,
							pack_header_field_flag,
							program_packet_sequence_counter_flag,
							P_STD_buffer_flag,
							PES_extension_flag_2);

                        /* test for wifi display, copy src header to dst header */
						/*tsSrc.*/

						//hdcp2x_parser_log("[DEBUG] PES_header_data_length: %x\n", PES_header_data_length);
						
                        if (PES_header_data_length == 0x18)
                        {
                        	Parse_PES_HDCP_private_data(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
                        }
						else if (PES_header_data_length == 0x17)
						{
							Parse_PES_HDCP_private_data_one_stuff(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
						}
                        else if (PES_header_data_length == 0x16)
                        {    // no 0xffff case (for ts_encrypted_testvect2.ts case)
                            Parse_PES_HDCP_private_data_no_stuffing(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
                            //hdcp2x_parser_log("[DEBUG] Warning, no stuffing case \n");
                        }
                        /* has DTS flag case for LG optimus */
                        else if (PES_header_data_length == 0x1D)
                        {
                            //printf("[DEBUG] has DTS flag case for LG optimus, has stuffing case, PES_header_data_length:%d \n", PES_header_data_length);
                            Parse_PES_HDCP_private_data(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
                        }
						else if (PES_header_data_length == 0x1C)
						{
						    Parse_PES_HDCP_private_data_one_stuff(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
						}
                        else if (PES_header_data_length == 0x1B)
                        {
                            //printf("[DEBUG] has DTS flag case for LG optimus, no stuffing case,  PES_header_data_length:%d \n", PES_header_data_length);
                            Parse_PES_HDCP_private_data_no_stuffing(BIT_STREAM_SRC, gPData.ulStreamCtr, gPData.ullInputCounter);
							//_DumpHex("0d:ullInputCounter", (unsigned char*)&gPData.ullInputCounter, 8);
                        }

						#if 0 // only for debug, compare with golden data
                        /* test for wifi display */
                        if (PES_header_data_length == 0x18 || PES_header_data_length==0x1D) // optimusG case
                        {
                            Add_PES_header_PES_extension(BIT_STREAM_DST,
                                PES_private_data_flag,
                                pack_header_field_flag,
                                program_packet_sequence_counter_flag,
                                P_STD_buffer_flag,
                                PES_extension_flag_2);
                        } 
                        else if (PES_header_data_length == 0x16 || PES_header_data_length==0x1B) // optimusG case
                        {
                            /* for ts_encrypted_testvect2.ts case  */
                            Add_PES_header_PES_extension_no_reserved(BIT_STREAM_DST,
                                PES_private_data_flag,
                                pack_header_field_flag,
                                program_packet_sequence_counter_flag,
                                P_STD_buffer_flag,
                                PES_extension_flag_2);
                        }
						#endif
                        Add_PES_header_PES_extension(BIT_STREAM_DST,
                                PES_private_data_flag,
                                pack_header_field_flag,
                                program_packet_sequence_counter_flag,
                                P_STD_buffer_flag,
                                PES_extension_flag_2);
						
                        if (PES_header_data_length == 0x18 || PES_header_data_length==0x1D) // PTS only and PTS_DTS case
                        {
                            Add_PES_HDCP_private_data(BIT_STREAM_DST, gPData.ulStreamCtr, gPData.ullInputCounter);
                        }
						else if(PES_header_data_length == 0x17 || PES_header_data_length==0x1C)
						{
						   Add_PES_HDCP_private_data_one_stuff(BIT_STREAM_DST, gPData.ulStreamCtr, gPData.ullInputCounter);

						}
                        else if (PES_header_data_length == 0x16 || PES_header_data_length==0x1B) // PTS only and PTS_DTS case
                        {
                            /* for ts_encrypted_testvect2.ts case */
                            Add_PES_HDCP_private_data_no_stuffing(BIT_STREAM_DST, 0, 0);
                        }

                        #if 0
                        if (usVidPesID == ucPesId)
                            printf("1 stream:%d input:%lld (video) \n", ulStreamCtr, ullInputCounter);
                        else if (usAudPesID == ucPesId)
                            printf("1 stream:%d input:%lld (audio) \n", ulStreamCtr, ullInputCounter);
                        else
                            printf("1 stream:%d input:%lld (unknown) \n", ulStreamCtr, ullInputCounter);
                        #endif
                        
                        #if 0
                        {
                            int i=0;
                            unsigned char _ptr[12];
                            memset(_ptr, 0x00, 12);
                            memcpy(_ptr, &ulStreamCtr, 4);
                            memcpy(&_ptr[4], &ullInputCounter, 8);

                            
                            for (i=0;i<12;i++)
                                printf("0x%2.2x ", _ptr[i]);
                            printf("\n");
                        }
                        #endif

					}

                    // for S3 test, must deal with previous PES data, because streamCtr is increasing...
                    #if MTK_LARGE_BUF_DEC
                    {
                        int i=0;
                        
                        /* final block for video */
                        if ((gPData.usVidPesID == ucPesId) && gPData.ulFrameSize && gPData.workBufIdx)
                        {   
                            //hdcp2x_parser_log("[DEBUG] ----> \n");
                            //hdcp2x_parser_log("[DEBUG] PES frame size (video): %d, new PES coming \n", (int)gPData.ulFrameSize );
                            //hdcp2x_parser_log("[DEBUG] bInfo[0].streamCtr:%lu bInfo[0].inputCtr:%llu *workBufIdx:%d \n", gPData.bInfo[0].streamCtr, gPData.bInfo[0].inputCtr, gPData.workBufIdx );
                            
                            #ifdef ENABLE_HDCP2X_RX
                            //hdcp2x_rx_decrypt(gPData.boundary_streamCtr, gPData.boundary_inputCtr,  (unsigned char*)&gPData.workBuf[0], gPData.workBufIdx, (unsigned char*)&gPData.workBuf[0]);

							//_DumpHex("2:boundary_streamCtr", (unsigned char*)&gPData.boundary_streamCtr, 4);
							//_DumpHex("2:boundary_inputCtr", (unsigned char*)&gPData.boundary_inputCtr, 8);
		
							gPData.mDecryptCallBack(gPData.boundary_streamCtr, gPData.boundary_inputCtr,  (unsigned char*)&gPData.workBuf[0], gPData.workBufIdx, (unsigned char*)&gPData.workBuf[0]);
                            #endif

                            /* copy decrypted-data to dst buffer */
                            for (int i=0;i<(gPData.bInfoIdx);i++)
                            {
                                //#if S3_DEBUG_LOG
                                //if ((nFrame-1)*188 >= 0xcf5e680)
                                //    printf("copy(%d) dstIdx:%d workIdx:%d size:%d \n", i, bInfo[i].dst_idx, bInfo[i].work_idx, bInfo[i].size);
                                //#endif
            
                                if (gPData.bInfo[i].dst_idx>=0)
                                {
                                    memcpy(&pDst[gPData.bInfo[i].dst_idx], &gPData.workBuf[gPData.bInfo[i].work_idx], gPData.bInfo[i].size); 
                                }   
                            }

                            /* reset all information */
                            for (int i=0;i<TS_UNIT_NUM;i++)
                            {
                                //gPData.bInfo[i].header = 0;
                                gPData.bInfo[i].dst_idx = 0;
                                gPData.bInfo[i].work_idx = 0;
                                gPData.bInfo[i].size = 0;
                                gPData.bInfo[i].inputCtr = 0;
                                gPData.bInfo[i].streamCtr= 0;
                            }
                            gPData.workBufIdx=0;
                            gPData.bInfoIdx=0;

                            gPData.ulFrameSize = 0;
                        }
                        
                        // final block for audio
                        if ((gPData.usAudPesID == ucPesId) && gPData.ulFrameSizeA && gPData.workBufIdxA)
                        {   
                            #ifdef ENABLE_HDCP2X_RX
                            //hdcp2x_rx_decrypt(gPData.boundary_streamCtrA, gPData.boundary_inputCtrA,  (unsigned char*)&gPData.workBufA[0], gPData.workBufIdxA, (unsigned char*)&gPData.workBufA[0]);
							
							gPData.mDecryptCallBack(gPData.boundary_streamCtrA, gPData.boundary_inputCtrA,  (unsigned char*)&gPData.workBufA[0], gPData.workBufIdxA, (unsigned char*)&gPData.workBufA[0]);
                           
							#endif
                            
                            /* copy decrypted-data to dst buffer */
                            for ( i=0;i<(gPData.bInfoIdxA);i++)
                            {
                                if (gPData.bInfoA[i].dst_idx>=0)
                                {
                                    memcpy(&pDst[gPData.bInfoA[i].dst_idx], &gPData.workBufA[gPData.bInfoA[i].work_idx], gPData.bInfoA[i].size); 
                                }
                                    
                            }

                            /* reset all information */
                            for (int i=0;i<TS_UNIT_NUM;i++)
                            {
                                //gPData.bInfoA[i].header = 0;
                                gPData.bInfoA[i].dst_idx = 0;
                                gPData.bInfoA[i].work_idx = 0;
                                gPData.bInfoA[i].size = 0;
                                gPData.bInfoA[i].inputCtr = 0;
                                gPData.bInfoA[i].streamCtr= 0;
                            }
                            gPData.workBufIdxA=0;
                            gPData.bInfoIdxA=0;
                           
                            gPData.ulFrameSizeA = 0;
                        }
                        
                    }
                    #endif
                    
                    //if (*ulFrameSizeV)
                    //{
                    //    printf("[DEBUG] frame size:%d \n", (int)*ulFrameSizeV);
                    //}
					//*ulFrameSizeV = 0;

					// copy video PES bytes to buffer for encrypting/decrypting
					unsigned long ulSrcHdrBytes=/*tsSrc.*/ByteOffset(BIT_STREAM_SRC);
					unsigned long ulDstHdrBytes=/*tsDst.*/ByteOffset(BIT_STREAM_DST);
					unsigned long ulSrcCnt = TS_PACKET_SIZE-ulSrcHdrBytes;
					unsigned long ulDstCnt = TS_PACKET_SIZE-ulDstHdrBytes;
                    
					// we can decrypt
                    //Joey 20120717 add -- START					
                    if ( PES_extension_flag/*ulSrcCnt == 144 && PES_extension_flag*/)					
                    {
                        if (gPData.usVidPesID == ucPesId)
                        {
                        	
        				    gPData.ulFrameSize = gPData.ulFrameSize + ulDstCnt;
							
                        }
                        else if (gPData.usAudPesID == ucPesId)
                        {
                            gPData.ulFrameSizeA = gPData.ulFrameSizeA + ulDstCnt;
                        }
                        
                        //m_bHDCP = 1;

                        //if (tmpLogEnc==0)
                        {
                            if (gPData.usVidPesID == ucPesId)
                            {
                            	gPData.m_bHDCPV = 100;
                                if (gPData.m_bHasHDCPDataV!=100) {
									gPData.m_bHasHDCPDataV=100;
									
                                	hdcp2x_parser_log("[DEBUG] 144 case, PES_extension_flag:%d m_bHDCPV:%d ulSrcCnt:%d ulDstCnt:%d ulStreamCtr:%ld (Video) \n", 
                                    	PES_extension_flag, gPData.m_bHDCPV, (int)ulSrcCnt, (int)ulDstCnt, gPData.ulStreamCtr);
                                }
                            }
                            else if (gPData.usAudPesID == ucPesId)
                            {
                                gPData.m_bHDCPA = 200;
                                if (gPData.m_bHasHDCPDataA!=200) {
									gPData.m_bHasHDCPDataA=200;
                                	hdcp2x_parser_log("[DEBUG] 144 case, PES_extension_flag:%d m_bHDCPA:%d ulSrcCnt:%d ulDstCnt:%d ulStreamCtr:%ld (Audio) \n", 
                                    	PES_extension_flag, gPData.m_bHDCPA, (int)ulSrcCnt, (int)ulDstCnt, gPData.ulStreamCtr);
                                }
                            }
                            tmpLogClr=0;
                        }
                        tmpLogEnc++;
                        
                        
                        /* test for wifi display, to support encapsulation case 2 */
                        if (gPData.usVidPesID == ucPesId)
                        {
                            gPData.boundary_inputCtr = gPData.ullInputCounterV = gPData.ullInputCounter;
                            gPData.boundary_streamCtr = gPData.ulStreamCtrV1=gPData.ulStreamCtr;

							//_DumpHex("0:ullInputCounter", (unsigned char*)&gPData.ullInputCounter, 8);
                        }
                        else if (gPData.usAudPesID == ucPesId)
                        {
                            gPData.boundary_inputCtrA = gPData.ullInputCounterA = gPData.ullInputCounter;
                            gPData.boundary_streamCtrA = gPData.ulStreamCtrA1=gPData.ulStreamCtr;
                        }

                        if (gPData.usVidPesID == ucPesId)
                        {
                            //hdcp2x_parser_log("[DEBUG] update video streamCtr:%ld inputCtr:%lld \n", gPData.ulStreamCtrV1, gPData.ullInputCounterV);
                            
                            //SpuDecrypt(ulSrcCnt, (unsigned char*)ucSrcTs+ulSrcHdrBytes, (unsigned char*)ucDstTs+ulDstHdrBytes, ulStreamCtr, ullInputCounter, &result);

                            /* copy to work buffer */
                            //printf("2 stream, srcHdr:%d dstHdr:%d \n", ulSrcHdrBytes, ulDstHdrBytes);
                            memcpy(&gPData.workBuf[gPData.workBufIdx], ucSrcTs+ulSrcHdrBytes, ulSrcCnt);

                            /* update point information */
                            gPData.bInfo[gPData.bInfoIdx].size = ulSrcCnt;
                            gPData.bInfo[gPData.bInfoIdx].work_idx = gPData.workBufIdx;
                            gPData.bInfo[gPData.bInfoIdx].dst_idx = blockSize + ulDstHdrBytes;
                            gPData.bInfo[gPData.bInfoIdx].streamCtr = gPData.ulStreamCtr;
                            gPData.bInfo[gPData.bInfoIdx].inputCtr = gPData.ullInputCounter;
//                            bInfo[*bInfoIdx].used = 1;

                            #if MTK_LARGE_BUF_LOG
                            printf("(%d) size:%d workBufIdx:%d dst_idx:%d \n", 
                                    *bInfoIdx, 
                                    bInfo[*bInfoIdx].size, 
                                    bInfo[*bInfoIdx].work_idx,
                                    bInfo[*bInfoIdx].dst_idx);
                            #endif
                            
                            gPData.workBufIdx = gPData.workBufIdx + ulSrcCnt;
                            gPData.bInfoIdx = (gPData.bInfoIdx) + 1;

                        }
                        else if (gPData.usAudPesID == ucPesId)
                        {
                            //hdcp2x_parser_log("[DEBUG] update audio streamCtrA:%ld ulStreamCtr:%ld inputCtr:%lld \n", gPData.ulStreamCtrA1, gPData.ulStreamCtr, gPData.ullInputCounterA);
                            
                            memcpy(&gPData.workBufA[gPData.workBufIdxA], ucSrcTs+ulSrcHdrBytes, ulSrcCnt);
                            
                            /* update point information */
                            gPData.bInfoA[gPData.bInfoIdxA].size = ulSrcCnt;
                            gPData.bInfoA[gPData.bInfoIdxA].work_idx = gPData.workBufIdxA;
                            gPData.bInfoA[gPData.bInfoIdxA].dst_idx = blockSize + ulDstHdrBytes;
                            gPData.bInfoA[gPData.bInfoIdxA].streamCtr = gPData.ulStreamCtr;
                            gPData.bInfoA[gPData.bInfoIdxA].inputCtr = gPData.ullInputCounter;
//                            bInfoA[*bInfoIdxA].used = 1;

                            gPData.workBufIdxA = gPData.workBufIdxA + ulSrcCnt;
                            gPData.bInfoIdxA = (gPData.bInfoIdxA) + 1;
                        }

                    }
                    else
                    {
                        #if MTK_DEBUG_LOG
                        printf("2 stream:%d input:%lld, not encrypted pack \n", ulStreamCtr, ullInputCounter);
                        #endif

                        //if (tmpLogClr==0)
                        {
                            if (gPData.usVidPesID == ucPesId)
                                ;//hdcp2x_parser_log("[DEBUG] not 144 case, PES_extension_flag:%d (Video) \n", PES_extension_flag);
                            else if (gPData.usAudPesID == ucPesId)
                                ;//hdcp2x_parser_log("[DEBUG] not 144 case, PES_extension_flag:%d (Audio) \n", PES_extension_flag);
                            tmpLogEnc=0;
                        }
                        tmpLogClr++;

                        if (gPData.usVidPesID == ucPesId)
                        {
                            gPData.m_bHDCPV = 0;
                        }
                        else if (gPData.usAudPesID == ucPesId)
                        {
                            gPData.m_bHDCPA = 0;
                        }
                        
                        memcpy(ucDstTs, ucSrcTs, TS_PACKET_SIZE);
                    }
                    
					return result;
				}
			}
		}
        else
        {
            //hdcp2x_parser_log("[DEBUG] Warning, no handle case \n");
        }

        //hdcp2x_parser_log("[DEBUG] select one, usVID_PID:%d usAUD_PID:%d PID:%d  \n", gPData.usVID_PID, gPData.usAUD_PID, PID);
		if (gPData.usVID_PID == PID) {
            
			// video packet, copy bytes, then decrypt and send
			unsigned long ulSrcHdrBytes=/*tsSrc.*/ByteOffset(BIT_STREAM_SRC);
			unsigned long ulSrcCnt = TS_PACKET_SIZE-ulSrcHdrBytes;

            //hdcp2x_parser_log("[DEBUG] video packet, usVID_PID=%d PID=%d ulSrcHdrBytes=%lu ulSrcCnt=%lu \n", gPData.usVID_PID, PID, ulSrcHdrBytes, ulSrcCnt);
            
			// copy header bytes directly from src to dst
			memcpy(ucDstTs, ucSrcTs, ulSrcHdrBytes);
            
            //Joey 20120717 add -- START
            //hdcp2x_parser_log("m_bHDCPV:%d count:%ld ulStreamCtrV1:%ld ulStreamCtr:%ld \n", gPData.m_bHDCPV, ulSrcCnt, gPData.ulStreamCtrV1, gPData.ulStreamCtr);
            if (gPData.m_bHDCPV == 100)
            {
                gPData.ulFrameSize = (gPData.ulFrameSize)+ TS_PACKET_SIZE-ulSrcHdrBytes;
                
    			//SpuDecrypt(ulSrcCnt, (unsigned char*)ucSrcTs+ulSrcHdrBytes, (unsigned char*)ucDstTs+ulSrcHdrBytes, ullInputCounterV, &result);
                
                #if MTK_LARGE_BUF_DEC

                //SpuDecrypt(ulSrcCnt, (unsigned char*)ucSrcTs+ulSrcHdrBytes, (unsigned char*)ucDstTs+ulSrcHdrBytes, ulStreamCtrV, ullInputCounterV, &result);

                /* copy to work buffer */
                memcpy(&gPData.workBuf[gPData.workBufIdx], ucSrcTs+ulSrcHdrBytes, ulSrcCnt);
                
                /* update point information */
                gPData.bInfo[gPData.bInfoIdx].size = ulSrcCnt;
                gPData.bInfo[gPData.bInfoIdx].work_idx = gPData.workBufIdx;
                gPData.bInfo[gPData.bInfoIdx].dst_idx = blockSize + ulSrcHdrBytes;
                gPData.bInfo[gPData.bInfoIdx].streamCtr = gPData.ulStreamCtrV1;
                gPData.bInfo[gPData.bInfoIdx].inputCtr = gPData.ullInputCounterV;
//                bInfo[*bInfoIdx].used = 1;

                #if MTK_LARGE_BUF_LOG
                printf("(%d) size:%d workBufIdx:%d dst_idx:%d \n", 
                                *bInfoIdx, 
                                bInfo[*bInfoIdx].size, 
                                bInfo[*bInfoIdx].work_idx,
                                bInfo[*bInfoIdx].dst_idx);
                #endif
                
                gPData.workBufIdx = gPData.workBufIdx + ulSrcCnt;
                gPData.bInfoIdx = gPData.bInfoIdx + 1;
        
                //printf("3 stream, bInfoIdx:%d workBufIdx:%d \n", *bInfoIdx, *workBufIdx);
                #endif
                
            }
            else
            {
                #if MTK_DEBUG_LOG
                printf("3 stream:%d input:%lld not encrypted pack \n", gPData.ulStreamCtrV, gPData.ullInputCounterV);
                #endif
                
                memcpy(ucDstTs, ucSrcTs, TS_PACKET_SIZE);
            }
            
			return result;

		}
        /* test for wifi display */
        else if (gPData.usAUD_PID == PID) {
            
			// audio packet, copy bytes, then decrypt and send
			unsigned long ulSrcHdrBytes=/*tsSrc.*/ByteOffset(BIT_STREAM_SRC);
			unsigned long ulSrcCnt = TS_PACKET_SIZE-ulSrcHdrBytes;

            //hdcp2x_parser_log("[DEBUG] audio packet, usAUD_PID=%d PID=%d ulSrcHdrBytes=%d\n", usAUD_PID, PID, ulSrcHdrBytes);
            
			// copy header bytes directly from src to dst
			memcpy(ucDstTs, ucSrcTs, ulSrcHdrBytes);
            
            //hdcp2x_parser_log("m_bHDCPA:%d count:%ld ulStreamCtrA1:%ld ulStreamCtr:%ld\n", gPData.m_bHDCPA, ulSrcCnt, gPData.ulStreamCtrA1, gPData.ulStreamCtr);
            if (gPData.m_bHDCPA == 200)
            {
                gPData.ulFrameSizeA = (gPData.ulFrameSizeA)+ TS_PACKET_SIZE-ulSrcHdrBytes;
                
    			//SpuDecrypt(ulSrcCnt, (unsigned char*)ucSrcTs+ulSrcHdrBytes, (unsigned char*)ucDstTs+ulSrcHdrBytes, ullInputCounterA, &result);
                
                #if MTK_LARGE_BUF_DEC

                //SpuDecrypt(ulSrcCnt, (unsigned char*)ucSrcTs+ulSrcHdrBytes, (unsigned char*)ucDstTs+ulSrcHdrBytes, ulStreamCtrA, ullInputCounterA, &result);

                /* copy to work buffer */
                memcpy(&gPData.workBufA[gPData.workBufIdxA], ucSrcTs+ulSrcHdrBytes, ulSrcCnt);
                
                /* update point information */
                gPData.bInfoA[gPData.bInfoIdxA].size = ulSrcCnt;
                gPData.bInfoA[gPData.bInfoIdxA].work_idx = gPData.workBufIdxA;
                gPData.bInfoA[gPData.bInfoIdxA].dst_idx = blockSize + ulSrcHdrBytes;
                gPData.bInfoA[gPData.bInfoIdxA].streamCtr = gPData.ulStreamCtrA1;
                gPData.bInfoA[gPData.bInfoIdxA].inputCtr = gPData.ullInputCounterA;
//                bInfoA[*bInfoIdxA].used = 1;

                #if MTK_LARGE_BUF_LOG
                printf("(%d) size:%d workBufIdx:%d dst_idx:%d \n", 
                                *bInfoIdxA, 
                                bInfoA[*bInfoIdxA].size, 
                                bInfoA[*bInfoIdxA].work_idx,
                                bInfoA[*bInfoIdxA].dst_idx);
                #endif
                
                gPData.workBufIdxA = gPData.workBufIdxA + ulSrcCnt;
                gPData.bInfoIdxA = gPData.bInfoIdxA + 1;

                //printf("3 stream, bInfoIdx:%d workBufIdx:%d \n", *bInfoIdx, *workBufIdx);

                #endif
                
            }
            else
            {
                #if MTK_DEBUG_LOG
                printf("3 stream:%d input:%lld not encrypted pack \n", gPData.ulStreamCtrA, gPData.ullInputCounterA);
                #endif
                
                memcpy(ucDstTs, ucSrcTs, TS_PACKET_SIZE);
            }
            
			return result;

		}
        else {
		    //hdcp2x_parser_log("[DEBUG] other packet, usAUD_PID=%d usVID_PID:%d PID=%d\n", gPData.usAUD_PID, gPData.usVID_PID, PID);
            
			// not a audio/video packet
			#if MTK_DEBUG_LOG
			{
                int i=0;
                unsigned char _ptr[12];
                
                memset(_ptr, 0x00, 12);
                memcpy(_ptr, &ulStreamCtr, 4);
                memcpy(&_ptr[4], &ullInputCounter, 8);

                printf("4 stream:%d input:%lld \n", ulStreamCtr, ullInputCounter);
                for (i=0;i<12;i++)
                    printf("0x%2.2x ", _ptr[i]);
                printf("\n");
            }
            #endif
            
			memcpy(ucDstTs, ucSrcTs, TS_PACKET_SIZE);

			return result;
		}
} 
#if 0
catch (...) {
//H2DBGLOG((LOCALDBG, "Unexpected exception in mux_demux function\n"));
printf("Unexpected exception in mux_demux function, %d \n", nFrame);
}
#endif    

    
	return -1;
}



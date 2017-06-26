/**
 * Code for demuxing transport stream.
 *
 * INTEL CONFIDENTIAL
 * Copyright 2010-2011 Intel Corporation All Rights Reserved.
 *
 * The source code contained or described herein and all documents related to
 * the source code ("Material") are owned by Intel Corporation or its
 * suppliers or licensors.  Title to the Material remains with Intel
 * Corporation or its suppliers and licensors.  The Material contains trade
 * secrets and proprietary and confidential information of Intel or its
 * suppliers and licensors.  The Material is protected by worldwide copyright
 * and trade secret laws and treaty provisions. No part of the Material may
 * be used, copied, reproduced, modified, published, uploaded, posted,
 * transmitted, distributed, or disclosed in any way without Intel's prior
 * express written permission.
 *
 * No license under any patent, copyright, trade secret or other intellectual
 * property right is granted to or conferred upon you by disclosure or
 * delivery of the Materials,  either expressly, by implication, inducement,
 * estoppel or otherwise.  Any license under such intellectual property
 * rights must be express and approved by Intel in writing.
 */

/**
 * This file handles an incoming encrypted data stream, demuxes, and decrypts it
 */

//#include <media/stagefright/foundation/AHandler.h>


//#ifdef __cplusplus
//extern "C" {
//#endif

#ifndef __MUX_DEMUX_H__
#define __MUX_DEMUX_H__


#include <sys/types.h>

#define	MPG2_STREAM_TYPE	0x2
#define AVC_STREAM_TYPE		0x1b
#define ADTS_STREAM_TYPE	0xf

#define LPCM_STREAM_TYPE	0x83 /* test for wifi display */
#define AC3_STREAM_TYPE	    0x81 /* test for wifi display */

#define PAT_PID	0
#define TS_PACKET_SIZE	188
#define TS_HEADER		4
#define TS_PACK_LEN_MINUS_HDR	(TS_PACKET_SIZE-TS_HEADER)
#define PES_HDR_LEN		9
#define ADAP_FIELD_LEN	1
#define AES_BLOCK_SIZE 16
#define STREAM_KEY_LEN	16
#define RIV_LEN 8
#define CTR_SIZE 8
#define LC128_SIZE 16
#define CTR_BITS 64
#define PES_STARTCODE	0x000001
#define TS_PACKET_COUNT	7

//namespace android {


//#ifdef __cplusplus
//extern "C" {
//#endif

typedef int (*DECRYPT_CB_T)(unsigned long pulStreamCounter,
                    unsigned long long pullInputCounter,
                    unsigned char *ucSrcFrame,
                    unsigned long ulCount,
                    unsigned char *ucDstFrame);


uint32_t hdcp2x_parser_ts_decrypt( unsigned char *ucSrcTs, unsigned char *ucDstTs, int dataLen);

uint32_t hdcp2x_parser_reg_callback(DECRYPT_CB_T pFnDecrypt);

int hdcp2x_parser_init();

int hdcp2x_parser_uninit();

//#ifdef __cplusplus
//}
//#endif

//} namespace android

#endif //__MUX_DEMUX_H__

//#ifdef __cplusplus
//}
//#endif



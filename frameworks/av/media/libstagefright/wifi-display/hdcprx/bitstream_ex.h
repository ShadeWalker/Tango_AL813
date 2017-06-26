#ifndef __BITSTREAM_EX_H__
#define __BITSTREAM_EX_H__

#ifdef __ANDROID__
/* Android */
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <assert.h>
#include <errno.h>
#else
#include <assert.h>
#include <memory.h>
#endif


#define BIT_STREAM_NUM 2
#define BIT_STREAM_SRC 0
#define BIT_STREAM_DST 1

void Add(int type, unsigned long long llValue, int nBits);
unsigned long long Get(int type, int nBits);


//class CBitstream
//{
	unsigned char*	m_pBuf[BIT_STREAM_NUM];
	int				m_nByteSize[BIT_STREAM_NUM];
	int				m_nCurrElem[BIT_STREAM_NUM];
	int				m_nCurrBit[BIT_STREAM_NUM];

//public:

	void CBitstream(int type, void* pBuf, int nLen)
	{
	    m_pBuf[type] = ((unsigned char*)pBuf);
		m_nByteSize[type] = (nLen);
		m_nCurrElem[type] = (0);
		m_nCurrBit[type] = (0);
        
		if (m_pBuf[type])
            memset(m_pBuf[type], 0, nLen);
	}
    
	//virtual ~CBitstream() {}



	int ByteOffset(int type) {return m_nCurrElem[type]+m_nCurrBit[type]/8;}
	void SetPos(int type, int nNewPos) {m_nCurrElem[type] = nNewPos; m_nCurrBit[type] = 0;}

	void SetBuffer(int type, void* pBuf, bool bClear)
	{
		m_nCurrElem[type] = m_nCurrBit[type] = 0;

		m_pBuf[type] = (unsigned char*)pBuf;
		if (m_pBuf[type] && bClear)
			memset(m_pBuf[type], 0, m_nByteSize[type]);
	}
#if 1

	void FillRemainder(int type, unsigned char value)
	{
        if (m_nByteSize[type] <= m_nCurrElem[type])
            return;

		switch (m_nCurrBit[type]) {
		case 0:
			m_pBuf[type][m_nCurrElem[type]] = value;
			break;
		case 1:
			m_pBuf[type][m_nCurrElem[type]] = (0x80&m_pBuf[type][m_nCurrElem[type]]) | (0x7f&value);
			break;
		case 2:
			m_pBuf[type][m_nCurrElem[type]] = (0xc0&m_pBuf[type][m_nCurrElem[type]]) | (0x3f&value);
			break;
		case 3:
			m_pBuf[type][m_nCurrElem[type]] = (0xe0&m_pBuf[type][m_nCurrElem[type]]) | (0x1f&value);
			break;
		case 4:
			m_pBuf[type][m_nCurrElem[type]] = (0xf0&m_pBuf[type][m_nCurrElem[type]]) | (0x0f&value);
			break;
		case 5:
			m_pBuf[type][m_nCurrElem[type]] = (0xf8&m_pBuf[type][m_nCurrElem[type]]) | (0x07&value);
			break;
		case 6:
			m_pBuf[type][m_nCurrElem[type]] = (0xfc&m_pBuf[type][m_nCurrElem[type]]) | (0x03&value);
			break;
		case 7:
			m_pBuf[type][m_nCurrElem[type]] = (0xfe&m_pBuf[type][m_nCurrElem[type]]) | (0x01&value);
			break;
		}

		memset(m_pBuf[type]+m_nCurrElem[type]+1, value, m_nByteSize[type]-m_nCurrElem[type]-1);
	}

	unsigned long ComputeCRC_32(int type, unsigned char* pBuf, int nLen)
	{
		return 0; // this is not needed for this Linux Rx HRI if we don't rebuild the PMT
	}

	void AddCRC32(int type, int nLen)
	{
		unsigned long dwCRC = 0;
		dwCRC = ComputeCRC_32(type, m_pBuf[type] + m_nCurrElem[type] - nLen, nLen);
		Add(type, dwCRC, 32);                    // CRC_32
	}

	void Add(int type, unsigned long long llValue, int nBits)
	{
		if ((m_nByteSize[type] - m_nCurrElem[type])*8 - m_nCurrBit[type] < nBits)
        {
            while(1);
        }      
			//throw -1;


		int nBitsToUse=nBits;
		unsigned char* pSrcBytes = (unsigned char*)&llValue;

		llValue <<= (64-nBitsToUse);

		if (m_nCurrBit[type]) {
			m_pBuf[type][m_nCurrElem[type]] |= pSrcBytes[7]>>m_nCurrBit[type];
			int nTemp = m_nCurrBit[type] + nBitsToUse;
			nBitsToUse -= (8-m_nCurrBit[type]);
			llValue <<= (8-m_nCurrBit[type]);
			if (nBitsToUse > 0) {
				m_nCurrElem[type]++;
				m_nCurrBit[type] = 0;
			} else {
				m_nCurrElem[type] += nTemp/8;
				m_nCurrBit[type] = nTemp%8;
			}
		}

		for (int i=7; 0 < nBitsToUse; nBitsToUse-=8, i--) {
			m_pBuf[type][m_nCurrElem[type]] |= pSrcBytes[i];
			if (nBitsToUse >= 8)
				m_nCurrElem[type]++;
			else
				m_nCurrBit[type] = nBitsToUse;
		}
	}

	unsigned long long Check(int type, unsigned long long llExpectedValue, int nBits)
	{
		unsigned long long llValue = Get(type, nBits);
		if (llExpectedValue != llValue) {
			//throw -1; /* testing for wifi display */
            //printf("[DEBUG] Warning, format not follow spec \n");
		}

		return llValue;
	}

	unsigned long long Get(int type, int nBits)
	{
		int nBitsToUse=nBits;
		int nSaveCurrBits=m_nCurrBit[type];
		unsigned long long llValue=0;
		unsigned char* pDstBytes = (unsigned char*)&llValue;

		for (int i=7; 0 < nBitsToUse; i--) {
			pDstBytes[i] |= m_pBuf[type][m_nCurrElem[type]];
			if (7 == i && nSaveCurrBits) {
				// first byte and nSaveCurrBits != 0, we won't count a full 8 bits
				if (8-nSaveCurrBits < nBitsToUse) {
					m_nCurrElem[type]++;
					nBitsToUse -= 8-nSaveCurrBits;
					m_nCurrBit[type] = 0;
				} else {
					m_nCurrBit[type] += nBitsToUse;
					nBitsToUse = 0;
				}
			} else {
				if (nBitsToUse >= 8) {
					m_nCurrElem[type]++;
					nBitsToUse -= 8;
				} else {
					m_nCurrBit[type] += nBitsToUse;
					nBitsToUse = 0;
				}
			}

			if (0 == i && 0 != nBitsToUse) {
				// we're on the last byte and there's still bits to use, 
				// so we will need to shift left before we can copy the last bits

				llValue <<= nSaveCurrBits;
				pDstBytes[i] |= m_pBuf[type][m_nCurrElem[type]] >> (8-nSaveCurrBits);
			} else if (0 == nBitsToUse && 0 != nSaveCurrBits) {
				// no more bits, but we started with a bit offset so need to shift
				llValue <<= nSaveCurrBits;
			}
		}

		llValue >>= (64-nBits);

		return llValue;
	}

	void Set(int type, int nBitOffset, unsigned long long llValue, int nBits)
	{
		int nSaveBit = m_nCurrBit[type];
		int nSaveElem = m_nCurrElem[type];

		m_nCurrBit[type] = nBitOffset;
		m_nCurrElem[type] = m_nCurrBit[type]/8;
		m_nCurrBit[type] %= m_nCurrBit[type];

		Add(type, llValue, nBits);

		m_nCurrBit[type] = nSaveBit;
		m_nCurrElem[type] = nSaveElem;
	}

	unsigned long long Peek(int type, int nBits)
	{
		unsigned long long llValue=0;

		int nSaveBit = m_nCurrBit[type];
		int nSaveElem = m_nCurrElem[type];

		llValue = Get(type, nBits);

		m_nCurrBit[type] = nSaveBit;
		m_nCurrElem[type] = nSaveElem;

		return llValue;
	}

	void Pad(int type, unsigned char byte, int nCount)
	{
		assert(m_nCurrElem[type] + nCount <= m_nByteSize[type]);

		memset(m_pBuf[type]+m_nCurrElem[type], byte, nCount);
		m_nCurrElem[type] += nCount;
	}

	int CopyPayload(int type, unsigned char* pBuf)
	{
		int nCount = m_nByteSize[type]-m_nCurrElem[type];

		memcpy(m_pBuf[type]+m_nCurrElem[type], pBuf, nCount);
		m_nCurrElem[type] += nCount;

		return nCount;
	}
#endif

//	int CopyPayload2(unsigned char* pBuf)
//	{
//		int nCount = m_nByteSize-m_nCurrElem;
//
//		memcpy(m_pBuf+m_nCurrElem, pBuf, nCount);
//FILE* fp=fopen("d:\\payload.bin", "a+b");
//fwrite(m_pBuf+m_nCurrElem, nCount, 1, fp);
//fclose(fp);
//		m_nCurrElem += nCount;
//
//		return nCount;
//	}
//};

#endif // __BITSTREAM_EX_H__


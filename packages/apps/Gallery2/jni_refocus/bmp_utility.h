#ifndef _BMP_UTIL_H_
#define _BMP_UTIL_H_

#include "kal_release.h"
//#include "pp_comm_def.h"
//#include "utility_comm_def.h"
#include <stdio.h>

#define MY(a,b,c) (( a*  0.2989  + b*  0.5866  + c*  0.1145))
#define MU(a,b,c) (( a*(-0.1688) + b*(-0.3312) + c*  0.5000 + 128))
#define MV(a,b,c) (( a*  0.5000  + b*(-0.4184) + c*(-0.0816) + 128))

#define DY(a,b,c) (MY(a,b,c) > 255 ? 255 : (MY(a,b,c) < 0 ? 0 : MY(a,b,c)))
#define DU(a,b,c) (MU(a,b,c) > 255 ? 255 : (MU(a,b,c) < 0 ? 0 : MU(a,b,c)))
#define DV(a,b,c) (MV(a,b,c) > 255 ? 255 : (MV(a,b,c) < 0 ? 0 : MV(a,b,c)))


#define RGB888_TO_YUV_Y(R, G, B)    ((  306 * (R) + 601 * (G) + 117 * (B) + 512) >> 10)
#define RGB888_TO_YUV_U(R, G, B)    (((-173 * (R) - 339 * (G) + 512 * (B)) >> 10) + 128)
#define RGB888_TO_YUV_V(R, G, B)    ((( 512 * (R) - 429 * (G) -  83 * (B)) >> 10) + 128)
//void ASSERT(int x);
//#define RGB888_TO_YUV_Y(R, G, B)    ((  ((R) + (G) + (B)) / 3 ))

typedef struct tagBITMAP {
	int offbits;
	int width;
	int height;
	int is_444;
	int is_gray;
	unsigned char *r;
	unsigned char *g;
	unsigned char *b;
	unsigned char *y;
} BITMAP;

typedef struct tagRECT {
	int x;
	int y;
	int width;
	int height;
} RECT;

int bmp_parse(char *file, BITMAP* bmp);
void bmp_read(char *file, BITMAP* bmp);
void bmp_create(BITMAP* bmp, int width, int height, int is_gray);
void bmp_free(BITMAP *src);
void bmp_write(char *file, const BITMAP* bmp);
void RGB888to565( BITMAP *image);
void bmp_rgb2gray( BITMAP *image, BITMAP *gray_image);
//void bmp_rgb2gray( BITMAP *image, BITMAP *gray_image, RECT rect);
void bmp_resize(BITMAP *src, BITMAP *dest);
void bmp_transpose(BITMAP *src, BITMAP *dest);
void bmp_copy(BITMAP *src, BITMAP *dest);
void bmp_gray2edge(BITMAP *src, BITMAP *dest);
void bmp_toRGB565(BITMAP *src, kal_uint16 *dest);
void bmp_toYUV420(BITMAP *src, kal_uint8 *dest);
void bmp_toRGB888(BITMAP *src, kal_uint32 *dest);
void RGB565_toBMP(kal_uint16 *src, BITMAP *dest);
void RGB888_toBMP(kal_uint32 *src, BITMAP *dest);
void YUV420_toBMP(kal_uint8 *src, BITMAP *dest);
void Gray2BMP(kal_uint8 *src, BITMAP *dest);
void raw_write(char *file, kal_uint16* src, kal_uint32 w, kal_uint32 h);
void raw_write8(char *file, kal_uint8* src, kal_uint32 w, kal_uint32 h);
//void GetROI(kal_uint16 *src, size_struct *src_size, kal_uint16 *dest, RECT *roi);
//kal_uint8 ClipImage(UTL_CLIP_STRUCT *clipinfo);
void highPassFilter(kal_uint16 *src, kal_uint16 *lo_src, kal_uint16 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w);
void lowPassFilter(kal_uint16 *src, kal_uint16 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w);
void lowPassFilter8(kal_uint8 *src, kal_uint8 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w);
#endif
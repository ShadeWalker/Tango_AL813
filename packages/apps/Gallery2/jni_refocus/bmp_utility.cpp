#include "bmp_utility.h"
//#include "utility_comm_def.h"
#include "mm_comm_def.h"
//#include "libutility.h"
#define USE_SOBEL 1
#define USE_LAPLACIAN 0

// Sobel mask
int GX[3][3]={{-1,0,1},{-2,0,2},{-1,0,1}};
int GY[3][3]={{1,2,1},{0,0,0},{-1,-2,-1}};

int kernel[25] = 
{  1,  5,  8,  5,  1,
   5, 20, 33, 20,  5,
   8, 33, 54, 33,  8,
   5, 20, 33, 20,  5,
   1,  5,  8,  5,  1
};

int bmp_parse(char *file, BITMAP* bmp)
{
	FILE *fp;

	// open the file 
	if ((fp = fopen(file,"rb")) == NULL)
    {
		return 0;
	}
	
	//check bitmap file header
	if (fgetc(fp)!='B'||fgetc(fp)!='M')
    {
		fclose(fp);
        return 0;
	}
	
	//read offset bits, width and height only. ignore others
	fseek(fp, 8, 1);
	fread(&bmp->offbits, sizeof(int), 1, fp);
	fseek(fp, 4, 1);
	fread(&bmp->width, sizeof(int), 1, fp);
	fread(&bmp->height, sizeof(int), 1, fp);

    return 1;
}

void bmp_read(char *file, BITMAP* bmp) {
	FILE *fp;
	int index;
	int x;
	int pad;

	// open the file 
	if ((fp = fopen(file,"rb")) == NULL) {
		printf("Error opening file %s.\n",file);
		exit(1);
	}
	
	//check bitmap file header
	if (fgetc(fp)!='B'||fgetc(fp)!='M') {
		fclose(fp);
		printf("not a bitmap file\n");
		exit(1);
	}
	
	//read offset bits, width and height only. ignore others
	fseek(fp, 8, 1);
	fread(&bmp->offbits, sizeof(int), 1, fp);
	fseek(fp, 4, 1);
	fread(&bmp->width, sizeof(int), 1, fp);
	fread(&bmp->height, sizeof(int), 1, fp);
	
	pad = bmp->width % 4;
	
	fseek(fp, 28, 1);
	
	//printf(" offbits %d width %d  height %d\n", bmp->offbits, bmp->width, bmp->height);
	
	//allocating memory for r g b 
	if ((bmp->r = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char) )) == NULL) {
		fclose(fp);
		printf("Error allocating memory for r\n");
		exit(1);
	}
	
	if ((bmp->g = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char))) == NULL) {
		fclose(fp);
		printf("Error allocating memory for g\n");
		exit(1);
	}
	
	if ((bmp->b = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char))) == NULL) {
		fclose(fp);
		printf("Error allocating memory for b\n");
		exit(1);
	}
	
	//read bitmap pixel value
	for(index=((bmp->height-1)* bmp->width); index>=0; index-=bmp->width) {
		for(x=0; x<bmp->width; x++) {
			bmp->b[index+x]=fgetc(fp);
			bmp->g[index+x]=fgetc(fp);
			bmp->r[index+x]=fgetc(fp);
		}
		for(x=0; x<pad; x++)
			fgetc(fp);
	}
	
	bmp->is_444 = 1;
	bmp->is_gray = 0;
	
	fclose(fp);
}

void bmp_create(BITMAP* bmp, int width, int height, int is_gray) {
	
	bmp->offbits = 54;
	bmp->width = width;
	bmp->height = height;
	
	if (is_gray == 0)
	{
		if ((bmp->r = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char) )) == NULL) {
			printf("Error allocating memory for r\n");
			exit(1);
		}
		
		if ((bmp->g = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char))) == NULL) {
			printf("Error allocating memory for g\n");
			exit(1);
		}
		
		if ((bmp->b = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char))) == NULL) {
			printf("Error allocating memory for b\n");
			exit(1);
		}
		bmp->is_444 = 1;
		bmp->is_gray = 0;
	}
	else
	{
		if ((bmp->y = (unsigned char *)calloc( bmp->height*bmp->width,sizeof(unsigned char) )) == NULL) {
			printf("Error allocating memory for y\n");
			exit(1);
		}
		bmp->is_444 = 0;
		bmp->is_gray = 1;
	}
}

void bmp_free(BITMAP *src) {
	if (!src->is_gray == 1)
	{
		free(src->r);
		free(src->g);
		free(src->b);
		
		src->r = 0x0;
		src->g = 0x0;
		src->b = 0x0;
	}
	else
	{
		free(src->y);

		src->y = 0x0;
	}
}

void bmp_write(char *file, const BITMAP* bmp) {
	FILE *fp;
	int index;
	int x;
	int temp;
	int pad = bmp->width %4;
	
	/* open the file */
	if ((fp = fopen(file,"wb")) == NULL) {
		printf("Error opening file %s.\n",file);
		exit(1);
	}

	if (bmp->is_gray == 1) 
	{
		for(index=0; index<bmp->width*bmp->height; index++) 
		{
			fputc(bmp->y[index],fp);
		}
		return;
	}
	
	fputc('B',fp);
	fputc('M',fp);
	
	//total file size = w * h * 3 + offset
	temp=bmp->width*bmp->height*3+pad*bmp->height+bmp->offbits;
	//printf(" offbits %d width %d  height %d temp %ld\n", bmp->offbits, bmp->width, bmp->height, temp);
	fwrite(&temp, sizeof(int), 1, fp);
	
	fputc(0x00,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	
	fwrite(&bmp->offbits, sizeof(int), 1, fp);
	
	//offset - already read bits : 54 - 14 =40
	fputc(0x28,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	
	//width and height
	fwrite(&bmp->width, sizeof(int), 1, fp);
	fwrite(&bmp->height, sizeof(int), 1, fp);
	
	//reserved
	fputc(0x01,fp);
	fputc(0x00,fp);
	
	//24 bit bitmap
	fputc(0x18,fp);
	fputc(0x00,fp);
	
	fputc(0x00,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	fputc(0x00,fp);
	
	//total pixel value = w * h * 3
	temp=bmp->width * bmp->height *3 +pad * bmp->height;
	fwrite(&temp, sizeof(int), 1, fp);
	
	for(x=0;x<16;x++)
		fputc(0x00,fp);
	
	//put pixel value 
	
	for(index=((bmp->height-1)* bmp->width); index>=0; index-=bmp->width) {
		for(x=0; x<bmp->width; x++) {
			fputc(bmp->b[index+x],fp);
			fputc(bmp->g[index+x],fp);
			fputc(bmp->r[index+x],fp);
		}
		for(x=0; x<pad; x++)
			fputc(0x00,fp);
	}
	
	fclose(fp);
}
//-----------------------------------------------------------------------------------------------

void RGB888to565( BITMAP *image) 
{
	int i, j ;
	int index ;

	if (image->is_gray == 1)
	{
		printf("rgb888 to 565 error: src image is gray format.\n");
		exit(1);
	}
	
	for (j = 0 ; j < image->height ; j++)
	{
		for (i = 0 ; i < image->width ; i++)
		{
			index = image->width * j + i ;   
			
			image->r[index] = image->r[index] & 0xF8 ;
			image->g[index] = image->g[index] & 0xFC ;
			image->b[index] = image->b[index] & 0xF8 ;   
		}
	}
}
/*
void bmp_rgb2gray( BITMAP *image, BITMAP *gray_image, RECT rect)
{
	int i, j, k;
	int index ;
    
	if ((!image->is_444) || (!gray_image->is_gray))
	{
		printf("format mismatch\n");
		exit(1);
	}
	if ((rect.width + rect.x > image->width) || (rect.height + rect.y > image->height))
	{
		printf("rect over size!\n");
		exit(1);
	}
	
	k = 0;
	for (j = rect.y ; j < rect.y + rect.height ; j++)
	{
		for (i = rect.x ; i < rect.x + rect.width ; i++)
		{
			index = image->width * j + i ;   
			
			gray_image->y[k] = 0.3*(float)image->r[index] + 0.59*(float)image->g[index] + 0.11*(float)image->b[index];
			k++;		
		}
	}
}
*/

void bmp_rgb2gray( BITMAP *image, BITMAP *gray_image)
{
	int i, j ;
	int index ;

	if ((!image->is_444) || (!gray_image->is_gray))
	{
		printf("format mismatch\n");
		exit(1);
	}

	if ((image->width != gray_image->width) || (image->height != gray_image->height))
	{
		printf("size not match!\n");
		exit(1);
	}
	
	for (j = 0 ; j < image->height ; j++)
	{
		for (i = 0 ; i < image->width ; i++)
		{
			index = image->width * j + i ;   
			float temp = 0.3f*(float)image->r[index] + 0.59f*(float)image->g[index] + 0.11f*(float)image->b[index];
			gray_image->y[index] = (unsigned char)temp;
					
		}
	}
}

void bmp_resize(BITMAP *src, BITMAP *dest)
{
	int x,y;
	float x_scale,y_scale;
	int sx,sy;
	int index, s_index;

	if (dest->is_gray != src->is_gray)
	{
		printf("bmp_resize: image type not match!\n");
		exit(1);
	}

	x_scale = (float)src->width / (float)dest->width;
	y_scale = (float)src->height / (float)dest->height;

	for (y = 0; y < dest->height; y++)
	{
		for (x = 0; x < dest->width; x++)
		{
			index = x + y*dest->width;
			sx = (int)(x*x_scale);
			sy = (int)(y*y_scale);

			s_index = sx + sy*src->width;
			if (dest->is_gray == 1)
			{
				dest->y[index] = src->y[s_index];
			}
			else
			{
				dest->r[index] = src->r[s_index];
				dest->g[index] = src->g[s_index];
				dest->b[index] = src->b[s_index];
			}
		}
	}
}

void bmp_transpose(BITMAP *src, BITMAP *dest)
{
	int x,y;
	int d_index, s_index;

	if (dest->is_gray != src->is_gray)
	{
		printf("bmp_transpose: image type not match!\n");
		exit(1);
	}

	for (y = 0; y < dest->height; y++)
	{
		for (x = 0; x < dest->width; x++)
		{
			s_index = (src->width-y-1) + x*src->width;
			d_index = x + y*dest->width;
			if (dest->is_gray == 1)
			{
				dest->y[d_index] = src->y[s_index];
			}
			else
			{
				dest->r[d_index] = src->r[s_index];
				dest->g[d_index] = src->g[s_index];
				dest->b[d_index] = src->b[s_index];
			}
		}
	}
}

void bmp_copy(BITMAP *src, BITMAP *dest)
{
	int x,y;
	int index;

	if (dest->is_gray != src->is_gray)
	{
		printf("bmp_transpose: image type not match!\n");
		exit(1);
	}

	for (y = 0; y < dest->height; y++)
	{
		for (x = 0; x < dest->width; x++)
		{
			index = x + y*dest->width;
			if (dest->is_gray == 1)
			{
				dest->y[index] = src->y[index];
			}
			else
			{
				dest->r[index] = src->r[index];
				dest->g[index] = src->g[index];
				dest->b[index] = src->b[index];
			}
		}
	}
}

void bmp_gray2edge(BITMAP *src, BITMAP *dest)
{
	int x,y;
	int sumX;
	int sumY;
	int sum;
	int I,J;

	if (!dest->is_gray || dest->is_gray != src->is_gray)
	{
		printf("bmp_gray2edge: image type not match!\n");
		exit(1);
	}

	for(y=0; y<=(src->height-1); y++) 
	{
		for(x=0; x<=(src->width-1); x++)  
		{
			sumX = 0;
			sumY = 0;

			/* image boundaries */
			if(y==0 || y==src->height-1)
			{
				sum = 0;
			}
			else if(x==0 || x==src->width-1)
			{
				sum = 0;
			}
			/* Convolution starts here */
			else   
			{

				/*-------X GRADIENT APPROXIMATION------*/
				for(I=-1; I<=1; I++)  
				{
					for(J=-1; J<=1; J++)  
					{
						sumX = sumX + (int)( (*(src->y + x + I + 
								(y + J)*src->width)) * GX[I+1][J+1]);
					}
				}

				/*-------Y GRADIENT APPROXIMATION-------*/
				for(I=-1; I<=1; I++)  
				{
					for(J=-1; J<=1; J++)  
					{
						sumY = sumY + (int)( (*(src->y + x + I + 
                              (y + J)*src->width)) * GY[I+1][J+1]);
					}
				}

				/*---GRADIENT MAGNITUDE APPROXIMATION----*/
				sum = abs(sumX) + abs(sumY);
             }

             if(sum>255) sum=255;
             if(sum<0) sum=0;
			 
			 *(dest->y + x + y*src->width) = 255 - (unsigned char)(sum);//(unsigned char)(sum);//255 - (unsigned char)(sum);
		}
	}

}

void bmp_toRGB565(BITMAP *src, kal_uint16 *dest)
{
    kal_int32 x, y;
	kal_uint32 k;
	kal_uint32 r, g, b;
	k = 0;
	for(y = 0; y < src->height; y++)
	{
		for(x = 0; x < src->width; x++)
		{
			r = src->r[x+y*src->width];
			g = src->g[x+y*src->width];
			b = src->b[x+y*src->width];
			dest[k] = ((r >> 3)<<11) | ((g>>2)<<5) | (b>>3);
			k++;
		}
	}
}

void bmp_toYUV420(BITMAP *src, kal_uint8 *dest)
{
    unsigned int i,x,y,j;
    unsigned char *Y = NULL;
    unsigned char *U = NULL;
    unsigned char *V = NULL;
    unsigned char *R = NULL;
    unsigned char *G = NULL;
    unsigned char *B = NULL;
	unsigned int HEIGHT,WIDTH;

	HEIGHT = src->height;
	WIDTH = src->width;
   
    Y = dest;
    U = dest + WIDTH*HEIGHT;
    V = U + ((WIDTH*HEIGHT)>>2);

    for(y=0; y < HEIGHT; y++)
	{
        for(x=0; x < WIDTH; x++)
        {
            j = y*WIDTH + x;
            i = j*3;

			R = &src->r[j];
			G = &src->g[j];
			B = &src->b[j];

            Y[j] = (unsigned char)(DY(*R, *G, *B));

            if(x%2 == 1 && y%2 == 1)
            {
                j = (WIDTH>>1) * (y>>1) + (x>>1);
                U[j] = (unsigned char)
                       ((DU(*R, *G, *B) +
                         DU(*(R-1), *(G-1), *(B-1)) +
                         DU(*(R-WIDTH), *(G-WIDTH), *(B-WIDTH)) +
                         DU(*(R-WIDTH-1), *(G-WIDTH-1), *(B-WIDTH-1)))/4);

                V[j] = (unsigned char)
                       ((DV(*R, *G, *B) +
                         DV(*(R-1), *(G-1), *(B-1)) +
                         DV(*(R-WIDTH), *(G-WIDTH), *(B-WIDTH)) +
                         DV(*(R-WIDTH-1), *(G-WIDTH-1), *(B-WIDTH-1)))/4);
            }

        }
	}

}

void bmp_toRGB888(BITMAP *src, kal_uint32 *dest)
{
	kal_int32 x,y;
    kal_uint32 k;
	kal_uint32 r, g, b;
	k = 0;
	for(y = 0; y < src->height; y++)
	{
		for(x = 0; x < src->width; x++)
		{
			r = src->r[x+y*src->width];
			g = src->g[x+y*src->width];
			b = src->b[x+y*src->width];
			dest[k] = ((r)<<16) | ((g)<<8) | (b);
			k++;
		}
	}
}

void RGB565_toBMP(kal_uint16 *src, BITMAP *dest)
{
	kal_int32 x,y;
    kal_uint32 k;
	k = 0;
	for(y = 0; y < dest->height; y++)
	{
		for(x = 0; x < dest->width; x++)
		{
			dest->r[x+y*dest->width] = ((src[k] >> 11) << 3)&0xFF;
			dest->g[x+y*dest->width] = ((src[k] >> 5) << 2)& 0xFF;
			dest->b[x+y*dest->width] = (src[k] << 3)&0xFF;
			k++;
		}
	}
}

void RGB888_toBMP(kal_uint32 *src, BITMAP *dest)
{
	kal_int32 x,y;
    kal_uint32 k;
	k = 0;
	for(y = 0; y < dest->height; y++)
	{
		for(x = 0; x < dest->width; x++)
		{
			dest->r[x+y*dest->width] = ((src[k] >> 16))&0xFF;
			dest->g[x+y*dest->width] = ((src[k] >> 8))& 0xFF;
			dest->b[x+y*dest->width] = (src[k])&0xFF;
			k++;
		}
	}
}

double YuvToRgb[3][3] =
{
    {1,  0.0000,  1.4022},
    {1, -0.3456, -0.7145},
    {1,  1.7710,  0.0000}
};


void YUV420_toBMP(kal_uint8 *src, BITMAP *dest)
{
	kal_uint32 x,y;
	kal_uint32 ImgSize;
	kal_uint32 height;
	kal_uint32 width;
	kal_uint8* cTemp[6];
	kal_int32 temp;

	width = dest->width;
	height = dest->height;
	ImgSize = width*height;

	cTemp[0] = src;                         // y component address
    cTemp[1] = src + ImgSize;               // u component address
    cTemp[2] = cTemp[1] + (ImgSize>>2);     // v component address
    cTemp[3] = dest->r;                     // r component address
    cTemp[4] = dest->g;					    // g component address
    cTemp[5] = dest->b;					    // b component address
    for(y=0; y < height; y++)
	{
        for(x=0; x < width; x++)
        {
            // r component
            temp = (kal_int32)(cTemp[0][y*width+x] + (cTemp[2][(y/2)*(width/2)+x/2]-128) * YuvToRgb[0][2]);
            cTemp[3][y*width+x] = temp<0 ? 0 : (temp>255 ? 255 : temp);
            // g component
            temp = (kal_int32)(cTemp[0][y*width+x] + (cTemp[1][(y/2)*(width/2)+x/2]-128) * YuvToRgb[1][1]
                                       + (cTemp[2][(y/2)*(width/2)+x/2]-128) * YuvToRgb[1][2]);
            cTemp[4][y*width+x] = temp<0 ? 0 : (temp>255 ? 255 : temp);
            // b component
            temp = (kal_int32)(cTemp[0][y*width+x] + (cTemp[1][(y/2)*(width/2)+x/2]-128) * YuvToRgb[2][1]);
            cTemp[5][y*width+x] = temp<0 ? 0 : (temp>255 ? 255 : temp);
        }
	}

}

void Gray2BMP(kal_uint8 *src, BITMAP *dest)
{
	kal_uint32 x,y;
	kal_uint32 ImgSize;
	kal_uint32 height;
	kal_uint32 width;
	kal_uint8* cTemp[6];
	kal_int32 temp;

	width = dest->width;
	height = dest->height;
	ImgSize = width*height;

	cTemp[0] = src;                         // y component address
    cTemp[3] = dest->r;                     // r component address
    cTemp[4] = dest->g;					    // g component address
    cTemp[5] = dest->b;					    // b component address
    for(y=0; y < height; y++)
	{
        for(x=0; x < width; x++)
        {
            // r component
            temp = (kal_int32)(cTemp[0][y*width+x]);
            cTemp[3][y*width+x] = temp<0 ? 0 : (temp>255 ? 255 : temp);
            // g component
            cTemp[4][y*width+x] = cTemp[3][y*width+x];
            // b component
            cTemp[5][y*width+x] = cTemp[3][y*width+x];
        }
	}

}

#if 0
kal_uint8 ClipImage(UTL_CLIP_STRUCT *clipinfo)
{
	kal_uint32 sx,sy, dx, dy;
	kal_uint32 s_index, d_index;

	if (MM_IMAGE_FORMAT_JPEG == clipinfo->ImageSrcFormat)
	{
		if (MM_IMAGE_FORMAT_RGB565 == clipinfo->ImageDstFormat)
		{		
			for (sy = clipinfo->SrcRoiY, dy = clipinfo->DstRoiY; sy < clipinfo->SrcRoiY+clipinfo->RoiHeight; sy++, dy++)
			{
				for (sx = clipinfo->SrcRoiX, dx = clipinfo->DstRoiX; sx < clipinfo->SrcRoiX+clipinfo->RoiWidth; sx++, dx++)
				{
					s_index = sx+sy*clipinfo->SrcWidth;
					d_index = dx+dy*clipinfo->DstWidth;
					((kal_uint16*)clipinfo->ImageDstBuffer)[d_index] = ((kal_uint16*)clipinfo->ImageSrcBuffer)[s_index];
				}
			}
		}
		else if (MM_IMAGE_FORMAT_YUV420 == clipinfo->ImageDstFormat)
		{
			printf("clipinfo->ImageDstFormat == MM_IMAGE_FORMAT_YUV420 not support!\n");
			ASSERT(0);
		}
		else if (MM_IMAGE_FORMAT_YUV400 == clipinfo->ImageDstFormat)
		{
			kal_uint32 R,G,B,Y;
			kal_uint16 *src = (kal_uint16*)clipinfo->ImageSrcBuffer;
			// Y data
			for (sy = clipinfo->SrcRoiY, dy = clipinfo->DstRoiY; sy < clipinfo->SrcRoiY+clipinfo->RoiHeight; sy++, dy++)
			{
				for (sx = clipinfo->SrcRoiX, dx = clipinfo->DstRoiX; sx < clipinfo->SrcRoiX+clipinfo->RoiWidth; sx++, dx++)
				{
					s_index = sx+sy*clipinfo->SrcWidth;
					R = UTL_RGB565_TO_RGB888_R(src[s_index]);
					G = UTL_RGB565_TO_RGB888_G(src[s_index]);
					B = UTL_RGB565_TO_RGB888_B(src[s_index]);
					Y = (unsigned char)(DY(R, G, B));
					d_index = dx+dy*clipinfo->DstWidth;
					(clipinfo->ImageDstBuffer)[d_index] = Y;
				}
			}
		}
		else
		{
			printf("clipinfo->ImageDstFormat not suppot!\n");
			ASSERT(0);
		}
	}
	else if (MM_IMAGE_FORMAT_YUV420 == clipinfo->ImageSrcFormat)
	{
		if (MM_IMAGE_FORMAT_YUV420 == clipinfo->ImageDstFormat)
		{
			kal_uint32 src_y_addr, src_u_addr,src_v_addr;
			kal_uint32 dst_y_addr, dst_u_addr,dst_v_addr;
			kal_uint32 j;
			kal_uint32 copy_from, copy_to,copy_len;
			kal_uint32 result = KAL_TRUE;
			kal_uint32 src_offset, dst_offset;
			kal_uint32 src_uv_offset, dst_uv_offset;

			src_y_addr = (kal_uint32)clipinfo->ImageSrcBuffer;
			src_u_addr = ((kal_uint32)clipinfo->ImageSrcBuffer)+(clipinfo->SrcWidth*clipinfo->SrcHeight);
			src_v_addr = src_u_addr+((clipinfo->SrcWidth*clipinfo->SrcHeight)>>2);
			dst_y_addr = (kal_uint32)clipinfo->ImageDstBuffer;
			dst_u_addr = ((kal_uint32)clipinfo->ImageDstBuffer)+(clipinfo->DstWidth*clipinfo->DstHeight);
			dst_v_addr = dst_u_addr+((clipinfo->DstWidth*clipinfo->DstHeight)>>2);


			src_offset = ((1)*clipinfo->SrcWidth);
			dst_offset = ((1)*clipinfo->DstWidth);
			copy_from= src_y_addr+((clipinfo->SrcRoiY)*clipinfo->SrcWidth)+clipinfo->SrcRoiX;
			copy_to = dst_y_addr+((clipinfo->DstRoiY)*clipinfo->DstWidth)+clipinfo->DstRoiX;
			copy_len = clipinfo->RoiWidth;
			memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			for(j = 1 ; j <clipinfo->RoiHeight; j++)
			{
				copy_from += src_offset;
				copy_to += dst_offset;
				memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			}
			//copy u data
			src_uv_offset = ((1)*(clipinfo->SrcWidth>>1));
			dst_uv_offset = ((1)*(clipinfo->DstWidth>>1));
			copy_from= src_u_addr+((((clipinfo->SrcRoiY)>>1)*(clipinfo->SrcWidth>>1))+(clipinfo->SrcRoiX>>1));
			copy_to = dst_u_addr+((((clipinfo->DstRoiY)>>1)*(clipinfo->DstWidth>>1))+(clipinfo->DstRoiX>>1));
			copy_len = (clipinfo->RoiWidth>>1);
			memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			for(j = 2 ; j <clipinfo->RoiHeight; j+=2)
			{
				copy_from += src_uv_offset;
				copy_to += dst_uv_offset;
				memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			}
			//copy v data
			copy_from= src_v_addr+((((clipinfo->SrcRoiY)>>1)*(clipinfo->SrcWidth>>1))+(clipinfo->SrcRoiX>>1));
			copy_to = dst_v_addr+((((clipinfo->DstRoiY)>>1)*(clipinfo->DstWidth>>1))+(clipinfo->DstRoiX>>1));
			copy_len = (clipinfo->RoiWidth>>1);
			memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			for(j = 2 ; j <clipinfo->RoiHeight; j+=2)
			{
				copy_from += src_uv_offset;
				copy_to += dst_uv_offset;
				memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			}
		}
		else if (MM_IMAGE_FORMAT_YUV400 == clipinfo->ImageDstFormat)
		{
			kal_uint32 src_y_addr, src_u_addr,src_v_addr;
			kal_uint32 dst_y_addr, dst_u_addr,dst_v_addr;
			kal_uint32 j;
			kal_uint32 copy_from, copy_to,copy_len;
			kal_uint32 result = KAL_TRUE;
			kal_uint32 src_offset, dst_offset;

			src_y_addr = (kal_uint32)clipinfo->ImageSrcBuffer;
			src_u_addr = ((kal_uint32)clipinfo->ImageSrcBuffer)+(clipinfo->SrcWidth*clipinfo->SrcHeight);
			src_v_addr = src_u_addr+((clipinfo->SrcWidth*clipinfo->SrcHeight)>>2);
			dst_y_addr = (kal_uint32)clipinfo->ImageDstBuffer;
			dst_u_addr = ((kal_uint32)clipinfo->ImageDstBuffer)+(clipinfo->DstWidth*clipinfo->DstHeight);
			dst_v_addr = dst_u_addr+((clipinfo->DstWidth*clipinfo->DstHeight)>>2);


			src_offset = ((1)*clipinfo->SrcWidth);
			dst_offset = ((1)*clipinfo->DstWidth);
			copy_from= src_y_addr+((clipinfo->SrcRoiY)*clipinfo->SrcWidth)+clipinfo->SrcRoiX;
			copy_to = dst_y_addr+((clipinfo->DstRoiY)*clipinfo->DstWidth)+clipinfo->DstRoiX;
			copy_len = clipinfo->RoiWidth;
			memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			for(j = 1 ; j <clipinfo->RoiHeight; j++)
			{
				copy_from += src_offset;
				copy_to += dst_offset;
				memcpy((void*) copy_to, (void*) copy_from,  copy_len);
			}
		}
		else
		{
			printf("clipinfo->ImageDstFormat not suppot!\n");
			ASSERT(0);
		}
	}
	else
	{
		printf("clipinfo->ImageSrcFormat not suppot!\n");
		ASSERT(0);
	}



	return 0;
}
#endif

void highPassFilter(kal_uint16 *src, kal_uint16 *lo_src, kal_uint16 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w)
{
	kal_int32 x,y;
    kal_uint32 k,j;
	int r1,r2;
	int g1,g2;
	int b1,b2;
	for (y = 0; y < h; y++) 
	{
		for (x = 0; x < w; x++) 
		{
			k = x+y*acc_w;
			j = x+y*w;
			r1 = ((src[k] >> 11) << 3)&0xFF;
			g1 = ((src[k] >> 5) << 2)& 0xFF;
			b1 = (src[k] << 3)&0xFF;
			r2 = ((lo_src[j] >> 11) << 3)&0xFF;
			g2 = ((lo_src[j] >> 5) << 2)& 0xFF;
			b2 = (lo_src[j] << 3)&0xFF;
			r1 = (r1 - r2 >= 0)?r1 - r2:0;
			g1 = (g1 - g2 >= 0)?g1 - g2:0;
			b1 = (b1 - b2 >= 0)?b1 - b2:0;
			dst[j] = ((r1 >> 3)<<11) | ((g1>>2)<<5) | (b1>>3);
		}
	}
}
void lowPassFilter(kal_uint16 *src, kal_uint16 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w)
{
	kal_int32 x,y,k,j;
	kal_int32 m,n,i;
	kal_int32 div;
	int r1,r2;
	int g1,g2;
	int b1,b2;
	for(y = 0; y < h; y++)
	{
		for(x = 0; x < w; x++)
		{
			k = x+y*acc_w;
			j = x+y*w;
			
			div = 0;
			r2 = g2 = b2 = 0;
			for (m = -2; m < 2; m++)
			{
				for (n = -9; n < 9; n++)
				{
					if (y+m<0||x+n<0) continue;
					i = k+m*acc_w+n;
					r1 = ((src[i] >> 11) << 3)&0xFF;
					g1 = ((src[i] >> 5) << 2)& 0xFF;
					b1 = (src[i] << 3)&0xFF;	
					r1 *= kernel[(m+2)*5+(n+2)];
					g1 *= kernel[(m+2)*5+(n+2)];
					b1 *= kernel[(m+2)*5+(n+2)];
					r2 += r1;
					g2 += g1;
					b2 += b1;
					div += kernel[(m+2)*5+(n+2)];
				}
			}
			r2 /= div;
			g2 /= div;
			b2 /= div;
			dst[j] = ((r2 >> 3)<<11) | ((g2>>2)<<5) | (b2>>3);			
		}
	}
}

void lowPassFilter8(kal_uint8 *src, kal_uint8 *dst, kal_int32 w, kal_int32 h, kal_int32 acc_w)
{
	kal_int32 x,y,k,j;
	kal_int32 m,n,i;
	kal_int32 div;
	int r1,r2;
	for(y = 0; y < h; y++)
	{
		for(x = 0; x < w; x++)
		{
			k = x+y*acc_w;
			j = x+y*w;
			
			div = 0;
			r2 = 0;
			for (m = -2; m < 2; m++)
			{
				for (n = -9; n < 9; n++)
				{
					if (y+m<0||x+n<0) continue;
					i = k+m*acc_w+n;
					r1 = src[i];
					r1 *= kernel[(m+2)*5+(n+2)];
					r2 += r1;
					div += kernel[(m+2)*5+(n+2)];
				}
			}
			r2 /= div;
			dst[j] = r2;
		}
	}
}

void raw_write(char *file, kal_uint16* src, kal_uint32 w, kal_uint32 h)
{
	FILE *fp;
	unsigned int index;
	
	/* open the file */
	if ((fp = fopen(file,"wb")) == NULL) {
		printf("Error opening file %s.\n",file);
		exit(1);
	}
	
	for(index=0; index<w*h; index++) 
	{
		fputc((char)src[index],fp);
	}
	
	fclose(fp);
}

void raw_write8(char *file, kal_uint8* src, kal_uint32 w, kal_uint32 h)
{
	FILE *fp;
	unsigned int index;
	
	/* open the file */
	if ((fp = fopen(file,"wb")) == NULL) {
		printf("Error opening file %s.\n",file);
		exit(1);
	}
	
	for(index=0; index<w*h; index++) 
	{
		fputc((char)src[index],fp);
	}
	
	fclose(fp);
}

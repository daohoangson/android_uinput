#ifndef UTIL_H
#define UTIL_H

#include <stdbool.h>

#ifndef PATH_MAX
#define PATH_MAX 255
#endif

bool write_idc(const char* device_name);

// fb ideas from https://code.google.com/p/android-fb2png/
struct fb {
	unsigned int bpp;
	unsigned int size;
	unsigned int width;
	unsigned int height;
	unsigned int red_offset;
	unsigned int red_length;
	unsigned int blue_offset;
	unsigned int blue_length;
	unsigned int green_offset;
	unsigned int green_length;
	unsigned int alpha_offset;
	unsigned int alpha_length;
	void* mapbase;
	ssize_t mapsize;
	void* base;
};
struct fb_buf {
	void* data;
	size_t size;
};

#define PIXEL_FORMAT_UNKNOWN 0
#define PIXEL_FORMAT_RGB_565 16
#define PIXEL_FORMAT_RGB_888 24
#define PIXEL_FORMAT_RGBX_8888 32

typedef struct rgb565 {
        short b:5;
        short g:6;
        short r:5;
} rgb565_t;

typedef struct rgb888 {
        char r;
        char g;
        char b;
} rgb888_t;

typedef struct rgbx8888 {
        char r;
        char g;
        char b;
        char a;
} rgbx8888_t;

size_t fb2png(void* out_data, size_t out_size);

#endif

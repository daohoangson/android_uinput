#include <errno.h>
#include <android/bitmap.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#include "log.h"
#include "png.h"
#include "util.h"

size_t write_line(const char* line, FILE* fp) {
	return fwrite(line, sizeof(line[0]), strlen(line), fp);
}

bool write_idc(const char* device_name) {
	char* chmod_dir = "/data/system/devices";
	char* idc_dir = "/data/system/devices/idc";
	char idc_path[PATH_MAX]; // to build path to idc file
	char cmd[PATH_MAX * 2]; // to prepare command to execute
	char* line; // to prepare line to write to idc file
	bool retval = false;

	sprintf(idc_path, "%s/%s.idc", idc_dir, device_name);
	if (access(idc_path, F_OK) != -1) {
		// file exists, do nothing
		LOGV("idc_path(%s) exists", idc_path);
		return true;
	}

	sprintf(cmd, "su -c \"mkdir -p %s\"", idc_dir);
	system(cmd);

	// TODO: keep track of existing permissions
	sprintf(cmd, "su -c \"chmod -R 0777 %s\"", chmod_dir);
	system(cmd);

	FILE* fp = fopen(idc_path, "w");
	if (fp == NULL) {
		LOGE("write_idc -> fopen(%s) failed: %s", idc_path, strerror(errno));
		goto write_idc_cleanup;
	}

	write_line("touch.deviceType = touchScreen\n", fp);
	write_line("touch.orientationAware = 1\n", fp);

	fclose(fp);
	retval = true;

	// chmod the idc file but keep us as the file owner
	// this may cause problem in multi-user environment?
	sprintf(cmd, "su -c \"chmod 0777 %s\"", idc_path);
	system(cmd);

	write_idc_cleanup:

	// TODO: do some cleanup

	return retval;
}

bool fb_get_device(struct fb* fb) {
	int fd;
	size_t bytespp;
	size_t offset;
	struct fb_var_screeninfo vinfo;
	bool retval = false;

	char* fb0_path = "/dev/graphics/fb0";
	char cmd[PATH_MAX * 2]; // to prepare command to execute

	fd = open(fb0_path, O_RDONLY);
	if (fd < 0) {
		// TODO: keep track of existing permissions
		sprintf(cmd, "su -c \"chmod -R 0666 %s\"", fb0_path);
		system(cmd);

		// re-open the fb0 after trying to chmod
		fd = open(fb0_path, O_RDONLY);
	}

	if (fd < 0) {
		LOGE("fb_get_device -> open failed: %s", strerror(errno));
		goto fb_get_device_cleanup;
	}

	if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo) < 0) {
		LOGE("fb_get_device -> ioctl(FBIOGET_VSCREENINFO) failed: %s", strerror(errno));
		goto fb_get_device_cleanup;
	}

	bytespp = vinfo.bits_per_pixel / 8;

	fb->bpp = vinfo.bits_per_pixel;
	fb->size = vinfo.xres * vinfo.yres * bytespp;
	fb->width = vinfo.xres;
	fb->height = vinfo.yres;
	fb->red_offset = vinfo.red.offset;
	fb->red_length = vinfo.red.length;
	fb->green_offset = vinfo.green.offset;
	fb->green_length = vinfo.green.length;
	fb->blue_offset = vinfo.blue.offset;
	fb->blue_length = vinfo.blue.length;
	fb->alpha_offset = vinfo.transp.offset;
	fb->alpha_length = vinfo.transp.length;
	LOGV("fb_get_device: w=%d, h=%d, bpp=%d", fb->width, fb->height, fb->bpp);

	// https://github.com/android/platform_frameworks_base/blob/master/cmds/screencap/screencap.cpp
	offset = (vinfo.xoffset + vinfo.yoffset * vinfo.xres) * bytespp;

	fb->mapsize = offset + fb->size;
	fb->mapbase = mmap(0, fb->mapsize, PROT_READ, MAP_PRIVATE, fd, 0);
	if (fb->mapbase != MAP_FAILED) {
		fb->base = (void*) ((char*) fb->mapbase + offset);
	}

	if (fb->base == NULL) {
		LOGE("fb_get_device -> mmap failed: %s", strerror(errno));
		goto fb_get_device_cleanup;
	} else {
		LOGV("fb_get_device -> mmap ok");
	}

	retval = true;

	fb_get_device_cleanup:

	if (fd > 0) {
		close(fd);
	}

	if (retval == false && fb->mapbase != MAP_FAILED) {
		// only free data when the return value is false
		munmap((void *) fb->mapbase, fb->mapsize);
		fb->mapbase = MAP_FAILED;
		fb->base = NULL;
	}

	return retval;
}

int fb_get_format(const struct fb* fb) {
	switch (fb->bpp) {
	case 16:
		return PIXEL_FORMAT_RGB_565;
	case 24:
		return PIXEL_FORMAT_RGB_888;
	case 32:
		return PIXEL_FORMAT_RGBX_8888;
	}

	return PIXEL_FORMAT_UNKNOWN;
}

void fb_png_write_data(png_structp png_ptr, png_bytep data, png_size_t length) {
	struct fb_buf* buf = (struct fb_buf*) png_ptr->io_ptr;
	size_t size_new = buf->size + length;

	if (buf->data != NULL) {
		buf->data = realloc(buf->data, size_new);
	} else {
		buf->data = malloc(size_new);
	}
	if (buf->data == NULL) {
		png_error(png_ptr, "fb_png_write_data -> malloc/realloc failed");
		return;
	}

	memcpy(buf->data + buf->size, data, length);

	buf->size += length;
}

void fb_png_flush(png_structp png_ptr) {
	// intentionally left blank
}

int rgb565_to_rgb888(const char* src, char* dst, size_t pixel) {
	struct rgb565* from;
	struct rgb888* to;

	from = (struct rgb565*) src;
	to = (struct rgb888*) dst;

	int i = 0;
	/* traverse pixel of the row */
	while (i++ < pixel) {
		to->r = from->r;
		to->g = from->g;
		to->b = from->b;

		/* scale */
		to->r <<= 3;
		to->g <<= 2;
		to->b <<= 3;

		to++;
		from++;
	}

	return 0;
}

int rgbx8888_to_rgb888(const char* src, char* dst, size_t pixel) {
	int i;
	struct rgbx8888* from;
	struct rgb888* to;

	from = (struct rgbx8888*) src;
	to = (struct rgb888*) dst;

	i = 0;
	/* traverse pixel of the row */
	while (i++ < pixel) {
		to->r = from->r;
		to->g = from->g;
		to->b = from->b;

		to++;
		from++;
	}

	return 0;
}

size_t fb2png(void* out_data, size_t out_size) {
	struct fb fb;
	fb.mapbase = MAP_FAILED;
	fb.base = NULL;
	char* rgb = NULL;
	struct fb_buf buf = { NULL, 0 };
	png_byte ** volatile rows = NULL;
	png_struct *png = NULL;
	png_info *info = NULL;
	bool retval;

	retval = fb_get_device(&fb);
	if (retval == false) {
		LOGE("fb2png -> fb_get_device() failed");
		goto fb2png_cleanup;
	} else {
		LOGV("fb2png -> fb_get_device() ok");
	}

	size_t rgb_size = fb.width * fb.height * 3;
	rgb = malloc(rgb_size);
	if (rgb == NULL) {
		LOGE("fb2png -> malloc(%d) failed: %s", rgb_size, strerror(errno));
		goto fb2png_cleanup;
	} else {
		LOGV("fb2png -> malloc(%d) ok", rgb_size);
	}

	int fmt = fb_get_format(&fb);
	int convert_result = 0;
	switch (fmt) {
	case PIXEL_FORMAT_RGB_565:
		LOGV("fb2png -> fb_get_format() = PIXEL_FORMAT_RGB_565");
		convert_result = rgb565_to_rgb888(fb.base, rgb, fb.width * fb.height);
		break;
	case PIXEL_FORMAT_RGB_888:
		LOGV("fb2png -> fb_get_format() = PIXEL_FORMAT_RGB_888");
		memcpy(rgb, fb.base, fb.size);
		break;
	case PIXEL_FORMAT_RGBX_8888:
		LOGV("fb2png -> fb_get_format() = PIXEL_FORMAT_RGBX_8888");
		convert_result = rgbx8888_to_rgb888(fb.base, rgb, fb.width * fb.height);
		break;
	default:
		LOGE("fb2png -> fb_get_format() = PIXEL_FORMAT_UNKNOWN");
		goto fb2png_cleanup;
	}
	if (convert_result != 0) {
		LOGE("fb2png -> *_to_rgb888() = %d", convert_result);
		goto fb2png_cleanup;
	} else {
		LOGV("fb2png -> *_to_rgb888() ok");
	}

	int rows_size = fb.height * sizeof rows[0];
	rows = malloc(rows_size);
	if (rows == NULL) {
		LOGE("fb2png -> malloc(%d) failed: %s", rows_size, strerror(errno));
		goto fb2png_cleanup;
	} else {
		LOGV("fb2png -> malloc(%d) ok", rows_size);
	}

	int i;
	for (i = 0; i < fb.height; i++) {
		rows[i] = (png_byte *) rgb + i * fb.width * 3 /*fb.stride*/;
	}

	png = png_create_write_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
	if (png == NULL) {
		LOGE("fb2png -> png_create_write_struct() failed");
		goto fb2png_cleanup;
	}

	info = png_create_info_struct(png);
	if (info == NULL) {
		LOGE("fb2png -> png_create_info_struct() failed");
		goto fb2png_cleanup;
	}

	// ideas from http://stackoverflow.com/questions/1821806/how-to-encode-png-to-buffer-using-libpng
	png_set_write_fn(png, &buf, fb_png_write_data, fb_png_flush);

	png_set_IHDR(png, info, fb.width, fb.height, 8, PNG_COLOR_TYPE_RGB,
			PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT,
			PNG_FILTER_TYPE_DEFAULT);

	png_color_16 white;
	white.gray = (1 << 8) - 1;
	white.red = white.blue = white.green = white.gray;
	png_set_bKGD(png, info, &white);

	png_write_info(png, info);
	png_write_image(png, rows);
	png_write_end(png, info);
	png_destroy_write_struct(&png, &info);

	if (buf.size <= out_size) {
		memcpy(out_data, buf.data, buf.size);
		LOGV("fb2png: copied %d bytes to out_data", buf.size);
		retval = true;
	} else {
		LOGE("fb2png: out_size too small (out_size=%d,buf.size=%d)", out_size, buf.size);
	}

	fb2png_cleanup:

	if (fb.mapbase != MAP_FAILED) {
		munmap((void *) fb.mapbase, fb.mapsize);
	}

	if (rgb != NULL) {
		free(rgb);
	}

	if (buf.data != NULL) {
		free(buf.data);
	}

	if (rows != NULL) {
		free(rows);
	}

	if (retval == true) {
		return buf.size;
	} else {
		return 0;
	}
}

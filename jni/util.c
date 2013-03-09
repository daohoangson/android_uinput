#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "util.h"

bool write_idc(const char* device_name) {
	char* chmod_dir = "/data/system/devices";
	char* idc_dir = "/data/system/devices/idc";
	char idc_path[PATH_MAX]; // to build path to idc file
	char cmd[PATH_MAX * 2]; // to prepare command to execute
	char* line; // to prepare line to write to idc file
	bool written = false;

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
		goto cleanup;
	}

	write_line("touch.deviceType = touchScreen\n", fp);
	write_line("touch.orientationAware = 1\n", fp);

	fclose(fp);
	written = true;

	// chmod the idc file but keep us as the file owner
	// this may cause problem in multi-user environment?
	sprintf(cmd, "su -c \"chmod 0777 %s\"", idc_path);
	system(cmd);

cleanup:

	// TODO: do some cleanup

	return written;
}

size_t write_line(const char* line, FILE* fp) {
	return fwrite(line, sizeof(line[0]), strlen(line), fp);
}

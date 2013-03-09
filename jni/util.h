#ifndef UTIL_H
#define UTIL_H

#include <android/log.h>
#include <stdbool.h>

#define LOGTAG "jni"
#define LOGV(fmt, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOGTAG, fmt, ##__VA_ARGS__);
#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, LOGTAG, fmt, ##__VA_ARGS__);
#define LOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOGTAG, fmt, ##__VA_ARGS__);
#define LOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, LOGTAG, fmt, ##__VA_ARGS__);
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOGTAG, fmt, ##__VA_ARGS__);

#ifndef PATH_MAX
#define PATH_MAX 255
#endif

bool write_idc(const char* device_name);
size_t write_line(const char* line, FILE* fp);

#endif

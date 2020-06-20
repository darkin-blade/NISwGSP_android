#include "common.h"

static char sprint_buf[1024];
static char tmp_fmt[1024];

#if !defined(UBUNTU)
extern JNIEnv * total_env;
#endif

void print_message(const char *fmt, ...) {
  va_list args;
  int n;
  va_start(args, fmt);

#if defined(UBUNTU)
  sprintf(tmp_fmt, "\033[1;32m%s\33[0m\n", fmt);
#else
  sprintf(tmp_fmt, "%s", fmt);
#endif

  n = vsprintf(sprint_buf, tmp_fmt, args);
  va_end(args);

#if defined(UBUNTU)
  write(STDOUT_FILENO, sprint_buf, n);
#else
  __android_log_vprint(ANDROID_LOG_INFO, "fuck", tmp_fmt, args);
  // 加载到控制台
  if (total_env != NULL) {
    jclass clazz = total_env->FindClass("com.example.niswgsp_1/MainActivity");
    if (clazz != NULL) {
        jmethodID id = total_env->GetStaticMethodID(clazz, "callback", "(Ljava/lang/String;)V");
        if (id != NULL) {
//            __android_log_print(ANDROID_LOG_INFO, "fuck", "debug");
            jstring msg = total_env->NewStringUTF(sprint_buf);
            total_env->CallStaticVoidMethod(clazz, id, msg);
        } else {
            assert(0);
        }
    } else {
        assert(0);
    }
  }
#endif
}

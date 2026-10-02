/*
 * PTY 桥接：openptm → fork/exec → 与 Java 层 (com.aos.agent.terminal.JNI) 对接。
 * Android bionic 无 openpty()，按 Termux 同款思路直接操作 /dev/ptmx。
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>

static int ptm_open(void) {
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) return -1;

    int ptn = -1;
    if (ioctl(ptm, TIOCGPTN, &ptn) != 0) {
        close(ptm);
        return -1;
    }

    int unlock = 0;
    if (ioctl(ptm, TIOCSPTLCK, &unlock) != 0) {
        close(ptm);
        return -1;
    }

    return ptm;
}

static void throw_errno(JNIEnv *env, const char *what) {
    char message[256];
    snprintf(message, sizeof(message), "%s: %s", what, strerror(errno));
    jclass io_exception = (*env)->FindClass(env, "java/io/IOException");
    if (io_exception != NULL) {
        (*env)->ThrowNew(env, io_exception, message);
    }
}

static char **copy_string_array(JNIEnv *env, jobjectArray array) {
    jsize length = (*env)->GetArrayLength(env, array);
    char **strings = calloc((size_t) length + 1, sizeof(char *));
    if (strings == NULL) return NULL;

    for (jsize i = 0; i < length; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        strings[i] = strdup(utf != NULL ? utf : "");
        (*env)->ReleaseStringUTFChars(env, item, utf);
    }
    return strings;
}

static void free_string_array(char **strings) {
    if (strings == NULL) return;
    for (char **cursor = strings; *cursor != NULL; cursor++) free(*cursor);
    free(strings);
}

JNIEXPORT jint JNICALL
Java_com_aos_agent_terminal_JNI_createSubprocess(
        JNIEnv *env, jclass clazz,
        jstring cmd, jstring cwd,
        jobjectArray args, jobjectArray envVars,
        jintArray processId, jint rows, jint columns) {

    int ptm = ptm_open();
    if (ptm < 0) {
        throw_errno(env, "open /dev/ptmx");
        return -1;
    }

    int ptn = -1;
    ioctl(ptm, TIOCGPTN, &ptn);

    const char *cmd_utf = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *cwd_utf = (*env)->GetStringUTFChars(env, cwd, NULL);
    char **argv = copy_string_array(env, args);
    char **envp = copy_string_array(env, envVars);

    pid_t pid = fork();
    if (pid < 0) {
        throw_errno(env, "fork");
        free_string_array(argv);
        free_string_array(envp);
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        close(ptm);
        return -1;
    }

    if (pid == 0) {
        char pts_path[64];
        snprintf(pts_path, sizeof(pts_path), "/dev/pts/%d", ptn);
        int pts = open(pts_path, O_RDWR);
        if (pts < 0) _exit(127);

        setsid();
        ioctl(pts, TIOCSCTTY, 0);

        dup2(pts, STDIN_FILENO);
        dup2(pts, STDOUT_FILENO);
        dup2(pts, STDERR_FILENO);
        if (pts > STDERR_FILENO) close(pts);
        close(ptm);

        if (cwd_utf != NULL && chdir(cwd_utf) != 0) {
            // 工作目录打不开不致命，落在根目录继续跑 shell
        }

        execve(cmd_utf, argv, envp);
        _exit(127);
    }

    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = rows;
    size.ws_col = columns;
    ioctl(ptm, TIOCSWINSZ, &size);

    jint out_pid = (jint) pid;
    if (processId != NULL) {
        (*env)->SetIntArrayRegion(env, processId, 0, 1, &out_pid);
    }

    free_string_array(argv);
    free_string_array(envp);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);

    return ptm;
}

JNIEXPORT void JNICALL
Java_com_aos_agent_terminal_JNI_setPtyWindowSize(
        JNIEnv *env, jclass clazz, jint fd, jint rows, jint columns) {
    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = rows;
    size.ws_col = columns;
    ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL
Java_com_aos_agent_terminal_JNI_waitFor(JNIEnv *env, jclass clazz, jint processId) {
    int status = 0;
    if (waitpid(processId, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_aos_agent_terminal_JNI_close(JNIEnv *env, jclass clazz, jint fileDescriptor) {
    close(fileDescriptor);
}

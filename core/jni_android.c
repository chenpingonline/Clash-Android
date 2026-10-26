#include <jni.h>
#include <stdlib.h>
extern char* ClashCall(char* input);
static JavaVM* vm;
static jclass core_class;
static jmethodID protect_method;
static jmethodID owner_method;
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* java_vm, void* reserved) {
    JNIEnv* env;
    vm = java_vm;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = (*env)->FindClass(env, "cn/nascp/clash/core/NativeCore");
    if (!local) return JNI_ERR;
    core_class = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    if (!core_class) return JNI_ERR;
    protect_method = (*env)->GetStaticMethodID(env, core_class, "protectSocket", "(I)Z");
    owner_method = (*env)->GetStaticMethodID(env, core_class, "connectionOwnerUid", "(ILjava/lang/String;ILjava/lang/String;I)I");
    return protect_method && owner_method ? JNI_VERSION_1_6 : JNI_ERR;
}
int ClashProtectSocket(int fd) {
    JNIEnv* env;
    int attached = 0;
    jint status = (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return 0;
        attached = 1;
    } else if (status != JNI_OK) return 0;
    jboolean ok = (*env)->CallStaticBooleanMethod(env, core_class, protect_method, fd);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); ok = JNI_FALSE; }
    if (attached) (*vm)->DetachCurrentThread(vm);
    return ok == JNI_TRUE;
}
int ClashConnectionOwnerUid(int protocol, const char* source, int sourcePort, const char* destination, int destinationPort) {
    JNIEnv* env;
    int attached = 0;
    jint status = (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return -1;
        attached = 1;
    } else if (status != JNI_OK) return -1;
    jstring local = (*env)->NewStringUTF(env, source);
    jstring remote = (*env)->NewStringUTF(env, destination);
    jint uid = -1;
    if (local && remote && !(*env)->ExceptionCheck(env)) {
        uid = (*env)->CallStaticIntMethod(env, core_class, owner_method, protocol, local, sourcePort, remote, destinationPort);
    }
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); uid = -1; }
    if (local) (*env)->DeleteLocalRef(env, local);
    if (remote) (*env)->DeleteLocalRef(env, remote);
    if (attached) (*vm)->DetachCurrentThread(vm);
    return uid;
}
JNIEXPORT jstring JNICALL Java_cn_nascp_clash_core_NativeCore_call(JNIEnv* env, jobject self, jstring input) {
    const char* raw = (*env)->GetStringUTFChars(env, input, 0);
    if (!raw) return NULL;
    char* result = ClashCall((char*)raw);
    (*env)->ReleaseStringUTFChars(env, input, raw);
    jstring output = (*env)->NewStringUTF(env, result);
    free(result);
    return output;
}

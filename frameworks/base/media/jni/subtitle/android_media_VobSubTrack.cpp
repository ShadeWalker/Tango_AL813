#include <stdio.h>
#include <unistd.h>
#define LOG_NDEBUG 0
#define LOG_TAG "VobSubTrack-JNI"

#include <utils/Log.h>
#include <nativehelper/jni.h>
#include <nativehelper/JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>
#include <SUBParser.h>
#include <VOBSubtitleParser.h>


using namespace android;
// ----------------------------------------------------------------------------

static const char* const kClassVobSubTrack =
        "android/media/VobSubTrack";


static const char* const kIllegalStateException =
        "java/lang/IllegalStateException";

static const char* const kIllegalArgumentException =
        "java/lang/IllegalArgumentException";


struct fields_t {
    jfieldID    context;
};
static fields_t fields;


// This function gets a field ID, which in turn causes class initialization.
// It is called from a static block in VobSubTrack, which won't run until the
// first time an instance of this class is used.
static void
android_media_VobSubTrack_native_init(JNIEnv *env)
{
    ALOGV("android_media_VobSubTrack_native_init");
    jclass clazz = env->FindClass(kClassVobSubTrack);
    if (clazz == NULL) {
        return;
    }

    fields.context = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.context == NULL) {
        return;
    }
}

//Get sub file path from file descriptor
static jstring
android_media_VobSubTrack_getPathFromFileDescriptor(JNIEnv *env, jobject thiz, jobject fileDescriptor)
{
    ALOGV("android_media_VobSubTrack_getPathFromFileDescriptor");
    int fd = jniGetFDFromFileDescriptor(env, fileDescriptor);
    const size_t SIZE = 256;
    char procPath[SIZE];
    char fileSource[SIZE];
	
    snprintf(procPath, SIZE, "/proc/%d/fd/%d", gettid(), fd);
    ALOGD("procPath: %s", procPath);
    int len = readlink(procPath, fileSource, SIZE);   
    if (len< 0) {
    	ALOGE("readlink fail");
    	fileSource[0]= 0;
    } else if(len > 255) {
      	ALOGE("file path is too long");
        fileSource[0]= 0;
    } else {
    	fileSource[len] = 0;
    }
	
    ALOGD("idx file path: %s", fileSource);
    char *p = strstr(fileSource, ".idx");
    if (p==NULL) {
        env->NewStringUTF(""); 
    }
    strcpy(p, ".sub");
    ALOGD("sub path: %s", fileSource);
	
    return env->NewStringUTF(fileSource);
}

static SUBParser *getSubParser(JNIEnv *env, jobject thiz)
{
    return (SUBParser *)env->GetLongField(thiz, fields.context);
}

static void setSubParser(JNIEnv *env, jobject thiz, SUBParser *subParser)
{
    env->SetLongField(thiz, fields.context, (jlong)subParser);
}

static void
android_media_VobSubTrack_native_setup_SubParser(JNIEnv *env, jobject thiz, jstring path)
{
    ALOGV("android_media_VobSubTrack_native_setup_SubParser");

    if (path == NULL) {
        jniThrowException(env, kIllegalArgumentException, NULL);
        return;
    }	
	
    const char *subPath = env->GetStringUTFChars(path, NULL);
    if (subPath == NULL) {
        return;
    }

    SUBParser *subParser = new SUBParser(subPath);

    env->ReleaseStringUTFChars(path, subPath);

    if (subParser == NULL) {
        ALOGE("can't create subParser object");
        return;
    }

    setSubParser(env, thiz, subParser);
}

static void
android_media_VobSubTrack_setVobPalette(JNIEnv *env, jobject thiz, jintArray palette)
{
    ALOGV("android_media_VobSubTrack_setVobPalette");
    SUBParser *subParser = getSubParser(env, thiz);
    if (subParser == NULL) {
        ALOGE("SubPaser is NULL pointer");
        jniThrowException(env, kIllegalStateException, "Paser is NULL pointer");
    }
	
    if (palette == NULL) {
        ALOGE("palette is NULL pointer");
        return; 
    }
	 
    jint *nPalette = env->GetIntArrayElements(palette, NULL);
    if (nPalette == NULL) {
        ALOGE("GetIntArrayElements from palette is NULL pointer");
        return;
    }
    subParser->vSetVOBPalette(nPalette);
    env->ReleaseIntArrayElements(palette, nPalette, 0);
	
    return;
}


static jintArray
android_media_VobSubTrack_executeParser(JNIEnv *env, jobject thiz, jint offset)
{
    
    ALOGV("android_media_VobSubTrack_executeParser");
    SUBParser *subParser = getSubParser(env, thiz);
    if (subParser == NULL) {
        ALOGE("SubPaser is NULL pointer");
        jniThrowException(env, kIllegalStateException, "Paser is NULL pointer");
    }

    //parse sub file at offset
    subParser->parse(offset);

    jintArray jArray = env->NewIntArray(3);
    jint *nArray = env->GetIntArrayElements(jArray, NULL);
    //get  notify elements
    nArray[0]  = subParser->iGetFileIdx();
    nArray[1] = subParser->iGetSubtitleWidth();
    nArray[2] = subParser->iGetSubtitleHeight();
    ALOGD("index: %d, width: %d, height: %d\n", nArray[0], nArray[1], nArray[2]);

    env->ReleaseIntArrayElements(jArray, nArray, 0);

    //increase index
    subParser->incFileIdx();  

    return jArray;
}

static void
android_media_VobSubTrack_native_finalized_SubParser(JNIEnv *env, jobject thiz)
{
   ALOGV("android_media_VobSubTrack_native_finalized_SubParser");
   SUBParser *subParser = getSubParser(env, thiz);
   if (subParser == NULL) {
       return;
   }
   delete subParser;
   setSubParser(env, thiz, NULL);
}


// ----------------------------------------------------------------------------

// Dalvik VM type signatures
static JNINativeMethod gMethods[] = {
    {   "native_init",
        "()V",
        (void *)android_media_VobSubTrack_native_init
    }, 
    {   "getPathFromFileDescriptor",
        "(Ljava/io/FileDescriptor;)Ljava/lang/String;",
        (void *)android_media_VobSubTrack_getPathFromFileDescriptor
    }, 
    {   "native_setup_SubParser",
        "(Ljava/lang/String;)V",
        (void *)android_media_VobSubTrack_native_setup_SubParser
    }, 
    {   "setVobPalette",
        "([I)V",
        (void *)android_media_VobSubTrack_setVobPalette
    },
    {   "executeParser",
        "(I)[I",
        (void *)android_media_VobSubTrack_executeParser
    },
    {   "native_finalized_SubParser",
        "()V",
        (void *)android_media_VobSubTrack_native_finalized_SubParser
    }, 
};

static const char* const kClassPathName = "android/media/VobSubTrack";

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;
    jclass clazz;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed\n");
        return result;
    }
    assert(env != NULL);

    clazz = env->FindClass(kClassPathName);
    if (clazz == NULL) {
        ALOGE("Can't find %s", kClassPathName);
        return result;
    }

    if (AndroidRuntime::registerNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods)) < 0)
        return result;

    /* success -- return valid version number */
    return JNI_VERSION_1_4;
}


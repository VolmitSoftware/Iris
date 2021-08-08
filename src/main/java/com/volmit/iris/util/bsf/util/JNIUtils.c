/*
 * Copyright 2003,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>

#include <jni.h>

#include "JNIUtils.h"

/*****
 *
 * NOTE: Whereever I think an exception may occur, I need to check
 * whether it did and recover appropriately .. otherwise the behavior
 * of JNI is undefined!
 *
 *****/

/* throw a BSFException with the given code and message. */
void bsf_exception (JNIEnv *jenv, int code, char *msg) {
  jclass bsfexceptclass = 
    (*jenv)->FindClass (jenv, "org/apache/bsf/BSFException");
  (*jenv)->ThrowNew (jenv, bsfexceptclass, msg);
}

/* cvt a pointer to a Long object whose value is the pointer value */
jobject bsf_pointer2longobj (JNIEnv *jenv, void *ptr) {
  return bsf_makeLong (jenv, (long) ptr);
}

/* cvt a Long object whose value is the pointer value to the pointer */
void *bsf_longobj2pointer (JNIEnv *jenv, jobject lobj) {
  jclass longclass = (*jenv)->FindClass (jenv, "java/lang/Long");
  jmethodID mi = (*jenv)->GetMethodID (jenv, longclass, "longValue", "()J");
  void *ptr = (void *) (*jenv)->CallLongMethod (jenv, lobj, mi);
  return ptr;
}

/* convert an object to a string obj */
jstring bsf_obj2jstring (JNIEnv *jenv, jobject obj) {
  jclass objclass = (*jenv)->GetObjectClass (jenv, obj);
  jmethodID tostr = (*jenv)->GetMethodID (jenv, objclass, "toString",
					  "()Ljava/lang/String;");
  jstring strobj = (jstring) (*jenv)->CallObjectMethod (jenv, obj, tostr);
  return strobj;
}

/* cvt an object to a c-string .. wastes memory, but useful for debug */
const char *bsf_obj2cstring (JNIEnv *jenv, jobject obj) {
  return (*jenv)->GetStringUTFChars (jenv, 
				     bsf_obj2jstring (jenv, obj),
				     0);
}

/* call the named method with the given args on the given bean */
jobject bsf_createbean (JNIEnv *jenv, char *classname, jobjectArray args) {
  jclass cl;
  jmethodID mid;
  jobject result;

  /* find the BSFUtils.createBean method ID if needed */
  cl = (*jenv)->FindClass (jenv, "org/apache/bsf/util/EngineUtils");
  mid = (*jenv)->GetStaticMethodID (jenv, cl, "createBean",
      			      "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
  if ((*jenv)->ExceptionOccurred (jenv)) {
    (*jenv)->ExceptionDescribe (jenv);
    (*jenv)->ExceptionClear (jenv);
    return 0;
  }

  result = (*jenv)->CallStaticObjectMethod (jenv, cl, mid, 
					    (*jenv)->NewStringUTF (jenv,
								   classname),
					    args);
  if ((*jenv)->ExceptionOccurred (jenv)) {
    (*jenv)->ExceptionDescribe (jenv);
    (*jenv)->ExceptionClear (jenv);
    /* I should really throw a BSF exception here and the caller should
       check whether an exception was thrown and in that case return.
       later. */
    return 0;
  } else {
    return result;
  }
}

/* call the named method with the given args on the given bean */
jobject bsf_callmethod (JNIEnv *jenv, jobject target,
			char *methodname, jobjectArray args) {
  jclass cl;
  jmethodID mid;
  jobject result;

  /* find the BSFUtils.callBeanMethod method ID if needed */
  cl = (*jenv)->FindClass (jenv, "org/apache/bsf/util/EngineUtils");
  mid = (*jenv)->GetStaticMethodID (jenv, cl, "callBeanMethod",
				      "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
  if ((*jenv)->ExceptionOccurred (jenv)) {
      (*jenv)->ExceptionDescribe (jenv);
      (*jenv)->ExceptionClear (jenv);
      return 0;
    }
  result = (*jenv)->CallStaticObjectMethod (jenv, cl, mid, target,
					    (*jenv)->NewStringUTF (jenv,
								   methodname),
					    args);
  if ((*jenv)->ExceptionOccurred (jenv)) {
    (*jenv)->ExceptionDescribe (jenv);
    (*jenv)->ExceptionClear (jenv);
    /* I should really throw a BSF exception here and the caller should
       check whether an exception was thrown and in that case return.
       later. */
    return 0;
  } else {
    return result;
  }
}

/* return the named bean from the given mgr's bean registry */
jobject bsf_lookupbean (JNIEnv *jenv, jobject mgr, char *beanname) {
  jmethodID lookupMethod;
  jobject result;

  jclass bsfmgrclass = (*jenv)->GetObjectClass (jenv, mgr);
  lookupMethod = 
      (*jenv)->GetMethodID (jenv, bsfmgrclass, "lookupBean",
			    "(Ljava/lang/String;)Ljava/lang/Object;");
  if ((*jenv)->ExceptionOccurred (jenv)) {
      (*jenv)->ExceptionDescribe (jenv);
      (*jenv)->ExceptionClear (jenv);
      return 0;
    }

  result = (*jenv)->CallObjectMethod (jenv, mgr, lookupMethod, 
				      (*jenv)->NewStringUTF (jenv, beanname));
  if ((*jenv)->ExceptionOccurred (jenv)) {
    (*jenv)->ExceptionDescribe (jenv);
    (*jenv)->ExceptionClear (jenv);
    /* I should really throw a BSF exception here and the caller should
       check whether an exception was thrown and in that case return.
       later. */
    return 0;
  } else {
    return result;
  }
}

/* return the type signature string component for the given type:
   I for ints, J for long, ... */
char *bsf_getTypeSignatureString (JNIEnv *jenv, jclass objclass) {
  jclass cl = 0;
  jmethodID mid = 0;
  jstring str;

  cl = (*jenv)->FindClass (jenv, "org/apache/bsf/util/EngineUtils");
  mid = (*jenv)->GetStaticMethodID (jenv, cl, "getTypeSignatureString",
      			      "(Ljava/lang/Class;)Ljava/lang/String;");
  if ((*jenv)->ExceptionOccurred (jenv)) {
    (*jenv)->ExceptionDescribe (jenv);
    (*jenv)->ExceptionClear (jenv);
    return 0;
  }
  str = (jstring) (*jenv)->CallStaticObjectMethod (jenv, cl, mid, objclass);
  return (char *) bsf_obj2cstring (jenv, str);
}

/* make objects from primitives */

jobject bsf_makeBoolean (JNIEnv *jenv, int val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Boolean");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(Z)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jboolean) val);
}

jobject bsf_makeByte (JNIEnv *jenv, int val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Byte");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(B)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jbyte) val);
}

jobject bsf_makeShort (JNIEnv *jenv, int val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Short");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(S)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jshort) val);
}

jobject bsf_makeInteger (JNIEnv *jenv, int val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Integer");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(I)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jint) val);
}

jobject bsf_makeLong (JNIEnv *jenv, long val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Long");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(J)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jlong) val);
}

jobject bsf_makeFloat (JNIEnv *jenv, float val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Float");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(F)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jfloat) val);
}

jobject bsf_makeDouble (JNIEnv *jenv, double val) {
  jclass classobj = (*jenv)->FindClass (jenv, "java/lang/Double");
  jmethodID constructor = 
    (*jenv)->GetMethodID (jenv, classobj, "<init>", "(D)V");
  return (*jenv)->NewObject (jenv, classobj, constructor, (jdouble) val);
}

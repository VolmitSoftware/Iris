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

#include <jni.h>
#if defined(__cplusplus)
extern "C" {
#endif

/* throw a BSFException with the given message  */
extern void bsf_exception (JNIEnv *jenv, int code, char *msg);

/* cvt a pointer to a Long object whose value is the pointer value */
extern jobject bsf_pointer2longobj (JNIEnv *jenv, void *ptr);

/* cvt a Long object whose value is the pointer value to the pointer */
extern void *bsf_longobj2pointer (JNIEnv *jenv, jobject lobj);

/* convert an object to a string obj */
jstring bsf_obj2jstring (JNIEnv *jenv, jobject obj);

/* cvt an object to a c-string .. wastes memory, but useful for debug */
const char *bsf_obj2cstring (JNIEnv *jenv, jobject obj);

/* create an instance of the named class with the given args */
extern jobject bsf_createbean (JNIEnv *jenv, char *methodname, 
                               jobjectArray args);

/* call the named method with the given args on the given bean */
extern jobject bsf_callmethod (JNIEnv *jenv, jobject target,
			       char *methodname, jobjectArray args);

/* return the named bean from the given mgr's bean registry */
extern jobject bsf_lookupbean (JNIEnv *jenv, jobject mgr, char *beanname);

/* return the type signature string component for the given type:
   I for ints, J for long, ... */
extern char *bsf_getTypeSignatureString (JNIEnv *jenv, jclass objclass);

/* make objects from primitives */
extern jobject bsf_makeBoolean (JNIEnv *jenv, int val);
extern jobject bsf_makeByte (JNIEnv *jenv, int val);
extern jobject bsf_makeShort (JNIEnv *jenv, int val);
extern jobject bsf_makeInteger (JNIEnv *jenv, int val);
extern jobject bsf_makeLong (JNIEnv *jenv, long val);
extern jobject bsf_makeFloat (JNIEnv *jenv, float val);
extern jobject bsf_makeDouble (JNIEnv *jenv, double val);
#if defined(__cplusplus)
}
#endif

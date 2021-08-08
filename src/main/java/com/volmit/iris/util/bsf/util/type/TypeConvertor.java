/*
 * Copyright 2004,2004 The Apache Software Foundation.
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

package com.volmit.iris.util.bsf.util.type;

/**
 * A <em>TypeConvertor</em> is used to convert an object of one type to
 * one of another type. The convertor is invoked with the class of the
 * from object, the desired class, and the from object itself. The
 * convertor must return a new object of the desired class.
 * 
 * @author   Sanjiva Weerawarana
 * @see      TypeConvertorRegistry
 */
public interface TypeConvertor {
  public Object convert (Class from, Class to, Object obj);
  public String getCodeGenString ();
}

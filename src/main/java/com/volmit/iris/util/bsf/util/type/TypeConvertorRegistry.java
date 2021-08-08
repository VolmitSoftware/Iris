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

import java.awt.Color;
import java.awt.Font;
import java.util.Hashtable;

/**
 * The <em>TypeConvertorRegistry</em> is the registry of type convertors.
 * It has lookup and register capabilities based on the types to be
 * converted as well as by some object key.
 * 
 * @author   Sanjiva Weerawarana
 * @author   Matthew J. Duftler
 * @see      TypeConvertorRegistry
 */
public class TypeConvertorRegistry {
  Hashtable reg = new Hashtable ();
  Hashtable keyedReg = new Hashtable ();

  // register some standard convertors at construction time
  public TypeConvertorRegistry () {
	// no-op convertors: cvt from primitive wrappers to the object wrapper
	TypeConvertor tc = new TypeConvertor () {
	  public Object convert (Class from, Class to, Object obj) {
	      return obj;
	  }
		
	  public String getCodeGenString() {
		return "(Class from, Class to, Object obj) {\n" +
			   "return obj;\n" +
			   "}";
	  }
	};
	register (Boolean.class, boolean.class, tc);
	register (boolean.class, Boolean.class, tc);
	register (Byte.class, byte.class, tc);
	register (byte.class, Byte.class, tc);
	register (Character.class, char.class, tc);
	register (char.class, Character.class, tc);
	register (Short.class, short.class, tc);
	register (short.class, Short.class, tc);
	register (Integer.class, int.class, tc);
	register (int.class, Integer.class, tc);
	register (Long.class, long.class, tc);
	register (long.class, Long.class, tc);
	register (Float.class, float.class, tc);
	register (float.class, Float.class, tc);
	register (Double.class, double.class, tc);
	register (double.class, Double.class, tc);

	// object to string: the registry special cases this one as the backup
	// if the target is string and there is no special convertor available
	// otherwise
	tc = new TypeConvertor () {
	  public Object convert (Class from, Class to, Object obj) {
	      return (obj == null) ? "(null)" : obj.toString ();
	  }
		
		public String getCodeGenString() {
		return "(Class from, Class to, Object obj) {\n" +
			   "return (obj == null) ? \"(null)\" : obj.toString ();\n" +
			   "}";
		}
	};
	register (Object.class, String.class, tc);
	
	// convert strings to various primitives (both their object versions
	// and wrappers for primitive versions)
	tc = new TypeConvertor () {
	  public Object convert (Class from, Class to, Object obj) {
		String str = (String) obj;
		if (to == Boolean.class || to == boolean.class) {
		  return Boolean.valueOf (str);
		} else if (to == Byte.class || to == byte.class) {
		  return Byte.valueOf (str);
		} else if (to == Character.class || to == char.class) {
		  return new Character (str.charAt (0));
		} else if (to == Short.class || to == short.class) {
		  return Short.valueOf (str);
		} else if (to == Integer.class || to == int.class) {
		  return Integer.valueOf (str);
		} else if (to == Long.class || to == long.class) {
		  return Long.valueOf (str);
		} else if (to == Float.class || to == float.class) {
		  return Float.valueOf (str);
		} else if (to == Double.class || to == double.class) {
		  return Double.valueOf (str);
		} else {
		  return null;
		}
	  }
		
		public String getCodeGenString() {
		return "(Class from, Class to, Object obj) {\n" +
			   "String str = (String) obj;\n" +
			   "if (to == Boolean.class || to == boolean.class) {\n" +
			   "return Boolean.valueOf (str);\n" +
			   "} else if (to == Byte.class || to == byte.class) {\n" +
			   "return Byte.valueOf (str);\n" +
			   "} else if (to == Character.class || to == char.class) {\n" +
			   "return new Character (str.charAt (0));\n" +
			   "} else if (to == Short.class || to == short.class) {\n" +
			   "return Short.valueOf (str);\n" +
			   "} else if (to == Integer.class || to == int.class) {\n" +
			   "return Integer.valueOf (str);\n" +
			   "} else if (to == Long.class || to == long.class) {\n" +
			   "return Long.valueOf (str);\n" +
			   "} else if (to == Float.class || to == float.class) {\n" +
			   "return Float.valueOf (str);\n" +
			   "} else if (to == Double.class || to == double.class) {\n" +
			   "return Double.valueOf (str);\n" +
			   "} else {\n" +
			   "return null;\n" +
			   "}\n" +
			   "}";
	  }
	};
	register (String.class, boolean.class, tc);
	register (String.class, Boolean.class, tc);
	register (String.class, byte.class, tc);
	register (String.class, Byte.class, tc);
	register (String.class, char.class, tc);
	register (String.class, Character.class, tc);
	register (String.class, short.class, tc);
	register (String.class, Short.class, tc);
	register (String.class, int.class, tc);
	register (String.class, Integer.class, tc);
	register (String.class, long.class, tc);
	register (String.class, Long.class, tc);
	register (String.class, float.class, tc);
	register (String.class, Float.class, tc);
	register (String.class, double.class, tc);
	register (String.class, Double.class, tc);

	// strings to fonts
	tc = new TypeConvertor () {
	  public Object convert (Class from, Class to, Object obj) {
	      return Font.decode ((String) obj);
	  }
		
	  public String getCodeGenString() {
		return "(Class from, Class to, Object obj) {\n" +
			   "return Font.decode ((String) obj);\n" +
			   "}";
	  }
	};
	register (String.class, Font.class, tc);

	// strings to colors
	tc = new TypeConvertor () {
	  public Object convert (Class from, Class to, Object obj) {
	      return Color.decode ((String) obj);
	  }
		
		public String getCodeGenString() {
		return "(Class from, Class to, Object obj) {\n" +
			   "return Color.decode ((String) obj);\n" +
			   "}";
		}
	};
	register (String.class, Color.class, tc);
  }
  // lookup a convertor
  public TypeConvertor lookup (Class from, Class to) {
	String key = from.getName () + " -> " + to.getName ();
	TypeConvertor tc = (TypeConvertor) reg.get (key);
	if (tc == null) {
	  if (from != void.class
		  && from != Void.class
		  && to == String.class) {
		// find the object -> string convertor
		return lookup (Object.class, String.class);
	  }
	}
	return tc;
  }
  // lookup a convertor by key
  public TypeConvertor lookupByKey (Object key) {
	return (TypeConvertor) keyedReg.get (key);
  }
  // register a convertor
  public void register (Class from, Class to, TypeConvertor convertor) {
	String key = from.getName () + " -> " + to.getName ();
	reg.put (key, convertor);
  }
  // register a convertor by key
  public void registerByKey (Object key, TypeConvertor convertor) {
	keyedReg.put (key, convertor);
  }
}

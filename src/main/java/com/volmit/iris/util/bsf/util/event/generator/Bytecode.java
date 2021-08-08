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

package com.volmit.iris.util.bsf.util.event.generator;

/**
 * Bytecode handling utilities
 *
 * Handle standard byte arrays as defined in Java VM and Class File
 *
 * 5 April 1999 - functions to append Class File byte subarrays
 *                into a Class File byte array
 *
 * @author Richard F. Boehme
 *
 */
public class Bytecode
{
  // Constant Pool Item Codes
  public static final byte C_Utf8               = 0x01;   //  1
  public static final byte C_Integer            = 0x03;   //  3
  public static final byte C_Float              = 0x04;   //  4
  public static final byte C_Long               = 0x05;   //  5
  public static final byte C_Double             = 0x06;   //  6
  public static final byte C_Class              = 0x07;   //  7
  public static final byte C_String             = 0x08;   //  8
  public static final byte C_FieldRef           = 0x09;   //  9
  public static final byte C_MethodRef          = 0x0A;   // 10
  public static final byte C_InterfaceMethodRef = 0x0B;   // 11
  public static final byte C_NameAndType        = 0x0C;   // 12

//public static byte[] addDouble(byte[] array,double value)
//{
//  array = ByteUtility.addBytes(array,C_Double);
//  array = ByteUtility.addBytes(array,value);
//  return array;
//}

  public static byte[] addClass(byte[] array,short value)
  { return addRef(C_Class,array,value); }
  public static byte[] addFieldRef(byte[] array,short value1,short value2)
  { return addRef(C_FieldRef,array,value1,value2); }
  public static byte[] addInteger(byte[] array,int value)
  {
	array = ByteUtility.addBytes(array,C_Integer);
	array = ByteUtility.addBytes(array,value);
	return array;
  }
  public static byte[] addInterfaceMethodRef(byte[] array,short value1,short value2)
  { return addRef(C_InterfaceMethodRef,array,value1,value2); }
//public static byte[] addFloat(byte[] array,float value)
//{
//  array = ByteUtility.addBytes(array,C_Float);
//  array = ByteUtility.addBytes(array,value);
//  return array;
//}

  public static byte[] addLong(byte[] array,long value)
  {
	array = ByteUtility.addBytes(array,C_Long);
	array = ByteUtility.addBytes(array,value);
	return array;
  }
  public static byte[] addMethodRef(byte[] array,short value1,short value2)
  { return addRef(C_MethodRef,array,value1,value2); }
  public static byte[] addNameAndType(byte[] array,short value1,short value2)
  { return addRef(C_NameAndType,array,value1,value2); }
  public static byte[] addRef(byte refType,byte[] array,short value)
  {
	array = ByteUtility.addBytes(array,refType);
	array = ByteUtility.addBytes(array,value);
	return array;
  }
  // Generic Bytecode Methods
  public static byte[] addRef(byte refType,byte[] array,short value1,short value2)
  {
	array = ByteUtility.addBytes(array,refType);
	array = ByteUtility.addBytes(array,value1);
	array = ByteUtility.addBytes(array,value2);
	return array;
  }
  public static byte[] addString(byte[] array,short value)
  { return addRef(C_String,array,value); }
  // Constant Pool Item Methods
  public static byte[] addUtf8(byte[] array,String value)
  {
	array = ByteUtility.addBytes(array,C_Utf8);
	array = ByteUtility.addBytes(array,(short)value.length());
	array = ByteUtility.addBytes(array,value);
	return array;
  }
}

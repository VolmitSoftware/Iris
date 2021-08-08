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

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** EventAdapterGenerator
  *
  * Generate an "Event Adapter" dynamically during program execution
  *
  **/
public class EventAdapterGenerator
{
  public static AdapterClassLoader ldr = new AdapterClassLoader();
  static Class  EVENTLISTENER          = null;
  static String CLASSPACKAGE           = "org/apache/bsf/util/event/adapters/";
  static String WRITEDIRECTORY         = null;

  // starting 8 bytes of all Java Class files
  static byte   CLASSHEADER[];
  // constant pool items found in all event adapters
  static short  BASECPCOUNT; // number of cp items + 1 ( cp item # 0 reserved for JVM )
  static byte   BASECP[];    //
  // some bytes in the middle of the class file (see below)
  static byte   FIXEDCLASSBYTES[];
  // the initialization method, noargs constructor
  static byte   INITMETHOD[];

  private static Log logger;

  /* The static initializer */
  static
  {
	logger = LogFactory.getLog(
				(com.volmit.iris.util.bsf.util.event.generator.EventAdapterGenerator.class).getName());

	String USERCLASSPACKAGE = System.getProperty("DynamicEventClassPackage",
												 "");

	if (!USERCLASSPACKAGE.equals(""))
	{
	  CLASSPACKAGE = USERCLASSPACKAGE;
	}

	if(CLASSPACKAGE.length() > 0 )
	{
	  CLASSPACKAGE = CLASSPACKAGE.replace('\\','/');
	  if(!CLASSPACKAGE.endsWith("/"))
	  { CLASSPACKAGE = CLASSPACKAGE+"/"; }
	}
	WRITEDIRECTORY = System.getProperty("DynamicEventClassWriteDirectory",CLASSPACKAGE);
	if(WRITEDIRECTORY.length() > 0 )
	{
	  WRITEDIRECTORY = WRITEDIRECTORY.replace('\\','/');
	  if(!WRITEDIRECTORY.endsWith("/"))
	  { WRITEDIRECTORY = WRITEDIRECTORY+"/"; }
	}
	try
	// { EVENTLISTENER = Class.forName("java.util.EventListener"); }
	{ EVENTLISTENER = Thread.currentThread().getContextClassLoader().loadClass ("java.util.EventListener"); } // rgf, 2006-01-05
	catch(ClassNotFoundException ex)
	{
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }


	// start of the Java Class File
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(byte)0xCA);  // magic
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(byte)0xFE);  // magic
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(byte)0xBA);  // magic
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(byte)0xBE);  // magic
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(short)3);    // minor version
	CLASSHEADER = ByteUtility.addBytes(CLASSHEADER,(short)45);   // major version

	// Start the constant pool for base items in all event adapter classes
	BASECPCOUNT = 17; // number of cp items + 1 ( cp item # 0 reserved for JVM )

	// cp item 01
	BASECP = Bytecode.addUtf8(BASECP,"()V");

	// cp item 02
	BASECP = Bytecode.addUtf8(BASECP,"<init>");

	// cp item 03
	BASECP = Bytecode.addUtf8(BASECP,"Code");

	// cp item 04
	BASECP = Bytecode.addUtf8(BASECP,"eventProcessor");

	// cp item 05
	BASECP = Bytecode.addUtf8(BASECP,"java/lang/Object");

	// cp item 06
	BASECP = Bytecode.addUtf8(BASECP,"org/apache/bsf/util/event/EventAdapterImpl");

	// cp item 07
	BASECP = Bytecode.addUtf8(BASECP,"org/apache/bsf/util/event/EventProcessor");

	// cp item 08
	BASECP = Bytecode.addUtf8(BASECP,"(Ljava/lang/String;[Ljava/lang/Object;)V");

	// cp item 09
	BASECP = Bytecode.addUtf8(BASECP,"Lorg/apache/bsf/util/event/EventProcessor;");

	// cp item 10
	BASECP = Bytecode.addClass(BASECP,(short)5); // Class "java/lang/Object"

	// cp item 11
	BASECP = Bytecode.addClass(BASECP,(short)6); // Class "org/apache/bsf/util/event/EventAdapterImpl"

	// cp item 12
	BASECP = Bytecode.addClass(BASECP,(short)7); // Class "org/apache/bsf/util/event/EventProcessor"

	// cp item 13
	BASECP = Bytecode.addNameAndType(BASECP,(short)2,(short)1); // "<init>" "()V"

	// cp item 14
	BASECP = Bytecode.addNameAndType(BASECP,(short)4,(short)9); // "eventProcessor" "Lorg/apache/bsf/util/event/EventProcessor;"

	// cp item 15
	BASECP = Bytecode.addFieldRef(BASECP,(short)11,(short)14);

	// cp item 16
	BASECP = Bytecode.addMethodRef(BASECP,(short)11,(short)13);

	// fixed bytes in middle of class file
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)0x21); // access_flags        (fixed)
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)20);   // this_class          (fixed)
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)11);   // super_class         (fixed)
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)1);    // interface_count     (fixed)
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)19);   // interfaces          (fixed)
	FIXEDCLASSBYTES = ByteUtility.addBytes(FIXEDCLASSBYTES,(short)0);    // field_count         (fixed)

	// initialization method, constructor
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)1);              // access_flags
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)2);              // name_index "<init>"
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)1);              // descriptor_index "()V"
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)1);              // attribute_count
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)3);              // attribute_name_index "Code"
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(long)17);              // attribute_length
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)1);              // max_stack
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)1);              // max_locals
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(long)5);               // code_length
	//code
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(byte)0x2A);            // aload_0
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(byte)0xB7);            // invokespecial
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)16);             // method_ref index
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(byte)0xB1);            // return
	// exception table
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)0);              // exception_table_length
	INITMETHOD = ByteUtility.addBytes(INITMETHOD,(short)0);              // attributes_count

  }

  /* methods that take an EventListener Class Type to create an EventAdapterClass */
  public static Class makeEventAdapterClass(Class listenerType,boolean writeClassFile)
  {
      logger.info("EventAdapterGenerator");

        if( EVENTLISTENER.isAssignableFrom(listenerType) )
	{
	  boolean exceptionable    = false;
	  boolean nonExceptionable = false;
	  byte    constantPool[]   = null;
	  short   cpBaseIndex;
	  short   cpCount          = 0;
	  short   cpExceptionBaseIndex;
	  short   exceptionableCount;
	  short   nonExceptionableCount;

	  /* Derive Names */
	  String listenerTypeName      = listenerType.getName();
          logger.info("ListenerTypeName: "+listenerTypeName);
	  String adapterClassName      =
		CLASSPACKAGE+
		(listenerTypeName.endsWith("Listener")
		 ? listenerTypeName.substring(0, listenerTypeName.length() - 8)
		 : listenerTypeName).replace('.', '_') +
		"Adapter";
	  String finalAdapterClassName = adapterClassName;
	  Class  cached                = null;
	  int    suffixIndex           = 0;

	  do
	  {
		if (null != (cached = ldr.getLoadedClass(finalAdapterClassName)))
		{
                    logger.info("cached:  "+cached);
		  try
		  {
			if (!listenerType.isAssignableFrom(cached))
			  finalAdapterClassName = adapterClassName + "_" + suffixIndex++;
			else
			  return cached;
		  }
		  catch(VerifyError ex)
		  {
                      System.err.println(ex.getMessage());
                      ex.printStackTrace();
                      return cached;
		  }
		}
	  }
	  while (cached != null);

	  String eventListenerName = listenerTypeName.replace('.', '/');

	  /* method stuff */
	  java.lang.reflect.Method lms[] = listenerType.getMethods();

	  /* ****************************************************************************************** */
	  // Listener interface
	  // Class name
	  cpCount += 4;

	  // cp item 17
	  constantPool = Bytecode.addUtf8(constantPool,eventListenerName);

	  // cp item 18
	  constantPool = Bytecode.addUtf8(constantPool,finalAdapterClassName);

	  // cp item 19
	  constantPool = Bytecode.addClass(constantPool,(short)17);

	  // cp item 20
	  constantPool = Bytecode.addClass(constantPool,(short)18);

	  // do we have nonExceptionalble event, exceptionable or both
	  for (int i = 0 ; i < lms.length ; ++i)
	  {
		Class exceptionTypes[] = lms[i].getExceptionTypes();
		if( 0 < exceptionTypes.length)
		{ exceptionable = true; }
		else
		{ nonExceptionable = true; }
	  }/* End for*/

	  /* ****************************************************************************************** */
	  // optional inclusion of nonexceptional events affects exceptional events indices

	  nonExceptionableCount = 0;
	  if(nonExceptionable)
	  {
		nonExceptionableCount = 3;
		cpCount += nonExceptionableCount;

		// cp item 21
		constantPool = Bytecode.addUtf8(constantPool,"processEvent");

		// cp item 22
		constantPool = Bytecode.addNameAndType(constantPool,(short)21,(short)8);


		// cp item 23
		constantPool = Bytecode.addInterfaceMethodRef(constantPool,(short)12,(short)22);
	  }

	  /* ****************************************************************************************** */
	  // optional inclusion of exceptional events affects CP Items which follow for specific methods

	  exceptionableCount = 0;
	  if(exceptionable)
	  {
		int classIndex = BASECPCOUNT + cpCount + 1;
		int nameIndex  = BASECPCOUNT + cpCount + 0;
		int natIndex   = BASECPCOUNT + cpCount + 3;

		exceptionableCount = 5;
		cpCount += exceptionableCount;

		// cp item 24 or 21
		constantPool = Bytecode.addUtf8(constantPool,"processExceptionableEvent");

		// cp item 25 or 22
		constantPool = Bytecode.addUtf8(constantPool,"java/lang/Exception");

		// cp item 26 or 23
		constantPool = Bytecode.addClass(constantPool,(short)classIndex);

		// cp item 27 or 24
		constantPool = Bytecode.addNameAndType(constantPool,(short)nameIndex,(short)8);

		// cp item 28 or 25
		constantPool = Bytecode.addInterfaceMethodRef(constantPool,(short)12,(short)natIndex);

	  }

	  // base index for method cp references
	  cpBaseIndex = (short)(BASECPCOUNT + cpCount);
          logger.debug("cpBaseIndex: " + cpBaseIndex);

	  for (int i = 0 ; i < lms.length ; ++i)
	  {
		String eventMethodName = lms[i].getName();
		String eventName = lms[i].getParameterTypes()[0].getName().replace('.','/');
		cpCount += 3;
		// cp items for event methods
		constantPool = Bytecode.addUtf8(constantPool,eventMethodName);
		constantPool = Bytecode.addUtf8(constantPool,("(L" + eventName + ";)V"));
		constantPool = Bytecode.addString(constantPool,(short)(BASECPCOUNT+cpCount-3));
	  }/* End for*/

	  boolean propertyChangeFlag[] = new boolean[lms.length];
	  int cpIndexPCE = 0;
	  for (int i = 0 ; i < lms.length ; ++i)
	  {
		String eventName = lms[i].getParameterTypes()[0].getName().replace('.','/');
		// cp items for PropertyChangeEvent special handling
		if(eventName.equalsIgnoreCase("java/beans/PropertyChangeEvent"))
		{
		  propertyChangeFlag[i] = true;
		  if( 0 == cpIndexPCE )
		  {
			constantPool = Bytecode.addUtf8(constantPool,eventName);
			constantPool = Bytecode.addUtf8(constantPool,"getPropertyName");
			constantPool = Bytecode.addUtf8(constantPool,"()Ljava/lang/String;");
			constantPool = Bytecode.addClass(constantPool,(short)(BASECPCOUNT + cpCount));
			constantPool = Bytecode.addNameAndType(constantPool,
												   (short)(BASECPCOUNT + cpCount + 1),
												   (short)(BASECPCOUNT + cpCount + 2));
			constantPool = Bytecode.addMethodRef(constantPool,
												 (short)(BASECPCOUNT + cpCount + 3),
												 (short)(BASECPCOUNT + cpCount + 4));
			cpCount += 6;
			cpIndexPCE = BASECPCOUNT + cpCount - 1;

		  }
		}
		else
		{ propertyChangeFlag[i] = false; }
	  }/* End for*/

	  cpExceptionBaseIndex = (short)(BASECPCOUNT + cpCount);
          logger.debug("cpExceptionBaseIndex: " + cpExceptionBaseIndex);

	  int excpIndex[][] = new int[lms.length][];
	  for (int i = 0 ; i < lms.length ; ++i)
	  {
		Class exceptionTypes[] = lms[i].getExceptionTypes();
		excpIndex[i] = new int[exceptionTypes.length];
		for ( int j = 0 ; j < exceptionTypes.length ; j++)
		{
		  constantPool = Bytecode.addUtf8(constantPool,exceptionTypes[j].getName().replace('.', '/'));
		  constantPool = Bytecode.addClass(constantPool,(short)(BASECPCOUNT+cpCount));
		  excpIndex[i][j] = BASECPCOUNT + cpCount + 1;
		  cpCount += 2;
		}
	  }/* End for*/
	  /* end constant pool */

	  /* ************************************************************************************************ */
	  // put the Class byte array together

	  /* start */
	  byte newClass[] = CLASSHEADER;                                   // magic, version      (fixed)
	  short count = (short)(BASECPCOUNT + cpCount);
	  newClass = ByteUtility.addBytes(newClass,count);                 // constant_pool_count (variable)
	  newClass = ByteUtility.addBytes(newClass,BASECP);                // constant_pool       (fixed)
	  newClass = ByteUtility.addBytes(newClass,constantPool);          // constant_pool       (variable)
	  newClass = ByteUtility.addBytes(newClass,FIXEDCLASSBYTES);       // see FIXEDCLASSBYTES (fixed)
	  newClass = ByteUtility.addBytes(newClass,(short)(lms.length+1)); // method_count        (variable)
	  newClass = ByteUtility.addBytes(newClass,INITMETHOD);            // constructor <init>  (fixed)
	  // methods

	  /* ****************************************************************************************** */
	  /* loop over listener methods from listenerType */
	  for (int i = 0 ; i < lms.length ; ++i)
	  {
		newClass = ByteUtility.addBytes(newClass,(short)1);                   // access_flags             (fixed)
		newClass = ByteUtility.addBytes(newClass,(short)(cpBaseIndex+3*i+0)); // name_index               (variable)
		newClass = ByteUtility.addBytes(newClass,(short)(cpBaseIndex+3*i+1)); // descriptor_index         (variable)
		newClass = ByteUtility.addBytes(newClass,(short)1);                   // attribute_count          (fixed)
		newClass = ByteUtility.addBytes(newClass,(short)3);                   // attribute_name_index code(fixed)

		// Code Attribute Length
		int length = 32;
		if( 0 < excpIndex[i].length )
		{ length += 5 + 8 * ( 1 + excpIndex[i].length ); }
		if(propertyChangeFlag[i])
		{ length += 2; }
		newClass = ByteUtility.addBytes(newClass,(long)length);               // attribute_length         (variable)

		// start code attribute
		newClass = ByteUtility.addBytes(newClass,(short)6);                   // max_stack                (fixed)
		newClass = ByteUtility.addBytes(newClass,(short)3);                   // max_locals               (fixed)

		// Code Length
		length = 20;
		if(exceptionable && 0 < excpIndex[i].length)
		{ length += 5; }
		if(propertyChangeFlag[i])
		{ length += 2; }
		newClass = ByteUtility.addBytes(newClass,(long)length);               // code_length              (variable)

		// start code
		newClass = ByteUtility.addBytes(newClass,(byte)0x2A);                 // aload_0                  (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0xB4);                 // getfield                 (fixed)
		newClass = ByteUtility.addBytes(newClass,(short)15);                  // index                    (fixed)


		if(propertyChangeFlag[i])
		{ // the propertyName is passed as the first parameter
		  newClass = ByteUtility.addBytes(newClass,(byte)0x2B);               // aload_1                  (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)0xB6);               // invokevirtual            (fixed)
		  newClass = ByteUtility.addBytes(newClass,(short)cpIndexPCE);        // methodref                (variable)
		}
		else
		{ // the eventMethodName is passed as the first parameter
		  // Target for method invocation.
		  newClass = ByteUtility.addBytes(newClass,(byte)0x12);                 // ldc                    (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)(cpBaseIndex+3*i+2));  // index (byte)           (variable)
		}

		newClass = ByteUtility.addBytes(newClass,(byte)0x04);                 // iconst_1                 (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0xBD);                 // anewarray                (fixed)
		newClass = ByteUtility.addBytes(newClass,(short)10);                  // Class java/lang/Object   (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0x59);                 // dup                      (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0x03);                 // iconst_0                 (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0x2B);                 // aload_1                  (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0x53);                 // aastore                  (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0xB9);                 // invokeinterface          (fixed)

		// index to processEvent or processExceptionableEvent method
		length = 23; // actually an index into cp
		if(exceptionable && nonExceptionable)
		{ // interface method index
		  if( 0 < lms[i].getExceptionTypes().length )
		  { length += 5; }
		}
		else if(exceptionable)
		{ length += 2; }
		newClass = ByteUtility.addBytes(newClass,(short)length);              // index (process??????...) (variable)

		newClass = ByteUtility.addBytes(newClass,(byte)0x03);                 // iconst_0                 (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0x00);                 // noop                     (fixed)
		newClass = ByteUtility.addBytes(newClass,(byte)0xB1);                 // return                   (fixed)

		if(exceptionable && 0 < excpIndex[i].length)
		{ // exception code
		  newClass = ByteUtility.addBytes(newClass,(byte)0x4D);               // astore_2                 (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)0x2C);               // aload_2                  (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)0xBF);               // athrow                   (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)0x57);               // pop                      (fixed)
		  newClass = ByteUtility.addBytes(newClass,(byte)0xB1);               // return                   (fixed)
		// end code

		  // exception table
		  length = excpIndex[i].length;
		  newClass = ByteUtility.addBytes(newClass,(short)(1+length));        // exception_table_length   (variable)
		  for( int j = 0 ; j < length ; j++ )
		  { // catch exception types and rethrow
			newClass = ByteUtility.addBytes(newClass,(short)0);               // start_pc                 (fixed)
			if(propertyChangeFlag[i])
			{
			  newClass = ByteUtility.addBytes(newClass,(short)21);            // end_pc                   (fixed)
			  newClass = ByteUtility.addBytes(newClass,(short)22);            // handler_pc               (fixed)
			}
			else
			{
			  newClass = ByteUtility.addBytes(newClass,(short)19);            // end_pc                   (fixed)
			  newClass = ByteUtility.addBytes(newClass,(short)20);            // handler_pc               (fixed)
			}
			newClass = ByteUtility.addBytes(newClass,(short)excpIndex[i][j]); // catch_type               (variable)
		  }
		  // catch "exception" and trap it
		  newClass = ByteUtility.addBytes(newClass,(short)0);                 // start_pc                 (fixed)
		  if(propertyChangeFlag[i])
		  {
			newClass = ByteUtility.addBytes(newClass,(short)21);              // end_pc                   (fixed)
			newClass = ByteUtility.addBytes(newClass,(short)25);              // handler_pc               (fixed)
		  }
		  else
		  {
			newClass = ByteUtility.addBytes(newClass,(short)19);              // end_pc                   (fixed)
			newClass = ByteUtility.addBytes(newClass,(short)23);              // handler_pc               (fixed)
		  }
		  if(nonExceptionable)
		  { newClass = ByteUtility.addBytes(newClass,(short)26); }            // catch_type               (fixed)
		  else                                                                                            // or
		  { newClass = ByteUtility.addBytes(newClass,(short)23); }            // catch_type               (fixed)
		}
		else
		{ newClass = ByteUtility.addBytes(newClass,(short)0); }               // exception_table_length   (fixed)
		// attributes on the code attribute (none)
		newClass = ByteUtility.addBytes(newClass,(short)0);                   // attribute_count          (fixed)
		// end code attribute


	  }/* End for*/
	  // Class Attributes (none for this)
	  newClass = ByteUtility.addBytes(newClass,(short)0);                     // attribute_count          (fixed)
	  /* done */

          logger.debug("adapterName: " + finalAdapterClassName);
          logger.debug("cpCount: " + count + " = " + BASECPCOUNT + " + " +  cpCount);
          logger.debug("methodCount: " + (lms.length+1));
	  // output to disk class file
	  /* ****************************************************************************************** */

	  // now create the class and load it
	  // return the Class.

	  if (writeClassFile)
	  {
		try
		{
                    // removed "WRITEDIRECTORY+", as this path is already part of 'finalAdapterClassName'
		  FileOutputStream fos =  new FileOutputStream(finalAdapterClassName+".class");
		  fos.write(newClass);
		  fos.close();
		}
		catch(IOException ex)
		{
                    System.err.println(ex.getMessage());
                    ex.printStackTrace();
                }

		try
		{
		  Class ret = ldr.loadClass(finalAdapterClassName);
		  logger.debug("EventAdapterGenerator: " +
							 ret.getName() +
							 " dynamically generated");
		  return ret;
		}
		catch (ClassNotFoundException ex)
		{
                    System.err.println(ex.getMessage());
                    ex.printStackTrace();
                }
	  }

	  try
	  {
		Class ret = ldr.defineClass(finalAdapterClassName,newClass);
		logger.debug("EventAdapterGenerator: " +
                                       ret.getName() +
                                       " dynamically generated");
		return ret;
	  }
	  catch(Exception ex)
	  {
              System.err.println(ex.getMessage());
              ex.printStackTrace();
          }
	}
	return null;
  }
}

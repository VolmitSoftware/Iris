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

package com.volmit.iris.util.bsf.util;

import java.beans.Introspector;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Deals with strings (probably need to elaborate some more).
 *
 * @author   Matthew J. Duftler
 */
public class StringUtils
{
  public static final String lineSeparator =
	System.getProperty("line.separator", "\n");
  public static final String lineSeparatorStr = cleanString(lineSeparator);

  public static String classNameToVarName(String className)
  {
	// Might represent an array.
	int arrayDim = 0;

	while (className.endsWith("[]"))
	{
	  className = className.substring(0, className.length() - 2);
	  arrayDim++;
	}

	int    iLastPeriod = className.lastIndexOf('.');
	String varName     = Introspector.decapitalize(
										 iLastPeriod != -1
										 ? className.substring(iLastPeriod + 1)
										 : className);

	if (arrayDim > 0)
	{
	  varName += "_" + arrayDim + "D";
	}

	return getValidIdentifierName(varName);
  }
  // Ensure that escape sequences are passed through properly.
  public static String cleanString(String str)
  {
	if (str == null)
	  return null;
	else
	{
	  char[]       charArray = str.toCharArray();
	  StringBuffer sBuf      = new StringBuffer();
	  
	  for (int i = 0; i < charArray.length; i++)
		switch (charArray[i])
		{
		  case '\"' : sBuf.append("\\\"");
					  break;
		  case '\\' : sBuf.append("\\\\");
					  break;
		  case '\n' : sBuf.append("\\n");
					  break;
		  case '\r' : sBuf.append("\\r");
					  break;
		  default   : sBuf.append(charArray[i]);
					  break;
		}
	  
	  return sBuf.toString();
	}
  }
  /**
   * Get a string consisting of <code>numberOfChars</code> theChars.
   *
   * @return a string consisting of <code>numberOfChars</code> theChars.
   */
  public static String getChars(int numberOfChars, char theChar)
  {
	if (numberOfChars <= 0)
	  return "";

	StringBuffer sRet = new StringBuffer(numberOfChars);

	for (int i = 0; i < numberOfChars; i++)
	  sRet.append(theChar);     

	return sRet.toString();
  }
  /*
	This method will return the correct name for a class object representing
	a primitive, a single instance of a class, as well as n-dimensional arrays
	of primitives or instances. This logic is needed to handle the string returned
	from Class.getName(). If the class object represents a single instance (or
	a primitive), Class.getName() returns the fully-qualified name of the class
	and no further work is needed. However, if the class object represents an
	array (of n dimensions), Class.getName() returns a Descriptor (the Descriptor
	grammar is defined in section 4.3 of the Java VM Spec). This method will
	parse the Descriptor if necessary.
  */
  public static String getClassName(Class targetClass)
  {
	String className = targetClass.getName();

	return targetClass.isArray() ? parseDescriptor(className) : className;
  }
  public static String getCommaListFromVector(Vector sourceVector)
  {
	StringBuffer strBuf = new StringBuffer();

	for (int i = 0; i < sourceVector.size(); i++)
	{
	  strBuf.append((i > 0 ? ", " : "") +
					sourceVector.elementAt(i));
	}

	return strBuf.toString();
  }
  /*
	Returns a Reader for reading from the specified resource, if the resource
	points to a stream.
  */
  public static Reader getContentAsReader(URL url) throws SecurityException,
														  IllegalArgumentException,
														  IOException
  {
	if (url == null)
	{
	  throw new IllegalArgumentException("URL cannot be null.");
	}

	try
	{
	  Object content = url.getContent();

	  if (content == null)
	  {
		throw new IllegalArgumentException("No content.");
	  }

	  if (content instanceof InputStream)
	  {
		Reader in = new InputStreamReader((InputStream)content);

		if (in.ready())
		{
		  return in;
		}
		else
		{
		  throw new FileNotFoundException();
		}
	  }
	  else
	  {
		throw new IllegalArgumentException((content instanceof String)
										   ? (String)content
										   : "This URL points to a: " +
											 StringUtils.getClassName(content.getClass()));
	  }
	}
	catch (SecurityException e)
	{
	  throw new SecurityException("Your JVM's SecurityManager has disallowed this.");
	}
	catch (FileNotFoundException e)
	{
	  throw new FileNotFoundException("This file was not found: " + url);
	}
  }
  /*
	Shorthand for: IOUtils.getStringFromReader(getContentAsReader(url)).
  */
  public static String getContentAsString(URL url) throws SecurityException,
														  IllegalArgumentException,
														  IOException
  {
	return IOUtils.getStringFromReader(getContentAsReader(url));
  }
  // Handles multi-line strings.
  public static String getSafeString(String scriptStr)
  {
	BufferedReader in           = new BufferedReader(new StringReader(scriptStr));
	StringBuffer   strBuf       = new StringBuffer();
	String         tempLine,
				   previousLine = null;

	try
	{
	  while ((tempLine = in.readLine()) != null)
	  {
		if (previousLine != null)
		{
		  strBuf.append("\"" + previousLine + lineSeparatorStr + "\" +" +
						lineSeparator);
		}

		previousLine = cleanString(tempLine);
	  }
	}
	catch (IOException e)
	{
	}      

	strBuf.append("\"" + (previousLine != null ? previousLine : "") + "\"" +
				  lineSeparator);

	return strBuf.toString();
  }
  /*
  */
  public static URL getURL(URL contextURL, String spec) throws MalformedURLException
  {
	return getURL(contextURL, spec, 1);
  }
  /*
	The recursiveDepth argument is used to insure that the algorithm gives up
	after hunting 2 levels up in the contextURL's path.
  */
  private static URL getURL(URL contextURL, String spec, int recursiveDepth)
												  throws MalformedURLException
  {
	URL url = null;

	try
	{
	  url = new URL(contextURL, spec);

	  try
	  {
		url.openStream();
	  }
	  catch (IOException ioe1)
	  {
		throw new MalformedURLException("This file was not found: " + url);
	  }
	}
	catch (MalformedURLException e1)
	{
	  url = new URL("file", "", spec);

	  try
	  {
		url.openStream();
	  }
	  catch (IOException ioe2)
	  {
		if (contextURL != null)
		{
		  String contextFileName = contextURL.getFile();
		  String parentName      = new File(contextFileName).getParent();

		  if (parentName != null && recursiveDepth < 3)
		  {
			return getURL(new URL("file", "", parentName + '/'),
						  spec,
						  recursiveDepth + 1);
		  }
		}

		throw new MalformedURLException("This file was not found: " + url);
	  }
	}

	return url;
  }
  public static String getValidIdentifierName(String identifierName)
  {
	if (identifierName == null || identifierName.length() == 0)
	  return null;

	StringBuffer strBuf = new StringBuffer();

	char[] chars = identifierName.toCharArray();

	strBuf.append(Character.isJavaIdentifierStart(chars[0])
				  ? chars[0]
				  : '_'
				 );

	for (int i = 1; i < chars.length; i++)
	{
	  strBuf.append(Character.isJavaIdentifierPart(chars[i])
					? chars[i]
					: '_'
				   );
	}

	return strBuf.toString();
  }
  public static boolean isValidIdentifierName(String identifierName)
  {
	if (identifierName == null || identifierName.length() == 0)
	  return false;

	char[] chars = identifierName.toCharArray();

	if (!Character.isJavaIdentifierStart(chars[0]))
	  return false;

	for (int i = 1; i < chars.length; i++)
	  if (!Character.isJavaIdentifierPart(chars[i]))
		return false;

	return true;
  }
  public static boolean isValidPackageName(String packageName)
  {
	if (packageName == null)
	  return false;
	else if (packageName.length() == 0)
	  // Empty is ok.
	  return true;

	StringTokenizer strTok = new StringTokenizer(packageName, ".", true);

	// Should have an odd number of tokens (including '.' delimiters).
	if (strTok.countTokens() % 2 != 1)
	  return false;

	// Must start with a valid identifier name.
	if (!isValidIdentifierName(strTok.nextToken()))
	  return false;

	// ... followed by 0 or more of ".ValidIdentifier".
	while (strTok.hasMoreTokens())
	{
	  // Must be a '.'.
	  if (!strTok.nextToken().equals("."))
		return false;

	  // Must be a valid identifier name.
	  if (strTok.hasMoreTokens())
	  {
		if (!isValidIdentifierName(strTok.nextToken()))
		  return false;
	  }
	  else
		return false;
	}

	return true;
  }
  /*
	See the comment above for getClassName(targetClass)...
  */
  private static String parseDescriptor(String className)
  {
	char[] classNameChars = className.toCharArray();
	int    arrayDim       = 0;
	int    i              = 0;

	while (classNameChars[i] == '[')
	{
	  arrayDim++;
	  i++;
	}

	StringBuffer classNameBuf = new StringBuffer();

	switch (classNameChars[i++])
	{
	  case 'B' : classNameBuf.append("byte");
				 break;
	  case 'C' : classNameBuf.append("char");
				 break;
	  case 'D' : classNameBuf.append("double");
				 break;
	  case 'F' : classNameBuf.append("float");
				 break;
	  case 'I' : classNameBuf.append("int");
				 break;
	  case 'J' : classNameBuf.append("long");
				 break;
	  case 'S' : classNameBuf.append("short");
				 break;
	  case 'Z' : classNameBuf.append("boolean");
				 break;
	  case 'L' : classNameBuf.append(classNameChars,
									 i, classNameChars.length - i - 1);
				 break;
	}

	for (i = 0; i < arrayDim; i++)
	  classNameBuf.append("[]");

	return classNameBuf.toString();
  }
}

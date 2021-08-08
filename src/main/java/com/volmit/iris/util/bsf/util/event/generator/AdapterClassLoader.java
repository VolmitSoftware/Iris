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

import java.util.Hashtable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AdapterClassLoader extends ClassLoader
{
  private static Hashtable classCache = new Hashtable();
  private Class c;

  private Log logger = LogFactory.getLog(this.getClass().getName());

  public AdapterClassLoader()
  {
	super();
  }
  public synchronized Class defineClass(String name, byte[] b)
  {
	if ((c = getLoadedClass(name)) == null)
	{
	  c = defineClass(name.replace('/','.'), b, 0, b.length);   // rgf, 2006-02-03
	  put(name, c);
	}
	else
	{
	  logger.error("AdapterClassLoader: " + c +
                                 " previously loaded. Can not redefine class.");
	}

	return c;
  }
  final protected Class findClass(String name)
  {
	return get(name);
  }
  final protected Class get(String name)
  {
	return (Class)classCache.get(name);
  }
  public synchronized Class getLoadedClass(String name)
  {
	Class c = findLoadedClass(name);

	if (c == null)
	{
	  try
	  {
		c = findSystemClass(name);
	  }
	  catch (ClassNotFoundException e)
	  {
	  }
	}

	if (c == null)
	{
	  c = findClass(name);
	}

	return c;
  }
  protected synchronized Class loadClass(String name, boolean resolve)
	throws ClassNotFoundException
  {
	Class c = getLoadedClass(name);

	if (c != null && resolve)
	{
	  resolveClass(c);
	}

	return c;
  }
  // Not in JDK 1.1, only in JDK 1.2.
//  public AdapterClassLoader(ClassLoader loader)
//  {
//    super(loader);
//  }

  final protected void put(String name, Class c)
  {
	classCache.put(name, c);
  }
}

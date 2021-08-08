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

package com.volmit.iris.util.bsf.engines.javaclass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;
import com.volmit.iris.util.bsf.util.MethodUtils;

/**
 * This is the interface to scripts consisting of Java objects from the 
 * Bean Scripting Framework. 
 *
 * @author   Sanjiva Weerawarana
 */
public class JavaClassEngine extends BSFEngineImpl {
  /**
   * call the named method of the given object. If object is an instance
   * of Class, then the call is a static call on that object. If not, its
   * an instance method call or a static call (as per Java) on the given 
   * object.
   */
  public Object call (Object object, String method, Object[] args) 
														throws BSFException {
	// determine arg types
	Class[] argTypes = null;
	if (args != null) {
	  argTypes = new Class[args.length];
	  for (int i = 0; i < args.length; i++) {
	argTypes[i] = (args[i] != null) ? args[i].getClass () : null;
	  }
	}

	// now find method with the right signature, call it and return result
	try {
	  Method m = MethodUtils.getMethod (object, method, argTypes);
	  return m.invoke (object, args);
	} catch (Exception e) {
	  // something went wrong while invoking method
	  Throwable t = (e instanceof InvocationTargetException) ?
	            ((InvocationTargetException)e).getTargetException () :
	            null;
	  throw new BSFException (BSFException.REASON_OTHER_ERROR,
			      "method invocation failed: " + e +
			      ((t==null)?"":(" target exception: "+t)), t);
	}
  }
  /**
   * This is used by an application to evaluate an object containing
   * some expression - clearly not possible for compiled code ..
   */
  public Object eval (String source, int lineNo, int columnNo, 
		      Object oscript) throws BSFException {
	throw new BSFException (BSFException.REASON_UNSUPPORTED_FEATURE,
			    "Java bytecode engine can't evaluate expressions");
  }
}

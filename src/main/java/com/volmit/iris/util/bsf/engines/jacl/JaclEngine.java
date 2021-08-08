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

package com.volmit.iris.util.bsf.engines.jacl;

import java.util.Vector;

import com.volmit.iris.util.bsf.BSFDeclaredBean;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;

import tcl.lang.Interp;
import tcl.lang.ReflectObject;
import tcl.lang.TclDouble;
import tcl.lang.TclException;
import tcl.lang.TclInteger;
import tcl.lang.TclObject;
import tcl.lang.TclString;

/**
 * This is the interface to Scriptics's Jacl (Tcl) from the
 * Bean Scripting Framework.
 * <p>
 *
 * @author   Sanjiva Weerawarana
 */

public class JaclEngine extends BSFEngineImpl {
  /* the Jacl interpretor object */
  private Interp interp;

  /**
   * 
   * @param method The name of the method to call.
   * @param args an array of arguments to be
   * passed to the extension, which may be either
   * Vectors of Nodes, or Strings.
   */
  public Object call (Object obj, String method, Object[] args) 
														throws BSFException {
	StringBuffer tclScript = new StringBuffer (method);
	if (args != null) {
	  for( int i = 0 ; i < args.length ; i++ ) {
	tclScript.append (" ");
	tclScript.append (args[i].toString ());
	  }
	}
	return eval ("<function call>", 0, 0, tclScript.toString ());
  }
  /**
   * Declare a bean
   */
  public void declareBean (BSFDeclaredBean bean) throws BSFException {
	String expr = "set " + bean.name + " [bsf lookupBean \"" + bean.name +
	  "\"]";
	eval ("<declare bean>", 0, 0, expr);
  }
  /**
   * This is used by an application to evaluate a string containing
   * some expression.
   */
  public Object eval (String source, int lineNo, int columnNo, 
		      Object oscript) throws BSFException {
	String script = oscript.toString ();
	try {
	  interp.eval (script);
	  TclObject result = interp.getResult();
	  Object internalRep = result.getInternalRep();

	  // if the object has a corresponding Java type, unwrap it
	  if (internalRep instanceof ReflectObject)
		return ReflectObject.get(interp,result);
	  if (internalRep instanceof TclString)
		return result.toString();
	  if (internalRep instanceof TclDouble)
		return new Double(TclDouble.get(interp,result));
	  if (internalRep instanceof TclInteger)
		return new Integer(TclInteger.get(interp,result));

	  return result;
	} catch (TclException e) { 
	  throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
			      "error while eval'ing Jacl expression: " + 
			      interp.getResult (), e);
	}
  }
  /**
   * Initialize the engine.
   */
  public void initialize (BSFManager mgr, String lang,
			  Vector declaredBeans) throws BSFException {
	super.initialize (mgr, lang, declaredBeans);

	// create interpreter
	interp = new Interp();

	// register the extension that user's can use to get at objects
	// registered by the app
	interp.createCommand ("bsf", new BSFCommand (mgr, this));

	// Make java functions be available to Jacl
        try {
   		interp.eval("jaclloadjava");
	} catch (TclException e) {
		throw new BSFException (BSFException.REASON_OTHER_ERROR,
					"error while loading java package: " +
					interp.getResult (), e);
	}

	int size = declaredBeans.size ();
	for (int i = 0; i < size; i++) {
	  declareBean ((BSFDeclaredBean) declaredBeans.elementAt (i));
	}
  }

  /**
   * Undeclare a previously declared bean.
   */
  public void undeclareBean (BSFDeclaredBean bean) throws BSFException {
	eval ("<undeclare bean>", 0, 0, "set " + bean.name + " \"\"");
  }
}

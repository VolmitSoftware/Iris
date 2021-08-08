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

package com.volmit.iris.util.bsf.engines.jython;

import java.io.ByteArrayInputStream;
import java.util.Vector;

import com.volmit.iris.util.bsf.BSFDeclaredBean;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;
import com.volmit.iris.util.bsf.util.BSFFunctions;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyJavaInstance;
import org.python.core.PyObject;
import org.python.util.InteractiveInterpreter;

/**
 * This is the interface to Jython (http://www.jython.org/) from BSF.
 * It's derived from the JPython 1.x engine
 *
 * @author   Sanjiva Weerawarana
 * @author   Finn Bock <bckfnn@worldonline.dk>
 * @author   Chuck Murcko
 */

public class JythonEngine extends BSFEngineImpl {
  BSFPythonInterpreter interp;
  
  /**
   * call the named method of the given object.
   */
  public Object call (Object object, String method, Object[] args) 
      throws BSFException {
      try {
          PyObject[] pyargs = Py.EmptyObjects;

          if (args != null) {
              pyargs = new PyObject[args.length];
              for (int i = 0; i < pyargs.length; i++)
                  pyargs[i] = Py.java2py(args[i]);
          }

          if (object != null) {
              PyObject o = Py.java2py(object);
              return unwrap(o.invoke(method, pyargs));
          }

          PyObject m = interp.get(method);

          if (m == null)
              m = interp.eval(method);
          if (m != null) {
              return unwrap(m.__call__(pyargs));
          }

          return null;
      } catch (PyException e) {
          throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
                                  "exception from Jython:\n" + e, e);
      }
  }

  /**
   * Declare a bean
   */
  public void declareBean (BSFDeclaredBean bean) throws BSFException {
	interp.set (bean.name, bean.bean);
  }

  /**
   * Evaluate an anonymous function (differs from eval() in that apply() 
   * handles multiple lines).
   */
  public Object apply (String source, int lineNo, int columnNo, 
                       Object funcBody, Vector paramNames,
                       Vector arguments) throws BSFException {
      try {
          /* We wrapper the original script in a function definition, and
           * evaluate the function. A hack, no question, but it allows
           * apply() to pretend to work on Jython.
           */
          StringBuffer script = new StringBuffer(byteify(funcBody.toString()));
          int index = 0;
          script.insert(0, "def bsf_temp_fn():\n");
         
          while (index < script.length()) {
              if (script.charAt(index) == '\n') {
                  script.insert(index+1, '\t');
              }
              index++;
          }
          
          interp.exec (script.toString ());
          
          Object result = interp.eval ("bsf_temp_fn()");
          
          if (result != null && result instanceof PyJavaInstance)
              result = ((PyJavaInstance)result).__tojava__(Object.class);
          return result;
      } catch (PyException e) {
          throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
                                  "exception from Jython:\n" + e, e);
      }
  }

  /**
   * Evaluate an expression.
   */
  public Object eval (String source, int lineNo, int columnNo, 
		      Object script) throws BSFException {
	try {
	  Object result = interp.eval (byteify(script.toString ()));
	  if (result != null && result instanceof PyJavaInstance)
		result = ((PyJavaInstance)result).__tojava__(Object.class);
	  return result;
	} catch (PyException e) {
	  throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
			      "exception from Jython:\n" + e, e);
	}
  }

  /**
   * Execute a script. 
   */
  public void exec (String source, int lineNo, int columnNo,
		    Object script) throws BSFException {
	try {
	  interp.exec (byteify(script.toString ()));
	} catch (PyException e) {
	  throw new BSFException (BSFException.REASON_EXECUTION_ERROR,
			      "exception from Jython:\n" + e, e);
	}
  }

  /**
   * Execute script code, emulating console interaction.
   */
  public void iexec (String source, int lineNo, int columnNo,
                     Object script) throws BSFException {
      String scriptStr = byteify(script.toString());
      int newline = scriptStr.indexOf("\n");

      if (newline > -1)
          scriptStr = scriptStr.substring(0, newline);

      try {
          if (interp.buffer.length() > 0)
              interp.buffer.append("\n");
          interp.buffer.append(scriptStr);
          if (!(interp.runsource(interp.buffer.toString())))
              interp.resetbuffer();
      } catch (PyException e) {
          interp.resetbuffer();
          throw new BSFException(BSFException.REASON_EXECUTION_ERROR, 
                                 "exception from Jython:\n" + e, e);
      }
  }

  /**
   * Initialize the engine.
   */
  public void initialize (BSFManager mgr, String lang,
						  Vector declaredBeans) throws BSFException {
	super.initialize (mgr, lang, declaredBeans);

	// create an interpreter
	interp = new BSFPythonInterpreter ();

    // ensure that output and error streams are re-directed correctly
    interp.setOut(System.out);
    interp.setErr(System.err);
    
	// register the mgr with object name "bsf"
	interp.set ("bsf", new BSFFunctions (mgr, this));

    // Declare all declared beans to the interpreter
	int size = declaredBeans.size ();
	for (int i = 0; i < size; i++) {
	  declareBean ((BSFDeclaredBean) declaredBeans.elementAt (i));
	}
  }

  /**
   * Undeclare a previously declared bean.
   */
  public void undeclareBean (BSFDeclaredBean bean) throws BSFException {
	interp.set (bean.name, null);
  }

  public Object unwrap(PyObject result) {
	if (result != null) {
	   Object ret = result.__tojava__(Object.class);
	   if (ret != Py.NoConversion)
		  return ret;
	}
	return result;
  }
  
  private String byteify (String orig) {
      // Ugh. Jython likes to be fed bytes, rather than the input string.
      ByteArrayInputStream bais = 
          new ByteArrayInputStream(orig.getBytes());
      StringBuffer s = new StringBuffer();
      int c;
      
      while ((c = bais.read()) >= 0) {
          s.append((char)c);
      }

      return s.toString();
  }
  
  private class BSFPythonInterpreter extends InteractiveInterpreter {

      public BSFPythonInterpreter() {
          super();
      }

      // Override runcode so as not to print the stack dump
      public void runcode(PyObject code) {
          try {
              this.exec(code);
          } catch (PyException exc) {
              throw exc;
          }
      }
  }
}

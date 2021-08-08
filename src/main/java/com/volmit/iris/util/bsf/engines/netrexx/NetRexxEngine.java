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

package com.volmit.iris.util.bsf.engines.netrexx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Vector;

import com.volmit.iris.util.bsf.BSFDeclaredBean;
import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;
import com.volmit.iris.util.bsf.util.BSFFunctions;
import com.volmit.iris.util.bsf.util.EngineUtils;
import com.volmit.iris.util.bsf.util.MethodUtils;
import com.volmit.iris.util.bsf.util.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the interface to NetRexx from the
 * Bean Scripting Framework.
 * <p>
 * The NetRexx code must be written script-style, without a "class" or
 * "properties" section preceeding the executable code. The NetRexxEngine will
 * generate a prefix for this code:
 * <pre>
 * <code>
 * class $$CLASSNAME$$;
 * method BSFNetRexxEngineEntry(bsf=com.volmit.iris.util.bsf.BSFManager) public static;
 * </code>
 * </pre>
 * $$CLASSNAME$$ will be replaced by a generated classname of the form
 * BSFNetRexx*, and the bsf parameter can be used to retrieve application
 * objects registered with the Bean Scripting Framework.
 * <p>
 * If you use the placeholder string $$CLASSNAME$$ elsewhere
 * in your script -- including within text strings -- BSFNetRexxEngine will
 * replace it with the generated name of the class before the NetRexx code
 * is compiled.
 * <p>
 * If you need to use full NetRexx functionality, we recommend that your
 * NetRexx script define and invoke a "minor class", with or without the
 * "dependent" keyword as suits your needs. You'll have to use $$CLASSNAME$$
 * in naming the minor class, since the name of the main class is synthesized;
 * for example, to create the minor class "bar" you'd write
 * "class $$CLASSNAME$$.Bar".
 * <p>
 * <h2>Hazards:</h2>
 * <p>
 * Since NetRexx has to be _compiled_ to a Java classfile, invoking it involves
 * a fair amount of computation to load and execute the compiler. We are
 * currently making an attempt to manage that by caching the class
 * after it has been loaded, but the indexing is fairly primitive; we
 * hash against the script string to find the class for it.
 * <p>
 * Minor-class .class files are now being deleted after the major class loads.
 * This coould potentially cause problems.
 *
 * @author  Joe Kesselman
 * @author  Sanjiva Weerawarana
 */
public class NetRexxEngine extends BSFEngineImpl
{
	BSFFunctions mgrfuncs;
	static Hashtable codeToClass=new Hashtable();
	static String serializeCompilation="";
	static String placeholder="$$CLASSNAME$$";
	String minorPrefix;
	
	private Log logger = LogFactory.getLog(this.getClass().getName());
	  
	/**
	 * Create a scratchfile, open it for writing, return its name.
	 * Relies on the filesystem to provide us with uniqueness testing.
	 * NOTE THAT uniqueFileOffset continues to count; we don't want to
	 * risk reusing a classname we have previously loaded in this session
	 * even if the classfile has been deleted.
	 *
	 * I've made the offset static, due to concerns about reuse/reentrancy
	 * of the NetRexx engine.
	 */
  private static int uniqueFileOffset=0;
  private class GeneratedFile 
  {
	File file=null;
	FileOutputStream fos=null;
	String className=null;
	GeneratedFile(File file,FileOutputStream fos,String className) 
	  {
		  this.file=file;
		  this.fos=fos;
		  this.className=className;
	  }
  }
	
	// rexxclass used to be an instance variable, on the theory that
	// each NetRexxEngine was an instance of a specific script.
	// BSF is currently reusing Engines, so caching the class
	// no longer makes sense.
	// Class rexxclass;
	
	/**
	 * Constructor.
	 */
	public NetRexxEngine ()
	{
		/*
		  The following line is intended to cause the constructor to
		  throw a NoClassDefFoundError if the NetRexxC.zip dependency
		  is not resolved.
		  
		  If this line was not here, the problem would not surface until
		  the actual processing of a script. We want to know all is well
		  at the time the engine is instantiated, not when we attempt to
		  process a script.
		  */
		
		new netrexx.lang.BadArgumentException();
	}
	/**
	 * Return an object from an extension.
	 * @param object object from which to call our static method
	 * @param method The name of the method to call.
	 * @param args an array of arguments to be
	 * passed to the extension, which may be either
	 * Vectors of Nodes, or Strings.
	 */
	public Object call (Object object, String method, Object[] args) 
	throws BSFException
	{
		throw new BSFException(BSFException.REASON_UNSUPPORTED_FEATURE,
							   "NetRexx doesn't currently support call()",
							   null);
	}
	/**
	 * Invoke a static method.
	 * @param rexxclass Class to invoke the method against
	 * @param method The name of the method to call.
	 * @param args an array of arguments to be
	 * passed to the extension, which may be either
	 * Vectors of Nodes, or Strings.
	 */
	Object callStatic(Class rexxclass, String method, Object[] args) 
	throws BSFException
	{
		//***** ISSUE: Currently supports only static methods
		Object retval = null;
		try
		{
			if (rexxclass != null)
			{
				//***** This should call the lookup used in BML, for typesafety
				Class[] argtypes=new Class[args.length];
				for(int i=0;i<args.length;++i)
					argtypes[i]=args[i].getClass();
				
				Method m=MethodUtils.getMethod(rexxclass, method, argtypes);
				retval=m.invoke(null,args);
			}
			else
			{
				logger.error("NetRexxEngine: ERROR: rexxclass==null!");
			}
		}
		catch(Exception e)
		{
			e.printStackTrace ();
			if (e instanceof InvocationTargetException)
			{
				Throwable t = ((InvocationTargetException)e).getTargetException ();
				t.printStackTrace ();
			}
			throw new BSFException (BSFException.REASON_IO_ERROR,
									e.getMessage (),
									e);
		}
		return retval;
	}
	public void declareBean (BSFDeclaredBean bean) throws BSFException {}
	/**
	 * Override impl of execute. In NetRexx, methods which do not wish
	 * to return a value should be invoked via exec, which will cause them
	 * to be generated without the "returns" clause.
	 * Those which wish to return a value should call eval instead.
	 * which will add "returns java.lang.Object" to the header.
	 *
	 * Note: It would be nice to have the "real" return type avaialable, so
	 * we could do something more type-safe than Object, and so we could
	 * return primitive types without having to enclose them in their
	 * object wrappers. BSF does not currently support that concept.
	 */
	public Object eval (String source, int lineNo, int columnNo,
					Object script)
	throws BSFException
	{
		return execEvalShared(source, lineNo, columnNo, script,true);
	}
	/**
	 * Override impl of execute. In NetRexx, methods which do not wish
	 * to return a value should be invoked via exec, which will cause them
	 * to be generated without the "returns" clause.
	 * Those which wish to return a value should call eval instead.
	 * which will add "returns java.lang.Object" to the header.
	 */
	public void exec (String source, int lineNo, int columnNo,
				  Object script)
	throws BSFException
	{
		 execEvalShared(source, lineNo, columnNo, script,false);
	}
	/**
	 * This is shared code for the exec() and eval() operations. It will
	 * evaluate a string containing a NetRexx method body -- which may be
	 * as simple as a single return statement.
	 * It should store the "bsf" handle where the
	 * script can get to it, for callback purposes.
	 * <p>
	 * Note that NetRexx compilation imposes serious overhead -- 11 seconds for
	 * the first compile, about 3 thereafter -- but in exchange you get
	 * Java-like speeds once the classes have been created (minus the cache
	 * lookup cost).
	 * <p>
	 * Nobody knows whether javac is threadsafe.
	 * I'm going to serialize access to the compilers to protect it.
	 */
	public Object execEvalShared (String source, int lineNo, int columnNo, 
							  Object oscript,boolean returnsObject)
	throws BSFException
	{
		Object retval=null;
		String classname=null;
		GeneratedFile gf=null;
		
		// Moved into the exec process; see comment above.
		Class rexxclass=null;
		
		String basescript=oscript.toString();
		String script=basescript; // May be altered by $$CLASSNAME$$ expansion
		
		try {
                    // Do we already have a class exactly matching this code?
                    rexxclass=(Class)codeToClass.get(basescript);
                    
                    if(rexxclass!=null)
                    	
			{
                            logger.debug("NetRexxEngine: Found pre-compiled class" +
                                                   " for script '" + basescript + "'");
                            classname=rexxclass.getName();
			}
                    else
			{
                            gf=openUniqueFile(tempDir,"BSFNetRexx",".nrx");
                            if(gf==null)
                                throw new BSFException("couldn't create NetRexx scratchfile");
                            
                            // Obtain classname
                            classname=gf.className;
                            
                            // Decide whether to declare a return type
                            String returnsDecl="";
                            if(returnsObject)
                                returnsDecl="returns java.lang.Object";
                            
                            // Write the kluge header to the file.
                            // ***** By doing so we give up the ability to use Property blocks.
                            gf.fos.write(("class "+classname+";\n")
                                         .getBytes());
                            gf.fos.write(
                                         ("method BSFNetRexxEngineEntry(bsf=com.volmit.iris.util.bsf.util.BSFFunctions) "+
                                          " public static "+returnsDecl+";\n")
								 .getBytes());
				
                            // Edit the script to replace placeholder with the generated
                            // classname. Note that this occurs _after_ the cache was
                            // checked!
                            int startpoint,endpoint;
                            if((startpoint=script.indexOf(placeholder))>=0)
				{
                                    StringBuffer changed=new StringBuffer();
                                    for(;
                                        startpoint>=0;
                                        startpoint=script.indexOf(placeholder,startpoint))
					{
                                            changed.setLength(0);   // Reset for 2nd pass or later
                                            if(startpoint>0)
                                                changed.append(script.substring(0,startpoint));
                                            changed.append(classname);
                                            endpoint=startpoint+placeholder.length();
                                            if(endpoint<script.length())
                                                changed.append(script.substring(endpoint));
                                            script=changed.toString();
					}
				}
                            
                            BSFDeclaredBean tempBean;
                            String          className;
                            
                            for (int i = 0; i < declaredBeans.size (); i++)
				{
                                    tempBean  = (BSFDeclaredBean) declaredBeans.elementAt (i);
                                    className = StringUtils.getClassName (tempBean.type);
                                    
                                    gf.fos.write ((tempBean.name + " =" + className + "   bsf.lookupBean(\"" +
                                                   tempBean.name + "\");").getBytes());
				}
                            
                            if(returnsObject)
                                gf.fos.write("return ".getBytes());
                            
                            // Copy the input to the file.
                            // Assumes all available -- probably mistake, but same as
                            // other engines.
                            gf.fos.write(script.getBytes());
                            gf.fos.close();
                            
                            logger.debug("NetRexxEngine: wrote temp file " + 
                                                   gf.file.getPath () + ", now compiling");
                            
                            // Compile through Java to .class file
                    String command=gf.file.getPath(); //classname;
                    if (logger.isDebugEnabled()) {  
                    	command += " -verbose4";
                    } else {
                        command += " -noverbose";
                        command += " -noconsole";
                    }
                    
                    netrexx.lang.Rexx cmdline= new netrexx.lang.Rexx(command);
                    int retValue;
                    
                    // May not be threadsafe. Serialize access on static object:
                    synchronized(serializeCompilation)
                        {
                            // compile to a .java file
                            retValue =
                                COM.ibm.netrexx.process.NetRexxC.main(cmdline,
                                                                      new PrintWriter(System.err)); 
                        }

				// Check if there were errors while compiling the Rexx code.
				if (retValue == 2)
				{
				  throw new BSFException(BSFException.REASON_EXECUTION_ERROR,
										 "There were NetRexx errors.");
				}

				// Load class.
                logger.debug("NetRexxEngine: loading class "+classname);
				rexxclass=EngineUtils.loadClass (mgr, classname);

				// Stash class for reuse
				codeToClass.put(basescript,rexxclass);
                        }

			Object[] args={mgrfuncs};
			retval=callStatic(rexxclass, "BSFNetRexxEngineEntry",args);
                }
                catch (BSFException e)
                    {
                        // Just forward the exception on.
                        throw e;
                    }
                catch(Exception e)
                    {
			e.printStackTrace ();
			if (e instanceof InvocationTargetException)
			{
				Throwable t = ((InvocationTargetException)e).getTargetException ();
				t.printStackTrace ();
			}
			throw new BSFException (BSFException.REASON_IO_ERROR,
									e.getMessage (), e);
		}
		finally
		{
			// Cleanup: delete the .nrx and .class files
			// (if any) generated by NetRexx Trace requests.
			
			if(gf!=null && gf.file!=null && gf.file.exists())
				gf.file.delete();  // .nrx file
			
			if(classname!=null)
			{
				// Generated src
				File file=new File(tempDir+File.separatorChar+classname+".java");
				if(file.exists())
					file.delete();
				
				// Generated class
				file=new File(classname+".class");
				if(file.exists())
					file.delete();
				
				// Can this be done without disrupting trace?
				file=new File(tempDir+File.separatorChar+classname+".crossref");
				if(file.exists())
					file.delete();
				
				// Search for and clean up minor classes, classname$xxx.class
				file=new File(tempDir);
				minorPrefix=classname+"$"; // Indirect arg to filter
				String[] minor_classfiles=
					file.list(
						// ANONYMOUS CLASS for filter:
						new FilenameFilter()
						{
							// Starts with classname$ and ends with .class
							public boolean accept(File dir,String name)
							{
								return
									(0==name.indexOf(minorPrefix))
									&&
									(name.lastIndexOf(".class")==name.length()-6)
									;
							}
						}
						);
				if(minor_classfiles!=null)
					for(int i=minor_classfiles.length;i>0;)
					{
						file=new File(minor_classfiles[--i]);
						file.delete();
					}
			}
		}
		
		return retval;
	}
	public void initialize(BSFManager mgr, String lang,Vector declaredBeans)
	throws BSFException
	{
		super.initialize(mgr, lang, declaredBeans);
		mgrfuncs = new BSFFunctions (mgr, this);
	}
private GeneratedFile openUniqueFile(String directory,String prefix,String suffix)
	{
		File file=null,obj=null;
		FileOutputStream fos=null;
		int max=1000;           // Don't try forever
		GeneratedFile gf=null;
		int i;
		String className = null;
		for(i=max,++uniqueFileOffset;
			fos==null && i>0;
			--i,++uniqueFileOffset)     
		{
			// Probably a timing hazard here... ***************
			try
				{
					className = prefix+uniqueFileOffset;
					file=new File(directory+File.separatorChar+className+suffix);
					obj=new File(directory+File.separatorChar+className+".class");
					if(file!=null && !file.exists() & obj!=null & !obj.exists())
						fos=new FileOutputStream(file);
				}
			catch(Exception e)
				{
					// File could not be opened for write, or Security Exception
					// was thrown. If someone else created the file before we could
					// open it, that's probably a threading conflict and we don't
					// bother reporting it.
					if(!file.exists())
					{
						logger.error("openUniqueFile: unexpected "+e);
					}
				}
		}
		if(fos==null)
			logger.error("openUniqueFile: Failed "+max+"attempts.");
		else
			gf=new GeneratedFile(file,fos,className);
		return gf;
	}

	public void undeclareBean (BSFDeclaredBean bean) throws BSFException {}
}

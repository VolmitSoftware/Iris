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

package com.volmit.iris.util.bsf.engines.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Vector;

import com.volmit.iris.util.bsf.BSFException;
import com.volmit.iris.util.bsf.BSFManager;
import com.volmit.iris.util.bsf.util.BSFEngineImpl;
import com.volmit.iris.util.bsf.util.CodeBuffer;
import com.volmit.iris.util.bsf.util.EngineUtils;
import com.volmit.iris.util.bsf.util.JavaUtils;
import com.volmit.iris.util.bsf.util.MethodUtils;
import com.volmit.iris.util.bsf.util.ObjInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is the interface to Java from the
 * Bean Scripting Framework.
 * <p>
 * The Java code must be written script-style -- that is, just the body of
 * the function, without class or method headers or footers.
 * The JavaEngine will generate those via a "boilerplate" wrapper:
 * <pre>
 * <code>
 * import java.lang.*;
 * import java.util.*;
 * public class $$CLASSNAME$$ {
 *   static public Object BSFJavaEngineEntry(com.volmit.iris.util.bsf.BSFManager bsf) {
 *     // Your code will be placed here
 *   }
 * }
 * </code>
 * </pre>
 * $$CLASSNAME$$ will be replaced by a generated classname of the form
 * BSFJava*, and the bsf parameter can be used to retrieve application
 * objects registered with the Bean Scripting Framework.
 * <p>
 * If you use the placeholder string $$CLASSNAME$$ elsewhere
 * in your script -- including within text strings -- BSFJavaEngine will
 * replace it with the generated name of the class before the Java code
 * is compiled.
 * <p>
 * <h2>Hazards:</h2>
 * <p>
 * NOTE that it is your responsibility to convert the code into an acceptable
 * Java string. If you're invoking the JavaEngine directly (as in the
 * JSPLikeInJava example) that means \"quoting\" characters that would
 * otherwise cause trouble.
 * <p>
 * ALSO NOTE that it is your responsibility to return an object, or null in
 * lieu thereof!
 * <p>
 * Since the code has to be compiled to a Java classfile, invoking it involves
 * a fair amount of computation to load and execute the compiler. We are
 * currently making an attempt to manage that by caching the class
 * after it has been loaded, but the indexing is fairly primitive. It has
 * been suggested that the Bean Scripting Framework may want to support
 * preload-and-name-script and execute-preloaded-script-by-name options to
 * provide better control over when and how much overhead occurs.
 * <p>
 * @author Joe Kesselman
 */
public class JavaEngine extends BSFEngineImpl {
    Class javaclass = null;
    static Hashtable codeToClass = new Hashtable();
    static String serializeCompilation = "";
    static String placeholder = "$$CLASSNAME$$";
    String minorPrefix;
        
    private Log logger = LogFactory.getLog(this.getClass().getName());
    
    /**
     * Create a scratchfile, open it for writing, return its name.
     * Relies on the filesystem to provide us with uniqueness testing.
     * NOTE THAT uniqueFileOffset continues to count; we don't want to
     * risk reusing a classname we have previously loaded in this session
     * even if the classfile has been deleted.
     */
    private int uniqueFileOffset = -1;
    
    private class GeneratedFile {
        File file = null;
        FileOutputStream fos = null;
        String className = null;
        GeneratedFile(File file, FileOutputStream fos, String className) {
            this.file = file;
            this.fos = fos;
            this.className = className;
        }
    }
    
    /**
     * Constructor.
     */
    public JavaEngine () {
        // Do compilation-possible check here??????????????
    }
    
    public Object call (Object object, String method, Object[] args) 
    throws BSFException
    {
        throw new BSFException (BSFException.REASON_UNSUPPORTED_FEATURE,
        "call() is not currently supported by JavaEngine");
    }
    
    public void compileScript (String source, int lineNo, int columnNo,
            Object script, CodeBuffer cb) throws BSFException {
        ObjInfo oldRet = cb.getFinalServiceMethodStatement ();
        
        if (oldRet != null && oldRet.isExecutable ()) {
            cb.addServiceMethodStatement (oldRet.objName + ";");
        }
        
        cb.addServiceMethodStatement (script.toString ());
        cb.setFinalServiceMethodStatement (null);
    }
    
    /**
     * This is used by an application to evaluate a string containing
     * some expression. It should store the "bsf" handle where the
     * script can get to it, for callback purposes.
     * <p>
     * Note that Java compilation imposes serious overhead,
     * but in exchange you get full Java performance
     * once the classes have been created (minus the cache lookup cost).
     * <p>
     * Nobody knows whether javac is threadsafe.
     * I'm going to serialize access to protect it.
     * <p>
     * There is no published API for invoking javac as a class. There's a trick
     * that seems to work for Java 1.1.x, but it stopped working in Java 1.2.
     * We will attempt to use it, then if necessary fall back on invoking
     * javac via the command line.
     */
    public Object eval (String source, int lineNo, int columnNo, 
            Object oscript) throws BSFException
            {
        Object retval = null;
        String classname = null;
        GeneratedFile gf = null;
        
        String basescript = oscript.toString();
        String script = basescript;	// May be altered by $$CLASSNAME$$ expansion
        
        try {
            // Do we already have a class exactly matching this code?
            javaclass = (Class)codeToClass.get(basescript);
            
            if(javaclass != null) {
                classname=javaclass.getName();
            } else {
                gf = openUniqueFile(tempDir, "BSFJava",".java");
                if( gf == null) {
                    throw new BSFException("couldn't create JavaEngine scratchfile");
                }
                // Obtain classname
                classname = gf.className;
                
                // Write the kluge header to the file.
                gf.fos.write(("import java.lang.*;"+
                        "import java.util.*;"+
                        "public class "+classname+" {\n" +
                "  static public Object BSFJavaEngineEntry(com.volmit.iris.util.bsf.BSFManager bsf) {\n")
                .getBytes());
                
                // Edit the script to replace placeholder with the generated
                // classname. Note that this occurs _after_ the cache was checked!
                int startpoint = script.indexOf(placeholder);
                int endpoint;
                if(startpoint >= 0) {
                    StringBuffer changed = new StringBuffer();
                    for(; startpoint >=0; startpoint = script.indexOf(placeholder,startpoint)) {
                        changed.setLength(0);	// Reset for 2nd pass or later
                        if(startpoint > 0) {
                            changed.append(script.substring(0,startpoint));
                        }
                        changed.append(classname);
                        endpoint = startpoint+placeholder.length();
                        if(endpoint < script.length()) {
                            changed.append(script.substring(endpoint));
                        }
                        script = changed.toString();
                    }
                }
                
                // MJD - debug
//              BSFDeclaredBean tempBean;
//              String          className;
//              
//              for (int i = 0; i < declaredBeans.size (); i++) {
//              tempBean  = (BSFDeclaredBean) declaredBeans.elementAt (i);
//              className = StringUtils.getClassName (tempBean.bean.getClass ());
//              
//              gf.fos.write ((className + " " +
//              tempBean.name + " = (" + className +
//              ")bsf.lookupBean(\"" +
//              tempBean.name + "\");").getBytes ());
//              }
                // MJD - debug
                
                // Copy the input to the file.
                // Assumes all available -- probably mistake, but same as other engines.
                gf.fos.write(script.getBytes());
                // Close the method and class
                gf.fos.write(("\n  }\n}\n").getBytes());
                gf.fos.close();
                
                // Compile through Java to .class file
                // May not be threadsafe. Serialize access on static object:
                synchronized(serializeCompilation) {
                    JavaUtils.JDKcompile(gf.file.getPath(), classPath);
                }
                
                // Load class.
                javaclass = EngineUtils.loadClass(mgr, classname);
                
                // Stash class for reuse
                codeToClass.put(basescript, javaclass);
            }
            
            Object[] callArgs = {mgr};      
            retval = internalCall(this,"BSFJavaEngineEntry",callArgs);
        }
        
        
        catch(Exception e) {
            e.printStackTrace ();
            throw new BSFException (BSFException.REASON_IO_ERROR, e.getMessage ());
        } finally {
            // Cleanup: delete the .java and .class files
            
//          if(gf!=null && gf.file!=null && gf.file.exists())
//          gf.file.delete();  // .java file
            
            
            if(classname!=null) {
                // Generated class
                File file = new File(tempDir+File.separatorChar+classname+".class");
//              if(file.exists())
//              file.delete();
                
                // Search for and clean up minor classes, classname$xxx.class
                file = new File(tempDir);  // ***** Is this required?
                minorPrefix = classname+"$"; // Indirect arg to filter
                String[] minorClassfiles = file.list(new FilenameFilter()
                            {
                        // Starts with classname$ and ends with .class
                        public boolean accept(File dir,String name) {
                            return
                            (0 == name.indexOf(minorPrefix))
                            &&
                            (name.lastIndexOf(".class") == name.length()-6);
                        }
                            });
                for(int i = 0; i < minorClassfiles.length; ++i) {
                    file = new File(minorClassfiles[i]);
//                  file.delete();
                }
            }
        }
        return retval;
    }
    
    public void initialize (BSFManager mgr, String lang,
            Vector declaredBeans) throws BSFException {
        super.initialize (mgr, lang, declaredBeans);
    }
    /**
     * Return an object from an extension.
     * @param object Object on which to make the internal_call (ignored).
     * @param method The name of the method to internal_call.
     * @param args an array of arguments to be
     * passed to the extension, which may be either
     * Vectors of Nodes, or Strings.
     */
    Object internalCall (Object object, String method, Object[] args) 
    throws BSFException
    {
        //***** ISSUE: Only static methods are currently supported
        Object retval = null;
        try {
            if(javaclass != null) {
                //***** This should call the lookup used in BML, for typesafety
                Class[] argtypes = new Class[args.length];
                for(int i=0; i<args.length; ++i) {
                    argtypes[i]=args[i].getClass();
                }
                Method m = MethodUtils.getMethod(javaclass, method, argtypes);
                retval = m.invoke(null, args);
            }
        }
        catch(Exception e) {
            throw new BSFException (BSFException.REASON_IO_ERROR, e.getMessage ());
        }
        return retval;
    }
    
    private GeneratedFile openUniqueFile(String directory,String prefix,String suffix) {
        File file = null;
        FileOutputStream fos = null;
        int max = 1000;		// Don't try forever
        GeneratedFile gf = null;
        int i;
        String className = null;
        for(i=max,++uniqueFileOffset; fos==null && i>0;--i,++uniqueFileOffset) {
            // Probably a timing hazard here... ***************
            try {
                className = prefix+uniqueFileOffset;
                file = new File(directory+File.separatorChar+className+suffix);
                if(file != null && !file.exists()) {
                    fos = new FileOutputStream(file);
                }
            }
            catch(Exception e) {
                // File could not be opened for write, or Security Exception
                // was thrown. If someone else created the file before we could
                // open it, that's probably a threading conflict and we don't
                // bother reporting it.
                if(!file.exists()) {
                    logger.error("openUniqueFile: unexpected ", e);
                }
            }
        }
        if(fos==null) {
            logger.error("openUniqueFile: Failed "+max+"attempts.");
        } else {
            gf = new GeneratedFile(file,fos,className);
        }
        return gf;
    }
}
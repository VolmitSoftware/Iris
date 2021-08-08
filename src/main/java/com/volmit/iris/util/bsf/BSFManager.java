/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.util.bsf;

import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import com.volmit.iris.util.bsf.util.CodeBuffer;
import com.volmit.iris.util.bsf.util.ObjectRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class is the entry point to the bean scripting framework. An
 * application wishing to integrate scripting to a Java app would
 * place an instance of a BSFManager in their code and use its services
 * to register the beans they want to make available for scripting,
 * load scripting engines, and run scripts.
 * <p>
 * BSFManager serves as the registry of available scripting engines
 * as well. Loading and unloading of scripting engines is
 * supported as well. Each BSFManager loads one engine per language.
 * Several BSFManagers can be created per JVM.
 *
 * @author   Sanjiva Weerawarana
 * @author   Matthew J. Duftler
 * @author   Sam Ruby
 * @author   Olivier Gruber (added original debugging support)
 * @author   Don Schwarz (added support for registering languages dynamically)
 */
public class BSFManager {
    // version string is in the form "abc.yyyymmdd" where
    // "abc" represents a dewey decimal number (three levels, each between 0 and 9),
    // and "yyyy" a four digit year, "mm" a two digit month, "dd" a two digit day.
    //
    // Example: "240.20060925" stands for: BSF version "2.4.0" as of "2006-09-25"
    protected static String version="240.20061006";

    // table of registered scripting engines
    protected static Hashtable registeredEngines = new Hashtable();

    // mapping of file extensions to languages
    protected static Hashtable extn2Lang = new Hashtable();

    // table of scripting engine instances created by this manager.
    // only one instance of a given language engine is created by a single
    // manager instance.
    protected Hashtable loadedEngines = new Hashtable();

    // table of registered beans for use by scripting engines.
    protected ObjectRegistry objectRegistry = new ObjectRegistry();

    // prop change support containing loaded engines to inform when any
    // of my interesting properties change
    protected PropertyChangeSupport pcs;

    // the class loader to use if a class loader is needed. Default is
    // he who loaded me (which may be null in which case its Class.forName).
    // protected ClassLoader classLoader = getClass().getClassLoader();
    protected ClassLoader classLoader = Thread.currentThread().getContextClassLoader(); // rgf, 2006-01-05

    // temporary directory to use to dump temporary files into. Note that
    // if class files are dropped here then unless this dir is in the
    // classpath or unless the classloader knows to look here, the classes
    // will not be found.
    protected String tempDir = ".";

    // classpath used by those that need a classpath
    protected String classPath;

    // stores BSFDeclaredBeans representing objects
    // introduced by a client of BSFManager
    protected Vector declaredBeans = new Vector();

    private Log logger = LogFactory.getLog(this.getClass().getName());

    //////////////////////////////////////////////////////////////////////
    //
    // pre-register engines that BSF supports off the shelf
    //
    //////////////////////////////////////////////////////////////////////

    static {
        try {
            Enumeration e = BSFManager.class.getClassLoader().getResources("org/apache/bsf/Languages.properties");
            while (e.hasMoreElements()) {
                URL url = (URL)e.nextElement();
                InputStream is = url.openStream();

                Properties p = new Properties();
                p.load(is);

                for (Enumeration keys = p.propertyNames(); keys.hasMoreElements();) {

                    String key = (String) keys.nextElement();
                    String value = p.getProperty(key);
                    String className = value.substring(0, value.indexOf(","));




                    // get the extensions for this language
                    String exts = value.substring(value.indexOf(",")+1, value.length());
                    StringTokenizer st = new StringTokenizer(exts, "|");
                    String[] extensions = new String[st.countTokens()];
                    for (int i = 0; st.hasMoreTokens(); i++) {
                        extensions[i] = ((String) st.nextToken()).trim();
                    }

                    registerScriptingEngine(key, className, extensions);
                }
            }
        } catch (IOException ex) {

            ex.printStackTrace();
            System.err.println("Error reading Languages file " + ex);
        } catch (NoSuchElementException nsee) {

            nsee.printStackTrace();
            System.err.println("Syntax error in Languages resource bundle");
        } catch (MissingResourceException mre) {

            mre.printStackTrace();
            System.err.println("Initialization error: " + mre.toString());
        }
    }

    public BSFManager() {
        pcs = new PropertyChangeSupport(this);
    }


   /** Returns the version string of BSF.
     *
     * @return version string in the form &quot;abc.yyyymmdd&quot; where
       &quot;abc&quot; represents a dewey decimal number (three levels, each between 0 and 9), and
       &quot;yyyy&quot; a four digit year, &quot;mm&quot; a two digit month,
       &quot;dd&quot; a two digit day.
    *
       <br>Example: &quot;<code>240.20061006</code>&quot;
       stands for: BSF version <code>2.4.0</code> as of <code>2006-10-06</code>.
    *
    *
     * @since 2006-01-17
     */
    public static String getVersion() {

        return version;
    }

    /**
     * Apply the given anonymous function of the given language to the given
     * parameters and return the resulting value.
     *
     * @param lang language identifier
     * @param source (context info) the source of this expression
     (e.g., filename)
     * @param lineNo (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param funcBody the multi-line, value returning script to evaluate
     * @param paramNames the names of the parameters above assumes
     * @param arguments values of the above parameters
     *
     * @exception BSFException if anything goes wrong while running the script
     */
    public Object apply(String lang,
                        String source,
                        int lineNo,
                        int columnNo,
                        Object funcBody,
                        Vector paramNames,
                        Vector arguments)
        throws BSFException {
    	logger.debug("BSFManager:apply");

    	final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object funcBodyf = funcBody;
        final Vector paramNamesf = paramNames;
        final Vector argumentsf = arguments;
        Object result = null;

        try {
            final Object resultf =
                AccessController.doPrivileged(new PrivilegedExceptionAction() {
                        public Object run() throws Exception {
                            return e.apply(sourcef, lineNof, columnNof,
                                           funcBodyf, paramNamesf, argumentsf);
                        }
                    });
            result = resultf;
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception: ", prive);
            throw (BSFException) prive.getException();
        }

        return result;
    }

    /**
     * Compile the application of the given anonymous function of the given
     * language to the given parameters into the given <tt>CodeBuffer</tt>.
     *
     * @param lang language identifier
     * @param source (context info) the source of this expression
     (e.g., filename)
     * @param lineNo (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param funcBody the multi-line, value returning script to evaluate
     * @param paramNames the names of the parameters above assumes
     * @param arguments values of the above parameters
     * @param cb       code buffer to compile into
     *
     * @exception BSFException if anything goes wrong while running the script
     */
    public void compileApply(String lang,
                             String source,
                             int lineNo,
                             int columnNo,
                             Object funcBody,
                             Vector paramNames,
                             Vector arguments,
                             CodeBuffer cb)
        throws BSFException {
    	logger.debug("BSFManager:compileApply");

        final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object funcBodyf = funcBody;
        final Vector paramNamesf = paramNames;
        final Vector argumentsf = arguments;
        final CodeBuffer cbf = cb;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        e.compileApply(sourcef, lineNof, columnNof,
                                       funcBodyf, paramNamesf,
                                       argumentsf, cbf);
                        return null;
                    }
                });
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception :", prive);
            throw (BSFException) prive.getException();
        }
    }

    /**
     * Compile the given expression of the given language into the given
     * <tt>CodeBuffer</tt>.
     *
     * @param lang     language identifier
     * @param source   (context info) the source of this expression
     (e.g., filename)
     * @param lineNo   (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param expr     the expression to compile
     * @param cb       code buffer to compile into
     *
     * @exception BSFException if any error while compiling the expression
     */
    public void compileExpr(String lang,
                            String source,
                            int lineNo,
                            int columnNo,
                            Object expr,
                            CodeBuffer cb)
        throws BSFException {
    	logger.debug("BSFManager:compileExpr");

    	final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object exprf = expr;
        final CodeBuffer cbf = cb;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        e.compileExpr(sourcef, lineNof, columnNof, exprf, cbf);
                        return null;
                    }
                });
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception :", prive);
            throw (BSFException) prive.getException();
        }
    }

    /**
     * Compile the given script of the given language into the given
     * <tt>CodeBuffer</tt>.
     *
     * @param lang     language identifier
     * @param source   (context info) the source of this script
     (e.g., filename)
     * @param lineNo   (context info) the line number in source for script
     * @param columnNo (context info) the column number in source for script
     * @param script   the script to compile
     * @param cb       code buffer to compile into
     *
     * @exception BSFException if any error while compiling the script
     */
    public void compileScript(String lang,
                              String source,
                              int lineNo,
                              int columnNo,
                              Object script,
                              CodeBuffer cb)
        throws BSFException {
    	logger.debug("BSFManager:compileScript");

        final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object scriptf = script;
        final CodeBuffer cbf = cb;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        e.compileScript(sourcef, lineNof, columnNof,
                                        scriptf, cbf);
                        return null;
                    }
                });
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception :", prive);
            throw (BSFException) prive.getException();
        }
    }

    /**
     * Declare a bean. The difference between declaring and registering
     * is that engines are spsed to make declared beans "pre-available"
     * in the scripts as far as possible. That is, if a script author
     * needs a registered bean, he needs to look it up in some way. However
     * if he needs a declared bean, the language has the responsibility to
     * make those beans avaialable "automatically."
     * <p>
     * When a bean is declared it is automatically registered as well
     * so that any declared bean can be gotton to by looking it up as well.
     * <p>
     * If any of the languages that are already running in this manager
     * says they don't like this (by throwing an exception) then this
     * method will simply quit with that exception. That is, any engines
     * that come after than in the engine enumeration will not even be
     * told about this new bean.
     * <p>
     * So, in general its best to declare beans before the manager has
     * been asked to load any engines because then the user can be informed
     * when an engine rejects it. Also, its much more likely that an engine
     * can declare a bean at start time than it can at any time.
     *
     * @param beanName name to declare bean as
     * @param bean     the bean that's being declared
     * @param type     the type to represent the bean as
     *
     * @exception BSFException if any of the languages that are already
     *            running decides to throw an exception when asked to
     *            declare this bean.
     */
    public void declareBean(String beanName, Object bean, Class type)
        throws BSFException {
    	logger.debug("BSFManager:declareBean");

        registerBean(beanName, bean);

        BSFDeclaredBean tempBean = new BSFDeclaredBean(beanName, bean, type);
        declaredBeans.addElement(tempBean);

        Enumeration enginesEnum = loadedEngines.elements();
        BSFEngine engine;
        while (enginesEnum.hasMoreElements()) {
            engine = (BSFEngine) enginesEnum.nextElement();
            engine.declareBean(tempBean);
        }
    }

    /**
     * Evaluate the given expression of the given language and return the
     * resulting value.
     *
     * @param lang language identifier
     * @param source (context info) the source of this expression
     (e.g., filename)
     * @param lineNo (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param expr the expression to evaluate
     *
     * @exception BSFException if anything goes wrong while running the script
     */
    public Object eval(String lang,
                       String source,
                       int lineNo,
                       int columnNo,
                       Object expr)
        throws BSFException {
    	logger.debug("BSFManager:eval");

        final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object exprf = expr;
        Object result = null;

        try {
            final Object resultf =
                AccessController.doPrivileged(new PrivilegedExceptionAction() {
                        public Object run() throws Exception {
                            return e.eval(sourcef, lineNof, columnNof, exprf);
                        }
                    });
            result = resultf;
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception: ", prive);
            throw (BSFException) prive.getException();
        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience functions for exec'ing and eval'ing scripts directly
    // without loading and dealing with engines etc..
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Execute the given script of the given language.
     *
     * @param lang     language identifier
     * @param source   (context info) the source of this expression
     (e.g., filename)
     * @param lineNo   (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param script   the script to execute
     *
     * @exception BSFException if anything goes wrong while running the script
     */
    public void exec(String lang,
                     String source,
                     int lineNo,
                     int columnNo,
                     Object script)
        throws BSFException {
    	logger.debug("BSFManager:exec");

        final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object scriptf = script;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        e.exec(sourcef, lineNof, columnNof, scriptf);
                        return null;
                    }
                });
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception :", prive);
            throw (BSFException) prive.getException();
        }
    }

    /**
     * Execute the given script of the given language, attempting to
     * emulate an interactive session w/ the language.
     *
     * @param lang     language identifier
     * @param source   (context info) the source of this expression
     *                 (e.g., filename)
     * @param lineNo   (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param script   the script to execute
     *
     * @exception BSFException if anything goes wrong while running the script
     */
    public void iexec(String lang,
                     String source,
                     int lineNo,
                     int columnNo,
                     Object script)
        throws BSFException {
    	logger.debug("BSFManager:iexec");

        final BSFEngine e = loadScriptingEngine(lang);
        final String sourcef = source;
        final int lineNof = lineNo, columnNof = columnNo;
        final Object scriptf = script;

        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        e.iexec(sourcef, lineNof, columnNof, scriptf);
                        return null;
                    }
                });
        } catch (PrivilegedActionException prive) {

        	logger.error("Exception :", prive);
            throw (BSFException) prive.getException();
        }
    }

    /**
     * Get classLoader
     */
    public ClassLoader getClassLoader() {
    	logger.debug("BSFManager:getClassLoader");
        return classLoader;
    }

    /**
     * Get classPath
     */
    public String getClassPath() {
    	logger.debug("BSFManager:getClassPath");
        if (classPath == null) {
            try {
                classPath = System.getProperty("java.class.path");
            } catch (Throwable t) {

            	logger.debug("Exception :", t);
                // prolly a security exception .. so no can do
            }
        }
        return classPath;
    }

    /**
     * Determine the language of a script file by looking at the file
     * extension.
     *
     * @param fileName the name of the file
     *
     * @return the scripting language the file is in if the file extension
     *         is known to me (must have been registered via
     *         registerScriptingEngine).
     *
     * @exception BSFException if file's extension is unknown.
     */
    public static String getLangFromFilename(String fileName)
        throws BSFException {
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex != -1) {
            String extn = fileName.substring(dotIndex + 1);
            String langval = (String) extn2Lang.get(extn);
            String lang = null;
            int index, loops = 0;

            if (langval != null) {
                while ((index = langval.indexOf(":", 0)) != -1) {
                    // Great. Multiple language engines registered
                    // for this extension.
                    // Try to find first one that is in our classpath.
                    lang = langval.substring(0, index);
                    langval = langval.substring(index + 1);
                    loops++;

                    // Test to see if in classpath
                    try {
                        String engineName =
                            (String) registeredEngines.get(lang);
                        Class.forName(engineName);
                    } catch (ClassNotFoundException cnfe) {

                        // Bummer.
                        lang = langval;
                        continue;
                    }

                    // Got past that? Good.
                    break;
                }
                if (loops == 0) { lang = langval; }
            }

            if (lang != null && lang != "") {
                return lang;
            }
        }
        throw new BSFException(BSFException.REASON_OTHER_ERROR,
                               "file extension missing or unknown: "
                               + "unable to determine language for '"
                               + fileName
                               + "'");
    }

    /**
     * Return the current object registry of the manager.
     *
     * @return the current registry.
     */
    public ObjectRegistry getObjectRegistry() {
        return objectRegistry;
    }

    /**
     * Get tempDir
     */
    public String getTempDir() {
        return tempDir;
    }

    /**
     * Determine whether a language is registered.
     *
     * @param lang string identifying a language
     *
     * @return true iff it is
     */
    public static boolean isLanguageRegistered(String lang) {
        return (registeredEngines.get(lang) != null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Bean scripting framework services
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Load a scripting engine based on the lang string identifying it.
     *
     * @param lang string identifying language
     * @exception BSFException if the language is unknown (i.e., if it
     *            has not been registered) with a reason of
     *            REASON_UNKNOWN_LANGUAGE. If the language is known but
     *            if the interface can't be created for some reason, then
     *            the reason is set to REASON_OTHER_ERROR and the actual
     *            exception is passed on as well.
     */
    public BSFEngine loadScriptingEngine(String lang) throws BSFException {
    	logger.debug("BSFManager:loadScriptingEngine");

        // if its already loaded return that
        BSFEngine eng = (BSFEngine) loadedEngines.get(lang);
        if (eng != null) {
            return eng;
        }

        // is it a registered language?
        String engineClassName = (String) registeredEngines.get(lang);
        if (engineClassName == null) {
        	logger.error("unsupported language: " + lang);
            throw new BSFException(BSFException.REASON_UNKNOWN_LANGUAGE,
                                   "unsupported language: " + lang);
        }

        // create the engine and initialize it. if anything goes wrong
        // except.
        try {
            Class engineClass =
                (classLoader == null)
                ? Class.forName(engineClassName)
                : classLoader.loadClass(engineClassName);
            final BSFEngine engf = (BSFEngine) engineClass.newInstance();
            final BSFManager thisf = this;
            final String langf = lang;
            final Vector dbf = declaredBeans;
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        engf.initialize(thisf, langf, dbf);
                        return null;
                    }
                });
            eng = engf;
            loadedEngines.put(lang, eng);
            pcs.addPropertyChangeListener(eng);
            return eng;
        } catch (PrivilegedActionException prive) {

        	    logger.error("Exception :", prive);
                throw (BSFException) prive.getException();
        } catch (Throwable t) {

        	logger.error("Exception :", t);
            throw new BSFException(BSFException.REASON_OTHER_ERROR,
                                   "unable to load language: " + lang,
                                   t);
        }
    }

    /**
     * return a handle to a bean registered in the bean registry by the
     * application or a scripting engine. Returns null if bean is not found.
     *
     * @param beanName name of bean to look up
     *
     * @return the bean if its found or null
     */
    public Object lookupBean(String beanName) {
    	logger.debug("BSFManager:lookupBean");

        try {
            return ((BSFDeclaredBean)objectRegistry.lookup(beanName)).bean;
        } catch (IllegalArgumentException e) {

        	logger.debug("Exception :", e);
            return null;
        }
    }

    /**
     * Registering a bean allows a scripting engine or the application to
     * access that bean by name and to manipulate it.
     *
     * @param beanName name to register under
     * @param bean     the bean to register
     */
    public void registerBean(String beanName, Object bean) {
    	logger.debug("BSFManager:registerBean");

        BSFDeclaredBean tempBean;

        if(bean == null) {
            tempBean = new BSFDeclaredBean(beanName, null, null);
        } else {

            tempBean = new BSFDeclaredBean(beanName, bean, bean.getClass());
        }
        objectRegistry.register(beanName, tempBean);
    }

    /**
     * Register a scripting engine in the static registry of the
     * BSFManager.
     *
     * @param lang string identifying language
     * @param engineClassName fully qualified name of the class interfacing
     *        the language to BSF.
     * @param extensions array of file extensions that should be mapped to
     *        this language type. may be null.
     */
    public static void registerScriptingEngine(String lang,
                                               String engineClassName,
                                               String[] extensions) {
        registeredEngines.put(lang, engineClassName);
        if (extensions != null) {
            for (int i = 0; i < extensions.length; i++) {
                String langstr = (String) extn2Lang.get(extensions[i]);
                langstr = (langstr == null) ? lang : lang + ":" + langstr;
                extn2Lang.put(extensions[i], langstr);
            }
        }
    }

    /**
     * Set the class loader for those that need to use it. Default is he
     * who loaded me or null (i.e., its Class.forName).
     *
     * @param classLoader the class loader to use.
     */
    public void setClassLoader(ClassLoader classLoader) {
    	logger.debug("BSFManager:setClassLoader");

        pcs.firePropertyChange("classLoader", this.classLoader, classLoader);
        this.classLoader = classLoader;
    }

    /**
     * Set the classpath for those that need to use it. Default is the value
     * of the java.class.path property.
     *
     * @param classPath the classpath to use
     */
    public void setClassPath(String classPath) {
    	logger.debug("BSFManager:setClassPath");

        pcs.firePropertyChange("classPath", this.classPath, classPath);
        this.classPath = classPath;
    }

    /**
     * Set the object registry used by this manager. By default a new
     * one is created when the manager is new'ed and this overwrites
     * that one.
     *
     * @param objectRegistry the registry to use
     */
    public void setObjectRegistry(ObjectRegistry objectRegistry) {
    	logger.debug("BSFManager:setObjectRegistry");

        this.objectRegistry = objectRegistry;
    }

    /**
     * Temporary directory to put stuff into (for those who need to). Note
     * that unless this directory is in the classpath or unless the
     * classloader knows to look in here, any classes here will not
     * be found! BSFManager provides a service method to load a class
     * which uses either the classLoader provided by the class loader
     * property or, if that fails, a class loader which knows to load from
     * the tempdir to try to load the class. Default value of tempDir
     * is "." (current working dir).
     *
     * @param tempDir the temporary directory
     */
    public void setTempDir(String tempDir) {
    	logger.debug("BSFManager:setTempDir");

        pcs.firePropertyChange("tempDir", this.tempDir, tempDir);
        this.tempDir = tempDir;
    }

    /**
     * Gracefully terminate all engines
     */
    public void terminate() {
    	logger.debug("BSFManager:terminate");

        Enumeration enginesEnum = loadedEngines.elements();
        BSFEngine engine;
        while (enginesEnum.hasMoreElements()) {
            engine = (BSFEngine) enginesEnum.nextElement();
            engine.terminate();
        }

        loadedEngines = new Hashtable();
    }

    /**
     * Undeclare a previously declared bean. This removes the bean from
     * the list of declared beans in the manager as well as asks every
     * running engine to undeclared the bean. As with above, if any
     * of the engines except when asked to undeclare, this method does
     * not catch that exception. Quietly returns if the bean is unknown.
     *
     * @param beanName name of bean to undeclare
     *
     * @exception BSFException if any of the languages that are already
     *            running decides to throw an exception when asked to
     *            undeclare this bean.
     */
    public void undeclareBean(String beanName) throws BSFException {
    	logger.debug("BSFManager:undeclareBean");

        unregisterBean(beanName);

        BSFDeclaredBean tempBean = null;
        boolean found = false;
        for (Iterator i = declaredBeans.iterator(); i.hasNext();) {
            tempBean = (BSFDeclaredBean) i.next();
            if (tempBean.name.equals(beanName)) {
            	found = true;
                break;
            }
        }

        if (found) {
            declaredBeans.removeElement(tempBean);

            Enumeration enginesEnum = loadedEngines.elements();
            while (enginesEnum.hasMoreElements()) {
                BSFEngine engine = (BSFEngine) enginesEnum.nextElement();
                engine.undeclareBean(tempBean);
            }
        }
    }

    /**
     * Unregister a previously registered bean. Silent if name is not found.
     *
     * @param beanName name of bean to unregister
     */
    public void unregisterBean(String beanName) {
    	logger.debug("BSFManager:unregisterBean");

        objectRegistry.unregister(beanName);
    }
}
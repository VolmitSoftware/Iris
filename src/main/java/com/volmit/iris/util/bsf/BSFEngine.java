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

import java.beans.PropertyChangeListener;
import java.util.Vector;

import com.volmit.iris.util.bsf.util.CodeBuffer;

/**
 * This is the view of a scripting engine assumed by the Bean Scripting
 * Framework. This interface is used when an application decides to
 * run some script under application control. (This is the reverse of
 * the more common situation, which is that of the scripting language
 * calling into the application.)
 * <p>
 * When a scripting engine is first fired up, the initialize() 
 * method is called right after construction.
 * <p>
 * A scripting engine must provide two access points for applications
 * to call into them: via function calls and via expression evaluation.
 * It must also support loading scripts.
 * <p>
 * A scripting engine is a property change listener and will be notified
 * when any of the relevant properties of the manager change. (See
 * BSFManager to see which of its properties are bound.)
 *
 * @author  Sanjiva Weerawarana
 * @author  Matthew J. Duftler
 */
public interface BSFEngine extends PropertyChangeListener {
	
	/**
	 * This is used by an application to invoke an anonymous function. An
	 * anonymous function is a multi-line script which when evaluated will
	 * produce a value. These are separated from expressions and scripts
	 * because the prior are spsed to be good 'ol expressions and scripts
	 * are not value returning. We allow anonymous functions to have parameters
	 * as well for completeness.
	 *
	 * @param source   (context info) the source of this expression
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for expr
	 * @param columnNo (context info) the column number in source for expr
	 * @param funcBody the multi-line, value returning script to evaluate
	 * @param paramNames the names of the parameters above assumes
	 * @param arguments values of the above parameters
	 *
	 * @exception BSFException if anything goes wrong while doin' it.
	 */
	public Object apply(
		String source,
		int lineNo,
		int columnNo,
		Object funcBody,
		Vector paramNames,
		Vector arguments)
		throws BSFException;
	/**
	 * This is used by an application to call into the scripting engine
	 * to make a function/method call. The "object" argument is the object
	 * whose method is to be called, if that applies. For non-OO languages,
	 * this is typically ignored and should be given as null. For pretend-OO
	 * languages such as VB, this would be the (String) name of the object.
	 * The arguments are given in the args array.
	 *
	 * @param object object on which to make the call
	 * @param name   name of the method / procedure to call
	 * @param args   the arguments to be given to the procedure
	 *
	 * @exception BSFException if anything goes wrong while eval'ing a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public Object call(Object object, String name, Object[] args)
		throws BSFException;
	/**
	 * This is used by an application to compile an anonymous function. See
	 * comments in apply for more hdetails.
	 *
	 * @param source   (context info) the source of this expression
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for expr
	 * @param columnNo (context info) the column number in source for expr
	 * @param funcBody the multi-line, value returning script to evaluate
	 * @param paramNames the names of the parameters above assumes
	 * @param arguments values of the above parameters
	 * @param cb       the CodeBuffer to compile into
	 *
	 * @exception BSFException if anything goes wrong while doin' it.
	 */
	public void compileApply(
		String source,
		int lineNo,
		int columnNo,
		Object funcBody,
		Vector paramNames,
		Vector arguments,
		CodeBuffer cb)
		throws BSFException;
	/**
	 * This is used by an application to compile a value-returning expression.
	 * The expr may be string or some other type, depending on the language.
	 * The generated code is dumped into the <tt>CodeBuffer</tt>.
	 *
	 * @param source   (context info) the source of this expression
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for expr
	 * @param columnNo (context info) the column number in source for expr
	 * @param expr     the expression to compile
	 * @param cb       the CodeBuffer to compile into
	 *
	 * @exception BSFException if anything goes wrong while compiling a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public void compileExpr(
		String source,
		int lineNo,
		int columnNo,
		Object expr,
		CodeBuffer cb)
		throws BSFException;
	/**
	 * This is used by an application to compile some script. The
	 * script may be string or some other type, depending on the
	 * language. The generated code is dumped into the <tt>CodeBuffer</tt>.
	 *
	 * @param source   (context info) the source of this script
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for script
	 * @param columnNo (context info) the column number in source for script
	 * @param script   the script to compile
	 * @param cb       the CodeBuffer to compile into
	 *
	 * @exception BSFException if anything goes wrong while compiling a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public void compileScript(
		String source,
		int lineNo,
		int columnNo,
		Object script,
		CodeBuffer cb)
		throws BSFException;
	/**
	 * Declare a bean after the engine has been started. Declared beans
	 * are beans that are named and which the engine must make available
	 * to the scripts it runs in the most first class way possible.
	 *
	 * @param bean the bean to declare
	 *
	 * @exception BSFException if the engine cannot do this operation
	 */
	public void declareBean(BSFDeclaredBean bean) throws BSFException;
	/**
	 * This is used by an application to evaluate an expression. The
	 * expression may be string or some other type, depending on the
	 * language. (For example, for BML it'll be an org.w3c.dom.Element
	 * object.)
	 *
	 * @param source   (context info) the source of this expression
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for expr
	 * @param columnNo (context info) the column number in source for expr
	 * @param expr     the expression to evaluate
	 *
	 * @exception BSFException if anything goes wrong while eval'ing a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public Object eval(String source, int lineNo, int columnNo, Object expr)
		throws BSFException;
	/**
	 * This is used by an application to execute some script. The
	 * expression may be string or some other type, depending on the
	 * language. Returns nothing but if something goes wrong it excepts
	 * (of course).
	 *
	 * @param source   (context info) the source of this expression
	 *                 (e.g., filename)
	 * @param lineNo   (context info) the line number in source for expr
	 * @param columnNo (context info) the column number in source for expr
	 * @param script   the script to execute
	 *
	 * @exception BSFException if anything goes wrong while exec'ing a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public void exec(String source, int lineNo, int columnNo, Object script)
		throws BSFException;
    /**
     * This is used by an application to execute some script, as though
     * one were interacting with the language in an interactive session. 
     * The expression may be string or some other type, depending on the
     * language. Returns nothing but if something goes wrong it excepts (of
     * course).
     *
     * @param source   (context info) the source of this expression
     *                 (e.g., filename)
     * @param lineNo   (context info) the line number in source for expr
     * @param columnNo (context info) the column number in source for expr
     * @param script   the script to execute
     *
     * @exception BSFException if anything goes wrong while exec'ing a
     *            BSFException is thrown. The reason indicates the problem.
     */
    public void iexec(String source, int lineNo, int columnNo, Object script)
        throws BSFException;

	/**
	 * This method is used to initialize the engine right after construction.
	 * This method will be called before any calls to eval or call. At this
	 * time the engine should capture the current values of interesting
	 * properties from the manager. In the future, any changes to those 
	 * will be mirrored to me by the manager via a property change event.
	 * 
	 * @param mgr           The BSFManager that's hosting this engine.
	 * @param lang          Language string which this engine is handling.
	 * @param declaredBeans Vector of BSFDeclaredObject containing beans
	 *        that should be declared into the language runtime at init
	 *        time as best as possible.
	 *
	 * @exception BSFException if anything goes wrong while init'ing a
	 *            BSFException is thrown. The reason indicates the problem.
	 */
	public void initialize(BSFManager mgr, String lang, Vector declaredBeans)
		throws BSFException;
	/**
	 * Graceful termination
	 */
	public void terminate();
	/**
	 * Undeclare a previously declared bean.
	 *
	 * @param bean the bean to undeclare
	 *
	 * @exception BSFException if the engine cannot do this operation
	 */
	public void undeclareBean(BSFDeclaredBean bean) throws BSFException;
}

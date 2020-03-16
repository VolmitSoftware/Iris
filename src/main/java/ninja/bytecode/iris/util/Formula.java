package ninja.bytecode.iris.util;

import javax.script.ScriptException;

/**
 * Evaluates an expression using javascript engine and returns the double
 * result. This can take variable parameters, so you need to define them.
 * Parameters are defined as $[0-9]. For example evaluate("4$0/$1", 1, 2); This
 * makes the expression (4x1)/2 == 2. Keep note that you must use 0-9, you
 * cannot skip, or start at a number other than 0.
 * 
 * @author cyberpwn
 */
public class Formula
{
	private String expression;

	/**
	 * Evaluates an expression using javascript engine and returns the double
	 * result. This can take variable parameters, so you need to define them.
	 * Parameters are defined as $[0-9]. For example evaluate("4$0/$1", 1, 2); This
	 * makes the expression (4x1)/2 == 2. Keep note that you must use 0-9, you
	 * cannot skip, or start at a number other than 0.
	 * 
	 * @param expression
	 *            the expression with variables
	 * @param args
	 *            the arguments/variables
	 */
	public Formula(String expression)
	{
		this.expression = expression;
	}

	/**
	 * Evaluates the given args
	 * 
	 * @param args
	 *            the args
	 * @return the return result
	 * @throws IndexOutOfBoundsException
	 *             invalid number of args
	 * @throws ScriptException
	 *             syntax issue
	 */
	public double evaluate(Double... args) throws IndexOutOfBoundsException, ScriptException
	{
		return M.evaluate(expression, args);
	}
}
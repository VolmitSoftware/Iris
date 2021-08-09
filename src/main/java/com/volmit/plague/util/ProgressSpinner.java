package com.volmit.plague.util;
/**
 * A progress spinner. Calling toString will not only return the next state, but
 * it will also set the next index
 *
 * @author cyberpwn
 */
public class ProgressSpinner
{
	private String[] chars;
	private int index;
	public static final ProgressSpinner DEFAULT;
	public static final ProgressSpinner NETWORK;
	public static final ProgressSpinner CIRCLES;
	public static final ProgressSpinner RANDOMS;
	public static final ProgressSpinner MERGERS;

	/**
	 * Create a custom spinner
	 *
	 * @param chars
	 *            the animation chars
	 */
	public ProgressSpinner(String... chars)
	{
		index = 0;
		this.chars = chars;
	}

	/**
	 * Create a default spinner
	 */
	public ProgressSpinner()
	{
		this("" + '\u25d0', "" + '\u25d3', "" + '\u25d4', "" + '\u25d1', "" + '\u25d5', "" + '\u25d2');
	}

	/**
	 * Get the next char from the index
	 */
	@Override
	public String toString()
	{
		if(chars.length > index + 1)
		{
			index++;
		}

		else
		{
			index = 0;
		}

		return chars[index] + "";
	}

	public String[] getChars()
	{
		return chars;
	}

	public int getIndex()
	{
		return index;
	}

	static
	{
		DEFAULT = new ProgressSpinner();
		NETWORK = new ProgressSpinner("\u2630", "\u2631", "\u2632", "\u2633", "\u2634", "\u2635", "\u2636", "\u2637");
		CIRCLES = new ProgressSpinner("\u25F4", "\u25F5", "\u25F6", "\u25F7");
		RANDOMS = new ProgressSpinner("\u2680", "\u2681", "\u2682", "\u2683", "\u2684", "\u2685");
		MERGERS = new ProgressSpinner("\u26AC", "\u26AD", "\u26AE", "\u26AF", "\u26AE", "\u26AD");
	}
}
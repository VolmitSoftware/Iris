package com.volmit.plague.util;

import org.apache.commons.lang.WordUtils;




/**
 * Textual Utilities
 *
 * @author cyberpwn
 */
public class TXT
{
	/**
	 * Repeat a string
	 *
	 * @param str
	 *            the string
	 * @param len
	 *            the amount of repeats
	 * @return the string
	 */
	public static String repeat(String str, int len)
	{
		return Form.repeat(str, len);
	}

	/**
	 * Wrap the text into lines
	 *
	 * @param str
	 *            the string
	 * @param len
	 *            the length
	 * @return the strings
	 */
	public static KList<String> wrap(String str, int len)
	{
		String format = C.getLastColors(str);
		KList<String> lines = new KList<String>();

		for(String i : WordUtils.wrap(str, len).split("\n"))
		{
			lines.add(format + C.getLastColors(i) + i.trim());
		}

		return lines;
	}

	/**
	 * Build a tag
	 *
	 * @param brace
	 *            the brace color
	 * @param tag
	 *            the tag color
	 * @param colon
	 *            the colon color
	 * @param text
	 *            the text color
	 * @param tagName
	 *            the tag name
	 * @return the tag
	 */
	public static String makeTag(C brace, C tag, C colon, C text, String tagName)
	{
		return brace + "[" + tag + tagName + brace + "]" + colon + ": " + text;
	}

	/**
	 * Build a tag
	 *
	 * @param brace
	 *            the brace color
	 * @param tag
	 *            the tag color
	 * @param text
	 *            the text color
	 * @param tagName
	 *            the tag name
	 * @return the tag
	 */
	public static String makeTag(C brace, C tag, C text, String tagName)
	{
		return brace + "[" + tag + tagName + brace + "]" + ": " + text;
	}

	/**
	 * Create a line
	 *
	 * @param color
	 *            the color
	 * @param len
	 *            the length
	 * @return the line
	 */
	public static String line(C color, int len)
	{
		return color + "" + C.STRIKETHROUGH + repeat(" ", len);
	}

	/**
	 * Create a line
	 *
	 * @param color
	 *            the color
	 * @param len
	 *            the length
	 * @param text
	 *            the centered text to display
	 * @return the line
	 */
	public static String line(C color, int len, String text)
	{
		int side = (text.length() / 2) - 2;
		int lside = (len / 2) - side;
		return color + "" + C.STRIKETHROUGH + repeat(" ", lside) + C.RESET + C.BOLD + " " + text + " " + C.RESET + color + "" + C.STRIKETHROUGH + repeat(" ", lside);
	}

	/**
	 * Create a line
	 *
	 * @param color
	 *            the color
	 * @param text
	 *            the centered text to display
	 * @return the line
	 */
	public static String line(C color, String text)
	{
		int len = 66;
		int side = (text.length() / 2) - 2;
		int lside = (len / 2) - side;
		return color + "" + C.STRIKETHROUGH + repeat(" ", lside) + C.RESET + C.BOLD + " " + text + " " + C.RESET + color + "" + C.STRIKETHROUGH + repeat(" ", lside);
	}

	/**
	 * Create an underline
	 *
	 * @param color
	 *            the color
	 * @param len
	 *            the length
	 * @return the line
	 */
	public static String underline(C color, int len)
	{
		return color + "" + C.STRIKETHROUGH + repeat(" ", len);
	}

	/**
	 * Get a fancy underline
	 *
	 * @param cc
	 *            the color
	 * @param len
	 *            the length of the line
	 * @param percent
	 *            the progress of the line
	 * @param l
	 *            the left text
	 * @param f
	 *            the centered text
	 * @param r
	 *            the right text
	 * @return the line
	 */
	public static String getLine(C cc, int len, double percent, String l, String f, String r)
	{
		String k = cc + "" + C.UNDERLINE + l;
		len = len < l.length() + r.length() + f.length() ? l.length() + r.length() + f.length() + 6 : len;
		int a = len - (l.length() + r.length() + f.length());
		int b = (int) ((double) a * (double) percent);
		int c = len - b;
		return (percent == 0.0 ? ((k + C.DARK_GRAY + C.UNDERLINE + Form.repeat(" ", c) + C.DARK_GRAY + C.UNDERLINE + r)) : (k + Form.repeat(" ", b) + (percent == 1.0 ? r : (f + C.DARK_GRAY + C.UNDERLINE + Form.repeat(" ", c) + C.DARK_GRAY + C.UNDERLINE + r))));
	}
}

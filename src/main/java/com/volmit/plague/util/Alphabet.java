package com.volmit.plague.util;


import com.volmit.iris.util.collection.KList;

/**
 * Alphabet military style
 *
 * @author cyberpwn
 */
public enum Alphabet
{
	/**
	 * A
	 */
	ALPHA,

	/**
	 * B
	 */
	BRAVO,

	/**
	 * C
	 */
	CHARLIE,

	/**
	 * D
	 */
	DELTA,

	/**
	 * E
	 */
	ECHO,

	/**
	 * F
	 */
	FOXTROT,

	/**
	 * G
	 */
	GOLF,

	/**
	 * H
	 */
	HOTEL,

	/**
	 * I
	 */
	INDIA,

	/**
	 * J
	 */
	JULIET,

	/**
	 * K
	 */
	KILO,

	/**
	 * L
	 */
	LIMA,

	/**
	 * M
	 */
	MIKE,

	/**
	 * N
	 */
	NOVEMBER,

	/**
	 * O
	 */
	OSCAR,

	/**
	 * P
	 */
	PAPA,

	/**
	 * Q
	 */
	QUEBEC,

	/**
	 * R
	 */
	ROMEO,

	/**
	 * S
	 */
	SIERRA,

	/**
	 * T
	 */
	TANGO,

	/**
	 * U
	 */
	UNIFORM,

	/**
	 * V
	 */
	VICTOR,

	/**
	 * W
	 */
	WISKEY,

	/**
	 * X
	 */
	XRAY,

	/**
	 * Y
	 */
	YANKEE,

	/**
	 * Z
	 */
	ZULU;

	/**
	 * Get the lower case form of the char
	 *
	 * @return the lower case letter representation
	 */
	public char getChar()
	{
		return this.toString().substring(0, 1).toLowerCase().toCharArray()[0];
	}

	public AlphabetRange to(Alphabet a)
	{
		return new AlphabetRange(this, a);
	}

	/**
	 * ROMEO ALPHA DELTA INDIA OSCAR
	 *
	 * @param msg
	 * @return MIKE SIERRA GOLF
	 */
	public static String radioTalk(String msg)
	{
		StringBuilder total = new StringBuilder();

		for(Character i : msg.toCharArray())
		{
			Alphabet chr = fromChar(i);
			total.append(chr == null ? "" : chr.toString().toLowerCase()).append(" ");
		}

		return total.toString();
	}

	/**
	 * From char to alphabet
	 *
	 * @param c
	 *            the char
	 * @return the alphabet representation
	 */
	public static Alphabet fromChar(char c)
	{
		for(Alphabet a : values())
		{
			if(a.getChar() == Character.toLowerCase(c))
			{
				return a;
			}
		}

		return null;
	}

	/**
	 * Get the alphabet in a list of chars lowercased
	 *
	 * @return the alphabet
	 */
	public static KList<Character> getAlphabet()
	{
		KList<Character> al = new KList<>();

		for(Alphabet a : values())
		{
			al.add(a.getChar());
		}

		return al;
	}
}

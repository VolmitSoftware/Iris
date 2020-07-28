package com.volmit.iris.util;


import java.io.Serializable;

/**
 * A Biset
 *
 * @author cyberpwn
 *
 * @param <A>
 *            the first object type
 * @param <B>
 *            the second object type
 */
@SuppressWarnings("hiding")
public class GBiset<A, B> implements Serializable
{
	private static final long serialVersionUID = 1L;
	private A a;
	private B b;

	/**
	 * Create a new Biset
	 *
	 * @param a
	 *            the first object
	 * @param b
	 *            the second object
	 */
	public GBiset(A a, B b)
	{
		this.a = a;
		this.b = b;
	}

	/**
	 * Get the object of the type A
	 *
	 * @return the first object
	 */
	public A getA()
	{
		return a;
	}

	/**
	 * Set the first object
	 *
	 * @param a
	 *            the first object A
	 */
	public void setA(A a)
	{
		this.a = a;
	}

	/**
	 * Get the second object
	 *
	 * @return the second object
	 */
	public B getB()
	{
		return b;
	}

	/**
	 * Set the second object
	 *
	 * @param b
	 */
	public void setB(B b)
	{
		this.b = b;
	}
}

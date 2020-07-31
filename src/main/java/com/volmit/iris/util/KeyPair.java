package com.volmit.iris.util;

/**
 * Represents a keypair
 * @author cyberpwn
 *
 * @param <K> the key type
 * @param <V> the value type
 */
@SuppressWarnings("hiding")
public class KeyPair<K, V>
{
	private K k;
	private V v;

	/**
	 * Create a keypair
	 * @param k the key
	 * @param v the value
	 */
	public KeyPair(K k, V v)
	{
		this.k = k;
		this.v = v;
	}

	public K getK()
	{
		return k;
	}

	public void setK(K k)
	{
		this.k = k;
	}

	public V getV()
	{
		return v;
	}

	public void setV(V v)
	{
		this.v = v;
	}
}

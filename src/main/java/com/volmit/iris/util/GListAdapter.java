package com.volmit.iris.util;


import java.util.List;

/**
 * Adapts a list of objects into a list of other objects
 *
 * @author cyberpwn
 * @param <FROM>
 *            the from object in lists (the item INSIDE the list)
 * @param <TO>
 *            the to object in lists (the item INSIDE the list)
 */
public abstract class GListAdapter<FROM, TO>
{
	/**
	 * Adapts a list of FROM to a list of TO
	 *
	 * @param from
	 *            the from list
	 * @return the to list
	 */
	public List<TO> adapt(List<FROM> from)
	{
		List<TO> adapted = new KList<TO>();

		for(FROM i : from)
		{
			TO t = onAdapt(i);

			if(t != null)
			{
				adapted.add(onAdapt(i));
			}
		}

		return adapted;
	}

	/**
	 * Adapts a list object FROM to TO for use with the adapt method
	 *
	 * @param from
	 *            the from object
	 * @return the to object
	 */
	public abstract TO onAdapt(FROM from);
}

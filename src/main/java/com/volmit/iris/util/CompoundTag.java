package com.volmit.iris.util;

import java.util.Map;

/**
 * The <code>TAG_Compound</code> tag.
 *
 * @author Graham Edgecombe
 *
 */
public final class CompoundTag extends Tag
{

	/**
	 * The value.
	 */
	private final Map<String, Tag> value;

	/**
	 * Creates the tag.
	 *
	 * @param name
	 *            The name.
	 * @param value
	 *            The value.
	 */
	public CompoundTag(String name, Map<String, Tag> value)
	{
		super(name);
		this.value = value;
	}

	@Override
	public Map<String, Tag> getValue()
	{
		return value;
	}

	@Override
	public String toString()
	{
		String name = getName();
		String append = "";
		if(name != null && !name.equals(""))
		{
			append = "(\"" + this.getName() + "\")";
		}
		StringBuilder bldr = new StringBuilder();
		bldr.append("TAG_Compound" + append + ": " + value.size() + " entries\r\n{\r\n");
		for(Map.Entry<String, Tag> entry : value.entrySet())
		{
			bldr.append("   " + entry.getValue().toString().replaceAll("\r\n", "\r\n   ") + "\r\n");
		}
		bldr.append("}");
		return bldr.toString();
	}

}

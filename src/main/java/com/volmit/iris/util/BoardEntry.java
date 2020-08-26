package com.volmit.iris.util;

import org.apache.commons.lang.StringUtils;

import lombok.Getter;

/**
 * @author Missionary (missionarymc@gmail.com)
 * @since 3/29/2018
 */
public class BoardEntry
{

	@Getter
	private final String prefix, suffix;

	private BoardEntry(final String prefix, final String suffix)
	{
		this.prefix = prefix;
		this.suffix = suffix;
	}

	public static BoardEntry translateToEntry(String input)
	{
		if(input.isEmpty())
		{
			return new BoardEntry("", "");
		}
		if(input.length() <= 16)
		{
			return new BoardEntry(input, "");
		}
		else
		{
			String prefix = input.substring(0, 16);
			String suffix = "";

			if(prefix.endsWith("\u00a7"))
			{
				prefix = prefix.substring(0, prefix.length() - 1);
				suffix = "\u00a7" + suffix;
			}

			suffix = StringUtils.left(C.getLastColors(prefix) + suffix + input.substring(16), 16);
			return new BoardEntry(prefix, suffix);
		}
	}
}
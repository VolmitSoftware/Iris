package com.volmit.plague.util;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;



public class RawText
{
	public static final String COLOR_BLACK = "black";
	public static final String COLOR_DARK_BLUE = "dark_blue";
	public static final String COLOR_DARK_GREEN = "dark_green";
	public static final String COLOR_DARK_AQUA = "dark_aqua";
	public static final String COLOR_DARK_RED = "dark_red";
	public static final String COLOR_DARK_PURPLE = "dark_purple";
	public static final String COLOR_GOLD = "gold";
	public static final String COLOR_GRAY = "gray";
	public static final String COLOR_DARK_GRAY = "dark_gray";
	public static final String COLOR_BLUE = "blue";
	public static final String COLOR_GREEN = "green";
	public static final String COLOR_AQUA = "aqua";
	public static final String COLOR_RED = "red";
	public static final String COLOR_LIGHT_PURPLE = "light_purple";
	public static final String COLOR_YELLOW = "yellow";
	public static final String COLOR_WHITE = "white";
	public static final String COLOR_NONE = "none";

	private static final String HEAD_TEXT = "text";
	private static final String HEAD_COLOR = "color";
	private static final String HEAD_BOLD = "bold";
	private static final String HEAD_ITALIC = "italic";
	private static final String HEAD_UNDERLINED = "underlined";
	private static final String HEAD_STRIKETHROUGH = "strikethrough";
	private static final String HEAD_OBFUSCATED = "obfuscated";
	private static final String HEAD_CLICK_EVENT = "clickEvent";
	private static final String HEAD_HOVER_EVENT = "hoverEvent";
	private static final String HEAD_ACTION = "action";
	private static final String HEAD_VALUE = "value";
	private static final String HEAD_EXTRA = "extra";
	private static final String HEAD_ACTION_SHOW_TEXT = "show_text";
	private static final String HEAD_ACTION_COMMAND = "run_command";

	private ArrayList<JSONObject> components;

	public RawText()
	{
		this.components = new ArrayList<JSONObject>();
	}

	public RawText addText(String text)
	{
		return addText(text, COLOR_NONE);
	}

	public RawText addText(String text, String color)
	{
		return addText(text, color, false, false, false, false, false);
	}

	public RawText addText(String text, String color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated)
	{
		try
		{
			JSONObject object = new JSONObject();

			object.put(HEAD_TEXT, text);

			if(!color.equals(COLOR_NONE))
			{
				object.put(HEAD_COLOR, color);
			}

			if(bold)
			{
				object.put(HEAD_BOLD, true);
			}

			if(italic)
			{
				object.put(HEAD_ITALIC, true);
			}

			if(underlined)
			{
				object.put(HEAD_UNDERLINED, true);
			}

			if(strikethrough)
			{
				object.put(HEAD_STRIKETHROUGH, true);
			}

			if(obfuscated)
			{
				object.put(HEAD_OBFUSCATED, true);
			}

			components.add(object);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return this;
	}

	public RawText addTextWithCommand(String text, String color, String command)
	{
		return addTextWithCommand(text, color, command, false, false, false, false, false);
	}

	public RawText addTextWithCommand(String text, String color, String command, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated)
	{
		try
		{
			JSONObject object = new JSONObject();

			object.put(HEAD_TEXT, text);

			if(!color.equals(COLOR_NONE))
			{
				object.put(HEAD_COLOR, color);
			}

			if(bold)
			{
				object.put(HEAD_BOLD, true);
			}

			if(italic)
			{
				object.put(HEAD_ITALIC, true);
			}

			if(underlined)
			{
				object.put(HEAD_UNDERLINED, true);
			}

			if(strikethrough)
			{
				object.put(HEAD_STRIKETHROUGH, true);
			}

			if(obfuscated)
			{
				object.put(HEAD_OBFUSCATED, true);
			}

			object.put(HEAD_CLICK_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_COMMAND).put(HEAD_VALUE, command));

			components.add(object);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return this;
	}

	public RawText addTextWithHover(String text, String color, String hoverText, String hoverColor)
	{
		return addTextWithHover(text, color, hoverText, hoverColor, false, false, false, false, false);
	}

	public RawText addTextWithHover(String text, String color, String hoverText, String hoverColor, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated)
	{
		try
		{
			JSONObject object = new JSONObject();

			object.put(HEAD_TEXT, text);

			if(!color.equals(COLOR_NONE))
			{
				object.put(HEAD_COLOR, color);
			}

			if(bold)
			{
				object.put(HEAD_BOLD, true);
			}

			if(italic)
			{
				object.put(HEAD_ITALIC, true);
			}

			if(underlined)
			{
				object.put(HEAD_UNDERLINED, true);
			}

			if(strikethrough)
			{
				object.put(HEAD_STRIKETHROUGH, true);
			}

			if(obfuscated)
			{
				object.put(HEAD_OBFUSCATED, true);
			}

			JSONObject[] dummy = new JSONObject[1];

			if(!hoverColor.equals(COLOR_NONE))
			{
				dummy[0] = new JSONObject().put(HEAD_TEXT, hoverText).put(HEAD_COLOR, hoverColor);
				object.put(HEAD_HOVER_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_SHOW_TEXT).put(HEAD_VALUE, new JSONObject().put(HEAD_TEXT, "").put(HEAD_EXTRA, dummy)));
			}

			else
			{
				dummy[0] = new JSONObject().put(HEAD_TEXT, hoverText);
				object.put(HEAD_HOVER_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_SHOW_TEXT).put(HEAD_VALUE, new JSONObject().put(HEAD_TEXT, "").put(HEAD_EXTRA, dummy)));
			}

			components.add(object);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return this;
	}

	public RawText addTextWithHoverCommand(String text, String color, String command, String hoverText, String hoverColor)
	{
		return addTextWithHoverCommand(text, color, command, hoverText, hoverColor, false, false, false, false, false);
	}

	public RawText addTextWithHoverCommand(String text, String color, String command, String hoverText, String hoverColor, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated)
	{
		try
		{
			JSONObject object = new JSONObject();

			object.put(HEAD_TEXT, text);

			if(!color.equals(COLOR_NONE))
			{
				object.put(HEAD_COLOR, color);
			}

			if(bold)
			{
				object.put(HEAD_BOLD, true);
			}

			if(italic)
			{
				object.put(HEAD_ITALIC, true);
			}

			if(underlined)
			{
				object.put(HEAD_UNDERLINED, true);
			}

			if(strikethrough)
			{
				object.put(HEAD_STRIKETHROUGH, true);
			}

			if(obfuscated)
			{
				object.put(HEAD_OBFUSCATED, true);
			}

			object.put(HEAD_CLICK_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_COMMAND).put(HEAD_VALUE, command));

			JSONObject[] dummy = new JSONObject[1];

			if(!hoverColor.equals(COLOR_NONE))
			{
				dummy[0] = new JSONObject().put(HEAD_TEXT, hoverText).put(HEAD_COLOR, hoverColor);
				object.put(HEAD_HOVER_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_SHOW_TEXT).put(HEAD_VALUE, new JSONObject().put(HEAD_TEXT, "").put(HEAD_EXTRA, dummy)));
			}

			else
			{
				dummy[0] = new JSONObject().put(HEAD_TEXT, hoverText);
				object.put(HEAD_HOVER_EVENT, new JSONObject().put(HEAD_ACTION, HEAD_ACTION_SHOW_TEXT).put(HEAD_VALUE, new JSONObject().put(HEAD_TEXT, "").put(HEAD_EXTRA, dummy)));
			}

			components.add(object);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return this;
	}

	public String compile()
	{
		String base = "[\"\"";
		for(JSONObject i : components)
		{
			base = base + "," + i.toString();
		}

		return base + "]";
	}

	public void tellRawTo(JavaPlugin pl, Player p)
	{
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "tellraw " + p.getName() + " " + compile());
	}
}

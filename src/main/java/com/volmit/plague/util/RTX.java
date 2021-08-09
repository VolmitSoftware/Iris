package com.volmit.plague.util;

import com.volmit.iris.util.format.C;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Raw Text holder
 *
 * @author cyberpwn
 */
public class RTX
{
	private final JSONArray base = new JSONArray();

	/**
	 * Create a base raw text holder
	 */
	public RTX()
	{
		base.put("");
	}

	/**
	 * Add manual json (helper)
	 *
	 * @param object
	 *            the json object
	 */
	public void add(JSONObject object)
	{
		base.put(object);
	}

	/**
	 * Add all properties of one RTX to this one
	 * @param rtx the other RTX
	 */
	public RTX addAll(RTX rtx)
	{
		for(int i = 0; i < rtx.base.length(); i++)
		{
			try
			{
				add(rtx.base.getJSONObject(i));
			}

			catch(Throwable ignored)
			{

			}
		}

		return this;
	}

	public JSONArray getBase()
	{
		return base;
	}

	/**
	 * Add basic text
	 *
	 * @param text
	 *            the text
	 */
	public RTX addText(String text)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", "none");

		add(js);

		return this;
	}

	/**
	 * Add Text with color
	 *
	 * @param text
	 *            the text
	 * @param color
	 *            the color
	 */
	public RTX addText(String text, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());
		add(js);
		return this;
	}

	/**
	 * Add a text with RTEX hovers
	 *
	 * @param text
	 *            the text
	 * @param hover
	 *            the hover RTEX
	 * @param color
	 *            the color
	 */
	public RTX addTextHover(String text, RTEX hover, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject hoverEvent = new JSONObject();
		hoverEvent.put("action", "show_text");
		hoverEvent.put("value", hover.toJSON());
		js.put("hoverEvent", hoverEvent);
		add(js);

		return this;
	}

	/**
	 * Add a command Suggestion
	 *
	 * @param text
	 *            the text
	 * @param cmd
	 *            the suggestion command
	 * @param color
	 *            the color
	 */
	public RTX addTextSuggestedCommand(String text, String cmd, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "suggest_command");
		clickEvent.put("value", cmd);
		js.put("clickEvent", clickEvent);

		add(js);

		return this;
	}

	/**
	 * Add an open url clickable text
	 *
	 * @param text
	 *            the text
	 * @param url
	 *            the url
	 * @param color
	 *            the color
	 */
	public RTX addTextOpenURL(String text, String url, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "open_url");
		clickEvent.put("value", url);
		js.put("clickEvent", clickEvent);

		add(js);

		return this;
	}

	/**
	 * Add a run command text
	 *
	 * @param text
	 *            the text
	 * @param cmd
	 *            the command
	 * @param color
	 *            the color
	 */
	public RTX addTextFireCommand(String text, String cmd, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "run_command");
		clickEvent.put("value", cmd);
		js.put("clickEvent", clickEvent);

		add(js);

		return this;
	}

	/**
	 * Add a text suggestion clickable piece of text with hover text
	 *
	 * @param text
	 *            the text
	 * @param hover
	 *            the hover
	 * @param cmd
	 *            the command
	 * @param color
	 *            the color
	 */
	public RTX addTextSuggestedHoverCommand(String text, RTEX hover, String cmd, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "suggest_command");
		clickEvent.put("value", cmd);
		js.put("clickEvent", clickEvent);

		JSONObject hoverEvent = new JSONObject();
		hoverEvent.put("action", "show_text");
		hoverEvent.put("value", hover.toJSON());
		js.put("hoverEvent", hoverEvent);
		add(js);

		return this;
	}

	/**
	 * Add a url text with a hover event
	 *
	 * @param text
	 *            the text
	 * @param hover
	 *            the hover
	 * @param url
	 *            the url
	 * @param color
	 *            the color
	 */
	public RTX addTextOpenHoverURL(String text, RTEX hover, String url, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "open_url");
		clickEvent.put("value", url);
		js.put("clickEvent", clickEvent);

		JSONObject hoverEvent = new JSONObject();
		hoverEvent.put("action", "show_text");
		hoverEvent.put("value", hover.toJSON());
		js.put("hoverEvent", hoverEvent);
		add(js);

		return this;
	}

	/**
	 * Add a text fire event with hover
	 *
	 * @param text
	 *            the text
	 * @param hover
	 *            the hover rtex
	 * @param cmd
	 *            the command
	 * @param color
	 *            the color
	 */
	public RTX addTextFireHoverCommand(String text, RTEX hover, String cmd, C color)
	{
		JSONObject js = new JSONObject();
		js.put("text", text);
		js.put("color", color.name().toLowerCase());

		JSONObject clickEvent = new JSONObject();
		clickEvent.put("action", "run_command");
		clickEvent.put("value", cmd);
		js.put("clickEvent", clickEvent);

		JSONObject hoverEvent = new JSONObject();
		hoverEvent.put("action", "show_text");
		hoverEvent.put("value", hover.toJSON());
		js.put("hoverEvent", hoverEvent);
		add(js);

		return this;
	}

	/**
	 * Cram it into json
	 *
	 * @return the json
	 */
	public JSONArray toJSON()
	{
		return base;
	}

	/**
	 * Tell raw to a player (ASYNC SAFE)
	 *
	 * @param p
	 *            the player
	 */
	public void tellRawTo(Player p)
	{
		Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "tellraw " + p.getName() + " " + toJSON().toString());
	}
}

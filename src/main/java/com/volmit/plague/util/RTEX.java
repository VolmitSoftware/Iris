package com.volmit.plague.util;





/**
 * Raw Text EXTRA
 *
 * @author cyberpwn
 */
public class RTEX
{
	private KList<ColoredString> extras;

	/**
	 * Create a new raw text base
	 *
	 * @param extras
	 *            the extras
	 */
	public RTEX(ColoredString... extras)
	{
		this.extras = new KList<ColoredString>(extras);
	}

	public RTEX()
	{
		this.extras = new KList<ColoredString>();
	}

	public KList<ColoredString> getExtras()
	{
		return extras;
	}

	/**
	 * Get the json object for this
	 *
	 * @return
	 */
	public JSONObject toJSON()
	{
		JSONObject js = new JSONObject();
		JSONArray jsa = new JSONArray();

		for(ColoredString i : extras)
		{
			JSONObject extra = new JSONObject();
			extra.put("text", i.getS());
			extra.put("color", i.getC().name().toLowerCase());
			jsa.put(extra);
		}

		js.put("text", "");
		js.put("extra", jsa);

		return js;
	}
}

package com.volmit.iris;

import com.google.gson.Gson;
import com.volmit.iris.util.*;
import lombok.Data;

import java.io.File;
import java.io.IOException;

@Data
public class IrisSettings
{
	public static transient IrisSettings settings;

	@DontObfuscate
	public int configurationVersion = 2;

	@DontObfuscate
	public int streamingCacheSize = 8192;

	@DontObfuscate
	public int forceThreadCount = -1;

	@DontObfuscate
	public String defaultWorldType = "overworld";

	@DontObfuscate
	public int maxAsyncChunkPregenThreads = 32;

	@DontObfuscate
	public boolean maximumPregenGuiFPS = false;

	@DontObfuscate
	public boolean commandSounds = true;

	@DontObfuscate
	public boolean verbose = false;

	@DontObfuscate
	public boolean ignoreWorldEdit = false;

	@DontObfuscate
	public boolean disableNMS = false;

	@DontObfuscate
	public boolean systemEffects = true;

	@DontObfuscate
	public boolean systemEntitySpawnOverrides = true;

	@DontObfuscate
	public boolean systemEntityInitialSpawns = true;

	@DontObfuscate
	public int maxBiomeChildDepth = 5;

	@DontObfuscate
	public boolean sharedCaching = true;

	@DontObfuscate
	public boolean studio = true;

	@DontObfuscate
	public boolean useServerLaunchedGuis = true;

	@DontObfuscate
	public boolean openVSCode = true;

	@DontObfuscate
	public boolean metrics = true;

	@DontObfuscate
	public boolean splashLogoStartup = true;

	public static IrisSettings get()
	{
		if(settings != null)
		{
			return settings;
		}

		IrisSettings defaults = new IrisSettings();
		JSONObject def = new JSONObject(new Gson().toJson(defaults));
		if(settings == null)
		{
			settings = new IrisSettings();

			File s = Iris.instance.getDataFile("settings.json");

			if(!s.exists())
			{
				try
				{
					IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
				}

				catch(JSONException | IOException e)
				{
					e.printStackTrace();
				}
			}

			else
			{
				try
				{
					String ss = IO.readAll(s);
					settings = new Gson().fromJson(ss, IrisSettings.class);

					J.a(() ->
					{
						JSONObject j = new JSONObject(ss);
						boolean u = false;
						for(String i : def.keySet())
						{
							if(!j.has(i))
							{
								u = true;
								j.put(i, def.get(i));
								Iris.warn("Adding new config key: " + i);
							}
						}

						for(String i : j.keySet())
						{
							if(!def.has(i))
							{
								u = true;
								j.remove(i);
								Iris.warn("Removing unused config key: " + i);
							}
						}

						if(u)
						{
							try
							{
								IO.writeAll(s, j.toString(4));
								Iris.info("Updated Configuration Files");
							}

							catch(Throwable ignored)
							{

							}
						}
					});
				}

				catch(JSONException | IOException e)
				{
					e.printStackTrace();
					// noinspection ResultOfMethodCallIgnored
					s.delete();
				}
			}

			if(!s.exists())
			{
				try
				{
					IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
				}

				catch(JSONException | IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		return settings;
	}

	public static void invalidate()
	{
		synchronized(settings)
		{
			settings = null;
		}
	}
}

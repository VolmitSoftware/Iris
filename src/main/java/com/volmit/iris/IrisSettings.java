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
	private IrisSettingsCache cache = new IrisSettingsCache();
	private IrisSettingsConcurrency concurrency = new IrisSettingsConcurrency();
	private IrisSettingsParallax parallax = new IrisSettingsParallax();
	private IrisSettingsGeneral general = new IrisSettingsGeneral();
	private IrisSettingsGUI gui = new IrisSettingsGUI();
	private IrisSettingsGenerator generator = new IrisSettingsGenerator();
	private IrisSettingsStudio studio = new IrisSettingsStudio();

	@DontObfuscate
	public int configurationVersion = 3;

	public boolean isStudio() {
		return getStudio().isStudio();
	}

	public boolean isUseServerLaunchedGuis() {
		return getGui().isUseServerLaunchedGuis();
	}

	public long getParallaxRegionEvictionMS() {
		return getParallax().getParallaxRegionEvictionMS();
	}

	@Data
	public static class IrisSettingsCache
	{
		@DontObfuscate
		public int streamingCacheSize = 8192;
	}

	@Data
	public static class IrisSettingsConcurrency
	{
		@DontObfuscate
		public int threadCount = -1;
	}

	@Data
	public static class IrisSettingsParallax
	{
		@DontObfuscate
		public int parallaxRegionEvictionMS = 15000;

		@DontObfuscate
		public int parallaxChunkEvictionMS = 5000;
	}

	@Data
	public static class IrisSettingsGeneral
	{

		@DontObfuscate
		public boolean commandSounds = true;

		@DontObfuscate
		public boolean verbose = false;

		@DontObfuscate
		public boolean ignoreWorldEdit = false;

		@DontObfuscate
		public boolean disableNMS = false;

		@DontObfuscate
		public boolean pluginMetrics = true;

		@DontObfuscate
		public boolean splashLogoStartup = true;
	}

	@Data
	public static class IrisSettingsGUI
	{
		@DontObfuscate
		public boolean useServerLaunchedGuis = true;

		@DontObfuscate
		public boolean maximumPregenGuiFPS = false;

		@DontObfuscate
		public boolean localPregenGui = true;
	}

	@Data
	public static class IrisSettingsGenerator
	{
		@DontObfuscate
		public String defaultWorldType = "overworld";

		@DontObfuscate
		public boolean mcaPregenerator = false;

		@DontObfuscate
		public boolean systemEffects = true;

		@DontObfuscate
		public boolean systemEntitySpawnOverrides = true;

		@DontObfuscate
		public boolean systemEntityInitialSpawns = true;

		@DontObfuscate
		public int maxBiomeChildDepth = 5;

	}

	@Data
	public static class IrisSettingsStudio
	{

		@DontObfuscate
		public boolean studio = true;

		@DontObfuscate
		public boolean openVSCode = true;
	}

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
						try
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

							for(String i : new KSet<>(j.keySet()))
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

								catch(Throwable e)
								{
									e.printStackTrace();
								}
							}
						}

						catch(Throwable ee)
						{
							Iris.error("Configuration Error in settings.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
							Iris.warn("Attempting to fix configuration while retaining valid in-memory settings...");

							try {
								IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
								Iris.info("Configuration Fixed!");
							} catch (IOException e) {
								e.printStackTrace();
								Iris.error("ERROR! CONFIGURATION IMPOSSIBLE TO READ! Using an unmodifiable configuration from memory. Please delete the settings.json at some point to try to restore configurability!");
							}
						}
					});
				}

				catch(Throwable ee)
				{
					Iris.error("Configuration Error in settings.json! " + ee.getClass().getSimpleName() + ": " + ee.getMessage());
					Iris.warn("Attempting to fix configuration while retaining valid in-memory settings...");

					try {
						IO.writeAll(s, new JSONObject(new Gson().toJson(settings)).toString(4));
						Iris.info("Configuration Fixed!");
					} catch (IOException e) {
						e.printStackTrace();
						Iris.error("ERROR! CONFIGURATION IMPOSSIBLE TO READ! Using an unmodifiable configuration from memory. Please delete the settings.json at some point to try to restore configurability!");
					}
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

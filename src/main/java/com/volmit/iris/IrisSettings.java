package com.volmit.iris;

import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.J;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;

import lombok.Data;

@Data
public class IrisSettings
{
	public static transient IrisSettings settings;

	@DontObfuscate
	@Desc("Iris generator threads (must be 2 or higher). Threads in iris are not a perfect scale for performance as a lot of data has to be shared. 16 Threads is a good rule of thumb. Use 8 threads on a quad core processor.")
	public int forceThreadCount = -1;

	@DontObfuscate
	@Desc("The default world type incase iris doesnt have a dimension to use.")
	public String defaultWorldType = "overworld";

	@DontObfuscate
	@Desc("Iris uses a lot of caching to speed up chunk generation. Setting this higher uses more memory, but may improve performance. Anything past 8,000 should be avoided because there is little benefit past this value.")
	public int atomicCacheSize = 3000;

	@DontObfuscate
	@Desc("Compress parallax data in memory to reduce memory usage in exchange for more cpu usage.")
	public boolean parallaxCompression = true;

	@DontObfuscate
	@Desc("Uses a lot of cpu and slows down hotloading")
	public boolean regenerateLoadedChunksOnHotload = false;

	@DontObfuscate
	@Desc("Useful information when creating iris worlds. Shows object loads & more.")
	public boolean verbose = false;

	@DontObfuscate
	@Desc("System Effects")
	public boolean systemEffects = true;

	@DontObfuscate
	@Desc("System Spawn Overrides")
	public boolean systemEntitySpawnOverrides = true;

	@DontObfuscate
	@Desc("System Spawn Initials")
	public boolean systemEntityInitialSpawns = true;

	@DontObfuscate
	@Desc("Compression level (0-9) lower is faster, but is not as good compression. Best results around 3-5")
	public int parallaxCompressionLevel = 2;

	@DontObfuscate
	@Desc("If A is a child of B, and B is a child of A, how deep should iris follow the children in biomes. Lower is faster gen.")
	public int maxBiomeChildDepth = 5;

	@DontObfuscate
	@Desc("The size of each tile pregen will generate (in chunks)")
	public int pregenTileSize = 32;

	@DontObfuscate
	@Desc("When enabled, The cache is shared for all chunks and cleared periodically instead of per chunk. This uses more memory but provides a ~15% speedup.")
	public boolean sharedCaching = true;

	@DontObfuscate
	@Desc("Allows Iris to use studio commands & design worlds.")
	public boolean studio = false;

	@DontObfuscate
	@Desc("Allows iris to launch guis through the server process. Useful for local development.")
	public boolean useServerLaunchedGuis = true;

	@DontObfuscate
	@Desc("When using studio open or create, open the codeworkspace automatically.")
	public boolean openVSCode = true;

	@DontObfuscate
	@Desc("Collects anonymous metrics for bstats")
	public boolean metrics = true;

	@DontObfuscate
	@Desc("Skips preparing spawn by using nms to hijack the world init phase")
	public boolean skipPrepareSpawnNMS = true;

	@DontObfuscate
	@Desc("Used to activate Iris")
	public String activationCode = "";

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
								Iris.verbose("Adding new config key: " + i);
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
}

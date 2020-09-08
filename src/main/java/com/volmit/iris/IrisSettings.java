package com.volmit.iris;

import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IO;
import com.volmit.iris.util.JSONException;
import com.volmit.iris.util.JSONObject;

import lombok.Data;

@Data
public class IrisSettings
{
	public static transient IrisSettings settings;

	@DontObfuscate
	@Desc("Iris generator threads (must be 2 or higher). Threads in iris are not a perfect scale for performance as a lot of data has to be shared. 16 Threads is a good rule of thumb. Use 8 threads on a quad core processor.")
	public int threads = 16;

	@DontObfuscate
	@Desc("Compress parallax data in memory to reduce memory usage in exchange for more cpu usage.")
	public boolean parallaxCompression = true;

	@DontObfuscate
	@Desc("Useful information when creating iris worlds. Shows object loads & more.")
	public boolean verbose = false;

	@DontObfuscate
	@Desc("Compression level (0-9) lower is faster, but is not as good compression. Best results around 3-5")
	public int parallaxCompressionLevel = 2;

	@DontObfuscate
	@Desc("If A is a child of B, and B is a child of A, how deep should iris follow the children in biomes. Lower is faster gen.")
	public int maxBiomeChildDepth = 5;

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
	@Desc("Used to activate Iris")
	public String activationCode = "";

	public static IrisSettings get()
	{
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
					settings = new Gson().fromJson(IO.readAll(s), IrisSettings.class);
				}

				catch(JSONException | IOException e)
				{
					e.printStackTrace();
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

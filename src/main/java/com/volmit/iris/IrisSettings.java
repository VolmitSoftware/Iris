package com.volmit.iris;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;

public class IrisSettings
{
	public static transient IrisSettings settings;

	@DontObfuscate
	@Desc("Iris generator threads (must be 2 or higher).")
	public int threads = 128;

	@DontObfuscate
	@Desc("Compress parallax data in memory to reduce memory usage in exchange for more cpu usage.")
	public boolean parallaxCompression = true;

	@DontObfuscate
	@Desc("Compression level (0-9) lower is faster, but is not as good compression. Best results around 3-5")
	public int parallaxCompressionLevel = 3;

	public static IrisSettings get()
	{
		if(settings == null)
		{
			settings = new IrisSettings();
			// TODO LOAD
		}

		return settings;
	}
}

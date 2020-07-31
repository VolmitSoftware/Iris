package com.volmit.iris.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class CustomOutputStream extends GZIPOutputStream
{
	public CustomOutputStream(OutputStream out, int level) throws IOException
	{
		super(out);
		def.setLevel(level);
	}
}

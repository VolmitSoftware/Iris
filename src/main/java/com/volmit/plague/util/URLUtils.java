package com.volmit.plague.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class URLUtils
{
	public static int getFileSize(URL url)
	{
		URLConnection conn = null;
		try
		{
			conn = url.openConnection();

			if(conn instanceof HttpURLConnection)
			{
				((HttpURLConnection) conn).setRequestMethod("HEAD");
			}

			conn.getInputStream();

			return conn.getContentLength();
		}

		catch(IOException e)
		{
			throw new RuntimeException(e);
		}

		finally
		{
			if(conn instanceof HttpURLConnection)
			{
				((HttpURLConnection) conn).disconnect();
			}
		}
	}
}

package com.volmit.iris.util;

import java.io.File;

import lombok.Data;
import lombok.Getter;

@Data
public class FileWatcher
{
	@Getter
	private final File file;
	private boolean exists;
	private long lastModified;
	private long size;

	public FileWatcher(File file)
	{
		this.file = file;
		readProperties();
	}

	private void readProperties()
	{
		exists = file.exists();
		lastModified = exists ? file.lastModified() : -1;
		size = exists ? file.length() : -1;
	}

	public boolean checkModified()
	{
		long m = lastModified;
		long g = size;
		boolean mod = false;
		readProperties();

		if(lastModified != m || g != size)
		{
			mod = true;
		}

		return mod;
	}
}

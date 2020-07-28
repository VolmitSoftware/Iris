package com.volmit.iris.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarScanner
{
	private final KSet<Class<?>> classes;
	private final File jar;
	private final String superPackage;

	/**
	 * Create a scanner
	 *
	 * @param jar
	 *            the path to the jar
	 */
	public JarScanner(File jar, String superPackage)
	{
		this.jar = jar;
		this.classes = new KSet<Class<?>>();
		this.superPackage = superPackage;
	}

	/**
	 * Scan the jar
	 *
	 * @throws IOException
	 *             bad things happen
	 */
	public void scan() throws IOException
	{
		classes.clear();
		FileInputStream fin = new FileInputStream(jar);
		ZipInputStream zip = new ZipInputStream(fin);

		for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
		{
			if(!entry.isDirectory() && entry.getName().endsWith(".class"))
			{
				if(entry.getName().contains("$"))
				{
					continue;
				}

				String c = entry.getName().replaceAll("/", ".").replace(".class", "");

				if(c.startsWith(superPackage))
				{
					try
					{
						Class<?> clazz = Class.forName(c);
						classes.add(clazz);
					}

					catch(ClassNotFoundException e)
					{
						e.printStackTrace();
					}
				}
			}
		}

		zip.close();
	}

	/**
	 * Get the scanned clases
	 *
	 * @return a gset of classes
	 */
	public KSet<Class<?>> getClasses()
	{
		return classes;
	}

	/**
	 * Get the file object for the jar
	 *
	 * @return a file object representing the jar
	 */
	public File getJar()
	{
		return jar;
	}
}
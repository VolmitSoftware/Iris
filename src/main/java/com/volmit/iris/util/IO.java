package com.volmit.iris.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class IO
{
	/**
	 * The Unix directory separator character.
	 */
	public static final char DIR_SEPARATOR_UNIX = '/';
	/**
	 * The Windows directory separator character.
	 */
	public static final char DIR_SEPARATOR_WINDOWS = '\\';
	/**
	 * The system directory separator character.
	 */
	public static final char DIR_SEPARATOR = File.separatorChar;
	/**
	 * The Unix line separator string.
	 */
	public static final String LINE_SEPARATOR_UNIX = "\n";
	/**
	 * The Windows line separator string.
	 */
	public static final String LINE_SEPARATOR_WINDOWS = "\r\n";
	/**
	 * The system line separator string.
	 */
	public static final String LINE_SEPARATOR;

	/**
	 * The default buffer size to use.
	 */
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String decompress(String gz) throws IOException
	{
		ByteArrayInputStream bin = new ByteArrayInputStream(Base64.getUrlDecoder().decode(gz));
		GZIPInputStream gzi = new GZIPInputStream(bin);
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		IO.fullTransfer(gzi, boas, 256);
		gzi.close();

		return new String(boas.toByteArray(), StandardCharsets.UTF_8);
	}

	public static byte[] sdecompress(String compressed) throws IOException
	{
		ByteArrayInputStream bin = new ByteArrayInputStream(Base64.getUrlDecoder().decode(compressed));
		GZIPInputStream gzi = new GZIPInputStream(bin);
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		IO.fullTransfer(gzi, boas, 256);
		gzi.close();

		return boas.toByteArray();
	}

	public static String encode(byte[] data)
	{
		return Base64.getUrlEncoder().encodeToString(data);
	}

	public static byte[] decode(String u)
	{
		return Base64.getUrlDecoder().decode(u);
	}

	public static String hash(String b)
	{
		try
		{
			MessageDigest d = MessageDigest.getInstance("SHA-256");
			return bytesToHex(d.digest(b.getBytes(StandardCharsets.UTF_8)));
		}

		catch(NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}

		return "¯\\_(ツ)_/¯";
	}

	public static String hash(File b)
	{
		try
		{
			MessageDigest d = MessageDigest.getInstance("SHA-256");
			DigestInputStream din = new DigestInputStream(new FileInputStream(b), d);
			fullTransfer(din, new VoidOutputStream(), 8192);
			din.close();
			return bytesToHex(din.getMessageDigest().digest());
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return "¯\\_(ツ)_/¯";
	}

	public static String bytesToHex(byte[] bytes)
	{
		char[] hexChars = new char[bytes.length * 2];
		for(int j = 0; j < bytes.length; j++)
		{
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}

		return new String(hexChars).toUpperCase();
	}

	/**
	 * Transfers the length of the buffer amount of data from the input stream to
	 * the output stream
	 *
	 * @param in
	 *            the input
	 * @param out
	 *            the output
	 * @param amount
	 *            the buffer and size to use
	 * @return the actual transfered amount
	 * @throws IOException
	 *             shit happens
	 */
	public static int transfer(InputStream in, OutputStream out, byte[] buffer) throws IOException
	{
		int r = in.read(buffer);

		if(r != -1)
		{
			out.write(buffer, 0, r);
		}

		return r;
	}

	/**
	 * Transfers the length of the buffer amount of data from the input stream to
	 * the output stream
	 *
	 * @param in
	 *            the input
	 * @param out
	 *            the output
	 * @param targetBuffer
	 *            the buffer and size to use
	 * @param totalSize
	 *            the total amount to transfer
	 * @return the actual transfered amount
	 * @throws IOException
	 *             shit happens
	 */
	public static long transfer(InputStream in, OutputStream out, int targetBuffer, long totalSize) throws IOException
	{
		long total = totalSize;
		long wrote = 0;
		byte[] buf = new byte[targetBuffer];
		int r = 0;

		while((r = in.read(buf, 0, (int) (total < targetBuffer ? total : targetBuffer))) != -1)
		{
			total -= r;
			out.write(buf, 0, r);
			wrote += r;

			if(total <= 0)
			{
				break;
			}
		}

		return wrote;
	}

	/**
	 * Fully move data from a finite inputstream to an output stream using a buffer
	 * size of 8192. This does NOT close streams.
	 *
	 * @param in
	 * @param out
	 * @return total size transfered
	 * @throws IOException
	 */
	public static long fillTransfer(InputStream in, OutputStream out) throws IOException
	{
		return fullTransfer(in, out, 8192);
	}

	public static void deleteUp(File f)
	{
		if(f.exists())
		{
			f.delete();

			if(f.getParentFile().list().length == 0)
			{
				deleteUp(f.getParentFile());
			}
		}
	}

	/**
	 * Fully move data from a finite inputstream to an output stream using a given
	 * buffer size. This does NOT close streams.
	 *
	 * @param in
	 *            the input stream to read from
	 * @param out
	 *            the output stream to write to
	 * @param bufferSize
	 *            the target buffer size
	 * @return total size transfered
	 * @throws IOException
	 *             shit happens
	 */
	public static long fullTransfer(InputStream in, OutputStream out, int bufferSize) throws IOException
	{
		long wrote = 0;
		byte[] buf = new byte[bufferSize];
		int r = 0;

		while((r = in.read(buf)) != -1)
		{
			out.write(buf, 0, r);
			wrote += r;
		}

		return wrote;
	}

	/**
	 * Recursive delete (deleting folders)
	 *
	 * @param f
	 *            the file to delete (and subfiles if folder)
	 */
	public static void delete(File f)
	{
		if(f == null || !f.exists())
		{
			return;
		}

		if(f.isDirectory())
		{
			for(File i : f.listFiles())
			{
				delete(i);
			}
		}

		f.delete();
	}

	public static long size(File file)
	{
		long s = 0;

		if(file.exists())
		{
			if(file.isDirectory())
			{
				for(File i : file.listFiles())
				{
					s += size(i);
				}
			}

			else
			{
				s += file.length();
			}
		}

		return s;
	}

	public static long count(File file)
	{
		long s = 0;

		if(file.exists())
		{
			if(file.isDirectory())
			{
				for(File i : file.listFiles())
				{
					s += count(i);
				}
			}

			else
			{
				s++;
			}
		}

		return s;
	}

	public static long transfer(InputStream in, OutputStream out, byte[] buf, int totalSize) throws IOException
	{
		long total = totalSize;
		long wrote = 0;
		int r = 0;

		while((r = in.read(buf, 0, (int) (total < buf.length ? total : buf.length))) != -1)
		{
			total -= r;
			out.write(buf, 0, r);
			wrote += r;

			if(total <= 0)
			{
				break;
			}
		}

		return wrote;
	}

	public static void readEntry(File zipfile, String entryname, Consumer<InputStream> v) throws ZipException, IOException
	{
		ZipFile file = new ZipFile(zipfile);
		Throwable x = null;

		try
		{
			Enumeration<? extends ZipEntry> entries = file.entries();
			while(entries.hasMoreElements())
			{
				ZipEntry entry = entries.nextElement();

				if(entryname.equals(entry.getName()))
				{
					InputStream in = file.getInputStream(entry);
					v.accept(in);
				}
			}
		}

		catch(Exception ex)
		{
			x = ex.getCause();
		}

		finally
		{
			file.close();
		}

		if(x != null)
		{
			throw new IOException("Failed to read zip entry, however it has been closed safely.", x);
		}
	}

	public static void writeAll(File f, Object c) throws IOException
	{
		f.getParentFile().mkdirs();
		PrintWriter pw = new PrintWriter(new FileWriter(f));
		pw.println(c.toString());
		pw.close();
	}

	public static String readAll(File f) throws IOException
	{
		FileReader fr;
		try {
			fr = new FileReader(f);
		} catch (IOException e) {
			throw e;
		}
		BufferedReader bu = new BufferedReader(fr);
		String c = "";
		String l = "";

		while((l = bu.readLine()) != null)
		{
			c += l + "\n";
		}

		bu.close();

		return c;
	}

	public static String readAll(InputStream in) throws IOException
	{
		BufferedReader bu = new BufferedReader(new InputStreamReader(in));
		String c = "";
		String l = "";

		while((l = bu.readLine()) != null)
		{
			c += l + "\n";
		}

		bu.close();

		return c;
	}

	/**
	 * Implements the same behaviour as the "touch" utility on Unix. It creates a
	 * new file with size 0 or, if the file exists already, it is opened and closed
	 * without modifying it, but updating the file date and time.
	 *
	 * @param file
	 *            the File to touch
	 * @throws IOException
	 *             If an I/O problem occurs
	 */
	public static void touch(File file) throws IOException
	{
		if(!file.exists())
		{
			OutputStream out = new FileOutputStream(file);
			out.close();
		}
		file.setLastModified(System.currentTimeMillis());
	}

	/**
	 * Copies a file to a new location preserving the file date.
	 * <p>
	 * This method copies the contents of the specified source file to the specified
	 * destination file. The directory holding the destination file is created if it
	 * does not exist. If the destination file exists, then this method will
	 * overwrite it.
	 * 
	 * @param srcFile
	 *            an existing file to copy, must not be null
	 * @param destFile
	 *            the new file, must not be null
	 * 
	 * @throws NullPointerException
	 *             if source or destination is null
	 * @throws IOException
	 *             if source or destination is invalid
	 * @throws IOException
	 *             if an IO error occurs during copying
	 * @see #copyFileToDirectory
	 */
	public static void copyFile(File srcFile, File destFile) throws IOException
	{
		copyFile(srcFile, destFile, true);
	}

	/**
	 * Copies a file to a new location.
	 * <p>
	 * This method copies the contents of the specified source file to the specified
	 * destination file. The directory holding the destination file is created if it
	 * does not exist. If the destination file exists, then this method will
	 * overwrite it.
	 *
	 * @param srcFile
	 *            an existing file to copy, must not be null
	 * @param destFile
	 *            the new file, must not be null
	 * @param preserveFileDate
	 *            true if the file date of the copy should be the same as the
	 *            original
	 *
	 * @throws NullPointerException
	 *             if source or destination is null
	 * @throws IOException
	 *             if source or destination is invalid
	 * @throws IOException
	 *             if an IO error occurs during copying
	 * @see #copyFileToDirectory
	 */
	public static void copyFile(File srcFile, File destFile, boolean preserveFileDate) throws IOException
	{
		if(srcFile == null)
		{
			throw new NullPointerException("Source must not be null");
		}
		if(destFile == null)
		{
			throw new NullPointerException("Destination must not be null");
		}
		if(srcFile.exists() == false)
		{
			throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
		}
		if(srcFile.isDirectory())
		{
			throw new IOException("Source '" + srcFile + "' exists but is a directory");
		}
		if(srcFile.getCanonicalPath().equals(destFile.getCanonicalPath()))
		{
			throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
		}
		if(destFile.getParentFile() != null && destFile.getParentFile().exists() == false)
		{
			if(destFile.getParentFile().mkdirs() == false)
			{
				throw new IOException("Destination '" + destFile + "' directory cannot be created");
			}
		}
		if(destFile.exists() && destFile.canWrite() == false)
		{
			throw new IOException("Destination '" + destFile + "' exists but is read-only");
		}
		doCopyFile(srcFile, destFile, preserveFileDate);
	}

	/**
	 * Internal copy file method.
	 * 
	 * @param srcFile
	 *            the validated source file, not null
	 * @param destFile
	 *            the validated destination file, not null
	 * @param preserveFileDate
	 *            whether to preserve the file date
	 * @throws IOException
	 *             if an error occurs
	 */
	private static void doCopyFile(File srcFile, File destFile, boolean preserveFileDate) throws IOException
	{
		if(destFile.exists() && destFile.isDirectory())
		{
			throw new IOException("Destination '" + destFile + "' exists but is a directory");
		}

		FileInputStream input = new FileInputStream(srcFile);
		try
		{
			FileOutputStream output = new FileOutputStream(destFile);
			try
			{
				IO.copy(input, output);
			}
			finally
			{
				output.close();
			}
		}
		finally
		{
			input.close();
		}

		if(srcFile.length() != destFile.length())
		{
			throw new IOException("Failed to copy full contents from '" + srcFile + "' to '" + destFile + "'");
		}
		if(preserveFileDate)
		{
			destFile.setLastModified(srcFile.lastModified());
		}
	}

	// -----------------------------------------------------------------------
	/**
	 * Unconditionally close an <code>Reader</code>.
	 * <p>
	 * Equivalent to {@link Reader#close()}, except any exceptions will be ignored.
	 * This is typically used in finally blocks.
	 *
	 * @param input
	 *            the Reader to close, may be null or already closed
	 */
	public static void closeQuietly(Reader input)
	{
		try
		{
			if(input != null)
			{
				input.close();
			}
		}
		catch(IOException ioe)
		{
			// ignore
		}
	}

	/**
	 * Unconditionally close a <code>Writer</code>.
	 * <p>
	 * Equivalent to {@link Writer#close()}, except any exceptions will be ignored.
	 * This is typically used in finally blocks.
	 *
	 * @param output
	 *            the Writer to close, may be null or already closed
	 */
	public static void closeQuietly(Writer output)
	{
		try
		{
			if(output != null)
			{
				output.close();
			}
		}
		catch(IOException ioe)
		{
			// ignore
		}
	}

	/**
	 * Unconditionally close an <code>InputStream</code>.
	 * <p>
	 * Equivalent to {@link InputStream#close()}, except any exceptions will be
	 * ignored. This is typically used in finally blocks.
	 *
	 * @param input
	 *            the InputStream to close, may be null or already closed
	 */
	public static void closeQuietly(InputStream input)
	{
		try
		{
			if(input != null)
			{
				input.close();
			}
		}
		catch(IOException ioe)
		{
			// ignore
		}
	}

	/**
	 * Unconditionally close an <code>OutputStream</code>.
	 * <p>
	 * Equivalent to {@link OutputStream#close()}, except any exceptions will be
	 * ignored. This is typically used in finally blocks.
	 *
	 * @param output
	 *            the OutputStream to close, may be null or already closed
	 */
	public static void closeQuietly(OutputStream output)
	{
		try
		{
			if(output != null)
			{
				output.close();
			}
		}
		catch(IOException ioe)
		{
			// ignore
		}
	}

	// read toByteArray
	// -----------------------------------------------------------------------
	/**
	 * Get the contents of an <code>InputStream</code> as a <code>byte[]</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @return the requested byte array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static byte[] toByteArray(InputStream input) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		copy(input, output);
		return output.toByteArray();
	}

	/**
	 * Get the contents of a <code>Reader</code> as a <code>byte[]</code> using the
	 * default character encoding of the platform.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * 
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @return the requested byte array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static byte[] toByteArray(Reader input) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		copy(input, output);
		return output.toByteArray();
	}

	/**
	 * Get the contents of a <code>Reader</code> as a <code>byte[]</code> using the
	 * specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * 
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @return the requested byte array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static byte[] toByteArray(Reader input, String encoding) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		copy(input, output, encoding);
		return output.toByteArray();
	}

	/**
	 * Get the contents of a <code>String</code> as a <code>byte[]</code> using the
	 * default character encoding of the platform.
	 * <p>
	 * This is the same as {@link String#getBytes()}.
	 * 
	 * @param input
	 *            the <code>String</code> to convert
	 * @return the requested byte array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs (never occurs)
	 * @deprecated Use {@link String#getBytes()}
	 */
	public static byte[] toByteArray(String input) throws IOException
	{
		return input.getBytes();
	}

	// read char[]
	// -----------------------------------------------------------------------
	/**
	 * Get the contents of an <code>InputStream</code> as a character array using
	 * the default character encoding of the platform.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param is
	 *            the <code>InputStream</code> to read from
	 * @return the requested character array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static char[] toCharArray(InputStream is) throws IOException
	{
		CharArrayWriter output = new CharArrayWriter();
		copy(is, output);
		return output.toCharArray();
	}

	/**
	 * Get the contents of an <code>InputStream</code> as a character array using
	 * the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param is
	 *            the <code>InputStream</code> to read from
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @return the requested character array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static char[] toCharArray(InputStream is, String encoding) throws IOException
	{
		CharArrayWriter output = new CharArrayWriter();
		copy(is, output, encoding);
		return output.toCharArray();
	}

	/**
	 * Get the contents of a <code>Reader</code> as a character array.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * 
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @return the requested character array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static char[] toCharArray(Reader input) throws IOException
	{
		CharArrayWriter sw = new CharArrayWriter();
		copy(input, sw);
		return sw.toCharArray();
	}

	// read toString
	// -----------------------------------------------------------------------
	/**
	 * Get the contents of an <code>InputStream</code> as a String using the default
	 * character encoding of the platform.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @return the requested String
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static String toString(InputStream input) throws IOException
	{
		StringWriter sw = new StringWriter();
		copy(input, sw);
		return sw.toString();
	}

	/**
	 * Get the contents of an <code>InputStream</code> as a String using the
	 * specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @return the requested String
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static String toString(InputStream input, String encoding) throws IOException
	{
		StringWriter sw = new StringWriter();
		copy(input, sw, encoding);
		return sw.toString();
	}

	/**
	 * Get the contents of a <code>Reader</code> as a String.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * 
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @return the requested String
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static String toString(Reader input) throws IOException
	{
		StringWriter sw = new StringWriter();
		copy(input, sw);
		return sw.toString();
	}

	/**
	 * Get the contents of a <code>byte[]</code> as a String using the default
	 * character encoding of the platform.
	 * 
	 * @param input
	 *            the byte array to read from
	 * @return the requested String
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs (never occurs)
	 * @deprecated Use {@link String#String(byte[])}
	 */
	public static String toString(byte[] input) throws IOException
	{
		return new String(input);
	}

	/**
	 * Get the contents of a <code>byte[]</code> as a String using the specified
	 * character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * 
	 * @param input
	 *            the byte array to read from
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @return the requested String
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs (never occurs)
	 * @deprecated Use {@link String#String(byte[],String)}
	 */
	public static String toString(byte[] input, String encoding) throws IOException
	{
		if(encoding == null)
		{
			return new String(input);
		}
		else
		{
			return new String(input, encoding);
		}
	}

	// readLines
	// -----------------------------------------------------------------------
	/**
	 * Get the contents of an <code>InputStream</code> as a list of Strings, one
	 * entry per line, using the default character encoding of the platform.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 *
	 * @param input
	 *            the <code>InputStream</code> to read from, not null
	 * @return the list of Strings, never null
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static List<String> readLines(InputStream input) throws IOException
	{
		InputStreamReader reader = new InputStreamReader(input);
		return readLines(reader);
	}

	/**
	 * Get the contents of an <code>InputStream</code> as a list of Strings, one
	 * entry per line, using the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 *
	 * @param input
	 *            the <code>InputStream</code> to read from, not null
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @return the list of Strings, never null
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static List<String> readLines(InputStream input, String encoding) throws IOException
	{
		if(encoding == null)
		{
			return readLines(input);
		}
		else
		{
			InputStreamReader reader = new InputStreamReader(input, encoding);
			return readLines(reader);
		}
	}

	/**
	 * Get the contents of a <code>Reader</code> as a list of Strings, one entry per
	 * line.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 *
	 * @param input
	 *            the <code>Reader</code> to read from, not null
	 * @return the list of Strings, never null
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static List<String> readLines(Reader input) throws IOException
	{
		BufferedReader reader = new BufferedReader(input);
		List<String> list = new ArrayList<String>();
		String line = reader.readLine();
		while(line != null)
		{
			list.add(line);
			line = reader.readLine();
		}
		return list;
	}

	// -----------------------------------------------------------------------
	/**
	 * Convert the specified string to an input stream, encoded as bytes using the
	 * default character encoding of the platform.
	 *
	 * @param input
	 *            the string to convert
	 * @return an input stream
	 * @since Commons IO 1.1
	 */
	public static InputStream toInputStream(String input)
	{
		byte[] bytes = input.getBytes();
		return new ByteArrayInputStream(bytes);
	}

	/**
	 * Convert the specified string to an input stream, encoded as bytes using the
	 * specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 *
	 * @param input
	 *            the string to convert
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws IOException
	 *             if the encoding is invalid
	 * @return an input stream
	 * @since Commons IO 1.1
	 */
	public static InputStream toInputStream(String input, String encoding) throws IOException
	{
		byte[] bytes = encoding != null ? input.getBytes(encoding) : input.getBytes();
		return new ByteArrayInputStream(bytes);
	}

	// write byte[]
	// -----------------------------------------------------------------------
	/**
	 * Writes bytes from a <code>byte[]</code> to an <code>OutputStream</code>.
	 * 
	 * @param data
	 *            the byte array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(byte[] data, OutputStream output) throws IOException
	{
		if(data != null)
		{
			output.write(data);
		}
	}

	/**
	 * Writes bytes from a <code>byte[]</code> to chars on a <code>Writer</code>
	 * using the default character encoding of the platform.
	 * <p>
	 * This method uses {@link String#String(byte[])}.
	 * 
	 * @param data
	 *            the byte array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(byte[] data, Writer output) throws IOException
	{
		if(data != null)
		{
			output.write(new String(data));
		}
	}

	/**
	 * Writes bytes from a <code>byte[]</code> to chars on a <code>Writer</code>
	 * using the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method uses {@link String#String(byte[], String)}.
	 * 
	 * @param data
	 *            the byte array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(byte[] data, Writer output, String encoding) throws IOException
	{
		if(data != null)
		{
			if(encoding == null)
			{
				write(data, output);
			}
			else
			{
				output.write(new String(data, encoding));
			}
		}
	}

	// write char[]
	// -----------------------------------------------------------------------
	/**
	 * Writes chars from a <code>char[]</code> to a <code>Writer</code> using the
	 * default character encoding of the platform.
	 * 
	 * @param data
	 *            the char array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(char[] data, Writer output) throws IOException
	{
		if(data != null)
		{
			output.write(data);
		}
	}

	/**
	 * Writes chars from a <code>char[]</code> to bytes on an
	 * <code>OutputStream</code>.
	 * <p>
	 * This method uses {@link String#String(char[])} and {@link String#getBytes()}.
	 * 
	 * @param data
	 *            the char array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(char[] data, OutputStream output) throws IOException
	{
		if(data != null)
		{
			output.write(new String(data).getBytes());
		}
	}

	/**
	 * Writes chars from a <code>char[]</code> to bytes on an
	 * <code>OutputStream</code> using the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method uses {@link String#String(char[])} and
	 * {@link String#getBytes(String)}.
	 * 
	 * @param data
	 *            the char array to write, do not modify during output, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(char[] data, OutputStream output, String encoding) throws IOException
	{
		if(data != null)
		{
			if(encoding == null)
			{
				write(data, output);
			}
			else
			{
				output.write(new String(data).getBytes(encoding));
			}
		}
	}

	// write String
	// -----------------------------------------------------------------------
	/**
	 * Writes chars from a <code>String</code> to a <code>Writer</code>.
	 * 
	 * @param data
	 *            the <code>String</code> to write, null ignored
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(String data, Writer output) throws IOException
	{
		if(data != null)
		{
			output.write(data);
		}
	}

	/**
	 * Writes chars from a <code>String</code> to bytes on an
	 * <code>OutputStream</code> using the default character encoding of the
	 * platform.
	 * <p>
	 * This method uses {@link String#getBytes()}.
	 * 
	 * @param data
	 *            the <code>String</code> to write, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(String data, OutputStream output) throws IOException
	{
		if(data != null)
		{
			output.write(data.getBytes());
		}
	}

	/**
	 * Writes chars from a <code>String</code> to bytes on an
	 * <code>OutputStream</code> using the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method uses {@link String#getBytes(String)}.
	 * 
	 * @param data
	 *            the <code>String</code> to write, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(String data, OutputStream output, String encoding) throws IOException
	{
		if(data != null)
		{
			if(encoding == null)
			{
				write(data, output);
			}
			else
			{
				output.write(data.getBytes(encoding));
			}
		}
	}

	// write StringBuffer
	// -----------------------------------------------------------------------
	/**
	 * Writes chars from a <code>StringBuffer</code> to a <code>Writer</code>.
	 * 
	 * @param data
	 *            the <code>StringBuffer</code> to write, null ignored
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(StringBuffer data, Writer output) throws IOException
	{
		if(data != null)
		{
			output.write(data.toString());
		}
	}

	/**
	 * Writes chars from a <code>StringBuffer</code> to bytes on an
	 * <code>OutputStream</code> using the default character encoding of the
	 * platform.
	 * <p>
	 * This method uses {@link String#getBytes()}.
	 * 
	 * @param data
	 *            the <code>StringBuffer</code> to write, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(StringBuffer data, OutputStream output) throws IOException
	{
		if(data != null)
		{
			output.write(data.toString().getBytes());
		}
	}

	/**
	 * Writes chars from a <code>StringBuffer</code> to bytes on an
	 * <code>OutputStream</code> using the specified character encoding.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method uses {@link String#getBytes(String)}.
	 * 
	 * @param data
	 *            the <code>StringBuffer</code> to write, null ignored
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void write(StringBuffer data, OutputStream output, String encoding) throws IOException
	{
		if(data != null)
		{
			if(encoding == null)
			{
				write(data, output);
			}
			else
			{
				output.write(data.toString().getBytes(encoding));
			}
		}
	}

	// writeLines
	// -----------------------------------------------------------------------
	/**
	 * Writes the <code>toString()</code> value of each item in a collection to an
	 * <code>OutputStream</code> line by line, using the default character encoding
	 * of the platform and the specified line ending.
	 *
	 * @param lines
	 *            the lines to write, null entries produce blank lines
	 * @param lineEnding
	 *            the line separator to use, null is system default
	 * @param output
	 *            the <code>OutputStream</code> to write to, not null, not closed
	 * @throws NullPointerException
	 *             if the output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void writeLines(Collection<String> lines, String lineEnding, OutputStream output) throws IOException
	{
		if(lines == null)
		{
			return;
		}
		if(lineEnding == null)
		{
			lineEnding = LINE_SEPARATOR;
		}
		for(Iterator<String> it = lines.iterator(); it.hasNext();)
		{
			Object line = it.next();
			if(line != null)
			{
				output.write(line.toString().getBytes());
			}
			output.write(lineEnding.getBytes());
		}
	}

	/**
	 * Writes the <code>toString()</code> value of each item in a collection to an
	 * <code>OutputStream</code> line by line, using the specified character
	 * encoding and the specified line ending.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 *
	 * @param lines
	 *            the lines to write, null entries produce blank lines
	 * @param lineEnding
	 *            the line separator to use, null is system default
	 * @param output
	 *            the <code>OutputStream</code> to write to, not null, not closed
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if the output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void writeLines(Collection<String> lines, String lineEnding, OutputStream output, String encoding) throws IOException
	{
		if(encoding == null)
		{
			writeLines(lines, lineEnding, output);
		}
		else
		{
			if(lines == null)
			{
				return;
			}
			if(lineEnding == null)
			{
				lineEnding = LINE_SEPARATOR;
			}
			for(Iterator<String> it = lines.iterator(); it.hasNext();)
			{
				Object line = it.next();
				if(line != null)
				{
					output.write(line.toString().getBytes(encoding));
				}
				output.write(lineEnding.getBytes(encoding));
			}
		}
	}

	/**
	 * Writes the <code>toString()</code> value of each item in a collection to a
	 * <code>Writer</code> line by line, using the specified line ending.
	 *
	 * @param lines
	 *            the lines to write, null entries produce blank lines
	 * @param lineEnding
	 *            the line separator to use, null is system default
	 * @param writer
	 *            the <code>Writer</code> to write to, not null, not closed
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void writeLines(Collection<String> lines, String lineEnding, Writer writer) throws IOException
	{
		if(lines == null)
		{
			return;
		}
		if(lineEnding == null)
		{
			lineEnding = LINE_SEPARATOR;
		}
		for(Iterator<String> it = lines.iterator(); it.hasNext();)
		{
			Object line = it.next();
			if(line != null)
			{
				writer.write(line.toString());
			}
			writer.write(lineEnding);
		}
	}

	// copy from InputStream
	// -----------------------------------------------------------------------
	/**
	 * Copy bytes from an <code>InputStream</code> to an <code>OutputStream</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * <p>
	 * Large streams (over 2GB) will return a bytes copied value of <code>-1</code>
	 * after the copy has completed since the correct number of bytes cannot be
	 * returned as an int. For large streams use the
	 * <code>copyLarge(InputStream, OutputStream)</code> method.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @return the number of bytes copied
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws ArithmeticException
	 *             if the byte count is too large
	 * @since Commons IO 1.1
	 */
	public static int copy(InputStream input, OutputStream output) throws IOException
	{
		long count = copyLarge(input, output);
		if(count > Integer.MAX_VALUE)
		{
			return -1;
		}
		return (int) count;
	}

	/**
	 * Copy bytes from a large (over 2GB) <code>InputStream</code> to an
	 * <code>OutputStream</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @return the number of bytes copied
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.3
	 */
	public static long copyLarge(InputStream input, OutputStream output) throws IOException
	{
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		long count = 0;
		int n = 0;
		while(-1 != (n = input.read(buffer)))
		{
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	/**
	 * Copy bytes from an <code>InputStream</code> to chars on a <code>Writer</code>
	 * using the default character encoding of the platform.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * <p>
	 * This method uses {@link InputStreamReader}.
	 *
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void copy(InputStream input, Writer output) throws IOException
	{
		InputStreamReader in = new InputStreamReader(input);
		copy(in, output);
	}

	/**
	 * Copy bytes from an <code>InputStream</code> to chars on a <code>Writer</code>
	 * using the specified character encoding.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * This method uses {@link InputStreamReader}.
	 *
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void copy(InputStream input, Writer output, String encoding) throws IOException
	{
		if(encoding == null)
		{
			copy(input, output);
		}
		else
		{
			InputStreamReader in = new InputStreamReader(input, encoding);
			copy(in, output);
		}
	}

	// copy from Reader
	// -----------------------------------------------------------------------
	/**
	 * Copy chars from a <code>Reader</code> to a <code>Writer</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * <p>
	 * Large streams (over 2GB) will return a chars copied value of <code>-1</code>
	 * after the copy has completed since the correct number of chars cannot be
	 * returned as an int. For large streams use the
	 * <code>copyLarge(Reader, Writer)</code> method.
	 *
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @return the number of characters copied
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws ArithmeticException
	 *             if the character count is too large
	 * @since Commons IO 1.1
	 */
	public static int copy(Reader input, Writer output) throws IOException
	{
		long count = copyLarge(input, output);
		if(count > Integer.MAX_VALUE)
		{
			return -1;
		}
		return (int) count;
	}

	/**
	 * Copy chars from a large (over 2GB) <code>Reader</code> to a
	 * <code>Writer</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 *
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @param output
	 *            the <code>Writer</code> to write to
	 * @return the number of characters copied
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.3
	 */
	public static long copyLarge(Reader input, Writer output) throws IOException
	{
		char[] buffer = new char[DEFAULT_BUFFER_SIZE];
		long count = 0;
		int n = 0;
		while(-1 != (n = input.read(buffer)))
		{
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	/**
	 * Copy chars from a <code>Reader</code> to bytes on an
	 * <code>OutputStream</code> using the default character encoding of the
	 * platform, and calling flush.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * <p>
	 * Due to the implementation of OutputStreamWriter, this method performs a
	 * flush.
	 * <p>
	 * This method uses {@link OutputStreamWriter}.
	 *
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void copy(Reader input, OutputStream output) throws IOException
	{
		OutputStreamWriter out = new OutputStreamWriter(output);
		copy(input, out);
		// have to flush here.
		out.flush();
	}

	/**
	 * Copy chars from a <code>Reader</code> to bytes on an
	 * <code>OutputStream</code> using the specified character encoding, and calling
	 * flush.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedReader</code>.
	 * <p>
	 * Character encoding names can be found at
	 * <a href="http://www.iana.org/assignments/character-sets">IANA</a>.
	 * <p>
	 * Due to the implementation of OutputStreamWriter, this method performs a
	 * flush.
	 * <p>
	 * This method uses {@link OutputStreamWriter}.
	 *
	 * @param input
	 *            the <code>Reader</code> to read from
	 * @param output
	 *            the <code>OutputStream</code> to write to
	 * @param encoding
	 *            the encoding to use, null means platform default
	 * @throws NullPointerException
	 *             if the input or output is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static void copy(Reader input, OutputStream output, String encoding) throws IOException
	{
		if(encoding == null)
		{
			copy(input, output);
		}
		else
		{
			OutputStreamWriter out = new OutputStreamWriter(output, encoding);
			copy(input, out);
			// we have to flush here.
			out.flush();
		}
	}

	// content equals
	// -----------------------------------------------------------------------
	/**
	 * Compare the contents of two Streams to determine if they are equal or not.
	 * <p>
	 * This method buffers the input internally using
	 * <code>BufferedInputStream</code> if they are not already buffered.
	 *
	 * @param input1
	 *            the first stream
	 * @param input2
	 *            the second stream
	 * @return true if the content of the streams are equal or they both don't
	 *         exist, false otherwise
	 * @throws NullPointerException
	 *             if either input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException
	{
		if(!(input1 instanceof BufferedInputStream))
		{
			input1 = new BufferedInputStream(input1);
		}
		if(!(input2 instanceof BufferedInputStream))
		{
			input2 = new BufferedInputStream(input2);
		}

		int ch = input1.read();
		while(-1 != ch)
		{
			int ch2 = input2.read();
			if(ch != ch2)
			{
				return false;
			}
			ch = input1.read();
		}

		int ch2 = input2.read();
		return (ch2 == -1);
	}

	/**
	 * Compare the contents of two Readers to determine if they are equal or not.
	 * <p>
	 * This method buffers the input internally using <code>BufferedReader</code> if
	 * they are not already buffered.
	 *
	 * @param input1
	 *            the first reader
	 * @param input2
	 *            the second reader
	 * @return true if the content of the readers are equal or they both don't
	 *         exist, false otherwise
	 * @throws NullPointerException
	 *             if either input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 * @since Commons IO 1.1
	 */
	public static boolean contentEquals(Reader input1, Reader input2) throws IOException
	{
		if(!(input1 instanceof BufferedReader))
		{
			input1 = new BufferedReader(input1);
		}
		if(!(input2 instanceof BufferedReader))
		{
			input2 = new BufferedReader(input2);
		}

		int ch = input1.read();
		while(-1 != ch)
		{
			int ch2 = input2.read();
			if(ch != ch2)
			{
				return false;
			}
			ch = input1.read();
		}

		int ch2 = input2.read();
		return (ch2 == -1);
	}

	static
	{
		// avoid security issues
		StringWriter buf = new StringWriter(4);
		PrintWriter out = new PrintWriter(buf);
		out.println();
		LINE_SEPARATOR = buf.toString();
	}
}

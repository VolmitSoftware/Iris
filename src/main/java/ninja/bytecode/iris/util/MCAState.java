package ninja.bytecode.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ninja.bytecode.shuriken.math.M;

public class MCAState
{
	private boolean[] decorated;
	private boolean dirty;
	private long lastUsed;

	public MCAState()
	{
		lastUsed = M.ms();
		this.decorated = new boolean[1024];
	}
	
	public boolean isInUse()
	{
		return M.ms() - lastUsed < 30000;
	}

	public void setDirty()
	{
		lastUsed = M.ms();
		this.dirty = true;
	}

	public void saved()
	{
		lastUsed = M.ms();
		this.dirty = false;
	}

	public boolean isDirty()
	{
		return dirty;
	}

	public boolean isDecorated(int rcx, int rcz)
	{
		lastUsed = M.ms();
		return decorated[rcx + (32 * rcz)];
	}

	public void setDecorated(int rcx, int rcz, boolean decorated)
	{
		lastUsed = M.ms();
		this.decorated[rcx + (32 * rcz)] = decorated;
	}

	public void read(InputStream in) throws IOException
	{
		DataInputStream din = new DataInputStream(in);

		for(int i = 0; i < 1024; i++)
		{
			decorated[i] = din.readBoolean();
		}

		din.close();
	}

	public void write(OutputStream out) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(out);

		for(int i = 0; i < 1024; i++)
		{
			dos.writeBoolean(decorated[i]);
		}

		dos.close();
	}
}

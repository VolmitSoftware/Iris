package com.volmit.iris.edit;

import java.io.Closeable;

import org.bukkit.block.data.BlockData;

public interface BlockEditor extends Closeable
{
	public long last();

	public void set(int x, int y, int z, BlockData d);

	public BlockData get(int x, int y, int z);

	@Override
	public void close();
}

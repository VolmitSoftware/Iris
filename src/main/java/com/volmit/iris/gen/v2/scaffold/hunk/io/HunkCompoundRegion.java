package com.volmit.iris.gen.v2.scaffold.hunk.io;

import java.io.File;
import java.io.IOException;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.util.CompoundTag;

import lombok.Getter;

public class HunkCompoundRegion extends HunkRegion
{
	@Getter
	private HunkRegionSlice<BlockData> blockSlice;
	@Getter
	private HunkRegionSlice<String> objectSlice;
	@Getter
	private HunkRegionSlice<Boolean> updateSlice;

	private final int height;

	public HunkCompoundRegion(int height, File folder, int x, int z, CompoundTag compound)
	{
		super(folder, x, z, compound);
		this.height = height;
		setupSlices();
	}

	public HunkCompoundRegion(int height, File folder, int x, int z)
	{
		super(folder, x, z);
		this.height = height;
		setupSlices();
	}

	private void setupSlices()
	{
		blockSlice = HunkRegionSlice.BLOCKDATA.apply(height, getCompound());
		objectSlice = HunkRegionSlice.STRING.apply(height, getCompound(), "objects");
		updateSlice = HunkRegionSlice.BOOLEAN.apply(height, getCompound(), "updates");
	}

	public void save() throws IOException
	{
		blockSlice.save();
		objectSlice.save();
		updateSlice.save();
		super.save();
	}

	public void unload()
	{
		blockSlice.unloadAll();
		objectSlice.unloadAll();
		updateSlice.unloadAll();
	}
}

package com.volmit.iris.scaffold.parallax;

import com.volmit.iris.object.tile.TileData;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.scaffold.hunk.io.HunkIOAdapter;
import com.volmit.iris.scaffold.hunk.io.HunkRegion;
import com.volmit.iris.scaffold.hunk.io.HunkRegionSlice;
import com.volmit.iris.scaffold.parallel.GridLock;
import com.volmit.iris.util.ByteArrayTag;
import com.volmit.iris.util.CompoundTag;
import com.volmit.iris.util.M;
import com.volmit.iris.util.Tag;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

public class ParallaxRegion extends HunkRegion
{
	private boolean dirtyMeta;
	private Hunk<ParallaxChunkMeta> meta;
	private HunkIOAdapter<ParallaxChunkMeta> metaAdapter;
	private HunkRegionSlice<BlockData> blockSlice;
	private HunkRegionSlice<TileData<? extends TileState>> tileSlice;
	private HunkRegionSlice<String> objectSlice;
	private HunkRegionSlice<String> entitySlice;
	private HunkRegionSlice<Boolean> updateSlice;
	private final GridLock lock;
	private long lastUse;
	private final int height;

	public ParallaxRegion(int height, File folder, int x, int z, CompoundTag compound)
	{
		super(folder, x, z, compound);
		this.height = height;
		setupSlices();
		lock = new GridLock(32, 32);
	}

	public ParallaxRegion(int height, File folder, int x, int z)
	{
		super(folder, x, z);
		this.height = height;
		setupSlices();
		lock = new GridLock(32, 32);
	}

	private void setupSlices()
	{
		blockSlice = HunkRegionSlice.BLOCKDATA.apply(height, getCompound());
		tileSlice = HunkRegionSlice.TILE.apply(height, getCompound());
		objectSlice = HunkRegionSlice.STRING.apply(height, getCompound(), "objects");
		entitySlice = HunkRegionSlice.STRING.apply(height, getCompound(), "entities");
		updateSlice = HunkRegionSlice.BOOLEAN.apply(height, getCompound(), "updates");
		metaAdapter = ParallaxChunkMeta.adapter.apply(getCompound());
		dirtyMeta = false;
		meta = null;
		lastUse = M.ms();
	}

	public boolean hasBeenIdleLongerThan(long time)
	{
		return M.ms() - lastUse > time;
	}

	public ParallaxChunkMeta getMetaR(int x, int z)
	{
		return lock.withResult(x, z, () -> getMetaHunkR().getOr(x, 0, z, new ParallaxChunkMeta()));
	}

	public  ParallaxChunkMeta getMetaRW(int x, int z)
	{
		return lock.withResult(x, z, () -> {
			lastUse = M.ms();
			dirtyMeta = true;
			ParallaxChunkMeta p = getMetaHunkRW().get(x, 0, z);
			if(p == null)
			{
				p = new ParallaxChunkMeta();
				getMetaHunkRW().set(x,0,z,p);
			}

			return p;
		});
	}

	private  Hunk<ParallaxChunkMeta> getMetaHunkR()
	{
		if(meta == null)
		{
			meta = loadMetaHunk();
		}

		return meta;
	}

	private  Hunk<ParallaxChunkMeta> getMetaHunkRW()
	{
		dirtyMeta = true;
		return getMetaHunkR();
	}

	private Hunk<ParallaxChunkMeta> loadMetaHunk()
	{
		lastUse = M.ms();
		if(meta == null)
		{
			Tag t = getCompound().getValue().get("meta");

			if((t instanceof ByteArrayTag))
			{
				try {
					meta = metaAdapter.read((x,y,z) -> Hunk.newArrayHunk(32, 1, 32), (ByteArrayTag)t);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if(meta == null)
			{
				meta = Hunk.newArrayHunk(32, 1, 32);
			}
		}

		return meta;
	}

	public  void unloadMetaHunk()
	{
		if(dirtyMeta)
		{
			saveMetaHunk();
			dirtyMeta = false;
		}

		meta = null;
	}

	public  void saveMetaHunk()
	{
		if(meta != null && dirtyMeta)
		{
			try {
				getCompound().getValue().put("meta", meta.writeByteArrayTag(metaAdapter, "meta"));
				dirtyMeta = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public synchronized void save() throws IOException
	{
		blockSlice.save();
		objectSlice.save();
		entitySlice.save();
		tileSlice.save();
		updateSlice.save();
		saveMetaHunk();
		super.save();
	}

	public int unload()
	{
		unloadMetaHunk();
		return blockSlice.unloadAll()+
				objectSlice.unloadAll()+
				entitySlice.unloadAll()+
				tileSlice.unloadAll()+
		updateSlice.unloadAll();
	}

	public HunkRegionSlice<BlockData> getBlockSlice() {
		lastUse = M.ms();
		return blockSlice;
	}

	public HunkRegionSlice<String> getEntitySlice() {
		lastUse = M.ms();
		return entitySlice;
	}

	public HunkRegionSlice<TileData<? extends TileState>> getTileSlice() {
		lastUse = M.ms();
		return tileSlice;
	}

	public  HunkRegionSlice<String> getObjectSlice() {
		lastUse = M.ms();
		return objectSlice;
	}

	public HunkRegionSlice<Boolean> getUpdateSlice() {
		lastUse = M.ms();
		return updateSlice;
	}

	public synchronized int cleanup(long c) {
		return blockSlice.cleanup(c) +
				objectSlice.cleanup(c) +
				entitySlice.cleanup(c) +
				tileSlice.cleanup(c) +
		updateSlice.cleanup(c);
	}

	public int getChunkCount() {
		return blockSlice.getLoadCount() + objectSlice.getLoadCount() + entitySlice.getLoadCount() + tileSlice.getLoadCount() + updateSlice.getLoadCount();
	}
}

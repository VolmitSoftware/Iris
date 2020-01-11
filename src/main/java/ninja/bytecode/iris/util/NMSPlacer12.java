package ninja.bytecode.iris.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.WorldServer;

public class NMSPlacer12 extends Placer
{
	private CraftWorld craftWorld;
	private WorldServer worldServer;
	
	public NMSPlacer12(World world)
	{
		super(world);
		craftWorld = (CraftWorld) world;
		worldServer = craftWorld.getHandle();
	}

	@SuppressWarnings("deprecation")
	@Override
	public MB get(Location l)
	{
		Chunk c = worldServer.getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4);
		ChunkSection s = c.getSections()[l.getBlockY() >> 4];
		
		if(s == null)
		{
			return MB.of(Material.AIR);
		}
		
		IBlockData d = s.getType(l.getBlockX() & 15, l.getBlockY() & 15, l.getBlockZ() & 15);
		Block block = d.getBlock();
		return MB.of(Material.getMaterial(Block.getId(block)), block.toLegacyData(d) << 12);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void set(Location l, MB mb)
	{
		Chunk c = worldServer.getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4);
		int combined = mb.material.getId() + (mb.data << 12);
		IBlockData ibd = net.minecraft.server.v1_12_R1.Block.getByCombinedId(combined);
		
		if(c.getSections()[l.getBlockY() >> 4] == null)
		{
			c.getSections()[l.getBlockY() >> 4] = new net.minecraft.server.v1_12_R1.ChunkSection(l.getBlockY() >> 4 << 4, c.world.worldProvider.m());
		}

		int h = c.b(l.getBlockX() & 15, l.getBlockZ() & 15);
		if(l.getBlockY() > h)
		{
			c.heightMap[(l.getBlockZ() & 15) << 4 | (l.getBlockX() & 15)] = l.getBlockY();
		}
		
		net.minecraft.server.v1_12_R1.ChunkSection sec = c.getSections()[l.getBlockY() >> 4];
		sec.setType(l.getBlockX() & 15, l.getBlockY() & 15, l.getBlockZ() & 15, ibd);
		c.markDirty();
	}

	@Override
	public int getHighestY(Location l)
	{
		return worldServer.getChunkAt(l.getBlockX() >> 4, l.getBlockZ() >> 4)
				.b(l.getBlockX() & 15, l.getBlockZ() & 15);
	}
}

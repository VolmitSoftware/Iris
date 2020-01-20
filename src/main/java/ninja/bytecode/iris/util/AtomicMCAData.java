package ninja.bytecode.iris.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.World;
import org.jnbt.ByteArrayTag;
import org.jnbt.CompoundTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTOutputStream;
import org.jnbt.Tag;

import ninja.bytecode.shuriken.collections.GMap;

public class AtomicMCAData
{
	private final World world;
	private GMap<String, Tag> tag;

	public AtomicMCAData(World world)
	{
		this.world = world;
		tag = new GMap<>();
	}

	public void read(InputStream in) throws IOException
	{
		NBTInputStream nin = new NBTInputStream(in);
		tag = new GMap<>();
		tag.putAll(((CompoundTag) nin.readTag()).getValue());
		nin.close();
	}

	public void write(OutputStream out) throws IOException
	{
		NBTOutputStream nos = new NBTOutputStream(out);
		nos.writeTag(new CompoundTag("imca", tag));
		nos.close();
	}

	public boolean contains(int rx, int rz)
	{
		return tag.containsKey(rx + "." + rz);
	}

	public void delete(int rx, int rz)
	{
		tag.remove(rx + "." + rz);
	}

	public void set(int rx, int rz, AtomicChunkData data) throws IOException
	{
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		data.write(boas);
		tag.put(rx + "." + rz, new ByteArrayTag(rx + "." + rz, boas.toByteArray()));
	}

	public AtomicChunkData get(int rx, int rz) throws IOException
	{
		if(!contains(rx, rz))
		{
			return null;
		}

		AtomicChunkData data = new AtomicChunkData(world);
		ByteArrayTag btag = (ByteArrayTag) tag.get(rx + "." + rz);
		ByteArrayInputStream in = new ByteArrayInputStream(btag.getValue());
		data.read(in);

		return data;
	}

	public World getWorld()
	{
		return world;
	}
}

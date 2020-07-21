package ninja.bytecode.iris.object.atomics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.World;

import ninja.bytecode.iris.util.ByteArrayTag;
import ninja.bytecode.iris.util.CompoundTag;
import ninja.bytecode.iris.util.NBTInputStream;
import ninja.bytecode.iris.util.NBTOutputStream;
import ninja.bytecode.iris.util.Tag;
import ninja.bytecode.shuriken.collections.KMap;

public class AtomicRegionData
{
	private final World world;
	private KMap<String, Tag> tag;

	public AtomicRegionData(World world)
	{
		this.world = world;
		tag = new KMap<>();
	}

	public void read(InputStream in) throws IOException
	{
		NBTInputStream nin = new NBTInputStream(in);
		tag = new KMap<>();
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

	public void set(int rx, int rz, AtomicSliverMap data) throws IOException
	{
		if(data == null)
		{
			return;
		}

		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		data.write(boas);
		tag.put(rx + "." + rz, new ByteArrayTag(rx + "." + rz, boas.toByteArray()));
	}

	public AtomicSliverMap get(int rx, int rz) throws IOException
	{
		AtomicSliverMap data = new AtomicSliverMap();

		if(!contains(rx, rz))
		{
			return data;
		}

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

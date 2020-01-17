package ninja.bytecode.iris.pack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.World.Environment;
import org.bukkit.block.Biome;

import net.md_5.bungee.api.ChatColor;
import ninja.bytecode.iris.generator.genobject.GenObjectGroup;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.json.JSONObject;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.RNG;
import ninja.bytecode.shuriken.reaction.O;

public class CompiledDimension
{
	public static IrisBiome theVoid = new IrisBiome("Void", Biome.VOID).height(0).seal(RNG.r);
	private IrisDimension dimension;
	private GList<IrisBiome> biomes;
	private GMap<String, IrisBiome> biomeCache;
	private GMap<String, GenObjectGroup> objects;

	public CompiledDimension(IrisDimension dimension)
	{
		this.dimension = dimension;
		biomes = new GList<>();
		biomeCache = new GMap<>();
		objects = new GMap<>();
	}

	public void read(InputStream in) throws IOException
	{
		GZIPInputStream gin = new GZIPInputStream(in);
		DataInputStream din = new DataInputStream(gin);
		dimension = new IrisDimension();
		dimension.fromJSON(new JSONObject(din.readUTF()), false);
		int bi = din.readInt();
		int ob = din.readInt();

		for(int i = 0; i < bi; i++)
		{
			IrisBiome b = new IrisBiome("Loading", Biome.VOID);
			b.fromJSON(new JSONObject(din.readUTF()), false);
		}

		for(int i = 0; i < ob; i++)
		{
			GenObjectGroup g = new GenObjectGroup("Loading");
			g.read(din);
		}
	}

	public void write(OutputStream out, Consumer<Double> progress) throws IOException
	{
		GZIPOutputStream gzo = new CustomOutputStream(out, 1);
		DataOutputStream dos = new DataOutputStream(gzo);
		dos.writeUTF(dimension.toJSON().toString(0));
		dos.writeInt(biomes.size());
		dos.writeInt(objects.size());

		for(IrisBiome i : biomes)
		{
			dos.writeUTF(i.toJSON().toString(0));
		}

		O<Integer> tc = new O<>();
		O<Integer> oc = new O<>();
		O<Integer> cc = new O<>();
		tc.set(0);
		oc.set(0);
		cc.set(0);

		for(GenObjectGroup i : objects.v())
		{
			tc.set(tc.get() + i.size());
		}

		for(GenObjectGroup i : objects.v().shuffle())
		{
			i.write(dos, (o) ->
			{
				cc.set((int) (o * i.size()));

				if(progress != null)
				{
					progress.accept((double) (oc.get() + cc.get()) / (double) tc.get());
				}
			});

			oc.set(oc.get() + cc.get());
			cc.set(0);
		}

		dos.close();
	}

	public void registerBiome(IrisBiome j)
	{
		biomes.add(j);
		biomeCache.put(j.getName(), j);
	}

	public void registerObject(GenObjectGroup g)
	{
		if(g.getName().startsWith("pack/objects/"))
		{
			g.setName(g.getName().replaceFirst("\\Qpack/objects/\\E", ""));
		}

		objects.put(g.getName(), g);
	}

	public String getName()
	{
		return dimension.getName();
	}

	public GList<IrisBiome> getBiomes()
	{
		return biomes;
	}

	public Environment getEnvironment()
	{
		return dimension.getEnvironment();
	}

	public GenObjectGroup getObjectGroup(String j)
	{
		return objects.get(j);
	}

	public int countObjects()
	{
		int m = 0;

		for(GenObjectGroup i : objects.v())
		{
			m += i.size();
		}

		return m;
	}

	public void sort()
	{
		biomes.sort();
	}

	public IrisBiome getBiomeByName(String name)
	{
		IrisBiome b = biomeCache.get(name);

		if(b == null)
		{
			L.f(ChatColor.RED + "Cannot Find Biome: " + ChatColor.GOLD + name);
			return theVoid;
		}

		return b;
	}
}

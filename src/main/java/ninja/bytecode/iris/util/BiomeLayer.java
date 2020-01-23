package ninja.bytecode.iris.util;

import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.block.Biome;

import mortar.lang.collection.GList;
import mortar.lang.collection.GMap;
import mortar.logic.format.F;
import mortar.util.text.C;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.BiomeType;
import ninja.bytecode.iris.pack.IrisBiome;
import ninja.bytecode.iris.util.PolygonGenerator.EnumPolygonGenerator;
import ninja.bytecode.shuriken.collections.KSet;
import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.math.CNG;
import ninja.bytecode.shuriken.math.RNG;

public class BiomeLayer
{
	public static final IrisBiome VOID = new IrisBiome("Master", Biome.VOID).height(-1).dirt(MB.of(Material.END_BRICKS)).seal(RNG.r);
	private GList<BiomeLayer> children;
	private EnumPolygonGenerator<BiomeLayer> gen;
	private IrisBiome biome;
	private IrisGenerator iris;

	public BiomeLayer(IrisGenerator iris, IrisBiome biome)
	{
		this.biome = biome == null ? VOID : biome;
		this.iris = iris;
		this.children = new GList<>();
	}

	public void compileChildren(double scale, int octaves, Function<CNG, CNG> factory, boolean inf)
	{
		if(gen != null)
		{
			return;
		}

		if(children.isEmpty())
		{
			gen = null;
			return;
		}

		GList<BiomeLayer> b = new GList<>();
		GMap<BiomeLayer, Double> rarities = new GMap<>();
		for(BiomeLayer i : getChildren())
		{
			b.add(i);
			rarities.put(i, i.getBiome().getRarity());
		}

		if(!getBiome().equals(VOID))
		{
			b.add(this);
			rarities.put(this, getBiome().getRarity());
		}

		gen = new EnumPolygonGenerator<>(iris.getRTerrain().nextParallelRNG(1022 + getBiome().getRealBiome().ordinal()), scale, octaves, b, rarities, factory).useRarity();

		for(BiomeLayer i : getChildren())
		{
			i.compileChildren(scale, octaves, factory, inf);
		}
	}

	private IrisBiome computeBiome(double x, double z, GList<String> f)
	{
		if(gen != null)
		{
			BiomeLayer b = gen.getChoice(x, z);

			if(b.biome.equals(biome))
			{
				return biome;
			}

			if(f.contains(b.getBiome().getName()))
			{
				f.add("...");
				f.add(b.getBiome().getName());
				L.w(C.YELLOW + "Cyclic Biome Heiarchy Detected! " + C.RED + f.toString(C.GRAY + " -> " + C.RED));
				return b.biome;
			}

			f.add(b.getBiome().getName());

			return b.computeBiome(x, z, f);
		}

		return getBiome();
	}

	public IrisBiome computeBiome(double x, double z)
	{
		return computeBiome(x, z, new GList<String>());
	}

	public void addLayer(IrisBiome biome)
	{
		addLayer(new BiomeLayer(iris, biome));
	}

	public void addLayer(BiomeLayer layer)
	{
		getChildren().add(layer);
	}

	public IrisBiome getBiome()
	{
		return biome;
	}

	public GList<BiomeLayer> getChildren()
	{
		return children;
	}

	public void setChildren(GList<BiomeLayer> children)
	{
		this.children = children;
	}

	public void print(int indent)
	{
		print(0, F.repeat(" ", indent));
	}

	private void print(int index, String indent)
	{
		L.i(C.GRAY + F.repeat(indent, index) + "Layer " + C.DARK_GREEN + getBiome().getName() + C.GRAY + "(" + C.GOLD + getBiome().getRarityString() + C.GRAY + ")" + (getBiome().getGenAmplifier() != 0.35 ? C.DARK_AQUA + " A: " + getBiome().getGenAmplifier() : "") + (getBiome().getHeight() != 0.0 ? C.DARK_RED + " H: " + getBiome().getHeight() : "") + (getBiome().hasCliffs() ? C.DARK_PURPLE + " C: " + getBiome().getCliffChance() + " x " + getBiome().getCliffScale() : ""));
		L.flush();
		if(!getBiome().getSchematicGroups().isEmpty())
		{
			for(String i : getBiome().getSchematicGroups().k())
			{
				String f = "";
				double percent = getBiome().getSchematicGroups().get(i);

				if(percent > 1D)
				{
					f = (int) percent + " + " + F.pc(percent - (int) percent, percent - (int) percent >= 0.01 ? 0 : 3);
				}

				else
				{
					f = F.pc(percent, percent >= 0.01 ? 0 : 3);
				}

				L.i(C.GRAY + F.repeat(indent, index + 1) + "Object " + C.GOLD + i + C.GRAY + " at " + C.GOLD + f + C.GRAY + " (" + F.f(iris.getDimension().getObjectGroup(i).size()) + " variants)");
			}
		}

		L.flush();
		for(BiomeLayer i : children)
		{
			i.print(index + 1, indent);
		}
	}

	public static BiomeLayer compile(IrisGenerator g, double scale, int octaves, Function<CNG, CNG> factory)
	{
		return compile(g, scale, octaves, factory, false);
	}

	public static BiomeLayer compile(IrisGenerator g, double scale, int octaves, Function<CNG, CNG> factory, boolean inf)
	{
		GMap<String, BiomeLayer> components = new GMap<>();

		for(IrisBiome i : g.getDimension().getBiomes())
		{
			if(i.getType().equals(BiomeType.LAND))
			{
				components.put(i.getName(), new BiomeLayer(g, i));
			}
		}

		KSet<String> deject = new KSet<>();

		for(String i : components.keySet())
		{
			BiomeLayer b = components.get(i);

			for(String j : b.getBiome().getParents())
			{
				try
				{
					components.get(j).addLayer(b);
					deject.add(i);
				}

				catch(Throwable e)
				{
					L.w(C.YELLOW + "Cannot find Biome " + C.RED + j + C.YELLOW + " (" + C.WHITE + b.getBiome().getName() + C.YELLOW + "'s so-called 'parent'.)");
				}
			}
		}

		BiomeLayer master = new BiomeLayer(g, null);

		for(String i : components.k())
		{
			if(deject.contains(i))
			{
				continue;
			}

			master.addLayer(components.get(i));
		}

		master.compileChildren(scale, octaves, factory, inf);

		return master;
	}
}

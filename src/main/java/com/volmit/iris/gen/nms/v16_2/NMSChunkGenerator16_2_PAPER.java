package com.volmit.iris.gen.nms.v16_2;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_16_R2.util.CraftMagicNumbers;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

import com.mojang.serialization.Codec;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.provisions.ProvisionBukkit;
import com.volmit.iris.gen.scaffold.GeneratedChunk;
import com.volmit.iris.gen.scaffold.Provisioned;
import com.volmit.iris.gen.scaffold.ProvisionedHolder;
import com.volmit.iris.gen.scaffold.TerrainProvider;
import com.volmit.iris.util.O;
import com.volmit.iris.util.V;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.v1_16_R2.BiomeBase;
import net.minecraft.server.v1_16_R2.BiomeManager;
import net.minecraft.server.v1_16_R2.BiomeSettingsMobs;
import net.minecraft.server.v1_16_R2.Block;
import net.minecraft.server.v1_16_R2.BlockColumn;
import net.minecraft.server.v1_16_R2.BlockPosition;
import net.minecraft.server.v1_16_R2.Blocks;
import net.minecraft.server.v1_16_R2.ChunkCoordIntPair;
import net.minecraft.server.v1_16_R2.ChunkGenerator;
import net.minecraft.server.v1_16_R2.ChunkGeneratorAbstract;
import net.minecraft.server.v1_16_R2.CrashReport;
import net.minecraft.server.v1_16_R2.CrashReportSystemDetails;
import net.minecraft.server.v1_16_R2.DefinedStructureManager;
import net.minecraft.server.v1_16_R2.EnumCreatureType;
import net.minecraft.server.v1_16_R2.GeneratorAccess;
import net.minecraft.server.v1_16_R2.GeneratorAccessSeed;
import net.minecraft.server.v1_16_R2.GeneratorSettingBase;
import net.minecraft.server.v1_16_R2.HeightMap;
import net.minecraft.server.v1_16_R2.IBlockAccess;
import net.minecraft.server.v1_16_R2.IBlockData;
import net.minecraft.server.v1_16_R2.IChunkAccess;
import net.minecraft.server.v1_16_R2.IRegistry;
import net.minecraft.server.v1_16_R2.IRegistryCustom;
import net.minecraft.server.v1_16_R2.IStructureAccess;
import net.minecraft.server.v1_16_R2.NoiseSettings;
import net.minecraft.server.v1_16_R2.PacketDebug;
import net.minecraft.server.v1_16_R2.ProtoChunk;
import net.minecraft.server.v1_16_R2.RegionLimitedWorldAccess;
import net.minecraft.server.v1_16_R2.ReportedException;
import net.minecraft.server.v1_16_R2.ResourceKey;
import net.minecraft.server.v1_16_R2.SectionPosition;
import net.minecraft.server.v1_16_R2.SeededRandom;
import net.minecraft.server.v1_16_R2.SpawnerCreature;
import net.minecraft.server.v1_16_R2.StructureBoundingBox;
import net.minecraft.server.v1_16_R2.StructureFeature;
import net.minecraft.server.v1_16_R2.StructureFeatures;
import net.minecraft.server.v1_16_R2.StructureGenerator;
import net.minecraft.server.v1_16_R2.StructureManager;
import net.minecraft.server.v1_16_R2.StructurePiece;
import net.minecraft.server.v1_16_R2.StructureSettingsFeature;
import net.minecraft.server.v1_16_R2.StructureStart;
import net.minecraft.server.v1_16_R2.WorldChunkManager;
import net.minecraft.server.v1_16_R2.WorldGenFeatureDefinedStructureJigsawJunction;
import net.minecraft.server.v1_16_R2.WorldGenFeatureDefinedStructurePoolTemplate;
import net.minecraft.server.v1_16_R2.WorldGenFeaturePillagerOutpostPoolPiece;
import net.minecraft.server.v1_16_R2.WorldGenStage;
import net.minecraft.server.v1_16_R2.WorldServer;

@SuppressWarnings("deprecation")
public class NMSChunkGenerator16_2_PAPER extends ChunkGenerator implements ProvisionedHolder
{
	private static final IBlockData k = Blocks.AIR.getBlockData();
	private final Provisioned provisioned;
	private final int maxHeight;
	private final int xzSize;
	protected final IBlockData f;
	protected final IBlockData g;
	private final long w;
	protected final Supplier<GeneratorSettingBase> h;
	private final O<WorldServer> ws;
	private BlockColumn BC;

	public NMSChunkGenerator16_2_PAPER(Provisioned p, O<WorldServer> ws, WorldChunkManager worldchunkmanager, long i, Supplier<GeneratorSettingBase> supplier)
	{
		this(p, ws, worldchunkmanager, worldchunkmanager, i, supplier);
	}

	private NMSChunkGenerator16_2_PAPER(Provisioned p, O<WorldServer> ws, WorldChunkManager worldchunkmanager, WorldChunkManager worldchunkmanager1, long i, Supplier<GeneratorSettingBase> supplier)
	{
		super(worldchunkmanager, worldchunkmanager1, supplier.get().a(), i);
		this.provisioned = p;
		this.ws = ws;
		this.w = i;
		GeneratorSettingBase generatorsettingbase = supplier.get();

		this.h = supplier;
		NoiseSettings noisesettings = generatorsettingbase.b();

		this.maxHeight = noisesettings.f() * 4;
		this.f = generatorsettingbase.c();
		this.g = generatorsettingbase.d();
		this.xzSize = noisesettings.a() / this.maxHeight;
		BC = new BlockColumn(new IBlockData[this.xzSize * this.maxHeight]);
	}

	public int getSpawnHeight()
	{
		return getSeaLevel() + 8;
	}

	public WorldChunkManager getWorldChunkManager()
	{
		return this.c;
	}

	public int getGenerationDepth()
	{
		return 256;
	}

	public void doCarving(long i, BiomeManager biomemanager, IChunkAccess ichunkaccess, WorldGenStage.Features worldgenstage_features)
	{
		if(((IrisTerrainProvider) provisioned.getProvider()).getDimension().isVanillaCaves())
		{
			super.doCarving(i, biomemanager, ichunkaccess, worldgenstage_features);
		}
	}

	@Override
	protected Codec<? extends ChunkGenerator> a()
	{
		return ChunkGeneratorAbstract.d;
	}

	public boolean a(long i, ResourceKey<GeneratorSettingBase> resourcekey)
	{
		return this.w == i && this.h.get().a(resourcekey);
	}

	@Override
	public int getBaseHeight(int i, int j, HeightMap.Type heightmap_type)
	{
		return 63;
	}

	@Override
	public IBlockAccess a(int x, int z)
	{
		return BC;
	}

	protected IBlockData a(double d0, int i)
	{
		IBlockData iblockdata;

		if(d0 > 0.0D)
		{
			iblockdata = this.f;
		}
		else if(i < this.getSeaLevel())
		{
			iblockdata = this.g;
		}
		else
		{
			iblockdata = NMSChunkGenerator16_2_PAPER.k;
		}

		return iblockdata;
	}

	@Override
	public void buildBase(RegionLimitedWorldAccess regionlimitedworldaccess, IChunkAccess ichunkaccess)
	{

	}

	@Override
	public void buildNoise(GeneratorAccess generatoraccess, StructureManager structuremanager, IChunkAccess ichunkaccess)
	{
		ObjectList<StructurePiece> objectlist = new ObjectArrayList<StructurePiece>(10);
		ObjectList<WorldGenFeatureDefinedStructureJigsawJunction> objectlist1 = new ObjectArrayList<WorldGenFeatureDefinedStructureJigsawJunction>(32);
		ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
		int i = chunkcoordintpair.x;
		int j = chunkcoordintpair.z;

		if(((IrisTerrainProvider) provisioned.getProvider()).shouldGenerateVanillaStructures())
		{
			int k = i << 4;
			int l = j << 4;
			Iterator<?> iterator = StructureGenerator.t.iterator();

			while(iterator.hasNext())
			{
				StructureGenerator<?> structuregenerator = (StructureGenerator<?>) iterator.next();

				structuremanager.a(SectionPosition.a(chunkcoordintpair, 0), structuregenerator).forEach((structurestart) ->
				{
					Iterator<?> iterator1 = structurestart.d().iterator();

					while(iterator1.hasNext())
					{
						StructurePiece structurepiece = (StructurePiece) iterator1.next();

						if(structurepiece.a(chunkcoordintpair, 12))
						{
							if(structurepiece instanceof WorldGenFeaturePillagerOutpostPoolPiece)
							{
								WorldGenFeaturePillagerOutpostPoolPiece worldgenfeaturepillageroutpostpoolpiece = (WorldGenFeaturePillagerOutpostPoolPiece) structurepiece;
								WorldGenFeatureDefinedStructurePoolTemplate.Matching worldgenfeaturedefinedstructurepooltemplate_matching = worldgenfeaturepillageroutpostpoolpiece.b().e();

								if(worldgenfeaturedefinedstructurepooltemplate_matching == WorldGenFeatureDefinedStructurePoolTemplate.Matching.RIGID)
								{
									objectlist.add(worldgenfeaturepillageroutpostpoolpiece);
								}

								Iterator<?> iterator2 = worldgenfeaturepillageroutpostpoolpiece.e().iterator();

								while(iterator2.hasNext())
								{
									WorldGenFeatureDefinedStructureJigsawJunction worldgenfeaturedefinedstructurejigsawjunction = (WorldGenFeatureDefinedStructureJigsawJunction) iterator2.next();
									int i1 = worldgenfeaturedefinedstructurejigsawjunction.a();
									int j1 = worldgenfeaturedefinedstructurejigsawjunction.c();

									if(i1 > k - 12 && j1 > l - 12 && i1 < k + 15 + 12 && j1 < l + 15 + 12)
									{
										objectlist1.add(worldgenfeaturedefinedstructurejigsawjunction);
									}
								}
							}
							else
							{
								objectlist.add(structurepiece);
							}
						}
					}
				});
			}
		}

		ProtoChunk protochunk = (ProtoChunk) ichunkaccess;
		HeightMap heightmap = protochunk.a(HeightMap.Type.OCEAN_FLOOR_WG);
		HeightMap heightmap1 = protochunk.a(HeightMap.Type.WORLD_SURFACE_WG);
		GeneratedChunk gc = ((ProvisionBukkit) provisioned).generateNMSChunkData(ws.get().getWorld(), new Random(i + j), i, j, new ChunkData()
		{
			public int getMaxHeight()
			{
				return 256;
			}

			public void setBlock(int x, int y, int z, Material material)
			{
				this.setBlock(x, y, z, material.createBlockData());
			}

			public void setBlock(int x, int y, int z, MaterialData material)
			{
				this.setBlock(x, y, z, CraftMagicNumbers.getBlock((MaterialData) material));
			}

			public void setBlock(int x, int y, int z, BlockData blockData)
			{
				this.setBlock(x, y, z, ((CraftBlockData) blockData).getState());
			}

			public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material)
			{
				this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.createBlockData());
			}

			public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material)
			{
				this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, CraftMagicNumbers.getBlock((MaterialData) material));
			}

			public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData)
			{
				this.setRegion(xMin, yMin, zMin, xMax, yMax, zMax, ((CraftBlockData) blockData).getState());
			}

			public Material getType(int x, int y, int z)
			{
				return CraftMagicNumbers.getMaterial((Block) this.getTypeId(x, y, z).getBlock());
			}

			public MaterialData getTypeAndData(int x, int y, int z)
			{
				return CraftMagicNumbers.getMaterial((IBlockData) this.getTypeId(x, y, z));
			}

			public BlockData getBlockData(int x, int y, int z)
			{
				return CraftBlockData.fromData((IBlockData) this.getTypeId(x, y, z));
			}

			public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, IBlockData type)
			{
				if(xMin > 15 || yMin >= getMaxHeight() || zMin > 15)
				{
					return;
				}
				if(xMin < 0)
				{
					xMin = 0;
				}
				if(yMin < 0)
				{
					yMin = 0;
				}
				if(zMin < 0)
				{
					zMin = 0;
				}
				if(xMax > 16)
				{
					xMax = 16;
				}
				if(yMax > getMaxHeight())
				{
					yMax = getMaxHeight();
				}
				if(zMax > 16)
				{
					zMax = 16;
				}
				if(xMin >= xMax || yMin >= yMax || zMin >= zMax)
				{
					return;
				}
				int y = yMin;
				while(y < yMax)
				{
					int x = xMin;
					while(x < xMax)
					{
						int z = zMin;
						while(z < zMax)
						{
							protochunk.setType(new BlockPosition(x, y, z), type, false);
							++z;
						}
						++x;
					}
					++y;
				}
			}

			public IBlockData getTypeId(int x, int y, int z)
			{
				if(x != (x & 15) || y < 0 || y >= getMaxHeight() || z != (z & 15))
				{
					return Blocks.AIR.getBlockData();
				}
				return protochunk.getType(new BlockPosition(x, y, z));
			}

			public byte getData(int x, int y, int z)
			{
				return CraftMagicNumbers.toLegacyData((IBlockData) this.getTypeId(x, y, z));
			}

			private void setBlock(int x, int y, int z, IBlockData type)
			{
				if(x != (x & 15) || y < 0 || y >= getMaxHeight() || z != (z & 15))
				{
					return;
				}

				protochunk.setType(new BlockPosition(x, y, z), type, false);

				if(type.getBlock().isTileEntity())
				{
					// if (this.tiles == null) {
					// this.tiles = new HashSet<BlockPosition>();
					// }
					// this.tiles.add(new BlockPosition(x, y, z));
				}
			}
		}, new BiomeGrid()
		{
			@Override
			public void setBiome(int x, int y, int z, Biome bio)
			{
				protochunk.getBiomeIndex().setBiome(x, y, z, CraftBlock.biomeToBiomeBase(ws.get().r().b(IRegistry.ay), bio));
			}

			@Override
			public void setBiome(int x, int z, Biome bio)
			{
				protochunk.getBiomeIndex().setBiome(x, 0, z, CraftBlock.biomeToBiomeBase(ws.get().r().b(IRegistry.ay), bio));
			}

			@Override
			public Biome getBiome(int x, int y, int z)
			{
				return CraftBlock.biomeBaseToBiome(ws.get().r().b(IRegistry.ay), protochunk.getBiomeIndex().getBiome(x, y, z));
			}

			@Override
			public Biome getBiome(int x, int z)
			{
				return CraftBlock.biomeBaseToBiome(ws.get().r().b(IRegistry.ay), protochunk.getBiomeIndex().getBiome(x, 0, z));
			}
		});

		for(int xx = 0; xx < 16; xx++)
		{
			for(int zz = 0; zz < 16; zz++)
			{
				int y = gc.getHeight().getHeight(xx, zz);
				if(y < getSeaLevel())
				{
					heightmap.a(xx, y, zz, Blocks.STONE.getBlockData());
				}
				heightmap1.a(xx, Math.max(y, getSeaLevel()), zz, Blocks.STONE.getBlockData());
			}
		}
	}

	public void addDecorations(RegionLimitedWorldAccess regionlimitedworldaccess, StructureManager structuremanager)
	{
		if(((IrisTerrainProvider) provisioned.getProvider()).shouldGenerateVanillaStructures())
		{
			int i = regionlimitedworldaccess.a();
			int j = regionlimitedworldaccess.b();
			int k = i * 16;
			int l = j * 16;
			BlockPosition blockposition = new BlockPosition(k, 0, l);
			BiomeBase biomebase = this.b.getBiome((i << 2) + 2, 2, (j << 2) + 2);
			SeededRandom seededrandom = new SeededRandom();
			long i1 = seededrandom.a(regionlimitedworldaccess.getSeed(), k, l);
			try
			{
				a(biomebase, structuremanager, this, regionlimitedworldaccess, i1, seededrandom, blockposition);
			}
			catch(Exception exception)
			{

			}
		}
	}

	public void a(BiomeBase bbase, StructureManager var0, ChunkGenerator var1, RegionLimitedWorldAccess var2, long var3, SeededRandom var5, BlockPosition var6)
	{
		if(!((IrisTerrainProvider) provisioned.getProvider()).shouldGenerateVanillaStructures())
		{
			return;
		}

		int stages = WorldGenStage.Decoration.values().length;
		for(int stage = 0; stage < stages; ++stage)
		{
			WorldGenStage.Decoration st = WorldGenStage.Decoration.values()[stage];

			if(st.equals(WorldGenStage.Decoration.LAKES))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.LOCAL_MODIFICATIONS))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.RAW_GENERATION))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.TOP_LAYER_MODIFICATION))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.UNDERGROUND_DECORATION))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.UNDERGROUND_ORES))
			{
				continue;
			}

			if(st.equals(WorldGenStage.Decoration.VEGETAL_DECORATION))
			{
				continue;
			}

			StructureGenerator<?> var13;
			int var10 = 0;
			if(var0.a())
			{
				@SuppressWarnings("unchecked")
				List<StructureGenerator<?>> structureGenerators = ((Map<Integer, List<StructureGenerator<?>>>) new V(bbase).get("g")).getOrDefault(stage, Collections.emptyList());
				Iterator<StructureGenerator<?>> iterator = structureGenerators.iterator();
				while(iterator.hasNext())
				{
					var13 = (StructureGenerator<?>) iterator.next();

					if(var13.equals(StructureGenerator.VILLAGE))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.JUNGLE_PYRAMID))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.IGLOO))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.NETHER_FOSSIL))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.SHIPWRECK))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.SHIPWRECK))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.MONUMENT))
					{
						continue;
					}

					if(var13.equals(StructureGenerator.BASTION_REMNANT))
					{
						continue;
					}

					var5.b(var3, var10, stage);
					int var14 = var6.getX() >> 4;
					int var15 = var6.getZ() >> 4;
					int var16 = var14 << 4;
					int var17 = var15 << 4;

					try
					{
						var0.a(SectionPosition.a((BlockPosition) var6), var13).forEach(var8 -> var8.a((GeneratorAccessSeed) var2, var0, var1, (Random) var5, new StructureBoundingBox(var16, var17, var16 + 15, var17 + 15), new ChunkCoordIntPair(var14, var15)));
					}

					catch(Exception var18)
					{

					}

					++var10;
				}
			}
		}
	}

	@Override
	public int getSeaLevel()
	{
		return ((IrisTerrainProvider) provisioned.getProvider()).getFluidHeight();
	}

	@Override
	public List<BiomeSettingsMobs.c> getMobsFor(BiomeBase biomebase, StructureManager structuremanager, EnumCreatureType enumcreaturetype, BlockPosition blockposition)
	{
		if(structuremanager.a(blockposition, true, StructureGenerator.SWAMP_HUT).e())
		{
			if(enumcreaturetype == EnumCreatureType.MONSTER)
			{
				return StructureGenerator.SWAMP_HUT.c();
			}

			if(enumcreaturetype == EnumCreatureType.CREATURE)
			{
				return StructureGenerator.SWAMP_HUT.j();
			}
		}

		if(enumcreaturetype == EnumCreatureType.MONSTER)
		{
			if(structuremanager.a(blockposition, false, StructureGenerator.PILLAGER_OUTPOST).e())
			{
				return StructureGenerator.PILLAGER_OUTPOST.c();
			}

			if(structuremanager.a(blockposition, false, StructureGenerator.MONUMENT).e())
			{
				return StructureGenerator.MONUMENT.c();
			}

			if(structuremanager.a(blockposition, true, StructureGenerator.FORTRESS).e())
			{
				return StructureGenerator.FORTRESS.c();
			}
		}

		return super.getMobsFor(biomebase, structuremanager, enumcreaturetype, blockposition);
	}

	@Override
	public void addMobs(RegionLimitedWorldAccess regionlimitedworldaccess)
	{
		int i = regionlimitedworldaccess.a();
		int j = regionlimitedworldaccess.b();
		BiomeBase biomebase = regionlimitedworldaccess.getBiome((new ChunkCoordIntPair(i, j)).l());
		SeededRandom seededrandom = new SeededRandom();
		seededrandom.a(regionlimitedworldaccess.getSeed(), i << 4, j << 4);
		SpawnerCreature.a(regionlimitedworldaccess, biomebase, i, j, seededrandom);
	}

	public void createStructures(IRegistryCustom iregistrycustom, StructureManager structuremanager, IChunkAccess ichunkaccess, DefinedStructureManager definedstructuremanager, long i)
	{
		ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
		BiomeBase biomebase = this.b.getBiome((chunkcoordintpair.x << 2) + 2, 0, (chunkcoordintpair.z << 2) + 2);
		this.a(StructureFeatures.k, iregistrycustom, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
		for(Supplier<StructureFeature<?, ?>> supplier : biomebase.e().a())
		{
			StructureFeature<?, ?> structurefeature = (StructureFeature<?, ?>) supplier.get();
			if(StructureFeature.c == StructureGenerator.STRONGHOLD)
			{
				StructureFeature<?, ?> structureFeature = structurefeature;
				synchronized(structureFeature)
				{
					this.a(structurefeature, iregistrycustom, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
					continue;
				}
			}
			this.a(structurefeature, iregistrycustom, structuremanager, ichunkaccess, definedstructuremanager, i, chunkcoordintpair, biomebase);
		}
	}

	private void a(StructureFeature<?, ?> structurefeature, IRegistryCustom iregistrycustom, StructureManager structuremanager, IChunkAccess ichunkaccess, DefinedStructureManager definedstructuremanager, long i, ChunkCoordIntPair chunkcoordintpair, BiomeBase biomebase)
	{
		StructureStart<?> structurestart = structuremanager.a(SectionPosition.a((ChunkCoordIntPair) ichunkaccess.getPos(), (int) 0), structurefeature.d, (IStructureAccess) ichunkaccess);
		int j = structurestart != null ? structurestart.j() : 0;
		StructureSettingsFeature structuresettingsfeature = getSettings().a(structurefeature.d);
		if(structuresettingsfeature != null)
		{
			StructureStart<?> structurestart1 = structurefeature.a(iregistrycustom, this, this.b, definedstructuremanager, i, chunkcoordintpair, biomebase, j, structuresettingsfeature);
			structuremanager.a(SectionPosition.a((ChunkCoordIntPair) ichunkaccess.getPos(), (int) 0), structurefeature.d, structurestart1, (IStructureAccess) ichunkaccess);
		}
	}

	public void storeStructures(GeneratorAccessSeed generatoraccessseed, StructureManager structuremanager, IChunkAccess ichunkaccess)
	{
		int i = ichunkaccess.getPos().x;
		int j = ichunkaccess.getPos().z;
		int k = i << 4;
		int l = j << 4;
		SectionPosition sectionposition = SectionPosition.a((ChunkCoordIntPair) ichunkaccess.getPos(), (int) 0);
		int i1 = i - 8;
		while(i1 <= i + 8)
		{
			int j1 = j - 8;
			while(j1 <= j + 8)
			{
				long k1 = ChunkCoordIntPair.pair((int) i1, (int) j1);
				for(StructureStart<?> structurestart : generatoraccessseed.getChunkAt(i1, j1).h().values())
				{
					try
					{
						if(structurestart == StructureStart.a || !structurestart.c().a(k, l, k + 15, l + 15))
							continue;
						structuremanager.a(sectionposition, structurestart.l(), k1, (IStructureAccess) ichunkaccess);
						PacketDebug.a((GeneratorAccessSeed) generatoraccessseed, (StructureStart<?>) structurestart);
					}
					catch(Exception exception)
					{
						CrashReport crashreport = CrashReport.a((Throwable) exception, (String) "Generating structure reference");
						CrashReportSystemDetails crashreportsystemdetails = crashreport.a("Structure");
						crashreportsystemdetails.a("Name", () -> structurestart.l().i());
						crashreportsystemdetails.a("Class", () -> structurestart.l().getClass().getCanonicalName());
						throw new ReportedException(crashreport);
					}
				}
				++j1;
			}
			++i1;
		}
	}

	@Override
	public Provisioned getProvisioned()
	{
		return provisioned;
	}

	@Override
	public void clearRegeneratedLists()
	{
		getProvisioned().clearRegeneratedLists();
	}

	@Override
	public TerrainProvider getProvider()
	{
		return getProvisioned().getProvider();
	}

	@Override
	public void regenerate(int x, int z)
	{
		getProvisioned().regenerate(x, z);
	}
}

package com.volmit.iris.object;

import com.volmit.iris.gen.ParallaxChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("A text renderer to place text on terrain")
@Data
public class IrisTextPlacement
{
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance to place this font per chunk")
	private double chance = 0.1;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The amount of times to place randomly in a chunk if the chance passes")
	private int density = 1;

	@DontObfuscate
	@Desc("The rotation for this text placement")
	private IrisObjectRotation rotation = new IrisObjectRotation();

	@DontObfuscate
	@Desc("The mode to place this text")
	private ObjectPlaceMode mode = ObjectPlaceMode.PAINT;

	@DontObfuscate
	@Desc("The translation for this text placement")
	private IrisObjectTranslate translate = new IrisObjectTranslate();

	@DontObfuscate
	@Desc("The clamp for this text placement")
	private IrisObjectLimit clamp = new IrisObjectLimit();

	@Required
	@DontObfuscate
	@Desc("The text renderers to pick from")
	@ArrayType(min = 1, type = IrisTextRenderer.class)
	private KList<IrisTextRenderer> render = new KList<>();

	@DontObfuscate
	@Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
	private boolean underwater = false;

	@DontObfuscate
	@Desc("If set to true, Blocks placed underwater that could be waterlogged are waterlogged.")
	private boolean waterloggable = true;

	@DontObfuscate
	@Desc("If set to true, objects will place on the fluid height level Such as boats.")
	private boolean onwater = false;

	private AtomicCache<IrisObjectPlacement> config = new AtomicCache<>();

	public IrisObjectPlacement getConfig()
	{
		return config.aquire(() ->
		{
			IrisObjectPlacement p = new IrisObjectPlacement();

			p.setRotation(getRotation());
			p.setBore(false);
			p.setDensity(getDensity());
			p.setChance(getChance());
			p.setTranslate(getTranslate());
			p.setClamp(getClamp());
			p.setOnwater(isOnwater());
			p.setUnderwater(isUnderwater());
			p.setWaterloggable(isWaterloggable());

			return p;
		});
	}

	public IrisTextPlacement()
	{

	}

	public int maxDimension()
	{
		int m = 0;

		for(IrisTextRenderer i : getRender())
		{
			int g = (int) Math.ceil(i.getMaxLength());

			if(g > m)
			{
				m = g;
			}
		}

		return m;
	}

	public void place(ParallaxChunkGenerator g, RNG rng, int x, int z)
	{
		int tr = getConfig().getTriesForChunk(rng);

		for(int i = 0; i < tr; i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			int xb = (x * 16) + rng.nextInt(16);
			int zb = (z * 16) + rng.nextInt(16);
			getRender().get(rng.nextInt(getRender().size())).place(g, rng, getConfig(), xb, zb);
		}
	}
}

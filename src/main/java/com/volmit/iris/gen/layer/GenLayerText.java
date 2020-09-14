package com.volmit.iris.gen.layer;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.TopographicTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.object.IrisMaterialPalette;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.B;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.RNG;

public class GenLayerText extends GenLayer
{
	public static final BlockData AIR = B.getBlockData("AIR");

	private AtomicCache<IrisObject> debug = new AtomicCache<>();

	public GenLayerText(TopographicTerrainProvider iris, RNG rng)
	{
		super(iris, rng);
	}

	public IrisObject getDebug()
	{
		return debug.aquire(() ->
		{
			return createTextObject("Test", "Impact", 24, B.get("STONE"));
		});
	}

	public IrisObject createTextObject(String text, String font, int size, BlockData b)
	{
		Font f = new Font(font, Font.PLAIN, size);
		int w = ((Graphics2D) new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics()).getFontMetrics(f).stringWidth(text);
		int h = size;
		Iris.info("WH is " + w + " " + h);
		BufferedImage bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics gs = bufferedImage.getGraphics();
		Graphics2D g = (Graphics2D) gs;
		g.setFont(f);
		g.drawString(text, 0, h);
		IrisObject o = new IrisObject(w, 1, h);
		for(int y = 0; y < h; y++)
		{
			for(int x = 0; x < w; x++)
			{
				if(bufferedImage.getRGB(x, y) != -16777216)
				{
					o.setUnsigned(x, 0, y, b);
				}
			}
		}

		return o;
	}

	public IrisObject createTextObject(RNG rng, String text, int w, Font f, IrisMaterialPalette palette)
	{
		int h = f.getSize();
		BufferedImage bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics gs = bufferedImage.getGraphics();
		Graphics2D g = (Graphics2D) gs;
		g.setFont(f);
		g.drawString(text, 0, h);
		IrisObject o = new IrisObject(w, 1, h);
		for(int y = 0; y < h; y++)
		{
			for(int x = 0; x < w; x++)
			{
				if(bufferedImage.getRGB(x, y) != -16777216)
				{
					o.setUnsigned(x, 0, y, palette.get(rng, x, w, y, iris.getData()));
				}
			}
		}

		return o;
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}
}

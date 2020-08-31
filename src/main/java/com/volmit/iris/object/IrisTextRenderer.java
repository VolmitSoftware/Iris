package com.volmit.iris.object;

import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;

import com.volmit.iris.gen.ParallaxChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListFont;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("A text renderer to place text on terrain")
@Data
public class IrisTextRenderer
{
	@RegistryListFont
	@Required
	@DontObfuscate
	@Desc("The font to use for this renderer")
	private String font;

	@MinNumber(4)
	@MaxNumber(48)
	@DontObfuscate
	@Desc("The font scale 1 = 1pt = ~1-2 blocks high per character")
	private int size = 18;

	@DontObfuscate
	@Desc("The font style to use while rendering text")
	private FontStyle fontStyle = FontStyle.PLAIN;

	@Required
	@DontObfuscate
	@Desc("The lines of text to randomly pick from")
	@ArrayType(min = 1, type = String.class)
	private KList<String> text = new KList<>();

	@Required
	@DontObfuscate
	@Desc("The palette of blocks to use when drawing text")
	private IrisMaterialPalette blockPalette = new IrisMaterialPalette();

	@DontObfuscate
	@Desc("Use a generator to shuffle the surface field of coordinates. Using simplex for example would make the text rendered wavy. Use the multiplier to stretch further.")
	private IrisGeneratorStyle surfaceField = new IrisGeneratorStyle(NoiseStyle.FLAT);

	private transient AtomicCache<KList<IrisObject>> objects = new AtomicCache<>();
	private transient AtomicCache<Font> fontData = new AtomicCache<>();
	private transient AtomicCache<FontMetrics> fontMetrics = new AtomicCache<>();
	private transient AtomicCache<Double> maxLength = new AtomicCache<>();
	private transient AtomicCache<Integer> fontStyleData = new AtomicCache<>();

	public IrisTextRenderer()
	{

	}

	public KList<IrisObject> getObjects(ParallaxChunkGenerator g, RNG rng)
	{
		return objects.aquire(() ->
		{
			KList<IrisObject> o = new KList<>();

			for(String i : text)
			{
				o.add(g.getGlText().createTextObject(rng, i, (int) getLength(i), getFontData(), getBlockPalette()));
			}

			return o;
		});
	}

	public String getText(RNG rng)
	{
		return text.get(rng.nextInt(text.size()));
	}

	public double getMaxLength()
	{
		return maxLength.aquire(() ->
		{
			String l = "";

			for(String i : text)
			{
				if(i.length() > l.length())
				{
					l = i;
				}
			}

			return getLength(l);
		});
	}

	public double getLength(String str)
	{
		return getFontMetrics().stringWidth(str);
	}

	public double getHeight(String str)
	{
		return getSize() * 1.2;
	}

	public Font getFontData()
	{
		return fontData.aquire(() ->
		{
			return new Font(getFont(), fontStyleData.aquire(() ->
			{
				if(getFontStyle().equals(FontStyle.ITALIC))
				{
					return Font.ITALIC;
				}

				if(getFontStyle().equals(FontStyle.BOLD))
				{
					return Font.BOLD;
				}

				return Font.PLAIN;
			}), getSize());
		});
	}

	public FontMetrics getFontMetrics()
	{
		return fontMetrics.aquire(() ->
		{
			Canvas c = new Canvas();
			return c.getFontMetrics(getFontData());
		});
	}

	public void place(ParallaxChunkGenerator g, RNG rng, IrisObjectPlacement config, int xb, int zb)
	{
		getObjects(g, rng).get(rng.nextInt(getObjects(g, rng).size())).place(xb, zb, g, config, rng);
	}
}

package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.*;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a color")
@Data
public class IrisColor
{
	@DontObfuscate
	@MaxNumber(7)
	@MinNumber(6)
	@Desc("Pass in a 6 digit hexadecimal color to fill R G and B values. You can also include the # symbol, but it's not required.")
	private String hex = null;

	@DontObfuscate
	@MaxNumber(255)
	@MinNumber(0)
	@Desc("Represents the red channel. Only define this if you are not defining the hex value.")
	private int red = 0;

	@DontObfuscate
	@MaxNumber(255)
	@MinNumber(0)
	@Desc("Represents the green channel. Only define this if you are not defining the hex value.")
	private int green = 0;

	@DontObfuscate
	@MaxNumber(255)
	@MinNumber(0)
	@Desc("Represents the blue channel. Only define this if you are not defining the hex value.")
	private int blue = 0;

	private final transient AtomicCache<Color> color = new AtomicCache<>();

	public Color getColor()
	{
		return color.aquire(() -> {
			if(hex != null)
			{
				String v = (hex.startsWith("#") ? hex : "#" + hex).trim();
				try
				{
					return Color.decode(v);
				}

				catch(Throwable e)
				{

				}
			}

			return new Color(red, green, blue);
		});
	}

	public org.bukkit.Color getBukkitColor()
	{
		return org.bukkit.Color.fromRGB(getColor().getRGB());
	}

	public static Color blend(Color... c) {
		if (c == null || c.length <= 0) {
			return null;
		}
		float ratio = 1f / ((float) c.length);

		int a = 0;
		int r = 0;
		int g = 0;
		int b = 0;

		for (int i = 0; i < c.length; i++) {
			int rgb = c[i].getRGB();
			int a1 = (rgb >> 24 & 0xff);
			int r1 = ((rgb & 0xff0000) >> 16);
			int g1 = ((rgb & 0xff00) >> 8);
			int b1 = (rgb & 0xff);
			a += ((int) a1 * ratio);
			r += ((int) r1 * ratio);
			g += ((int) g1 * ratio);
			b += ((int) b1 * ratio);
		}

		return new Color(a << 24 | r << 16 | g << 8 | b);
	}
}

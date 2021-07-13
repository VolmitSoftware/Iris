package com.volmit.iris.manager.gui;

import java.awt.image.BufferedImage;

import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.util.IrisInterpolation;
import org.bukkit.Material;

public class IrisRenderer
{
	private Renderer renderer;

	public IrisRenderer(Renderer renderer)
	{
		this.renderer = renderer;
	}

	public BufferedImage render(double sx, double sz, double size, int resolution)
	{
		BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
		double x, z;
		int i, j;
		for(i = 0; i < resolution; i++)
		{
			x = IrisInterpolation.lerp(sx, sx + size, (double) i / (double) (resolution));

			for(j = 0; j < resolution; j++)
			{
				z = IrisInterpolation.lerp(sz, sz + size, (double) j / (double) (resolution));
				image.setRGB(i, j, renderer.draw(x, z).getRGB());
			}
		}

		return image;
	}

    public void set(double worldX, double worldZ) {
	    ((Engine)renderer).getWorld().getBlockAt((int)worldX, 20, (int)worldZ).setType(Material.DIAMOND_BLOCK);
    }
}

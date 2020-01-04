package ninja.bytecode.iris.util;

import java.awt.Color;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import ninja.bytecode.iris.util.ParticleEffect.OrdinaryColor;

public class ParticleRedstone extends ParticleBase implements ColoredEffect
{
	private Color color;
	private float size;

	public ParticleRedstone()
	{
		this.color = Color.WHITE;
		size = 1f;
	}

	@Override
	public void play(Location l, double range)
	{
		ParticleEffect.REDSTONE.display(new OrdinaryColor(getColor().getRed(), getColor().getGreen(), getColor().getBlue()), l , range);
	}

	@Override
	public void play(Location l, Player p)
	{
		ParticleEffect.REDSTONE.display(new OrdinaryColor(getColor().getRed(), getColor().getGreen(), getColor().getBlue()), l , p);
	}

	@Override
	public ParticleRedstone setColor(Color color)
	{
		this.color = color;
		return this;
	}

	@Override
	public Color getColor()
	{
		return color;
	}

	public ParticleRedstone setSize(float size)
	{
		this.size = size;
		return this;
	}

	public float getSize()
	{
		return size;
	}
}

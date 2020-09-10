package com.volmit.iris.object;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.IrisTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.DependsOn;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import lombok.experimental.Accessors;

@Accessors(chain = true)
@Builder
@AllArgsConstructor
@Desc("An iris effect")
@Data
public class IrisEffect
{
	@Builder.Default
	@DontObfuscate
	@Desc("The potion effect to apply in this area")
	private String potionEffect = "";

	@Builder.Default
	@DontObfuscate
	@Desc("The particle effect to apply in the area")
	private Particle particleEffect = null;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(-32)
	@MaxNumber(32)
	@DontObfuscate
	@Desc("Randomly offset from the surface to this surface+value")
	private int particleOffset = 0;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(-8)
	@MaxNumber(8)
	@DontObfuscate
	@Desc("The alt x, usually represents motion if the particle count is zero. Otherwise an offset.")
	private double particleAltX = 0;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(-8)
	@MaxNumber(8)
	@DontObfuscate
	@Desc("The alt y, usually represents motion if the particle count is zero. Otherwise an offset.")
	private double particleAltY = 0;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(-8)
	@MaxNumber(8)
	@DontObfuscate
	@Desc("The alt z, usually represents motion if the particle count is zero. Otherwise an offset.")
	private double particleAltZ = 0;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@DontObfuscate
	@Desc("Randomize the altX by -altX to altX")
	private boolean randomAltX = true;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@DontObfuscate
	@Desc("Randomize the altY by -altY to altY")
	private boolean randomAltY = false;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@DontObfuscate
	@Desc("Randomize the altZ by -altZ to altZ")
	private boolean randomAltZ = true;

	@Builder.Default
	@DontObfuscate
	@Desc("The sound to play")
	private Sound sound = null;

	@Builder.Default
	@DependsOn({"sound"})
	@MinNumber(0)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The max distance from the player the sound will play")
	private int soundDistance = 12;

	@Builder.Default
	@DependsOn({"sound", "maxPitch"})
	@MinNumber(0.01)
	@MaxNumber(1.99)
	@DontObfuscate
	@Desc("The minimum sound pitch")
	private double minPitch = 0.5D;

	@Builder.Default
	@DependsOn({"sound", "minVolume"})
	@MinNumber(0.01)
	@MaxNumber(1.99)
	@DontObfuscate
	@Desc("The max sound pitch")
	private double maxPitch = 1.5D;

	@Builder.Default
	@DependsOn({"sound"})
	@MinNumber(0.001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The sound volume.")
	private double volume = 1.5D;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(0)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The particle count. Try setting to zero for using the alt xyz to a motion value instead of an offset")
	private int particleCount = 0;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(0)
	@MaxNumber(64)
	@DontObfuscate
	@Desc("How far away from the player particles can play")
	private int particleDistance = 20;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(0)
	@MaxNumber(128)
	@DontObfuscate
	@Desc("How wide the particles can play (player's view left and right) RADIUS")
	private int particleDistanceWidth = 24;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@DontObfuscate
	@Desc("An extra value for some particles... Which bukkit doesn't even document.")
	private double extra = 0;

	@Builder.Default
	@DependsOn({"potionEffect"})
	@MinNumber(-1)
	@MaxNumber(1024)
	@DontObfuscate
	@Desc("The Potion Strength or -1 to disable")
	private int potionStrength = -1;

	@Builder.Default
	@DependsOn({"potionEffect", "potionTicksMin"})
	@MinNumber(1)
	@DontObfuscate
	@Desc("The max time the potion will last for")
	private int potionTicksMax = 155;

	@Builder.Default
	@DependsOn({"potionEffect", "potionTicksMax"})
	@MinNumber(1)
	@DontObfuscate
	@Desc("The min time the potion will last for")
	private int potionTicksMin = 75;

	@Builder.Default
	@Required
	@MinNumber(0)
	@DontObfuscate
	@Desc("The effect interval in milliseconds")
	private int interval = 150;

	@Builder.Default
	@DependsOn({"particleEffect"})
	@MinNumber(0)
	@MaxNumber(16)
	@DontObfuscate
	@Desc("The effect distance start away")
	private int particleAway = 5;

	@Builder.Default
	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("The chance is 1 in CHANCE per interval")
	private int chance = 50;

	private final transient AtomicCache<PotionEffectType> pt = new AtomicCache<>();
	private final transient AtomicCache<ChronoLatch> latch = new AtomicCache<>();

	public IrisEffect()
	{

	}

	public boolean canTick()
	{
		return latch.aquire(() -> new ChronoLatch(interval)).flip();
	}

	public PotionEffectType getRealType()
	{
		return pt.aquire(() ->
		{
			PotionEffectType t = PotionEffectType.LUCK;

			if(getPotionEffect().isEmpty())
			{
				return t;
			}

			try
			{
				for(PotionEffectType i : PotionEffectType.values())
				{
					if(i.getName().toUpperCase().replaceAll("\\Q \\E", "_").equals(getPotionEffect()))
					{
						t = i;

						return t;
					}
				}
			}

			catch(Throwable e)
			{

			}

			Iris.warn("Unknown Potion Effect Type: " + getPotionEffect());

			return t;
		});
	}

	public void apply(Player p, IrisTerrainProvider g)
	{
		if(!canTick())
		{
			return;
		}

		if(RNG.r.nextInt(chance) != 0)
		{
			return;
		}

		if(sound != null)
		{
			Location part = p.getLocation().clone().add(RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance), RNG.r.i(-soundDistance, soundDistance));
			p.playSound(part, getSound(), (float) volume, (float) RNG.r.d(minPitch, maxPitch));
		}

		if(particleEffect != null)
		{
			Location part = p.getLocation().clone().add(p.getLocation().getDirection().clone().multiply(RNG.r.i(particleDistance) + particleAway)).clone().add(p.getLocation().getDirection().clone().rotateAroundY(Math.toRadians(90)).multiply(RNG.r.d(-particleDistanceWidth, particleDistanceWidth)));

			part.setY(Math.round(g.getTerrainHeight(part.getBlockX(), part.getBlockZ())) + 1);
			part.add(RNG.r.d(), 0, RNG.r.d());
			if(extra != 0)
			{
				p.spawnParticle(particleEffect, part.getX(), part.getY() + RNG.r.i(particleOffset), part.getZ(), particleCount, randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX, randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY, randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ, extra);
			}

			else
			{
				p.spawnParticle(particleEffect, part.getX(), part.getY() + RNG.r.i(particleOffset), part.getZ(), particleCount, randomAltX ? RNG.r.d(-particleAltX, particleAltX) : particleAltX, randomAltY ? RNG.r.d(-particleAltY, particleAltY) : particleAltY, randomAltZ ? RNG.r.d(-particleAltZ, particleAltZ) : particleAltZ);
			}
		}

		if(potionStrength > -1)
		{
			if(p.hasPotionEffect(getRealType()))
			{
				PotionEffect e = p.getPotionEffect(getRealType());
				if(e.getAmplifier() > getPotionStrength())
				{
					return;
				}

				p.removePotionEffect(getRealType());
			}

			p.addPotionEffect(new PotionEffect(getRealType(), RNG.r.i(Math.min(potionTicksMax, potionTicksMin), Math.max(potionTicksMax, potionTicksMin)), getPotionStrength(), true, false, false));
		}
	}
}

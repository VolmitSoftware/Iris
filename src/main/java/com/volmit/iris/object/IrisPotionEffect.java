package com.volmit.iris.object;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@DontObfuscate
@Desc("An iris potion effect")
@Data
public class IrisPotionEffect
{

	@Required
	@DontObfuscate
	@Desc("The potion effect to apply in this area")
	private String potionEffect = "";

	@Required
	@MinNumber(-1)
	@MaxNumber(1024)
	@DontObfuscate
	@Desc("The Potion Strength or -1 to disable")
	private int strength = -1;

	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("The time the potion will last for")
	private int ticks = 200;

	@DontObfuscate
	@Desc("Is the effect ambient")
	private boolean ambient = false;

	@DontObfuscate
	@Desc("Is the effect showing particles")
	private boolean particles = true;

	private final transient AtomicCache<PotionEffectType> pt = new AtomicCache<>();

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

	public void apply(LivingEntity p)
	{
		if(strength > -1)
		{
			if(p.hasPotionEffect(getRealType()))
			{
				PotionEffect e = p.getPotionEffect(getRealType());
				if(e.getAmplifier() > strength)
				{
					return;
				}

				p.removePotionEffect(getRealType());
			}

			p.addPotionEffect(new PotionEffect(getRealType(), ticks, strength, ambient, particles, false));
		}
	}
}

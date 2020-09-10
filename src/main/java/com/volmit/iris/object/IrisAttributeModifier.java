package com.volmit.iris.object;

import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.inventory.meta.ItemMeta;

import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Represents an attribute modifier for an item or an entity. This allows you to create modifications to basic game attributes such as MAX_HEALTH or ARMOR_VALUE.")
@Data
public class IrisAttributeModifier
{
	@Required
	
	@DontObfuscate
	@Desc("The Attribute type. This type is pulled from the game attributes. Zombie & Horse attributes will not work on non-zombie/horse entities.\nUsing an attribute on an item will have affects when held, or worn. There is no way to specify further granularity as the game picks this depending on the item type.")
	private Attribute attribute = null;

	@MinNumber(2)
	@Required
	
	@DontObfuscate
	@Desc("The Attribute Name is used internally only for the game. This value should be unique to all other attributes applied to this item/entity. It is not shown in game.")
	private String name = "";

	@DontObfuscate
	
	@Desc("The application operation (add number is default). Add Number adds to the default value. \nAdd scalar_1 will multiply by 1 for example if the health is 20 and you multiply_scalar_1 by 0.5, the health will result in 30, not 10. Use negative values to achieve that.")
	private Operation operation = Operation.ADD_NUMBER;

	@DontObfuscate
	
	@Desc("Minimum amount for this modifier. Iris randomly chooses an amount, this is the minimum it can choose randomly for this attribute.")
	private double minAmount = 1;

	
	@DontObfuscate
	@Desc("Maximum amount for this modifier Iris randomly chooses an amount, this is the maximum it can choose randomly for this attribute.")
	private double maxAmount = 1;

	@MinNumber(0)
	@MaxNumber(1)
	
	@DontObfuscate
	@Desc("The chance that this attribute is applied (0 to 1). If the chance is 0.5 (50%), then Iris will only apply this attribute 50% of the time.")
	private double chance = 1;

	public void apply(RNG rng, ItemMeta meta)
	{
		if(rng.nextDouble() < getChance())
		{
			meta.addAttributeModifier(getAttribute(), new AttributeModifier(getName(), getAmount(rng), getOperation()));
		}
	}

	public void apply(RNG rng, Attributable meta)
	{
		if(rng.nextDouble() < getChance())
		{
			meta.getAttribute(getAttribute()).addModifier(new AttributeModifier(getName(), getAmount(rng), getOperation()));
		}
	}

	public double getAmount(RNG rng)
	{
		return rng.d(getMinAmount(), getMaxAmount());
	}
}

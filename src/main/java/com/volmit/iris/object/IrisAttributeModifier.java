package com.volmit.iris.object;

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

import lombok.Data;

@Desc("Represents an attribute modifier")
@Data
public class IrisAttributeModifier
{
	@Required
	@DontObfuscate
	@Desc("The Attribute type")
	private Attribute attribute = null;

	@MinNumber(2)
	@Required
	@DontObfuscate
	@Desc("The Attribute Name")
	private String name = "";

	@DontObfuscate
	@Desc("The application operation (add number is default)")
	private Operation operation = Operation.ADD_NUMBER;

	@DontObfuscate
	@Desc("Minimum amount for this modifier")
	private double minAmount = 1;

	@DontObfuscate
	@Desc("Maximum amount for this modifier")
	private double maxAmount = 1;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance that this attribute is applied (0 to 1)")
	private double chance = 1;

	public IrisAttributeModifier()
	{

	}

	public void apply(RNG rng, ItemMeta meta)
	{
		if(rng.nextDouble() < getChance())
		{
			meta.addAttributeModifier(getAttribute(), new AttributeModifier(getName(), getAmount(rng), getOperation()));
		}
	}

	public double getAmount(RNG rng)
	{
		return rng.d(getMinAmount(), getMaxAmount());
	}
}

package com.volmit.iris.object;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;
import net.md_5.bungee.api.ChatColor;

@Desc("Represents a loot entry")
@Data
public class IrisLoot
{
	@DontObfuscate
	@Desc("The target inventory slot types to fill this loot with")
	private InventorySlotType slotTypes = InventorySlotType.STORAGE;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The sub rarity of this loot. Calculated after this loot table has been picked.")
	private int rarity = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("Minimum amount of this loot")
	private int minAmount = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("Maximum amount of this loot")
	private int maxAmount = 1;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("Minimum durability percent")
	private double minDurability = 0;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("Maximum durability percent")
	private double maxDurability = 1;

	@MinNumber(1)
	@MaxNumber(10)
	@DontObfuscate
	@Desc("Minimum Enchantment level")
	private int minEnchantLevel = 1;

	@MinNumber(1)
	@MaxNumber(10)
	@DontObfuscate
	@Desc("Maximum Enchantment level")
	private int maxEnchantLevel = 1;

	@MinNumber(0)
	@MaxNumber(10)
	@DontObfuscate
	@Desc("Minimum Enchantmentments")
	private int minEnchants = 0;

	@MinNumber(0)
	@MaxNumber(10)
	@DontObfuscate
	@Desc("Maximum Enchantmentments")
	private int maxEnchants = 0;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The chance for every attempt to add an enchantment 1 in X")
	private int enchantmentRarity = 4;

	@Required
	@Desc("This is the item or block type. Does not accept minecraft:*. Only materials such as DIAMOND_SWORD or DIRT.")
	private String type = "";

	private transient AtomicCache<CNG> chance = new AtomicCache<>();

	public IrisLoot()
	{

	}

	public Material getType()
	{
		return B.getMaterial(type);
	}

	public ItemStack get(boolean debug, IrisLootTable table, RNG rng, int x, int y, int z)
	{
		if(debug)
		{
			chance.reset();
		}

		if(chance.aquire(() -> NoiseStyle.STATIC.create(rng)).fit(1, rarity * table.getRarity(), x, y, z) == 1)
		{
			if(getType() == null)
			{
				Iris.warn("Cant find item type " + type);
				return null;
			}

			ItemStack is = new ItemStack(getType(), Math.max(1, rng.i(getMinAmount(), getMaxAmount())));
			ItemMeta m = is.getItemMeta();

			if(getType().getMaxDurability() > 0 && m instanceof Damageable)
			{
				Damageable d = (Damageable) m;
				int max = getType().getMaxDurability();
				d.setDamage((int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(getMinDurability(), getMaxDurability())) * max))));
			}

			KList<String> lore = new KList<>();

			if(minEnchants > 0 || maxEnchants > 0)
			{
				KList<Enchantment> c = new KList<Enchantment>(Enchantment.values());

				for(int i = minEnchants; i < maxEnchants; i++)
				{
					if(rng.i(1, enchantmentRarity) == 1)
					{
						Enchantment e = c.get(rng.nextInt(c.size()));

						for(int ggh = 0; ggh < 8; ggh++)
						{
							if(e.canEnchantItem(is))
							{
								m.addEnchant(e, rng.i(getMinEnchantLevel(), getMaxEnchantLevel()), false);
								break;
							}

							e = c.get(rng.nextInt(c.size()));
						}
					}
				}
			}

			if(debug)
			{
				lore.add("From Table: " + table.getName() + " (" + Form.pc(1D / table.getRarity(), 5) + ")");
				lore.add(ChatColor.GRAY + "1 in " + (table.getRarity() * getRarity()) + " Chance (" + Form.pc(1D / (table.getRarity() * getRarity()), 5) + ")");
			}

			m.setLore(lore);
			is.setItemMeta(m);
			return is;
		}

		return null;
	}
}

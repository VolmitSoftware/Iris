package com.volmit.iris.object;

import java.awt.Color;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.Colorable;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.C;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.Form;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListItemType;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
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

	@MinNumber(1)
	@DontObfuscate
	@Desc("The display name of this item")
	private String displayName = null;

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

	@DontObfuscate
	@Desc("Define a custom model identifier 1.14+ only")
	private Integer customModel = null;

	@DontObfuscate
	@Desc("Set this to true to prevent it from being broken")
	private boolean unbreakable = false;

	@ArrayType(min = 1, type = ItemFlag.class)
	@DontObfuscate
	@Desc("The item flags to add")
	private KList<ItemFlag> itemFlags = new KList<>();

	@DontObfuscate
	@Desc("Apply enchantments to this item")
	@ArrayType(min = 1, type = IrisEnchantment.class)
	private KList<IrisEnchantment> enchantments = new KList<>();

	@DontObfuscate
	@Desc("Apply attribute modifiers to this item")
	@ArrayType(min = 1, type = IrisAttributeModifier.class)
	private KList<IrisAttributeModifier> attributes = new KList<>();

	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("Add lore to this item")
	private KList<String> lore = new KList<>();

	@RegistryListItemType
	@Required
	@DontObfuscate
	@Desc("This is the item or block type. Does not accept minecraft:*. Only materials such as DIAMOND_SWORD or DIRT.")
	private String type = "";

	@DontObfuscate
	@Desc("The dye color")
	private DyeColor dyeColor = null;

	@DontObfuscate
	@Desc("The leather armor color")
	private String leatherColor = null;

	private final transient AtomicCache<CNG> chance = new AtomicCache<>();

	public Material getType()
	{
		return B.getMaterial(type);
	}

	public ItemStack get(boolean debug, RNG rng)
	{
		try
		{
			ItemStack is = new ItemStack(getType(), Math.max(1, rng.i(getMinAmount(), getMaxAmount())));
			ItemMeta m = is.getItemMeta();

			if(getType().getMaxDurability() > 0 && m instanceof Damageable)
			{
				Damageable d = (Damageable) m;
				int max = getType().getMaxDurability();
				d.setDamage((int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(getMinDurability(), getMaxDurability())) * max))));
			}

			for(IrisEnchantment i : getEnchantments())
			{
				i.apply(rng, m);
			}

			for(IrisAttributeModifier i : getAttributes())
			{
				i.apply(rng, m);
			}

			if(Iris.customModels)
			{
				m.setCustomModelData(getCustomModel());
			}

			m.setLocalizedName(C.translateAlternateColorCodes('&', displayName));
			m.setDisplayName(C.translateAlternateColorCodes('&', displayName));
			m.setUnbreakable(isUnbreakable());

			for(ItemFlag i : getItemFlags())
			{
				m.addItemFlags(i);
			}

			KList<String> lore = new KList<>();

			getLore().forEach((i) ->
			{
				String mf = C.translateAlternateColorCodes('&', i);

				if(mf.length() > 24)
				{
					for(String g : Form.wrapWords(mf, 24).split("\\Q\n\\E"))
					{
						lore.add(g.trim());
					}
				}

				else
				{
					lore.add(mf);
				}
			});

			if(debug)
			{
				if(lore.isNotEmpty())
				{
					lore.add(C.GRAY + "--------------------");
				}

				lore.add(C.GRAY + "1 in " + (getRarity()) + " Chance (" + Form.pc(1D / (getRarity()), 5) + ")");
			}

			m.setLore(lore);

			if(getLeatherColor() != null && m instanceof LeatherArmorMeta)
			{
				Color c = Color.decode(getLeatherColor());
				((LeatherArmorMeta) m).setColor(org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()));
			}

			if(getDyeColor() != null && m instanceof Colorable)
			{
				((Colorable) m).setColor(getDyeColor());
			}

			is.setItemMeta(m);
			return is;
		}

		catch(Throwable e)
		{

		}

		return new ItemStack(Material.AIR);
	}

	public ItemStack get(boolean debug, boolean giveSomething, IrisLootTable table, RNG rng, int x, int y, int z)
	{
		if(debug)
		{
			chance.reset();
		}

		if(giveSomething || chance.aquire(() -> NoiseStyle.STATIC.create(rng)).fit(1, rarity * table.getRarity(), x, y, z) == 1)
		{
			if(getType() == null)
			{
				Iris.warn("Cant find item type " + type);
				return null;
			}

			try
			{
				ItemStack is = new ItemStack(getType(), Math.max(1, rng.i(getMinAmount(), getMaxAmount())));
				ItemMeta m = is.getItemMeta();

				if(getType().getMaxDurability() > 0 && m instanceof Damageable)
				{
					Damageable d = (Damageable) m;
					int max = getType().getMaxDurability();
					d.setDamage((int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(getMinDurability(), getMaxDurability())) * max))));
				}

				for(IrisEnchantment i : getEnchantments())
				{
					i.apply(rng, m);
				}

				for(IrisAttributeModifier i : getAttributes())
				{
					i.apply(rng, m);
				}

				if(Iris.customModels)
				{
					m.setCustomModelData(getCustomModel());
				}

				m.setLocalizedName(C.translateAlternateColorCodes('&', displayName));
				m.setDisplayName(C.translateAlternateColorCodes('&', displayName));
				m.setUnbreakable(isUnbreakable());

				for(ItemFlag i : getItemFlags())
				{
					m.addItemFlags(i);
				}

				KList<String> lore = new KList<>();

				getLore().forEach((i) ->
				{
					String mf = C.translateAlternateColorCodes('&', i);

					if(mf.length() > 24)
					{
						for(String g : Form.wrapWords(mf, 24).split("\\Q\n\\E"))
						{
							lore.add(g.trim());
						}
					}

					else
					{
						lore.add(mf);
					}
				});

				if(debug)
				{
					if(lore.isNotEmpty())
					{
						lore.add(C.GRAY + "--------------------");
					}

					lore.add(C.GRAY + "From: " + table.getName() + " (" + Form.pc(1D / table.getRarity(), 5) + ")");
					lore.add(C.GRAY + "1 in " + (table.getRarity() * getRarity()) + " Chance (" + Form.pc(1D / (table.getRarity() * getRarity()), 5) + ")");
				}

				m.setLore(lore);
				is.setItemMeta(m);
				return is;
			}

			catch(Throwable e)
			{

			}
		}

		return null;
	}
}

package com.volmit.iris.manager.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.nms.INMS;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.IRegistryWritable;
import net.minecraft.data.RegistryGeneration;
import net.minecraft.data.worldgen.biome.BiomeRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.CommandLocateBiome;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.biome.BiomeBase;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Map;

public class CommandIrisWhatBiome extends MortarCommand
{
	public CommandIrisWhatBiome()
	{
		super("biome", "bi", "b");
		setDescription("Get the biome data you are in.");
		requiresPermission(Iris.perm.studio);
		setCategory("Wut");
		setDescription("What biome am I in");
	}

	@Override
	public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

	}

	@Override
	public boolean handle(MortarSender sender, String[] args)
	{
		if(sender.isPlayer())
		{
			Player p = sender.player();
			World w = p.getWorld();

			try
			{

				IrisAccess g = IrisWorlds.access(w);
				assert g != null;
				IrisBiome b = g.getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
				sender.sendMessage("IBiome: " + b.getLoadKey() + " (" + b.getDerivative().name() + ")");

			}

			catch(Throwable e)
			{
				sender.sendMessage("Non-Iris Biome: " + p.getLocation().getBlock().getBiome().name());

				if(p.getLocation().getBlock().getBiome().equals(Biome.CUSTOM))
				{
					try
					{
						sender.sendMessage("Data Pack Biome: " + INMS.get().getTrueBiomeBaseKey(p.getLocation()) + " (ID: " + INMS.get().getTrueBiomeBaseId(INMS.get().getTrueBiomeBase(p.getLocation())) + ")");
					}

					catch(Throwable ex)
					{

					}
				}
			}
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	@Override
	protected String getArgsUsage()
	{
		return "";
	}
}

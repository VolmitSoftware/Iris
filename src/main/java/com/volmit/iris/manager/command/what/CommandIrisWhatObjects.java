package com.volmit.iris.manager.command.what;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.util.*;
import io.papermc.lib.PaperLib;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

public class CommandIrisWhatObjects extends MortarCommand
{
	public CommandIrisWhatObjects()
	{
		super("objects", "o", "obj", "capture", "capt");
		setDescription("Capture nearby information to help with reporting problems");
		requiresPermission(Iris.perm.studio);
		setCategory("World");
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
			World world = p.getWorld();

			if(!IrisWorlds.isIrisWorld(world))
			{
				sender.sendMessage("You must be in an iris world.");
				return true;
			}

			IrisAccess g = IrisWorlds.access(world);
			KList<Chunk> chunks = new KList<>();
			int bx = p.getLocation().getChunk().getX();
			int bz = p.getLocation().getChunk().getZ();

			try
			{
				Location l = p.getTargetBlockExact(48, FluidCollisionMode.NEVER).getLocation();

				if(l != null)
				{
					int cx = l.getChunk().getX();
					int cz = l.getChunk().getZ();
					new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + cx, z + cz))).drain();
				}
			}

			catch(Throwable e)
			{

			}

			new Spiraler(3, 3, (x, z) -> chunks.addIfMissing(world.getChunkAt(x + bx, z + bz))).drain();
			sender.sendMessage("Capturing IGenData from " + chunks.size() + " nearby chunks.");
			try
			{
				File ff = Iris.instance.getDataFile("reports/" + M.ms() + ".txt");
				PrintWriter pw = new PrintWriter(ff);
				pw.println("=== Iris Chunk Report ===");
				pw.println("== General Info ==");
				pw.println("Iris Version: " + Iris.instance.getDescription().getVersion());
				pw.println("Bukkit Version: " + Bukkit.getBukkitVersion());
				pw.println("MC Version: " + Bukkit.getVersion());
				pw.println("PaperSpigot: " + (PaperLib.isPaper() ? "Yup!" : "Nope!"));
				pw.println("Report Captured At: " + new Date().toString());
				pw.println("Chunks: (" + chunks.size() + "): ");

				for(Chunk i : chunks)
				{
					pw.println("- [" + i.getX() + ", " + i.getZ() + "]");
				}

				int regions = 0;
				long size = 0;
				String age = "No idea...";

				try
				{
					for(File i : Objects.requireNonNull(new File(world.getWorldFolder(), "region").listFiles()))
					{
						if(i.isFile())
						{
							size += i.length();
						}
					}
				}

				catch(Throwable ignored)
				{

				}

				try
				{
					FileTime creationTime = (FileTime) Files.getAttribute(world.getWorldFolder().toPath(), "creationTime");
					age = hrf(Duration.of(M.ms() - creationTime.toMillis(), ChronoUnit.MILLIS));
				}
				catch(IOException ignored)
				{

				}

				KList<String> biomes = new KList<>();
				KList<String> caveBiomes = new KList<>();
				KMap<String, KMap<String, KList<String>>> objects = new KMap<>();

				for(Chunk i : chunks)
				{
					for(int j = 0; j < 16; j += 3)
					{

						for(int k = 0; k < 16; k += 3)
						{

							assert g != null;
							IrisBiome bb = g.getBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
							IrisBiome bxf = g.getCaveBiome((i.getX() * 16) + j, (i.getZ() * 16) + k);
							biomes.addIfMissing(bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")");
							caveBiomes.addIfMissing(bxf.getName() + " (" + bxf.getLoadFile().getName() + ")");
							exportObjects(bb, pw, g, objects);
							exportObjects(bxf, pw, g, objects);
						}
					}
				}

				regions = Objects.requireNonNull(new File(world.getWorldFolder().getPath() + "/region").list()).length;

				pw.println();
				pw.println("== World Info ==");
				pw.println("World Name: " + world.getName());
				pw.println("Age: " + age);
				pw.println("Folder: " + world.getWorldFolder().getPath());
				pw.println("Regions: " + Form.f(regions));
				pw.println("Chunks: max. " + Form.f(regions * 32 * 32));
				pw.println("World Size: min. " + Form.fileSize(size));
				pw.println();
				pw.println("== Biome Info ==");
				pw.println("Found " + biomes.size() + " Biome(s): ");

				for(String i : biomes)
				{
					pw.println("- " + i);
				}
				pw.println();

				pw.println("== Object Info ==");

				for(String i : objects.k())
				{
					pw.println("- " + i);

					for(String j : objects.get(i).k())
					{
						pw.println("  @ " + j);

						for(String k : objects.get(i).get(j))
						{
							pw.println("    * " + k);
						}
					}
				}

				pw.println();
				pw.close();

				sender.sendMessage("Reported to: " + ff.getPath());
			}

			catch(FileNotFoundException e)
			{
				e.printStackTrace();
			}

			return true;
		}

		else
		{
			sender.sendMessage("Players only.");
		}

		return true;
	}

	private void exportObjects(IrisBiome bb, PrintWriter pw, IrisAccess g, KMap<String, KMap<String, KList<String>>> objects)
	{
		String n1 = bb.getName() + " [" + Form.capitalize(bb.getInferredType().name().toLowerCase()) + "] " + " (" + bb.getLoadFile().getName() + ")";
		int m = 0;
		KSet<String> stop = new KSet<>();
		for(IrisObjectPlacement f : bb.getObjects())
		{
			m++;
			String n2 = "Placement #" + m + " (" + f.getPlace().size() + " possible objects)";

			for(String i : f.getPlace())
			{
				String nn3 = i + ": [ERROR] Failed to find object!";

				try
				{
					if(stop.contains(i))
					{
						continue;
					}

					File ff = g.getData().getObjectLoader().findFile(i);
					BlockVector sz = IrisObject.sampleSize(ff);
					nn3 = i + ": size=[" + sz.getBlockX() + "," + sz.getBlockY() + "," + sz.getBlockZ() + "] location=[" + ff.getPath() + "]";
					stop.add(i);
				}

				catch(Throwable ignored)
				{

				}

				String n3 = nn3;

				objects.compute(n1, (k1, v1) ->
				{
					if(v1 == null)
					{
						return new KMap<>();
					}

					return v1;
				}).compute(n2, (k, v) ->
				{
					if(v == null)
					{
						return new KList<String>().qaddIfMissing(n3);
					}

					v.addIfMissing(n3);
					return v;
				});
			}
		}
	}

	public static String hrf(Duration duration)
	{
		return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
	}

	@Override
	protected String getArgsUsage()
	{
		return "[thread-count]";
	}
}

package com.volmit.iris.manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.bukkit.Bukkit;

import com.minelazz.epicworldgenerator.structures.StructureObject;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.util.Converter;
import com.volmit.iris.util.FastBlockData;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarSender;

public class ConversionManager
{
	private KList<Converter> converters;
	private File folder;

	public ConversionManager()
	{
		folder = Iris.instance.getDataFolder("convert");
		converters = new KList<>();

		J.s(() ->
		{
			if(Bukkit.getPluginManager().isPluginEnabled("WorldEdit"))
			{
				converters.add(new Converter()
				{
					@Override
					public String getOutExtension()
					{
						return "iob";
					}

					@Override
					public String getInExtension()
					{
						return "schem";
					}

					@Override
					public void convert(File in, File out)
					{
						convertSchematic(in, out);
					}
				});

				converters.add(new Converter()
				{
					@Override
					public String getOutExtension()
					{
						return "iob";
					}

					@Override
					public String getInExtension()
					{
						return "schematic";
					}

					@Override
					public void convert(File in, File out)
					{
						convertSchematic(in, out);
					}
				});
			}
		}, 5);

		converters.add(new Converter()
		{
			@Override
			public String getOutExtension()
			{
				return "iob";
			}

			@Override
			public String getInExtension()
			{
				return "ewg";
			}

			@Override
			public void convert(File in, File out)
			{
				try
				{
					StructureObject.convert(in).write(out);
				}

				catch(ClassNotFoundException | IOException e)
				{
					e.printStackTrace();
				}
			}
		});
	}

	public void convertSchematic(File in, File out)
	{
		ClipboardFormat format = ClipboardFormats.findByFile(in);
		try(ClipboardReader reader = format.getReader(new FileInputStream(in)))
		{
			Clipboard clipboard = reader.read();
			BlockVector3 size = clipboard.getMaximumPoint().subtract(clipboard.getMinimumPoint());
			IrisObject o = new IrisObject(size.getBlockX() + 1, size.getBlockY() + 1, size.getBlockZ() + 1);

			for(int i = clipboard.getMinimumPoint().getBlockX(); i <= clipboard.getMaximumPoint().getBlockX(); i++)
			{
				for(int j = clipboard.getMinimumPoint().getBlockY(); j <= clipboard.getMaximumPoint().getBlockY(); j++)
				{
					for(int k = clipboard.getMinimumPoint().getBlockZ(); k <= clipboard.getMaximumPoint().getBlockZ(); k++)
					{
						o.setUnsigned(i - clipboard.getMinimumPoint().getBlockX(), j - clipboard.getMinimumPoint().getBlockY(), k - clipboard.getMinimumPoint().getBlockZ(), FastBlockData.of(BukkitAdapter.adapt(clipboard.getFullBlock(BlockVector3.at(i, j, k)))));
					}
				}
			}

			o.write(out);
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void check(MortarSender s)
	{
		int m = 0;
		Iris.instance.getDataFolder("convert");

		for(File i : folder.listFiles())
		{
			for(Converter j : converters)
			{
				if(i.getName().endsWith("." + j.getInExtension()))
				{
					File out = new File(folder, i.getName().replaceAll("\\Q." + j.getInExtension() + "\\E", "." + j.getOutExtension()));
					m++;
					j.convert(i, out);
					s.sendMessage("Converted " + i.getName() + " -> " + out.getName());
				}
			}
		}

		s.sendMessage("Converted " + m + " File" + (m == 1 ? "" : "s"));
	}
}

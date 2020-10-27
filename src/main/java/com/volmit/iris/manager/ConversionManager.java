package com.volmit.iris.manager;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;

import com.minelazz.epicworldgenerator.structures.StructureObject;
import com.volmit.iris.Iris;
import com.volmit.iris.util.Converter;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarSender;
import com.volmit.iris.util.SKConversion;

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
			J.attemptAsync(() ->
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
							SKConversion.convertSchematic(in, out);
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
							SKConversion.convertSchematic(in, out);
						}
					});
				}
			});
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

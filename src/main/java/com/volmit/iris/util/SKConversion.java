package com.volmit.iris.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.volmit.iris.object.IrisObject;

public class SKConversion
{
	public static void convertSchematic(File in, File out)
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
						o.setUnsigned(i - clipboard.getMinimumPoint().getBlockX(), j - clipboard.getMinimumPoint().getBlockY(), k - clipboard.getMinimumPoint().getBlockZ(), BukkitAdapter.adapt(clipboard.getFullBlock(BlockVector3.at(i, j, k))));
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
}

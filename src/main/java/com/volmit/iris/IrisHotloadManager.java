package com.volmit.iris;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.FolderWatcher;

public class IrisHotloadManager
{
	private ChronoLatch latch;

	private FolderWatcher w;

	public IrisHotloadManager()
	{
		w = new FolderWatcher(Iris.instance.getDataFolder("packs"));
		latch = new ChronoLatch(3000);
	}

	public void check(IrisContext ch)
	{
		if(!latch.flip())
		{
			return;
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			boolean modified = false;
			int c = 0;

			try
			{
				if(w.checkModified())
				{
					for(File i : w.getCreated())
					{
						if(i.isDirectory())
						{
							continue;
						}

						if(i.getPath().contains(".git"))
						{
							continue;
						}

						if(i.getPath().contains("_docs"))
						{
							continue;
						}

						if(i.getName().endsWith(".code-workspace"))
						{
							continue;
						}

						modified = true;
						c++;
						Iris.verbose("File Created: " + i.getPath());
					}

					for(File i : w.getDeleted())
					{
						if(i.isDirectory())
						{
							continue;
						}

						if(i.getPath().contains("_docs"))
						{
							continue;
						}

						if(i.getPath().contains(".git"))
						{
							continue;
						}

						if(i.getName().endsWith(".code-workspace"))
						{
							continue;
						}

						modified = true;
						c++;
						Iris.verbose("File Deleted: " + i.getPath());
					}

					for(File i : w.getChanged())
					{
						if(i.isDirectory())
						{
							continue;
						}

						if(i.getPath().contains(".git"))
						{
							continue;
						}

						if(i.getPath().contains("_docs"))
						{
							continue;
						}

						if(i.getName().endsWith(".code-workspace"))
						{
							continue;
						}

						modified = true;
						c++;
						Iris.verbose("File Modified: " + i.getPath());
					}
				}
			}

			catch(Throwable e)
			{

			}

			if(modified)
			{
				String m = "Hotloaded " + c + " File" + (c == 1 ? "" : "s");

				for(Player i : Bukkit.getOnlinePlayers())
				{
					i.sendMessage(Iris.instance.getTag("Studio") + m);
				}

				Bukkit.getConsoleSender().sendMessage(Iris.instance.getTag("Studio") + m);
				Iris.globaldata.hotloaded();
				ch.onHotloaded();
			}
		});
	}

	public void track(File file)
	{

	}
}

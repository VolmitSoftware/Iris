package com.volmit.iris.manager;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.legacy.scaffold.IrisContext;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.FolderWatcher;
import com.volmit.iris.util.J;

public class IrisHotloadManager
{
	private ChronoLatch latch;
	private volatile boolean busy = false;
	private FolderWatcher w;

	public IrisHotloadManager()
	{
		if(!IrisSettings.get().studio)
		{
			w = null;
		}

		else
		{
			w = new FolderWatcher(Iris.proj.getWorkspaceFolder());
		}

		latch = new ChronoLatch(3000);
	}

	public void check(IrisContext ch)
	{
		if(!IrisSettings.get().isStudio())
		{
			return;
		}

		if(!latch.flip())
		{
			return;
		}

		if(busy)
		{
			return;
		}

		busy = true;
		J.attemptAsync(() ->
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
			int cc = c;
			if(modified)
			{
				J.s(() ->
				{
					String m = "Hotloaded " + cc + " File" + (cc == 1 ? "" : "s");

					for(Player i : Bukkit.getOnlinePlayers())
					{
						i.sendMessage(Iris.instance.getTag("Studio") + m);
					}

					Bukkit.getConsoleSender().sendMessage(Iris.instance.getTag("Studio") + m);
					Iris.globaldata.hotloaded();
					ch.onHotloaded();
					busy = false;
				});
			}

			else
			{
				busy = false;
			}
		});
	}
}

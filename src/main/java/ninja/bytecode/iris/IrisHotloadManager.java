package ninja.bytecode.iris;

import java.io.File;

import org.bukkit.Bukkit;

import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.FileWatcher;
import ninja.bytecode.shuriken.collections.KSet;

public class IrisHotloadManager
{
	private ChronoLatch latch;
	private KSet<FileWatcher> watchers;

	public IrisHotloadManager()
	{
		watchers = new KSet<>();
		latch = new ChronoLatch(3000);
	}

	public void check()
	{
		if(!latch.flip())
		{
			return;
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			boolean modified = false;
			int c = 0;

			for(FileWatcher i : watchers)
			{
				if(i.checkModified())
				{
					c++;
					Iris.info("File Modified: " + i.getFile().getPath());
					modified = true;
				}
			}

			if(modified)
			{
				watchers.clear();
				Iris.success("Hotloading Iris (" + c + " File" + (c == 1 ? "" : "s") + " changed)");
				Iris.data.hotloaded();
			}
		});
	}

	public void track(File file)
	{
		watchers.add(new FileWatcher(file));
	}
}

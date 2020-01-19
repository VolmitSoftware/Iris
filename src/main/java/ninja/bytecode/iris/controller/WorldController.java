package ninja.bytecode.iris.controller;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.CompiledDimension;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.io.IO;

public class WorldController implements IrisController
{
	@Override
	public void onStart()
	{

	}

	public boolean isChunkGenerated(World w, int x, int z)
	{
		return w.loadChunk(x, z, false);
	}

	@Override
	public void onStop()
	{

	}

	public World createIrisWorld(CompiledDimension dimension, long seed, boolean temp)
	{
		if(dimension == null)
		{
			dimension = Iris.getController(PackController.class).getDimension("overworld");
		}

		//@builder
		World ww = Bukkit.createWorld(new WorldCreator("iris-worlds/" + UUID.randomUUID().toString())
				.generator(new IrisGenerator(dimension))
				.environment(dimension.getEnvironment())
				.seed(seed));
		//@done
		ww.setSpawnFlags(false, false);
		ww.setAutoSave(false);
		ww.setKeepSpawnInMemory(false);
		ww.setSpawnLocation(0, 256, 0);

		if(temp)
		{
			File folder = ww.getWorldFolder();
			J.attempt(() -> new File(ww.getWorldFolder(), ".garbage").createNewFile());
			Runtime.getRuntime().addShutdownHook(new Thread()
			{
				@Override
				public void run()
				{
					IO.delete(folder);

					try
					{
						FileUtils.forceDelete(folder);
						System.out.println("Deleted Debug World: " + folder.getName());
					}

					catch(IOException e)
					{
						System.out.println("FAILED TO Delete Debug World: " + folder.getName());
					}
				}
			});
		}

		return ww;
	}
}

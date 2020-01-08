package ninja.bytecode.iris.controller;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.pack.IrisDimension;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.shuriken.execution.J;
import ninja.bytecode.shuriken.io.IO;

public class WorldController implements IrisController
{
	@Override
	public void onStart()
	{
		J.attemptAsync(() ->
		{
			for(File i : new File(Iris.instance.getDataFolder().getParentFile().getParentFile(), "iris-worlds").listFiles())
			{
				if(new File(i, ".garbage").exists())
				{
					IO.delete(i);
				}
			}
		});
	}

	@Override
	public void onStop()
	{

	}

	public World createIrisWorld(IrisDimension dimension, long seed, boolean temp)
	{
		if(dimension == null)
		{
			dimension = Iris.getController(PackController.class).getDimensions().get("overworld");
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
			J.attempt(() -> new File(ww.getWorldFolder(), ".garbage").createNewFile());
		}

		return ww;
	}
}

package ninja.bytecode.iris;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import ninja.bytecode.shuriken.execution.TaskExecutor;

public class Iris extends JavaPlugin
{
	public static TaskExecutor executor;
	
	public void onEnable()
	{
		executor = new TaskExecutor(-1, Thread.MIN_PRIORITY, "Iris Generator");
	}
	
	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator();
	}
}

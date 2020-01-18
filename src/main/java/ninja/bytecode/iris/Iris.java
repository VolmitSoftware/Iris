package ninja.bytecode.iris;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.IrisController;
import ninja.bytecode.iris.util.IrisControllerSet;
import ninja.bytecode.shuriken.logging.L;

public class Iris extends JavaPlugin implements Listener
{
	public IrisControllerSet controllerSet;
	public static Thread primaryThread;
	public static Settings settings;
	public static Iris instance;

	public void onEnable()
	{
		primaryThread = Thread.currentThread();
		instance = this;
		controllerSet = new IrisControllerSet();
		L.consoleConsumer = (s) -> Bukkit.getConsoleSender().sendMessage(s);

		try
		{
			controllerSet.startControllers(getFile());
		}

		catch(IOException e)
		{
			L.ex(e);
		}

		L.i("Controllers: " + controllerSet.size());

		Direction.calculatePermutations();
		settings = new Settings();
		getServer().getPluginManager().registerEvents((Listener) this, this);
		getCommand("iris").setExecutor(new CommandIris());
		getCommand("ish").setExecutor(new CommandIsh());

		if(!settings.performance.debugMode)
		{
			getController(PackController.class).compile();
		}
	}

	public void onDisable()
	{
		controllerSet.stopControllers();
		HandlerList.unregisterAll((Plugin) this);
		Bukkit.getScheduler().cancelTasks(this);
	}

	public void reload()
	{
		Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
		{
			onDisable();
			onEnable();
		});
	}

	@SuppressWarnings("unchecked")
	public static <T extends IrisController> T getController(Class<? extends T> c)
	{
		return (T) instance.controllerSet.get(c);
	}

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator();
	}
}

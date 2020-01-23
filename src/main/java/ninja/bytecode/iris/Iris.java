package ninja.bytecode.iris;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import mortar.bukkit.command.Command;
import mortar.bukkit.plugin.Control;
import mortar.bukkit.plugin.Instance;
import mortar.bukkit.plugin.MortarPlugin;
import mortar.util.text.C;
import ninja.bytecode.iris.command.CommandIris;
import ninja.bytecode.iris.controller.ExecutionController;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.shuriken.logging.L;

public class Iris extends MortarPlugin
{
	public static Thread primaryThread;
	public static Settings settings;
	public static IrisMetrics metrics;

	@Instance
	public static Iris instance;

	@Control
	private ExecutionController executionController;

	@Control
	private PackController packController;

	@Control
	private WandController wandController;

	@Command
	private CommandIris commandIris;

	@Override
	public void start()
	{
		primaryThread = Thread.currentThread();
		instance = this;
		L.consoleConsumer = (s) -> Bukkit.getConsoleSender().sendMessage(s);
		Direction.calculatePermutations();
		settings = new Settings();
		getServer().getPluginManager().registerEvents((Listener) this, this);
		packController.compile();
	}

	@Override
	public void stop()
	{
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

	@Override
	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id)
	{
		return new IrisGenerator();
	}

	@Override
	public String getTag(String arg0)
	{
		return makeTag(C.GREEN, C.DARK_GRAY, C.GRAY, C.BOLD + "Iris" + C.RESET);
	}

	public static String makeTag(C brace, C tag, C text, String tagName)
	{
		return brace + "\u3008" + tag + tagName + brace + "\u3009" + " " + text;
	}

	public static PackController pack()
	{
		return instance.packController;
	}

	public static ExecutionController exec()
	{
		return instance.executionController;
	}

	public static WandController wand()
	{
		return instance.wandController;
	}
}

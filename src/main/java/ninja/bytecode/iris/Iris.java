package ninja.bytecode.iris;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import mortar.api.rift.Rift;
import mortar.api.sched.J;
import mortar.bukkit.command.Command;
import mortar.bukkit.plugin.Control;
import mortar.bukkit.plugin.Instance;
import mortar.bukkit.plugin.Mortar;
import mortar.bukkit.plugin.MortarPlugin;
import mortar.lib.control.RiftController;
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
	private ExecutionController executionController;

	@Instance
	public static Iris instance;

	@Control
	private PackController packController;

	@Control
	private WandController wandController;

	@Command
	private CommandIris commandIris;

	private Rift r;

	@Override
	public void onEnable()
	{
		instance = this;
		executionController = new ExecutionController();
		executionController.start();
		super.onEnable();
	}

	public File getObjectCacheFolder()
	{
		return getDataFolder("cache", "object");
	}

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

		J.s(() ->
		{
			if(settings.performance.debugMode)
			{
				try
				{
					r = Mortar.getController(RiftController.class).createRift("iris/" + UUID.randomUUID().toString());
					r.setGenerator(IrisGenerator.class);
					r.setDifficulty(Difficulty.NORMAL);
					r.setTemporary(true);
					r.setSeed(0);
					r.setViewDistance(10);
					r.setTileTickLimit(0.1);
					r.setEntityTickLimit(0.1);
					r.setPhysicsThrottle(5);
					r.setMonsterActivationRange(5);
					r.setArrowDespawnRate(1);
					r.load();

					for(Player i : Bukkit.getOnlinePlayers())
					{
						i.teleport(r.getSpawn());
					}
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}
		}, 10);
	}

	@Override
	public void stop()
	{
		if(settings.performance.debugMode && r != null)
		{
			r.colapse();
		}

		HandlerList.unregisterAll((Plugin) this);
		Bukkit.getScheduler().cancelTasks(this);
		executionController.stop();
	}

	@EventHandler
	public void on(PlayerJoinEvent e)
	{
		if(settings.performance.debugMode && r != null)
		{
			e.getPlayer().teleport(r.getSpawn());
		}
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

package ninja.bytecode.iris;

import java.io.File;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import mortar.api.rift.PhantomRift;
import mortar.api.rift.Rift;
import mortar.api.rift.RiftException;
import mortar.bukkit.command.Command;
import mortar.bukkit.plugin.Control;
import mortar.bukkit.plugin.MortarPlugin;
import mortar.util.text.C;
import ninja.bytecode.iris.command.CommandIris;
import ninja.bytecode.iris.controller.ExecutionController;
import ninja.bytecode.iris.controller.PackController;
import ninja.bytecode.iris.controller.WandController;
import ninja.bytecode.iris.generator.IrisGenerator;
import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.HotswapGenerator;
import ninja.bytecode.iris.util.IrisMetrics;
import ninja.bytecode.shuriken.logging.L;

public class Iris extends MortarPlugin
{
	public static Thread primaryThread;
	public static Settings settings;
	public static IrisMetrics metrics;
	private static ExecutionController executionController;

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
		primaryThread = Thread.currentThread();
		L.consoleConsumer = (s) -> Bukkit.getConsoleSender().sendMessage(s);
		Direction.calculatePermutations();
		settings = new Settings();
		getServer().getPluginManager().registerEvents((Listener) this, this);
		super.onEnable();
	}

	public File getObjectCacheFolder()
	{
		return getDataFolder("cache", "object");
	}

	public static boolean isGen(World world)
	{
		IrisGenerator g = getGen(world);
		return g != null;
	}

	public static IrisGenerator getGen(World world)
	{
		try
		{
			return (IrisGenerator) ((HotswapGenerator) world.getGenerator()).getGenerator();
		}

		catch(Throwable e)
		{

		}

		return null;
	}

	@Override
	public void start()
	{
		instance = this;
		packController.compile();

		if(Iris.settings.performance.debugMode)
		{
			try
			{
				//@builder
				r = new PhantomRift("Iris-Debug/" + UUID.randomUUID().toString())
						.setTileTickLimit(0.1)
						.setEntityTickLimit(0.1)
						.setAllowBosses(false)
						.setEnvironment(Environment.NORMAL)
						.setDifficulty(Difficulty.PEACEFUL)
						.setRandomLightUpdates(false)
						.setViewDistance(32)
						.setHangingTickRate(2000)
						.setGenerator(IrisGenerator.class)
						.load();
				
				for(Player i : Bukkit.getOnlinePlayers())
				{
					r.send(i);
				}
				//@done
			}

			catch(RiftException e)
			{
				e.printStackTrace();
			}
		}
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
		return new HotswapGenerator(new IrisGenerator());
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
		if(executionController == null)
		{
			executionController = new ExecutionController();
			executionController.start();
		}

		return executionController;
	}

	public static WandController wand()
	{
		return instance.wandController;
	}
}

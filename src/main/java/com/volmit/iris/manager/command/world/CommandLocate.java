package com.volmit.iris.manager.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;

public class CommandLocate extends MortarCommand implements Listener
{
    CommandLocate instance;
    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (IrisWorlds.isIrisWorld(event.getPlayer().getWorld())){

            // Make sure the command starts with /locate and does not locate stronghold
            if (event.getMessage().contains("/locate") && event.getMessage().contains("stronghold")){
                return;
            }
            if (event.getMessage().contains("/locate")) {
                event.setCancelled(true); // Cancel the vanilla command process
                String command = event.getMessage().replace("/locate", "ir std goto");
                Bukkit.dispatchCommand(event.getPlayer(), command);
            }
        }
    }

    public CommandLocate()
    {
        super("locate");
        requiresPermission(Iris.perm);
        this.instance = this;
    }

    @Override
    public boolean handle(MortarSender sender, String[] args)
    {
        Bukkit.dispatchCommand(sender, "/ir std goto " + Arrays.toString(args));
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage()
    {
        return "[biome/region/structure]";
    }
}

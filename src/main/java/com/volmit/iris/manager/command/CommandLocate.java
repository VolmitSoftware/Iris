package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandLocate extends MortarCommand implements Listener
{
    @EventHandler
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().contains("stronghold") && event.getMessage().contains("locate") && IrisWorlds.isIrisWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("/locate command blocked in Iris worlds. Please use '/ir std goto' instead. You can /locate stronghold!");
        }
    }

    public CommandLocate()
    {
        super("locate");
        requiresPermission(Iris.perm);
    }

    @Override
    public boolean handle(MortarSender sender, String[] args)
    {
        sender.sendMessage("Locate command");
        if(sender.isPlayer()) {
            Player p = sender.player();
            World world = p.getWorld();
            if (!IrisWorlds.isIrisWorld(world)) {
                String cmd = "locate";
                for (int i = 0; i < args.length; i++){
                    cmd.concat(" " + args[i]);
                }
                Bukkit.dispatchCommand(sender, cmd);
                return true;
            }
            sender.sendMessage("You are in an Iris world and the /locate command is currently disabled in those.");
        } else {
            sender.sendMessage("Players only");
        }
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage()
    {
        return "";
    }
}

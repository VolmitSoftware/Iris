package com.volmit.iris.core;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.location.IrisLocation;
import com.volmit.iris.engine.object.location.IrisLocations;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.StructureType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;
import java.util.stream.Stream;

public class LocationManager implements Listener {

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e){

        if (e.isCancelled()){
            return;
        }

        if (!e.getMessage().contains("locate")){
            return;
        }

        if (!IrisToolbelt.isIrisWorld(e.getPlayer().getWorld())){
            return;
        }

        IrisLocations locations = IrisToolbelt.access(e.getPlayer().getWorld()).getCompound().getRootDimension().getLocations();
        VolmitSender sender = new VolmitSender(e.getPlayer());

        // The command should not be passed along anymore
        e.setCancelled(true);

        if (locations.isPreventLocate()){
            sender.sendMessage("/locate is disabled in this Iris Dimension");
            return;
        }

        StructureType type = getTypeFromCommand(e.getMessage());
        final Stream<IrisLocation> locationStream = locations.getLocations().stream();

        if (type == null){
            sender.sendMessage("That structure does not exist: " + e.getMessage().split(" ")[1]);
            sender.sendMessage("Options: " + locationStream);
            return;
        } else if (locationStream.noneMatch(l -> l.getType().equals(type))){
            sender.sendMessage("The " + type.getName() + " structure exists in vanilla, but is not defined in this Dimension");
            sender.sendMessage("Options: " + locationStream);
            return;
        }

        Optional<IrisLocation> hit = locationStream.filter(l -> l.getType().equals(type)).findFirst();
        if (hit.isPresent()){
            sender.sendMessage("Objects matching " + type.getName() + " are " + hit.get().getObjects().toString() + ".");
            sender.sendMessage("The locations of those objects are unknown, this functionality will be implemented later by Cyberpwn");
        } else {
            sender.sendMessage("No objects matching type " + type.getName() + " despite a list being defined");
        }
    }

    /**
     * Returns the StructureType from a command if it exists, otherwise it returns null
     * @param command The command to search
     * @return The StructureType
     */
    private StructureType getTypeFromCommand(String command){
        return StructureType.getStructureTypes().get(command.split(" ")[1]);
    }
}

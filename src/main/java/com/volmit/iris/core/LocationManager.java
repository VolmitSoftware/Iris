package com.volmit.iris.core;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.location.IrisLocations;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LocationManager implements Listener {

    // Every world has a map from StructureType to Locations
    public static KMap<World, KMap<StructureType, KList<Location>>> locations;

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e){

        getTypeFromCommand(e.getMessage());

        if (!e.getMessage().contains("locate")){
            return;
        }

        if (!IrisToolbelt.isIrisWorld(e.getPlayer().getWorld())){
            return;
        }

        IrisLocations locations = IrisToolbelt.access(e.getPlayer().getWorld()).getCompound().getRootDimension().getLocations();

        if (locations.isPreventLocate()){
            e.setCancelled(true);
            VolmitSender sender = new VolmitSender(e.getPlayer());
            sender.sendMessage("/locate is disabled in this Iris Dimension");
            return;
        }



        // TODO: Override locations with the preset ones
    }

    /**
     * Check if a location exists in a
     * @param world world of a
     * @param type structureType
     * @return true if locations exist, false if not
     */
    public boolean hasLocationIn(World world, StructureType type){
        return locations.get(world).get(type).isNotEmpty();
    }

    /**
     * Get a location in a
     * @param world world of a
     * @param type structureType
     * @return a KList with locations
     */
    public KList<Location> getLocationIn(World world, StructureType type){
        KList<Location> lcs = locations.get(world).get(type);
        return lcs.isEmpty() ? null : lcs;
    }

    private StructureType getTypeFromCommand(String command){
        String structureName = command.split(" ")[1];
        Iris.info(structureName);
        Iris.info(command);
        return StructureType.STRONGHOLD;
    }
}

package com.volmit.iris.core.service;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.IrisService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;

public class LogFilterSVC implements IrisService, Filter {

    private static final String HEIGHTMAP_MISMATCH = "Ignoring heightmap data for chunk";
    private static final String RAID_PERSISTENCE = "Could not save data net.minecraft.world.entity.raid.PersistentRaid";
    private static final String DUPLICATE_ENTITY_UUID = "UUID of added entity already exists";

    private static final KList<String> FILTERS = new KList<>();

    public void onEnable() {
        FILTERS.add(HEIGHTMAP_MISMATCH, RAID_PERSISTENCE, DUPLICATE_ENTITY_UUID);
        ((Logger) LogManager.getRootLogger()).addFilter(this);
    }

    public void initialize() {
    }

    public void start() {
    }

    public void stop() {
    }

    public void onDisable() {
    }

    public boolean isStarted() {
        return true;
    }

    public boolean isStopped() {
        return false;
    }

    public State getState() {
        try {
            return State.STARTED;
        } catch (Exception var2) {
            return null;
        }
    }

    public Filter.Result getOnMatch() {
        return Result.NEUTRAL;
    }

    public Filter.Result getOnMismatch() {
        return Result.NEUTRAL;
    }

    public Result filter(LogEvent event) {
        return check(event.getMessage().getFormattedMessage());
    }

    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return check(msg.toString());
    }

    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return check(msg.getFormattedMessage());
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        return check(message);
    }

    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        return check(message);
    }

    private Result check(String string) {
        if (FILTERS.stream().anyMatch(string::contains))
            return Result.DENY;
        return Result.NEUTRAL;
    }
}

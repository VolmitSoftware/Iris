-keep class art.arcane.iris.api.** { *; }
-keep class art.arcane.iris.world.event.** { *; }
-keep class art.arcane.iris.spi.** { *; }
-keep class art.arcane.iris.command.handlers.** { *; }
-keep class art.arcane.iris.command.context.** { *; }
-keep class art.arcane.iris.command.specialhandlers.** { *; }
-keep @art.arcane.iris.generation.mantle.ComponentFlag class * { *; }
-keep class art.arcane.volmlib.integration.** { *; }
-keep class * implements art.arcane.volmlib.integration.IntegrationServiceContract { *; }
-keep class * implements art.arcane.volmlib.integration.ReloadAware { *; }
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}

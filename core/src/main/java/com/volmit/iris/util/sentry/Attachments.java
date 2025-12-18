package com.volmit.iris.util.sentry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.iris.core.safeguard.IrisSafeguard;
import com.volmit.iris.util.collection.KMap;
import io.sentry.Attachment;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

public class Attachments {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Attachment PLUGINS = jsonProvider(Attachments::plugins, "plugins.json");
    public static final Attachment SAFEGUARD = jsonProvider(IrisSafeguard::asAttachment, "safeguard.json");

    public static Attachment json(Object object, String name) {
        return new Attachment(GSON.toJson(object).getBytes(StandardCharsets.UTF_8), name, "application/json", "event.attachment", true);
    }

    public static Attachment jsonProvider(Callable<Object> object, String name) {
        return new Attachment(() -> GSON.toJson(object.call()).getBytes(StandardCharsets.UTF_8), name, "application/json", "event.attachment", true);
    }

    private static KMap<String, Object> plugins() {
        KMap<String, String> enabled = new KMap<>();
        KMap<String, String> disabled = new KMap<>();

        var pm = Bukkit.getPluginManager();
        for (var plugin : pm.getPlugins()) {
            if (plugin.isEnabled()) {
                enabled.put(plugin.getName(), plugin.getDescription().getVersion());
            } else {
                disabled.put(plugin.getName(), plugin.getDescription().getVersion());
            }
        }

        return new KMap<String, Object>()
                .qput("enabled", enabled)
                .qput("disabled", disabled);
    }
}

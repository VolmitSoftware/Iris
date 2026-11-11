package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Iris's message catalog. The English text lives in {@value #ENGLISH_RESOURCE}; the message classes name its ids as
 * constants so callers stay checked at compile time. A constant whose id is missing from that file, or has another
 * shape there, fails when its class loads.
 */
public final class IrisMessages {
    static final String ENGLISH_RESOURCE = "/messages/en_US.json";
    private static final String PLURAL_SELECTOR_FIELD = "selector";
    private static final MessageCatalog CATALOG = createCatalog();

    public static final TextKey COMMAND_PERMISSION_DENIED = text("iris.command.permission_denied");
    public static final TextKey COMMAND_UNKNOWN = text("iris.command.unknown");
    public static final TextKey COMMAND_PLAYER_ONLY = text("iris.command.player_only");
    public static final TextKey COMMAND_IRIS_WORLD_REQUIRED = text("iris.command.iris_world_required");
    public static final TextKey COMMAND_RELOAD_SUCCESS = text("iris.command.reload.success");
    public static final TextKey COMMAND_RELOAD_FAILED = text("iris.command.reload.failed");
    public static final TextKey STUDIO_MAP_READY = text("iris.studio.map_ready");
    public static final TextKey STUDIO_MAP_FAILED = text("iris.studio.map_failed");
    public static final PluralKey STUDIO_MODULES_HEADER = plural("iris.studio.modules.header");
    public static final TextKey STUDIO_MODULES_ENTRY = text("iris.studio.modules.entry");
    public static final TextKey STUDIO_MODULES_NONE = text("iris.studio.modules.none");
    public static final TextKey STUDIO_MODULES_FAILED = text("iris.studio.modules.failed");
    public static final TextKey MODDED_HELP_UNKNOWN_SECTION = text("iris.modded.help.unknown_section");
    public static final TextKey MODDED_HELP_BACK_HOVER = text("iris.modded.help.back_hover");
    public static final TextKey MODDED_HELP_COMMAND_PARAMETER = text("iris.modded.help.command_parameter");
    public static final TextKey MODDED_HELP_BRIGADIER_TEXT = text("iris.modded.help.brigadier_text");
    public static final TextKey MODDED_HELP_OPERATOR_NOTICE = text("iris.modded.help.operator_notice");
    public static final TextKey MODDED_HELP_OPERATOR_INSTRUCTION = text("iris.modded.help.operator_instruction");
    public static final TextKey MODDED_PROPERTIES_NONE = text("iris.modded.command.properties.none");
    public static final TextKey MODDED_PROPERTIES = text("iris.modded.command.properties.list");

    private IrisMessages() {
    }

    public static MessageCatalog catalog() {
        return CATALOG;
    }

    public static MessageKey require(String id) {
        return CATALOG.require(id);
    }

    static TextKey text(String id) {
        return declared(id, TextKey.class);
    }

    static LinesKey lines(String id) {
        return declared(id, LinesKey.class);
    }

    static PluralKey plural(String id) {
        return declared(id, PluralKey.class);
    }

    private static <T extends MessageKey> T declared(String id, Class<T> shape) {
        MessageKey key = CATALOG.key(id);
        if (!shape.isInstance(key)) {
            throw new IllegalStateException("Message " + id + " is not " + shape.getSimpleName() + " in " + ENGLISH_RESOURCE
                    + (key == null ? " (missing)" : " (it is " + key.getClass().getSimpleName() + ")"));
        }
        return shape.cast(key);
    }

    private static MessageCatalog createCatalog() {
        MessageCatalog.Builder builder = MessageCatalog.builder(VolmitLocales.ENGLISH);
        builder.addAll(DirectorMessages.keys());
        for (Map.Entry<String, JsonElement> message : readEnglishMessages().entrySet()) {
            builder.add(englishKey(message.getKey(), message.getValue()));
        }
        return builder.build();
    }

    private static JsonObject readEnglishMessages() {
        InputStream input = IrisMessages.class.getResourceAsStream(ENGLISH_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing English message resource " + ENGLISH_RESOURCE);
        }
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("messages");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + ENGLISH_RESOURCE, e);
        }
    }

    private static MessageKey englishKey(String id, JsonElement value) {
        if (value.isJsonArray()) {
            return LinesKey.of(id, readLines(value.getAsJsonArray()));
        }
        if (value.isJsonObject()) {
            return readPlural(id, value.getAsJsonObject());
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return TextKey.of(id, value.getAsString());
        }
        throw new IllegalStateException("Message " + id + " in " + ENGLISH_RESOURCE + " must be text, lines or plural forms");
    }

    private static List<String> readLines(JsonArray array) {
        List<String> lines = new ArrayList<>(array.size());
        for (JsonElement line : array) {
            lines.add(line.getAsString());
        }
        return lines;
    }

    private static PluralKey readPlural(String id, JsonObject object) {
        JsonElement selector = object.get(PLURAL_SELECTOR_FIELD);
        if (selector == null) {
            throw new IllegalStateException("Plural message " + id + " in " + ENGLISH_RESOURCE + " names no "
                    + PLURAL_SELECTOR_FIELD);
        }
        Map<String, String> forms = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> form : object.entrySet()) {
            if (!form.getKey().equals(PLURAL_SELECTOR_FIELD)) {
                forms.put(form.getKey(), form.getValue().getAsString());
            }
        }
        return PluralKey.of(id, selector.getAsString(), forms);
    }
}

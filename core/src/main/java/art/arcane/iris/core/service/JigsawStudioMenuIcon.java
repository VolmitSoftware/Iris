package art.arcane.iris.core.service;

import com.volmit.resource.ResourceId;

import java.util.Locale;

/** The items the studio menus draw, named as the platform names them. */
enum JigsawStudioMenuIcon {
    ARROW,
    BARRIER,
    BLUE_DYE,
    BOOK,
    CHEST,
    CLOCK,
    COMPARATOR,
    COPPER_INGOT,
    CRAFTING_TABLE,
    EMERALD,
    EMERALD_BLOCK,
    ENDER_EYE,
    GOLD_INGOT,
    GOLD_NUGGET,
    GRAY_DYE,
    GRAY_WOOL,
    GREEN_DYE,
    IRON_BARS,
    IRON_INGOT,
    JIGSAW,
    LAVA_BUCKET,
    LEVER,
    LIGHT_BLUE_DYE,
    LIGHT_GRAY_WOOL,
    LIME_DYE,
    LIME_WOOL,
    NAME_TAG,
    NETHER_STAR,
    ORANGE_WOOL,
    PAPER,
    PURPLE_DYE,
    RECOVERY_COMPASS,
    RED_DYE,
    REDSTONE,
    REDSTONE_TORCH,
    REPEATER,
    SCAFFOLDING,
    STICK,
    STRUCTURE_VOID,
    TARGET,
    TRIPWIRE_HOOK,
    YELLOW_DYE,
    YELLOW_WOOL;

    private final ResourceId id = ResourceId.parse("minecraft:" + name().toLowerCase(Locale.ROOT));

    ResourceId id() {
        return id;
    }
}

// 
// Decompiled by Procyon v0.5.36
// 

package ninja.bytecode.iris.legacy;

import java.util.Iterator;
import java.util.HashMap;
import org.bukkit.Material;
import java.util.Map;

public class IDList
{
	private final Map<String, Material> list;

	public IDList()
	{
		this.list = new HashMap<String, Material>();
		try
		{
			this.list.put("68:0", Material.valueOf("WALL_SIGN"));
		}
		catch(IllegalArgumentException ex)
		{
		}
		try
		{
			this.list.put("72:1", Material.valueOf("SPRUCE_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex2)
		{
		}
		try
		{
			this.list.put("72:2", Material.valueOf("BIRCH_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex3)
		{
		}
		try
		{
			this.list.put("72:3", Material.valueOf("JUNGLE_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex4)
		{
		}
		try
		{
			this.list.put("72:4", Material.valueOf("ACACIA_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex5)
		{
		}
		try
		{
			this.list.put("72:5", Material.valueOf("DARK_OAK_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex6)
		{
		}
		try
		{
			this.list.put("75:0", Material.valueOf("REDSTONE_WALL_TORCH"));
		}
		catch(IllegalArgumentException ex7)
		{
		}
		try
		{
			this.list.put("96:1", Material.valueOf("SPRUCE_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex8)
		{
		}
		try
		{
			this.list.put("96:2", Material.valueOf("BIRCH_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex9)
		{
		}
		try
		{
			this.list.put("96:3", Material.valueOf("JUNGLE_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex10)
		{
		}
		try
		{
			this.list.put("96:4", Material.valueOf("ACACIA_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex11)
		{
		}
		try
		{
			this.list.put("96:5", Material.valueOf("DARK_OAK_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex12)
		{
		}
		try
		{
			this.list.put("104:1", Material.valueOf("ATTACHED_PUMPKIN_STEM"));
		}
		catch(IllegalArgumentException ex13)
		{
		}
		try
		{
			this.list.put("105:1", Material.valueOf("ATTACHED_MELON_STEM"));
		}
		catch(IllegalArgumentException ex14)
		{
		}
		try
		{
			this.list.put("143:1", Material.valueOf("SPRUCE_BUTTON"));
		}
		catch(IllegalArgumentException ex15)
		{
		}
		try
		{
			this.list.put("143:2", Material.valueOf("BIRCH_BUTTON"));
		}
		catch(IllegalArgumentException ex16)
		{
		}
		try
		{
			this.list.put("143:3", Material.valueOf("JUNGLE_BUTTON"));
		}
		catch(IllegalArgumentException ex17)
		{
		}
		try
		{
			this.list.put("143:4", Material.valueOf("ACACIA_BUTTON"));
		}
		catch(IllegalArgumentException ex18)
		{
		}
		try
		{
			this.list.put("143:5", Material.valueOf("DARK_OAK_BUTTON"));
		}
		catch(IllegalArgumentException ex19)
		{
		}
		try
		{
			this.list.put("355:0", Material.valueOf("WHITE_BED"));
		}
		catch(IllegalArgumentException ex20)
		{
		}
		try
		{
			this.list.put("355:14", Material.valueOf("RED_BED"));
		}
		catch(IllegalArgumentException ex21)
		{
		}
		try
		{
			this.list.put("383:0", Material.valueOf("BAT_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex22)
		{
		}
		try
		{
			this.list.put("383:1", Material.valueOf("BLAZE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex23)
		{
		}
		try
		{
			this.list.put("383:2", Material.valueOf("CAVE_SPIDER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex24)
		{
		}
		try
		{
			this.list.put("383:3", Material.valueOf("CHICKEN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex25)
		{
		}
		try
		{
			this.list.put("383:4", Material.valueOf("COD_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex26)
		{
		}
		try
		{
			this.list.put("383:5", Material.valueOf("COW_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex27)
		{
		}
		try
		{
			this.list.put("383:6", Material.valueOf("CREEPER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex28)
		{
		}
		try
		{
			this.list.put("383:7", Material.valueOf("DOLPHIN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex29)
		{
		}
		try
		{
			this.list.put("383:8", Material.valueOf("DONKEY_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex30)
		{
		}
		try
		{
			this.list.put("383:9", Material.valueOf("DROWNED_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex31)
		{
		}
		try
		{
			this.list.put("383:10", Material.valueOf("ELDER_GUARDIAN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex32)
		{
		}
		try
		{
			this.list.put("383:11", Material.valueOf("ENDERMAN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex33)
		{
		}
		try
		{
			this.list.put("383:12", Material.valueOf("ENDERMITE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex34)
		{
		}
		try
		{
			this.list.put("383:13", Material.valueOf("EVOKER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex35)
		{
		}
		try
		{
			this.list.put("383:14", Material.valueOf("GHAST_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex36)
		{
		}
		try
		{
			this.list.put("383:15", Material.valueOf("GUARDIAN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex37)
		{
		}
		try
		{
			this.list.put("383:16", Material.valueOf("HORSE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex38)
		{
		}
		try
		{
			this.list.put("383:17", Material.valueOf("HUSK_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex39)
		{
		}
		try
		{
			this.list.put("383:18", Material.valueOf("LLAMA_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex40)
		{
		}
		try
		{
			this.list.put("383:19", Material.valueOf("MAGMA_CUBE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex41)
		{
		}
		try
		{
			this.list.put("383:20", Material.valueOf("MOOSHROOM_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex42)
		{
		}
		try
		{
			this.list.put("383:21", Material.valueOf("MULE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex43)
		{
		}
		try
		{
			this.list.put("383:22", Material.valueOf("OCELOT_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex44)
		{
		}
		try
		{
			this.list.put("383:23", Material.valueOf("PARROT_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex45)
		{
		}
		try
		{
			this.list.put("383:24", Material.valueOf("PHANTOM_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex46)
		{
		}
		try
		{
			this.list.put("383:25", Material.valueOf("PIG_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex47)
		{
		}
		try
		{
			this.list.put("383:26", Material.valueOf("POLAR_BEAR_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex48)
		{
		}
		try
		{
			this.list.put("383:27", Material.valueOf("PUFFERFISH_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex49)
		{
		}
		try
		{
			this.list.put("383:28", Material.valueOf("RABBIT_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex50)
		{
		}
		try
		{
			this.list.put("383:29", Material.valueOf("SALMON_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex51)
		{
		}
		try
		{
			this.list.put("383:30", Material.valueOf("SHEEP_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex52)
		{
		}
		try
		{
			this.list.put("383:31", Material.valueOf("SHULKER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex53)
		{
		}
		try
		{
			this.list.put("383:32", Material.valueOf("SILVERFISH_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex54)
		{
		}
		try
		{
			this.list.put("383:33", Material.valueOf("SKELETON_HORSE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex55)
		{
		}
		try
		{
			this.list.put("383:34", Material.valueOf("SKELETON_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex56)
		{
		}
		try
		{
			this.list.put("383:35", Material.valueOf("SLIME_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex57)
		{
		}
		try
		{
			this.list.put("383:36", Material.valueOf("SPIDER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex58)
		{
		}
		try
		{
			this.list.put("383:37", Material.valueOf("SQUID_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex59)
		{
		}
		try
		{
			this.list.put("383:38", Material.valueOf("STRAY_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex60)
		{
		}
		try
		{
			this.list.put("383:39", Material.valueOf("TROPICAL_FISH_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex61)
		{
		}
		try
		{
			this.list.put("383:40", Material.valueOf("TURTLE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex62)
		{
		}
		try
		{
			this.list.put("383:41", Material.valueOf("VEX_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex63)
		{
		}
		try
		{
			this.list.put("383:42", Material.valueOf("VILLAGER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex64)
		{
		}
		try
		{
			this.list.put("383:43", Material.valueOf("VINDICATOR_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex65)
		{
		}
		try
		{
			this.list.put("383:44", Material.valueOf("WITCH_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex66)
		{
		}
		try
		{
			this.list.put("383:45", Material.valueOf("WITHER_SKELETON_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex67)
		{
		}
		try
		{
			this.list.put("383:46", Material.valueOf("WOLF_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex68)
		{
		}
		try
		{
			this.list.put("383:47", Material.valueOf("ZOMBIE_HORSE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex69)
		{
		}
		try
		{
			this.list.put("383:48", Material.valueOf("ZOMBIE_PIGMAN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex70)
		{
		}
		try
		{
			this.list.put("383:49", Material.valueOf("ZOMBIE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex71)
		{
		}
		try
		{
			this.list.put("383:50", Material.valueOf("ZOMBIE_VILLAGER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex72)
		{
		}
		try
		{
			this.list.put("397:6", Material.valueOf("SKELETON_WALL_SKULL"));
		}
		catch(IllegalArgumentException ex73)
		{
		}
		try
		{
			this.list.put("397:7", Material.valueOf("WITHER_SKELETON_WALL_SKULL"));
		}
		catch(IllegalArgumentException ex74)
		{
		}
		try
		{
			this.list.put("397:8", Material.valueOf("ZOMBIE_WALL_HEAD"));
		}
		catch(IllegalArgumentException ex75)
		{
		}
		try
		{
			this.list.put("397:9", Material.valueOf("PLAYER_WALL_HEAD"));
		}
		catch(IllegalArgumentException ex76)
		{
		}
		try
		{
			this.list.put("397:10", Material.valueOf("CREEPER_WALL_HEAD"));
		}
		catch(IllegalArgumentException ex77)
		{
		}
		try
		{
			this.list.put("397:11", Material.valueOf("DRAGON_WALL_HEAD"));
		}
		catch(IllegalArgumentException ex78)
		{
		}
		try
		{
			this.list.put("425:15", Material.valueOf("WHITE_BANNER"));
		}
		catch(IllegalArgumentException ex79)
		{
		}
		try
		{
			this.list.put("454:0", Material.valueOf("PUMPKIN"));
		}
		catch(IllegalArgumentException ex80)
		{
		}
		try
		{
			this.list.put("455:0", Material.valueOf("SHULKER_BOX"));
		}
		catch(IllegalArgumentException ex81)
		{
		}
		try
		{
			this.list.put("456:0", Material.valueOf("BLUE_ICE"));
		}
		catch(IllegalArgumentException ex82)
		{
		}
		try
		{
			this.list.put("457:0", Material.valueOf("STRIPPED_OAK_LOG"));
		}
		catch(IllegalArgumentException ex83)
		{
		}
		try
		{
			this.list.put("457:1", Material.valueOf("STRIPPED_SPRUCE_LOG"));
		}
		catch(IllegalArgumentException ex84)
		{
		}
		try
		{
			this.list.put("457:2", Material.valueOf("STRIPPED_BIRCH_LOG"));
		}
		catch(IllegalArgumentException ex85)
		{
		}
		try
		{
			this.list.put("457:3", Material.valueOf("STRIPPED_JUNGLE_LOG"));
		}
		catch(IllegalArgumentException ex86)
		{
		}
		try
		{
			this.list.put("457:4", Material.valueOf("STRIPPED_ACACIA_LOG"));
		}
		catch(IllegalArgumentException ex87)
		{
		}
		try
		{
			this.list.put("457:5", Material.valueOf("STRIPPED_DARK_OAK_LOG"));
		}
		catch(IllegalArgumentException ex88)
		{
		}
		try
		{
			this.list.put("458:0", Material.valueOf("OAK_WOOD"));
		}
		catch(IllegalArgumentException ex89)
		{
		}
		try
		{
			this.list.put("458:1", Material.valueOf("SPRUCE_WOOD"));
		}
		catch(IllegalArgumentException ex90)
		{
		}
		try
		{
			this.list.put("458:2", Material.valueOf("BIRCH_WOOD"));
		}
		catch(IllegalArgumentException ex91)
		{
		}
		try
		{
			this.list.put("458:3", Material.valueOf("JUNGLE_WOOD"));
		}
		catch(IllegalArgumentException ex92)
		{
		}
		try
		{
			this.list.put("458:4", Material.valueOf("ACACIA_WOOD"));
		}
		catch(IllegalArgumentException ex93)
		{
		}
		try
		{
			this.list.put("458:5", Material.valueOf("DARK_OAK_WOOD"));
		}
		catch(IllegalArgumentException ex94)
		{
		}
		try
		{
			this.list.put("459:0", Material.valueOf("STRIPPED_OAK_WOOD"));
		}
		catch(IllegalArgumentException ex95)
		{
		}
		try
		{
			this.list.put("459:1", Material.valueOf("STRIPPED_SPRUCE_WOOD"));
		}
		catch(IllegalArgumentException ex96)
		{
		}
		try
		{
			this.list.put("459:2", Material.valueOf("STRIPPED_BIRCH_WOOD"));
		}
		catch(IllegalArgumentException ex97)
		{
		}
		try
		{
			this.list.put("459:3", Material.valueOf("STRIPPED_JUNGLE_WOOD"));
		}
		catch(IllegalArgumentException ex98)
		{
		}
		try
		{
			this.list.put("459:4", Material.valueOf("STRIPPED_ACACIA_WOOD"));
		}
		catch(IllegalArgumentException ex99)
		{
		}
		try
		{
			this.list.put("459:5", Material.valueOf("STRIPPED_DARK_OAK_WOOD"));
		}
		catch(IllegalArgumentException ex100)
		{
		}
		try
		{
			this.list.put("460:0", Material.valueOf("BUBBLE_COLUMN"));
		}
		catch(IllegalArgumentException ex101)
		{
		}
		try
		{
			this.list.put("461:0", Material.valueOf("BRAIN_CORAL"));
		}
		catch(IllegalArgumentException ex102)
		{
		}
		try
		{
			this.list.put("461:1", Material.valueOf("BUBBLE_CORAL"));
		}
		catch(IllegalArgumentException ex103)
		{
		}
		try
		{
			this.list.put("461:2", Material.valueOf("FIRE_CORAL"));
		}
		catch(IllegalArgumentException ex104)
		{
		}
		try
		{
			this.list.put("461:3", Material.valueOf("HORN_CORAL"));
		}
		catch(IllegalArgumentException ex105)
		{
		}
		try
		{
			this.list.put("461:4", Material.valueOf("TUBE_CORAL"));
		}
		catch(IllegalArgumentException ex106)
		{
		}
		try
		{
			this.list.put("462:0", Material.valueOf("DEAD_BRAIN_CORAL"));
		}
		catch(IllegalArgumentException ex107)
		{
		}
		try
		{
			this.list.put("462:1", Material.valueOf("DEAD_BUBBLE_CORAL"));
		}
		catch(IllegalArgumentException ex108)
		{
		}
		try
		{
			this.list.put("462:2", Material.valueOf("DEAD_FIRE_CORAL"));
		}
		catch(IllegalArgumentException ex109)
		{
		}
		try
		{
			this.list.put("462:3", Material.valueOf("DEAD_HORN_CORAL"));
		}
		catch(IllegalArgumentException ex110)
		{
		}
		try
		{
			this.list.put("462:4", Material.valueOf("DEAD_TUBE_CORAL"));
		}
		catch(IllegalArgumentException ex111)
		{
		}
		try
		{
			this.list.put("463:0", Material.valueOf("BRAIN_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex112)
		{
		}
		try
		{
			this.list.put("463:1", Material.valueOf("BUBBLE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex113)
		{
		}
		try
		{
			this.list.put("463:2", Material.valueOf("FIRE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex114)
		{
		}
		try
		{
			this.list.put("463:3", Material.valueOf("HORN_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex115)
		{
		}
		try
		{
			this.list.put("463:4", Material.valueOf("TUBE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex116)
		{
		}
		try
		{
			this.list.put("464:0", Material.valueOf("DEAD_BRAIN_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex117)
		{
		}
		try
		{
			this.list.put("464:1", Material.valueOf("DEAD_BUBBLE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex118)
		{
		}
		try
		{
			this.list.put("464:2", Material.valueOf("DEAD_FIRE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex119)
		{
		}
		try
		{
			this.list.put("464:3", Material.valueOf("DEAD_HORN_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex120)
		{
		}
		try
		{
			this.list.put("464:4", Material.valueOf("DEAD_TUBE_CORAL_BLOCK"));
		}
		catch(IllegalArgumentException ex121)
		{
		}
		try
		{
			this.list.put("465:0", Material.valueOf("BRAIN_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex122)
		{
		}
		try
		{
			this.list.put("465:1", Material.valueOf("BUBBLE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex123)
		{
		}
		try
		{
			this.list.put("465:2", Material.valueOf("FIRE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex124)
		{
		}
		try
		{
			this.list.put("465:3", Material.valueOf("HORN_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex125)
		{
		}
		try
		{
			this.list.put("465:4", Material.valueOf("TUBE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex126)
		{
		}
		try
		{
			this.list.put("466:0", Material.valueOf("DEAD_BRAIN_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex127)
		{
		}
		try
		{
			this.list.put("466:1", Material.valueOf("DEAD_BUBBLE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex128)
		{
		}
		try
		{
			this.list.put("466:2", Material.valueOf("DEAD_FIRE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex129)
		{
		}
		try
		{
			this.list.put("466:3", Material.valueOf("DEAD_HORN_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex130)
		{
		}
		try
		{
			this.list.put("466:4", Material.valueOf("DEAD_TUBE_CORAL_FAN"));
		}
		catch(IllegalArgumentException ex131)
		{
		}
		try
		{
			this.list.put("467:0", Material.valueOf("BRAIN_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex132)
		{
		}
		try
		{
			this.list.put("467:1", Material.valueOf("BUBBLE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex133)
		{
		}
		try
		{
			this.list.put("467:2", Material.valueOf("FIRE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex134)
		{
		}
		try
		{
			this.list.put("467:3", Material.valueOf("HORN_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex135)
		{
		}
		try
		{
			this.list.put("467:4", Material.valueOf("TUBE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex136)
		{
		}
		try
		{
			this.list.put("468:0", Material.valueOf("DEAD_BRAIN_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex137)
		{
		}
		try
		{
			this.list.put("468:1", Material.valueOf("DEAD_BUBBLE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex138)
		{
		}
		try
		{
			this.list.put("468:2", Material.valueOf("DEAD_FIRE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex139)
		{
		}
		try
		{
			this.list.put("468:3", Material.valueOf("DEAD_HORN_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex140)
		{
		}
		try
		{
			this.list.put("468:4", Material.valueOf("DEAD_TUBE_CORAL_WALL_FAN"));
		}
		catch(IllegalArgumentException ex141)
		{
		}
		try
		{
			this.list.put("469:0", Material.valueOf("TROPICAL_FISH_BUCKET"));
		}
		catch(IllegalArgumentException ex142)
		{
		}
		try
		{
			this.list.put("469:1", Material.valueOf("COD_BUCKET"));
		}
		catch(IllegalArgumentException ex143)
		{
		}
		try
		{
			this.list.put("469:2", Material.valueOf("PUFFERFISH_BUCKET"));
		}
		catch(IllegalArgumentException ex144)
		{
		}
		try
		{
			this.list.put("469:3", Material.valueOf("SALMON_BUCKET"));
		}
		catch(IllegalArgumentException ex145)
		{
		}
		try
		{
			this.list.put("470:0", Material.valueOf("CONDUIT"));
		}
		catch(IllegalArgumentException ex146)
		{
		}
		try
		{
			this.list.put("471:0", Material.valueOf("PRISMARINE_SLAB"));
		}
		catch(IllegalArgumentException ex147)
		{
		}
		try
		{
			this.list.put("471:1", Material.valueOf("PRISMARINE_BRICK_SLAB"));
		}
		catch(IllegalArgumentException ex148)
		{
		}
		try
		{
			this.list.put("471:2", Material.valueOf("DARK_PRISMARINE_SLAB"));
		}
		catch(IllegalArgumentException ex149)
		{
		}
		try
		{
			this.list.put("472:0", Material.valueOf("PRISMARINE_STAIRS"));
		}
		catch(IllegalArgumentException ex150)
		{
		}
		try
		{
			this.list.put("472:1", Material.valueOf("PRISMARINE_BRICK_STAIRS"));
		}
		catch(IllegalArgumentException ex151)
		{
		}
		try
		{
			this.list.put("472:2", Material.valueOf("DARK_PRISMARINE_STAIRS"));
		}
		catch(IllegalArgumentException ex152)
		{
		}
		try
		{
			this.list.put("473:0", Material.valueOf("SEA_PICKLE"));
		}
		catch(IllegalArgumentException ex153)
		{
		}
		try
		{
			this.list.put("474:0", Material.valueOf("SEAGRASS"));
		}
		catch(IllegalArgumentException ex154)
		{
		}
		try
		{
			this.list.put("474:1", Material.valueOf("TALL_SEAGRASS"));
		}
		catch(IllegalArgumentException ex155)
		{
		}
		try
		{
			this.list.put("475:0", Material.valueOf("KELP"));
		}
		catch(IllegalArgumentException ex156)
		{
		}
		try
		{
			this.list.put("476:0", Material.valueOf("DRIED_KELP"));
		}
		catch(IllegalArgumentException ex157)
		{
		}
		try
		{
			this.list.put("477:0", Material.valueOf("DRIED_KELP_BLOCK"));
		}
		catch(IllegalArgumentException ex158)
		{
		}
		try
		{
			this.list.put("478:0", Material.valueOf("HEART_OF_THE_SEA"));
		}
		catch(IllegalArgumentException ex159)
		{
		}
		try
		{
			this.list.put("479:0", Material.valueOf("NAUTILUS_SHELL"));
		}
		catch(IllegalArgumentException ex160)
		{
		}
		try
		{
			this.list.put("480:0", Material.valueOf("PHANTOM_MEMBRANE"));
		}
		catch(IllegalArgumentException ex161)
		{
		}
		try
		{
			this.list.put("481:0", Material.valueOf("SCUTE"));
		}
		catch(IllegalArgumentException ex162)
		{
		}
		try
		{
			this.list.put("482:0", Material.valueOf("TURTLE_HELMET"));
		}
		catch(IllegalArgumentException ex163)
		{
		}
		try
		{
			this.list.put("483:0", Material.valueOf("TRIDENT"));
		}
		catch(IllegalArgumentException ex164)
		{
		}
		try
		{
			this.list.put("484:0", Material.valueOf("TURTLE_EGG"));
		}
		catch(IllegalArgumentException ex165)
		{
		}
		try
		{
			this.list.put("485:0", Material.valueOf("VOID_AIR"));
		}
		catch(IllegalArgumentException ex166)
		{
		}
		try
		{
			this.list.put("486:0", Material.valueOf("CAVE_AIR"));
		}
		catch(IllegalArgumentException ex167)
		{
		}
		try
		{
			this.list.put("487:0", Material.valueOf("DEBUG_STICK"));
		}
		catch(IllegalArgumentException ex168)
		{
		}
		try
		{
			this.list.put("488:0", Material.valueOf("BLACK_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex169)
		{
		}
		try
		{
			this.list.put("488:1", Material.valueOf("RED_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex170)
		{
		}
		try
		{
			this.list.put("488:2", Material.valueOf("GREEN_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex171)
		{
		}
		try
		{
			this.list.put("488:3", Material.valueOf("BROWN_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex172)
		{
		}
		try
		{
			this.list.put("488:4", Material.valueOf("BLUE_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex173)
		{
		}
		try
		{
			this.list.put("488:5", Material.valueOf("PURPLE_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex174)
		{
		}
		try
		{
			this.list.put("488:6", Material.valueOf("CYAN_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex175)
		{
		}
		try
		{
			this.list.put("488:7", Material.valueOf("LIGHT_GRAY_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex176)
		{
		}
		try
		{
			this.list.put("488:8", Material.valueOf("GRAY_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex177)
		{
		}
		try
		{
			this.list.put("488:9", Material.valueOf("PINK_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex178)
		{
		}
		try
		{
			this.list.put("488:10", Material.valueOf("LIME_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex179)
		{
		}
		try
		{
			this.list.put("488:11", Material.valueOf("YELLOW_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex180)
		{
		}
		try
		{
			this.list.put("488:12", Material.valueOf("LIGHT_BLUE_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex181)
		{
		}
		try
		{
			this.list.put("488:13", Material.valueOf("MAGENTA_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex182)
		{
		}
		try
		{
			this.list.put("488:14", Material.valueOf("ORANGE_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex183)
		{
		}
		try
		{
			this.list.put("488:15", Material.valueOf("WHITE_WALL_BANNER"));
		}
		catch(IllegalArgumentException ex184)
		{
		}
		try
		{
			this.list.put("489:0", Material.valueOf("POTTED_ACACIA_SAPLING"));
		}
		catch(IllegalArgumentException ex185)
		{
		}
		try
		{
			this.list.put("489:1", Material.valueOf("POTTED_ALLIUM"));
		}
		catch(IllegalArgumentException ex186)
		{
		}
		try
		{
			this.list.put("489:2", Material.valueOf("POTTED_AZURE_BLUET"));
		}
		catch(IllegalArgumentException ex187)
		{
		}
		try
		{
			this.list.put("489:3", Material.valueOf("POTTED_BIRCH_SAPLING"));
		}
		catch(IllegalArgumentException ex188)
		{
		}
		try
		{
			this.list.put("489:4", Material.valueOf("POTTED_BLUE_ORCHID"));
		}
		catch(IllegalArgumentException ex189)
		{
		}
		try
		{
			this.list.put("489:5", Material.valueOf("POTTED_BROWN_MUSHROOM"));
		}
		catch(IllegalArgumentException ex190)
		{
		}
		try
		{
			this.list.put("489:6", Material.valueOf("POTTED_CACTUS"));
		}
		catch(IllegalArgumentException ex191)
		{
		}
		try
		{
			this.list.put("489:7", Material.valueOf("POTTED_DANDELION"));
		}
		catch(IllegalArgumentException ex192)
		{
		}
		try
		{
			this.list.put("489:8", Material.valueOf("POTTED_DARK_OAK_SAPLING"));
		}
		catch(IllegalArgumentException ex193)
		{
		}
		try
		{
			this.list.put("489:9", Material.valueOf("POTTED_DEAD_BUSH"));
		}
		catch(IllegalArgumentException ex194)
		{
		}
		try
		{
			this.list.put("489:10", Material.valueOf("POTTED_FERN"));
		}
		catch(IllegalArgumentException ex195)
		{
		}
		try
		{
			this.list.put("489:11", Material.valueOf("POTTED_JUNGLE_SAPLING"));
		}
		catch(IllegalArgumentException ex196)
		{
		}
		try
		{
			this.list.put("489:12", Material.valueOf("POTTED_OAK_SAPLING"));
		}
		catch(IllegalArgumentException ex197)
		{
		}
		try
		{
			this.list.put("489:13", Material.valueOf("POTTED_ORANGE_TULIP"));
		}
		catch(IllegalArgumentException ex198)
		{
		}
		try
		{
			this.list.put("489:14", Material.valueOf("POTTED_OXEYE_DAISY"));
		}
		catch(IllegalArgumentException ex199)
		{
		}
		try
		{
			this.list.put("489:15", Material.valueOf("POTTED_PINK_TULIP"));
		}
		catch(IllegalArgumentException ex200)
		{
		}
		try
		{
			this.list.put("489:16", Material.valueOf("POTTED_POPPY"));
		}
		catch(IllegalArgumentException ex201)
		{
		}
		try
		{
			this.list.put("489:17", Material.valueOf("POTTED_RED_MUSHROOM"));
		}
		catch(IllegalArgumentException ex202)
		{
		}
		try
		{
			this.list.put("489:18", Material.valueOf("POTTED_RED_TULIP"));
		}
		catch(IllegalArgumentException ex203)
		{
		}
		try
		{
			this.list.put("489:19", Material.valueOf("POTTED_SPRUCE_SAPLING"));
		}
		catch(IllegalArgumentException ex204)
		{
		}
		try
		{
			this.list.put("489:20", Material.valueOf("POTTED_WHITE_TULIP"));
		}
		catch(IllegalArgumentException ex205)
		{
		}
		try
		{
			this.list.put("383:51", Material.valueOf("CAT_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex206)
		{
		}
		try
		{
			this.list.put("383:52", Material.valueOf("FOX_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex207)
		{
		}
		try
		{
			this.list.put("383:53", Material.valueOf("PANDA_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex208)
		{
		}
		try
		{
			this.list.put("383:54", Material.valueOf("PILLAGER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex209)
		{
		}
		try
		{
			this.list.put("383:55", Material.valueOf("RAVAGER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex210)
		{
		}
		try
		{
			this.list.put("383:56", Material.valueOf("TRADER_LLAMA_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex211)
		{
		}
		try
		{
			this.list.put("383:57", Material.valueOf("WANDERING_TRADER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex212)
		{
		}
		try
		{
			this.list.put("489:21", Material.valueOf("POTTED_CORNFLOWER"));
		}
		catch(IllegalArgumentException ex213)
		{
		}
		try
		{
			this.list.put("489:22", Material.valueOf("POTTED_LILY_OF_THE_VALLEY"));
		}
		catch(IllegalArgumentException ex214)
		{
		}
		try
		{
			this.list.put("489:23", Material.valueOf("POTTED_WITHER_ROSE"));
		}
		catch(IllegalArgumentException ex215)
		{
		}
		try
		{
			this.list.put("490:0", Material.valueOf("ACACIA_SIGN"));
		}
		catch(IllegalArgumentException ex216)
		{
		}
		try
		{
			this.list.put("490:1", Material.valueOf("BIRCH_SIGN"));
		}
		catch(IllegalArgumentException ex217)
		{
		}
		try
		{
			this.list.put("490:2", Material.valueOf("DARK_OAK_SIGN"));
		}
		catch(IllegalArgumentException ex218)
		{
		}
		try
		{
			this.list.put("490:3", Material.valueOf("JUNGLE_SIGN"));
		}
		catch(IllegalArgumentException ex219)
		{
		}
		try
		{
			this.list.put("490:4", Material.valueOf("OAK_SIGN"));
		}
		catch(IllegalArgumentException ex220)
		{
		}
		try
		{
			this.list.put("490:5", Material.valueOf("SPRUCE_SIGN"));
		}
		catch(IllegalArgumentException ex221)
		{
		}
		try
		{
			this.list.put("491:0", Material.valueOf("ACACIA_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex222)
		{
		}
		try
		{
			this.list.put("491:1", Material.valueOf("BIRCH_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex223)
		{
		}
		try
		{
			this.list.put("491:2", Material.valueOf("DARK_OAK_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex224)
		{
		}
		try
		{
			this.list.put("491:3", Material.valueOf("JUNGLE_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex225)
		{
		}
		try
		{
			this.list.put("491:4", Material.valueOf("OAK_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex226)
		{
		}
		try
		{
			this.list.put("491:5", Material.valueOf("SPRUCE_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex227)
		{
		}
		try
		{
			this.list.put("492:0", Material.valueOf("ANDESITE_SLAB"));
		}
		catch(IllegalArgumentException ex228)
		{
		}
		try
		{
			this.list.put("492:1", Material.valueOf("CUT_RED_SANDSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex229)
		{
		}
		try
		{
			this.list.put("492:2", Material.valueOf("CUT_SANDSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex230)
		{
		}
		try
		{
			this.list.put("492:3", Material.valueOf("DIORITE_SLAB"));
		}
		catch(IllegalArgumentException ex231)
		{
		}
		try
		{
			this.list.put("492:4", Material.valueOf("END_STONE_BRICK_SLAB"));
		}
		catch(IllegalArgumentException ex232)
		{
		}
		try
		{
			this.list.put("492:5", Material.valueOf("GRANITE_SLAB"));
		}
		catch(IllegalArgumentException ex233)
		{
		}
		try
		{
			this.list.put("492:6", Material.valueOf("MOSSY_COBBLESTONE_SLAB"));
		}
		catch(IllegalArgumentException ex234)
		{
		}
		try
		{
			this.list.put("492:7", Material.valueOf("MOSSY_STONE_BRICK_SLAB"));
		}
		catch(IllegalArgumentException ex235)
		{
		}
		try
		{
			this.list.put("492:8", Material.valueOf("POLISHED_ANDESITE_SLAB"));
		}
		catch(IllegalArgumentException ex236)
		{
		}
		try
		{
			this.list.put("492:9", Material.valueOf("POLISHED_DIORITE_SLAB"));
		}
		catch(IllegalArgumentException ex237)
		{
		}
		try
		{
			this.list.put("492:10", Material.valueOf("POLISHED_GRANITE_SLAB"));
		}
		catch(IllegalArgumentException ex238)
		{
		}
		try
		{
			this.list.put("492:11", Material.valueOf("RED_NETHER_BRICK_SLAB"));
		}
		catch(IllegalArgumentException ex239)
		{
		}
		try
		{
			this.list.put("492:12", Material.valueOf("SMOOTH_QUARTZ_SLAB"));
		}
		catch(IllegalArgumentException ex240)
		{
		}
		try
		{
			this.list.put("492:13", Material.valueOf("SMOOTH_RED_SANDSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex241)
		{
		}
		try
		{
			this.list.put("492:14", Material.valueOf("SMOOTH_SANDSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex242)
		{
		}
		try
		{
			this.list.put("493:0", Material.valueOf("ANDESITE_STAIRS"));
		}
		catch(IllegalArgumentException ex243)
		{
		}
		try
		{
			this.list.put("493:1", Material.valueOf("DIORITE_STAIRS"));
		}
		catch(IllegalArgumentException ex244)
		{
		}
		try
		{
			this.list.put("493:2", Material.valueOf("END_STONE_BRICK_STAIRS"));
		}
		catch(IllegalArgumentException ex245)
		{
		}
		try
		{
			this.list.put("493:3", Material.valueOf("GRANITE_STAIRS"));
		}
		catch(IllegalArgumentException ex246)
		{
		}
		try
		{
			this.list.put("493:4", Material.valueOf("MOSSY_COBBLESTONE_STAIRS"));
		}
		catch(IllegalArgumentException ex247)
		{
		}
		try
		{
			this.list.put("493:5", Material.valueOf("MOSSY_STONE_BRICK_STAIRS"));
		}
		catch(IllegalArgumentException ex248)
		{
		}
		try
		{
			this.list.put("493:6", Material.valueOf("POLISHED_ANDESITE_STAIRS"));
		}
		catch(IllegalArgumentException ex249)
		{
		}
		try
		{
			this.list.put("493:7", Material.valueOf("POLISHED_DIORITE_STAIRS"));
		}
		catch(IllegalArgumentException ex250)
		{
		}
		try
		{
			this.list.put("493:8", Material.valueOf("POLISHED_GRANITE_STAIRS"));
		}
		catch(IllegalArgumentException ex251)
		{
		}
		try
		{
			this.list.put("493:9", Material.valueOf("RED_NETHER_BRICK_STAIRS"));
		}
		catch(IllegalArgumentException ex252)
		{
		}
		try
		{
			this.list.put("493:10", Material.valueOf("SMOOTH_QUARTZ_STAIRS"));
		}
		catch(IllegalArgumentException ex253)
		{
		}
		try
		{
			this.list.put("493:11", Material.valueOf("SMOOTH_RED_SANDSTONE_STAIRS"));
		}
		catch(IllegalArgumentException ex254)
		{
		}
		try
		{
			this.list.put("493:12", Material.valueOf("SMOOTH_SANDSTONE_STAIRS"));
		}
		catch(IllegalArgumentException ex255)
		{
		}
		try
		{
			this.list.put("493:13", Material.valueOf("STONE_STAIRS"));
		}
		catch(IllegalArgumentException ex256)
		{
		}
		try
		{
			this.list.put("494:0", Material.valueOf("ANDESITE_WALL"));
		}
		catch(IllegalArgumentException ex257)
		{
		}
		try
		{
			this.list.put("494:1", Material.valueOf("BRICK_WALL"));
		}
		catch(IllegalArgumentException ex258)
		{
		}
		try
		{
			this.list.put("494:2", Material.valueOf("DIORITE_WALL"));
		}
		catch(IllegalArgumentException ex259)
		{
		}
		try
		{
			this.list.put("494:3", Material.valueOf("END_STONE_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex260)
		{
		}
		try
		{
			this.list.put("494:4", Material.valueOf("GRANITE_WALL"));
		}
		catch(IllegalArgumentException ex261)
		{
		}
		try
		{
			this.list.put("494:5", Material.valueOf("MOSSY_STONE_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex262)
		{
		}
		try
		{
			this.list.put("494:6", Material.valueOf("NETHER_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex263)
		{
		}
		try
		{
			this.list.put("494:7", Material.valueOf("PRISMARINE_WALL"));
		}
		catch(IllegalArgumentException ex264)
		{
		}
		try
		{
			this.list.put("494:8", Material.valueOf("RED_NETHER_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex265)
		{
		}
		try
		{
			this.list.put("494:9", Material.valueOf("RED_SANDSTONE_WALL"));
		}
		catch(IllegalArgumentException ex266)
		{
		}
		try
		{
			this.list.put("494:10", Material.valueOf("SANDSTONE_WALL"));
		}
		catch(IllegalArgumentException ex267)
		{
		}
		try
		{
			this.list.put("494:11", Material.valueOf("STONE_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex268)
		{
		}
		try
		{
			this.list.put("495:0", Material.valueOf("BAMBOO"));
		}
		catch(IllegalArgumentException ex269)
		{
		}
		try
		{
			this.list.put("496:0", Material.valueOf("BAMBOO_SAPLING"));
		}
		catch(IllegalArgumentException ex270)
		{
		}
		try
		{
			this.list.put("497:0", Material.valueOf("COCOA_BEANS"));
		}
		catch(IllegalArgumentException ex271)
		{
		}
		try
		{
			this.list.put("498:0", Material.valueOf("CORNFLOWER"));
		}
		catch(IllegalArgumentException ex272)
		{
		}
		try
		{
			this.list.put("499:0", Material.valueOf("LILY_OF_THE_VALLEY"));
		}
		catch(IllegalArgumentException ex273)
		{
		}
		try
		{
			this.list.put("500:0", Material.valueOf("SWEET_BERRY_BUSH"));
		}
		catch(IllegalArgumentException ex274)
		{
		}
		try
		{
			this.list.put("501:0", Material.valueOf("WITHER_ROSE"));
		}
		catch(IllegalArgumentException ex275)
		{
		}
		try
		{
			this.list.put("502:0", Material.valueOf("BARREL"));
		}
		catch(IllegalArgumentException ex276)
		{
		}
		try
		{
			this.list.put("503:0", Material.valueOf("BELL"));
		}
		catch(IllegalArgumentException ex277)
		{
		}
		try
		{
			this.list.put("504:0", Material.valueOf("BLAST_FURNACE"));
		}
		catch(IllegalArgumentException ex278)
		{
		}
		try
		{
			this.list.put("505:0", Material.valueOf("CAMPFIRE"));
		}
		catch(IllegalArgumentException ex279)
		{
		}
		try
		{
			this.list.put("506:0", Material.valueOf("CARTOGRAPHY_TABLE"));
		}
		catch(IllegalArgumentException ex280)
		{
		}
		try
		{
			this.list.put("507:0", Material.valueOf("COMPOSTER"));
		}
		catch(IllegalArgumentException ex281)
		{
		}
		try
		{
			this.list.put("508:0", Material.valueOf("FLETCHING_TABLE"));
		}
		catch(IllegalArgumentException ex282)
		{
		}
		try
		{
			this.list.put("509:0", Material.valueOf("GRINDSTONE"));
		}
		catch(IllegalArgumentException ex283)
		{
		}
		try
		{
			this.list.put("510:0", Material.valueOf("JIGSAW"));
		}
		catch(IllegalArgumentException ex284)
		{
		}
		try
		{
			this.list.put("511:0", Material.valueOf("LANTERN"));
		}
		catch(IllegalArgumentException ex285)
		{
		}
		try
		{
			this.list.put("512:0", Material.valueOf("LECTERN"));
		}
		catch(IllegalArgumentException ex286)
		{
		}
		try
		{
			this.list.put("513:0", Material.valueOf("LOOM"));
		}
		catch(IllegalArgumentException ex287)
		{
		}
		try
		{
			this.list.put("514:0", Material.valueOf("SCAFFOLDING"));
		}
		catch(IllegalArgumentException ex288)
		{
		}
		try
		{
			this.list.put("515:0", Material.valueOf("SMITHING_TABLE"));
		}
		catch(IllegalArgumentException ex289)
		{
		}
		try
		{
			this.list.put("516:0", Material.valueOf("SMOKER"));
		}
		catch(IllegalArgumentException ex290)
		{
		}
		try
		{
			this.list.put("517:0", Material.valueOf("STONECUTTER"));
		}
		catch(IllegalArgumentException ex291)
		{
		}
		try
		{
			this.list.put("518:0", Material.valueOf("CREEPER_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex292)
		{
		}
		try
		{
			this.list.put("518:1", Material.valueOf("FLOWER_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex293)
		{
		}
		try
		{
			this.list.put("518:2", Material.valueOf("GLOBE_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex294)
		{
		}
		try
		{
			this.list.put("518:3", Material.valueOf("MOJANG_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex295)
		{
		}
		try
		{
			this.list.put("518:4", Material.valueOf("SKULL_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex296)
		{
		}
		try
		{
			this.list.put("519:0", Material.valueOf("BLACK_DYE"));
		}
		catch(IllegalArgumentException ex297)
		{
		}
		try
		{
			this.list.put("519:1", Material.valueOf("BLUE_DYE"));
		}
		catch(IllegalArgumentException ex298)
		{
		}
		try
		{
			this.list.put("519:2", Material.valueOf("BROWN_DYE"));
		}
		catch(IllegalArgumentException ex299)
		{
		}
		try
		{
			this.list.put("519:3", Material.valueOf("WHITE_DYE"));
		}
		catch(IllegalArgumentException ex300)
		{
		}
		try
		{
			this.list.put("520:0", Material.valueOf("CROSSBOW"));
		}
		catch(IllegalArgumentException ex301)
		{
		}
		try
		{
			this.list.put("521:0", Material.valueOf("LEATHER_HORSE_ARMOR"));
		}
		catch(IllegalArgumentException ex302)
		{
		}
		try
		{
			this.list.put("522:0", Material.valueOf("SUSPICIOUS_STEW"));
		}
		catch(IllegalArgumentException ex303)
		{
		}
		try
		{
			this.list.put("523:0", Material.valueOf("SWEET_BERRIES"));
		}
		catch(IllegalArgumentException ex304)
		{
		}
		try
		{
			this.list.put("383:58", Material.valueOf("BEE_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex305)
		{
		}
		try
		{
			this.list.put("524:0", Material.valueOf("BEEHIVE"));
		}
		catch(IllegalArgumentException ex306)
		{
		}
		try
		{
			this.list.put("525:0", Material.valueOf("BEE_NEST"));
		}
		catch(IllegalArgumentException ex307)
		{
		}
		try
		{
			this.list.put("526:0", Material.valueOf("HONEY_BLOCK"));
		}
		catch(IllegalArgumentException ex308)
		{
		}
		try
		{
			this.list.put("527:0", Material.valueOf("HONEYCOMB_BLOCK"));
		}
		catch(IllegalArgumentException ex309)
		{
		}
		try
		{
			this.list.put("528:0", Material.valueOf("HONEYCOMB"));
		}
		catch(IllegalArgumentException ex310)
		{
		}
		try
		{
			this.list.put("529:0", Material.valueOf("HONEY_BOTTLE"));
		}
		catch(IllegalArgumentException ex311)
		{
		}
		try
		{
			this.list.put("5:6", Material.valueOf("CRIMSON_PLANKS"));
		}
		catch(IllegalArgumentException ex312)
		{
		}
		try
		{
			this.list.put("5:7", Material.valueOf("WARPED_PLANKS"));
		}
		catch(IllegalArgumentException ex313)
		{
		}
		try
		{
			this.list.put("72:6", Material.valueOf("CRIMSON_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex314)
		{
		}
		try
		{
			this.list.put("72:7", Material.valueOf("WARPED_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex315)
		{
		}
		try
		{
			this.list.put("96:6", Material.valueOf("CRIMSON_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex316)
		{
		}
		try
		{
			this.list.put("96:7", Material.valueOf("WARPED_TRAPDOOR"));
		}
		catch(IllegalArgumentException ex317)
		{
		}
		try
		{
			this.list.put("143:6", Material.valueOf("CRIMSON_BUTTON"));
		}
		catch(IllegalArgumentException ex318)
		{
		}
		try
		{
			this.list.put("143:7", Material.valueOf("WARPED_BUTTON"));
		}
		catch(IllegalArgumentException ex319)
		{
		}
		try
		{
			this.list.put("383:59", Material.valueOf("HOGLIN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex320)
		{
		}
		try
		{
			this.list.put("383:60", Material.valueOf("PIGLIN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex321)
		{
		}
		try
		{
			this.list.put("383:61", Material.valueOf("STRIDER_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex322)
		{
		}
		try
		{
			this.list.put("383:62", Material.valueOf("ZOGLIN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex323)
		{
		}
		try
		{
			this.list.put("383:63", Material.valueOf("ZOMBIFIED_PIGLIN_SPAWN_EGG"));
		}
		catch(IllegalArgumentException ex324)
		{
		}
		try
		{
			this.list.put("457:6", Material.valueOf("STRIPPED_CRIMSON_STEM"));
		}
		catch(IllegalArgumentException ex325)
		{
		}
		try
		{
			this.list.put("457:7", Material.valueOf("STRIPPED_WARPED_STEM"));
		}
		catch(IllegalArgumentException ex326)
		{
		}
		try
		{
			this.list.put("459:6", Material.valueOf("STRIPPED_CRIMSON_HYPHAE"));
		}
		catch(IllegalArgumentException ex327)
		{
		}
		try
		{
			this.list.put("459:7", Material.valueOf("STRIPPED_WARPED_HYPHAE"));
		}
		catch(IllegalArgumentException ex328)
		{
		}
		try
		{
			this.list.put("489:21", Material.valueOf("POTTED_BAMBOO"));
		}
		catch(IllegalArgumentException ex329)
		{
		}
		try
		{
			this.list.put("489:22", Material.valueOf("POTTED_CORNFLOWER"));
		}
		catch(IllegalArgumentException ex330)
		{
		}
		try
		{
			this.list.put("489:23", Material.valueOf("POTTED_LILY_OF_THE_VALLEY"));
		}
		catch(IllegalArgumentException ex331)
		{
		}
		try
		{
			this.list.put("489:24", Material.valueOf("POTTED_WITHER_ROSE"));
		}
		catch(IllegalArgumentException ex332)
		{
		}
		try
		{
			this.list.put("489:25", Material.valueOf("POTTED_CRIMSON_FUNGUS"));
		}
		catch(IllegalArgumentException ex333)
		{
		}
		try
		{
			this.list.put("489:26", Material.valueOf("POTTED_CRIMSON_ROOTS"));
		}
		catch(IllegalArgumentException ex334)
		{
		}
		try
		{
			this.list.put("489:27", Material.valueOf("POTTED_WARPED_FUNGUS"));
		}
		catch(IllegalArgumentException ex335)
		{
		}
		try
		{
			this.list.put("489:28", Material.valueOf("POTTED_WARPED_ROOTS"));
		}
		catch(IllegalArgumentException ex336)
		{
		}
		try
		{
			this.list.put("490:6", Material.valueOf("CRIMSON_SIGN"));
		}
		catch(IllegalArgumentException ex337)
		{
		}
		try
		{
			this.list.put("490:7", Material.valueOf("WARPED_SIGN"));
		}
		catch(IllegalArgumentException ex338)
		{
		}
		try
		{
			this.list.put("491:6", Material.valueOf("CRIMSON_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex339)
		{
		}
		try
		{
			this.list.put("491:7", Material.valueOf("WARPED_WALL_SIGN"));
		}
		catch(IllegalArgumentException ex340)
		{
		}
		try
		{
			this.list.put("492:15", Material.valueOf("STONE_SLAB"));
		}
		catch(IllegalArgumentException ex341)
		{
		}
		try
		{
			this.list.put("492:16", Material.valueOf("CRIMSON_SLAB"));
		}
		catch(IllegalArgumentException ex342)
		{
		}
		try
		{
			this.list.put("492:17", Material.valueOf("WARPED_SLAB"));
		}
		catch(IllegalArgumentException ex343)
		{
		}
		try
		{
			this.list.put("492:18", Material.valueOf("BLACKSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex344)
		{
		}
		try
		{
			this.list.put("492:19", Material.valueOf("POLISHED_BLACKSTONE_SLAB"));
		}
		catch(IllegalArgumentException ex345)
		{
		}
		try
		{
			this.list.put("492:20", Material.valueOf("POLISHED_BLACKSTONE_BRICK_SLAB"));
		}
		catch(IllegalArgumentException ex346)
		{
		}
		try
		{
			this.list.put("493:14", Material.valueOf("CRIMSON_STAIRS"));
		}
		catch(IllegalArgumentException ex347)
		{
		}
		try
		{
			this.list.put("493:15", Material.valueOf("WARPED_STAIRS"));
		}
		catch(IllegalArgumentException ex348)
		{
		}
		try
		{
			this.list.put("493:16", Material.valueOf("BLACKSTONE_STAIRS"));
		}
		catch(IllegalArgumentException ex349)
		{
		}
		try
		{
			this.list.put("493:17", Material.valueOf("POLISHED_BLACKSTONE_STAIRS"));
		}
		catch(IllegalArgumentException ex350)
		{
		}
		try
		{
			this.list.put("493:18", Material.valueOf("POLISHED_BLACKSTONE_BRICK_STAIRS"));
		}
		catch(IllegalArgumentException ex351)
		{
		}
		try
		{
			this.list.put("494:12", Material.valueOf("BLACKSTONE_WALL"));
		}
		catch(IllegalArgumentException ex352)
		{
		}
		try
		{
			this.list.put("494:13", Material.valueOf("POLISHED_BLACKSTONE_WALL"));
		}
		catch(IllegalArgumentException ex353)
		{
		}
		try
		{
			this.list.put("494:14", Material.valueOf("POLISHED_BLACKSTONE_BRICK_WALL"));
		}
		catch(IllegalArgumentException ex354)
		{
		}
		try
		{
			this.list.put("518:5", Material.valueOf("PIGLIN_BANNER_PATTERN"));
		}
		catch(IllegalArgumentException ex355)
		{
		}
		try
		{
			this.list.put("2268:0", Material.valueOf("MUSIC_DISC_PIGSTEP"));
		}
		catch(IllegalArgumentException ex356)
		{
		}
		try
		{
			this.list.put("530:0", Material.valueOf("NETHERITE_INGOT"));
		}
		catch(IllegalArgumentException ex357)
		{
		}
		try
		{
			this.list.put("531:0", Material.valueOf("NETHERITE_SCRAP"));
		}
		catch(IllegalArgumentException ex358)
		{
		}
		try
		{
			this.list.put("532:0", Material.valueOf("NETHERITE_SWORD"));
		}
		catch(IllegalArgumentException ex359)
		{
		}
		try
		{
			this.list.put("533:0", Material.valueOf("NETHERITE_SHOVEL"));
		}
		catch(IllegalArgumentException ex360)
		{
		}
		try
		{
			this.list.put("534:0", Material.valueOf("NETHERITE_PICKAXE"));
		}
		catch(IllegalArgumentException ex361)
		{
		}
		try
		{
			this.list.put("535:0", Material.valueOf("NETHERITE_AXE"));
		}
		catch(IllegalArgumentException ex362)
		{
		}
		try
		{
			this.list.put("536:0", Material.valueOf("NETHERITE_HOE"));
		}
		catch(IllegalArgumentException ex363)
		{
		}
		try
		{
			this.list.put("537:0", Material.valueOf("NETHERITE_HELMET"));
		}
		catch(IllegalArgumentException ex364)
		{
		}
		try
		{
			this.list.put("538:0", Material.valueOf("NETHERITE_CHESTPLATE"));
		}
		catch(IllegalArgumentException ex365)
		{
		}
		try
		{
			this.list.put("539:0", Material.valueOf("NETHERITE_LEGGINGS"));
		}
		catch(IllegalArgumentException ex366)
		{
		}
		try
		{
			this.list.put("540:0", Material.valueOf("NETHERITE_BOOTS"));
		}
		catch(IllegalArgumentException ex367)
		{
		}
		try
		{
			this.list.put("541:0", Material.valueOf("WARPED_FUNGUS_ON_A_STICK"));
		}
		catch(IllegalArgumentException ex368)
		{
		}
		try
		{
			this.list.put("542:0", Material.valueOf("TARGET"));
		}
		catch(IllegalArgumentException ex369)
		{
		}
		try
		{
			this.list.put("543:0", Material.valueOf("ANCIENT_DEBRIS"));
		}
		catch(IllegalArgumentException ex370)
		{
		}
		try
		{
			this.list.put("544:0", Material.valueOf("BASALT"));
		}
		catch(IllegalArgumentException ex371)
		{
		}
		try
		{
			this.list.put("545:0", Material.valueOf("POLISHED_BASALT"));
		}
		catch(IllegalArgumentException ex372)
		{
		}
		try
		{
			this.list.put("546:0", Material.valueOf("NETHERITE_BLOCK"));
		}
		catch(IllegalArgumentException ex373)
		{
		}
		try
		{
			this.list.put("547:0", Material.valueOf("BLACKSTONE"));
		}
		catch(IllegalArgumentException ex374)
		{
		}
		try
		{
			this.list.put("548:0", Material.valueOf("GILDED_BLACKSTONE"));
		}
		catch(IllegalArgumentException ex375)
		{
		}
		try
		{
			this.list.put("549:0", Material.valueOf("POLISHED_BLACKSTONE"));
		}
		catch(IllegalArgumentException ex376)
		{
		}
		try
		{
			this.list.put("550:0", Material.valueOf("POLISHED_BLACKSTONE_BRICKS"));
		}
		catch(IllegalArgumentException ex377)
		{
		}
		try
		{
			this.list.put("551:0", Material.valueOf("CHISELED_POLISHED_BLACKSTONE"));
		}
		catch(IllegalArgumentException ex378)
		{
		}
		try
		{
			this.list.put("552:0", Material.valueOf("CRACKED_POLISHED_BLACKSTONE_BRICKS"));
		}
		catch(IllegalArgumentException ex379)
		{
		}
		try
		{
			this.list.put("553:0", Material.valueOf("POLISHED_BLACKSTONE_BUTTON"));
		}
		catch(IllegalArgumentException ex380)
		{
		}
		try
		{
			this.list.put("554:0", Material.valueOf("POLISHED_BLACKSTONE_PRESSURE_PLATE"));
		}
		catch(IllegalArgumentException ex381)
		{
		}
		try
		{
			this.list.put("555:0", Material.valueOf("CHAIN"));
		}
		catch(IllegalArgumentException ex382)
		{
		}
		try
		{
			this.list.put("556:0", Material.valueOf("SHROOMLIGHT"));
		}
		catch(IllegalArgumentException ex383)
		{
		}
		try
		{
			this.list.put("557:0", Material.valueOf("LODESTONE"));
		}
		catch(IllegalArgumentException ex384)
		{
		}
		try
		{
			this.list.put("558:0", Material.valueOf("RESPAWN_ANCHOR"));
		}
		catch(IllegalArgumentException ex385)
		{
		}
		try
		{
			this.list.put("559:0", Material.valueOf("SOUL_CAMPFIRE"));
		}
		catch(IllegalArgumentException ex386)
		{
		}
		try
		{
			this.list.put("560:0", Material.valueOf("SOUL_FIRE"));
		}
		catch(IllegalArgumentException ex387)
		{
		}
		try
		{
			this.list.put("561:0", Material.valueOf("SOUL_LANTERN"));
		}
		catch(IllegalArgumentException ex388)
		{
		}
		try
		{
			this.list.put("562:0", Material.valueOf("SOUL_SOIL"));
		}
		catch(IllegalArgumentException ex389)
		{
		}
		try
		{
			this.list.put("563:0", Material.valueOf("SOUL_TORCH"));
		}
		catch(IllegalArgumentException ex390)
		{
		}
		try
		{
			this.list.put("564:0", Material.valueOf("SOUL_WALL_TORCH"));
		}
		catch(IllegalArgumentException ex391)
		{
		}
		try
		{
			this.list.put("565:0", Material.valueOf("CHISELED_NETHER_BRICKS"));
		}
		catch(IllegalArgumentException ex392)
		{
		}
		try
		{
			this.list.put("566:0", Material.valueOf("CRACKED_NETHER_BRICKS"));
		}
		catch(IllegalArgumentException ex393)
		{
		}
		try
		{
			this.list.put("567:0", Material.valueOf("CRIMSON_DOOR"));
		}
		catch(IllegalArgumentException ex394)
		{
		}
		try
		{
			this.list.put("568:0", Material.valueOf("CRIMSON_FENCE"));
		}
		catch(IllegalArgumentException ex395)
		{
		}
		try
		{
			this.list.put("569:0", Material.valueOf("CRIMSON_FENCE_GATE"));
		}
		catch(IllegalArgumentException ex396)
		{
		}
		try
		{
			this.list.put("570:0", Material.valueOf("CRIMSON_ROOTS"));
		}
		catch(IllegalArgumentException ex397)
		{
		}
		try
		{
			this.list.put("571:0", Material.valueOf("CRIMSON_FUNGUS"));
		}
		catch(IllegalArgumentException ex398)
		{
		}
		try
		{
			this.list.put("572:0", Material.valueOf("CRIMSON_HYPHAE"));
		}
		catch(IllegalArgumentException ex399)
		{
		}
		try
		{
			this.list.put("573:0", Material.valueOf("CRIMSON_NYLIUM"));
		}
		catch(IllegalArgumentException ex400)
		{
		}
		try
		{
			this.list.put("574:0", Material.valueOf("CRIMSON_STEM"));
		}
		catch(IllegalArgumentException ex401)
		{
		}
		try
		{
			this.list.put("575:0", Material.valueOf("WARPED_DOOR"));
		}
		catch(IllegalArgumentException ex402)
		{
		}
		try
		{
			this.list.put("576:0", Material.valueOf("WARPED_FENCE"));
		}
		catch(IllegalArgumentException ex403)
		{
		}
		try
		{
			this.list.put("577:0", Material.valueOf("WARPED_FENCE_GATE"));
		}
		catch(IllegalArgumentException ex404)
		{
		}
		try
		{
			this.list.put("578:0", Material.valueOf("WARPED_ROOTS"));
		}
		catch(IllegalArgumentException ex405)
		{
		}
		try
		{
			this.list.put("579:0", Material.valueOf("WARPED_FUNGUS"));
		}
		catch(IllegalArgumentException ex406)
		{
		}
		try
		{
			this.list.put("580:0", Material.valueOf("WARPED_HYPHAE"));
		}
		catch(IllegalArgumentException ex407)
		{
		}
		try
		{
			this.list.put("581:0", Material.valueOf("WARPED_NYLIUM"));
		}
		catch(IllegalArgumentException ex408)
		{
		}
		try
		{
			this.list.put("582:0", Material.valueOf("WARPED_STEM"));
		}
		catch(IllegalArgumentException ex409)
		{
		}
		try
		{
			this.list.put("583:0", Material.valueOf("WARPED_WART_BLOCK"));
		}
		catch(IllegalArgumentException ex410)
		{
		}
		try
		{
			this.list.put("584:0", Material.valueOf("CRYING_OBSIDIAN"));
		}
		catch(IllegalArgumentException ex411)
		{
		}
		try
		{
			this.list.put("585:0", Material.valueOf("WEEPING_VINES"));
		}
		catch(IllegalArgumentException ex412)
		{
		}
		try
		{
			this.list.put("586:0", Material.valueOf("WEEPING_VINES_PLANT"));
		}
		catch(IllegalArgumentException ex413)
		{
		}
		try
		{
			this.list.put("587:0", Material.valueOf("TWISTING_VINES"));
		}
		catch(IllegalArgumentException ex414)
		{
		}
		try
		{
			this.list.put("588:0", Material.valueOf("TWISTING_VINES_PLANT"));
		}
		catch(IllegalArgumentException ex415)
		{
		}
		try
		{
			this.list.put("589:0", Material.valueOf("NETHER_GOLD_ORE"));
		}
		catch(IllegalArgumentException ex416)
		{
		}
		try
		{
			this.list.put("590:0", Material.valueOf("NETHER_SPROUTS"));
		}
		catch(IllegalArgumentException ex417)
		{
		}
		try
		{
			this.list.put("591:0", Material.valueOf("QUARTZ_BRICKS"));
		}
		catch(IllegalArgumentException ex418)
		{
		}
		try
		{
			this.list.put("592:0", Material.valueOf("PURPLE_SHULKER_BOX"));
		}
		catch(IllegalArgumentException ex419)
		{
		}
		try
		{
			this.list.put("593:0", Material.valueOf("KELP_PLANT"));
		}
		catch(IllegalArgumentException ex420)
		{
		}
	}

	public Material getMaterial(final String ID)
	{
		return this.list.get(ID);
	}

	public String getIDData(final Material M)
	{
		for(final Map.Entry<String, Material> m : this.list.entrySet())
		{
			if(m.getValue().equals((Object) M))
			{
				return m.getKey();
			}
		}
		return null;
	}

	public int getID(final Material M)
	{
		for(final Map.Entry<String, Material> m : this.list.entrySet())
		{
			if(m.getValue().equals((Object) M))
			{
				final String s = m.getKey();
				return Integer.parseInt(s.split(":")[0]);
			}
		}
		return -1;
	}

	public byte getData(final Material M)
	{
		for(final Map.Entry<String, Material> m : this.list.entrySet())
		{
			if(m.getValue().equals((Object) M))
			{
				final String s = m.getKey();
				return Byte.parseByte(s.split(":")[1]);
			}
		}
		return -1;
	}
}

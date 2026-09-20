package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.world.entity.IrisAttributeModifier;
import art.arcane.iris.world.entity.IrisEnchantment;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.volmlib.nativelib.item.NativeItemRecipe;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record IrisItemRecipe(IrisLoot loot, RNG random) implements NativeItemRecipe {
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    @Override
    public String typeKey() { return loot.getTypeKey(); }
    @Override
    public int amount() { return LootResolver.inclusive(random, loot.getMinAmount(), loot.getMaxAmount()); }
    @Override
    public double chance() { return random.nextDouble(); }
    @Override
    public boolean unbreakable() { return loot.isUnbreakable(); }
    @Override
    public List<String> itemFlags() { return loot.getItemFlags(); }
    @Override
    public Integer customModel() { return loot.getCustomModel(); }
    @Override
    public double durability() { return random.d(loot.getMinDurability(), loot.getMaxDurability()); }
    @Override
    public String leatherColor() { return loot.getLeatherColor(); }
    @Override
    public String dyeColor() { return loot.getDyeColorKey(); }
    @Override
    public String displayName() { return loot.getDisplayName() == null ? null : colorize(loot.getDisplayName()); }
    @Override
    public Map<String, Object> customNbt() { return loot.getCustomNbt(); }

    @Override
    public List<? extends Enchantment> enchantments() {
        if (loot.getEnchantments().isEmpty()) {
            return List.of();
        }
        List<Enchantment> entries = new ArrayList<>(loot.getEnchantments().size());
        for (IrisEnchantment enchantment : loot.getEnchantments()) {
            entries.add(new EnchantmentRecipe(enchantment, random));
        }
        return entries;
    }

    @Override
    public List<? extends Attribute> attributes() {
        if (loot.getAttributes().isEmpty()) {
            return List.of();
        }
        List<Attribute> entries = new ArrayList<>(loot.getAttributes().size());
        for (IrisAttributeModifier attribute : loot.getAttributes()) {
            entries.add(new AttributeRecipe(attribute, random));
        }
        return entries;
    }

    @Override
    public List<String> lore() {
        if (loot.getLore().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : loot.getLore()) {
            String colored = colorize(line);
            if (colored.length() > 24) {
                for (String wrapped : Form.wrapWords(colored, 24).split("\\Q\n\\E")) {
                    lines.add(wrapped.trim());
                }
            } else {
                lines.add(colored);
            }
        }
        return lines;
    }

    private static String colorize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private record EnchantmentRecipe(IrisEnchantment enchantment, RNG random) implements Enchantment {
        @Override
        public String name() { return enchantment.getEnchantment(); }
        @Override
        public double chance() { return enchantment.getChance(); }
        @Override
        public int level() { return enchantment.getLevel(random); }
    }

    private record AttributeRecipe(IrisAttributeModifier modifier, RNG random) implements Attribute {
        @Override
        public String attribute() { return modifier.getAttribute(); }
        @Override
        public String name() { return modifier.getName(); }
        @Override
        public String operation() { return modifier.getOperation(); }
        @Override
        public double chance() { return modifier.getChance(); }
        @Override
        public double amount() { return modifier.getAmount(random); }
    }
}

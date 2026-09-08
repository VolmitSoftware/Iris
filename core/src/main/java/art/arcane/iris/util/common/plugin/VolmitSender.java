/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.util.common.plugin;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.IrisSettings;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
    private final CommandSender s;
    private String tag;
    @Getter
    @Setter
    private String command;

    /**
     * Wrap a command sender
     *
     * @param s the command sender
     */
    public VolmitSender(CommandSender s) {
        tag = "";
        this.s = s;
    }

    public VolmitSender(CommandSender s, String tag) {
        this.tag = tag;
        this.s = s;
    }

    public static long getTick() {
        return M.ms() / 16;
    }

    public static String pulse(String colorA, String colorB, double speed) {
        return "<gradient:" + colorA + ":" + colorB + ":" + pulse(speed) + ">";
    }

    public static String pulse(double speed) {
        return Form.f(invertSpread((((getTick() * 15D * speed) % 1000D) / 1000D)), 3).replaceAll("\\Q,\\E", ".").replaceAll("\\Q?\\E", "-");
    }

    public static double invertSpread(double v) {
        return ((1D - v) * 2D) - 1D;
    }

    public static <T> KList<T> paginate(KList<T> all, int linesPerPage, int page, AtomicBoolean hasNext) {
        int totalPages = (int) Math.ceil((double) all.size() / linesPerPage);
        page = page < 0 ? 0 : page >= totalPages ? totalPages - 1 : page;
        hasNext.set(page < totalPages - 1);
        KList<T> d = new KList<>();

        for (int i = linesPerPage * page; i < Math.min(all.size(), linesPerPage * (page + 1)); i++) {
            d.add(all.get(i));
        }

        return d;
    }

    /**
     * Get the command tag
     *
     * @return the command tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * Set a command tag (prefix for sendMessage)
     *
     * @param tag the tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Is this sender a player?
     *
     * @return true if it is
     */
    public boolean isPlayer() {
        return getS() instanceof Player;
    }

    /**
     * Force cast to player (be sure to check first)
     *
     * @return a casted player
     */
    public Player player() {
        return (Player) getS();
    }

    /**
     * Get the origin sender this object is wrapping
     *
     * @return the command sender
     */
    public CommandSender getS() {
        return s;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return s.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return s.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(String name) {
        return s.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return s.hasPermission(perm);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return s.addAttachment(plugin, name, value);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return s.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return s.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return s.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        s.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        s.recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return s.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return s.isOp();
    }

    @Override
    public void setOp(boolean value) {
        s.setOp(value);
    }

    public void hr() {
        ComponentMessenger.sendLiteral(s, "========================================================");
    }

    public void sendProgress(double percent, String thing) {
        int l = 44;
        int g = (int) ((percent < 0 ? 1D : percent) * l);
        sendActionNoProcessing("" + "" + pulse("#00ff80", "#00373d", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
    }

    public void sendAction(String action) {
        try {
            deliverAction(createNoPrefixComponent(action));
        } catch (Throwable ignored) {
        }
    }

    public void sendActionNoProcessing(String action) {
        try {
            deliverAction(createNoPrefixComponentNoProcessing(action));
        } catch (Throwable ignored) {
        }
    }

    private void deliverAction(ComponentText message) {
        Player player = player();
        if (BukkitPlatform.hasHud()) {
            String legacy = message.legacy();
            if (legacy.isBlank()) {
                BukkitPlatform.hudBar().clear(player, "iris:action");
            } else {
                BukkitPlatform.hudBar().publish(player, new HudSegment("iris:action", HudPriority.PROGRESS, 3000L, java.util.List.of(HudSlot.CENTER, HudSlot.LEFT), legacy));
            }
            return;
        }
        ComponentMessenger.sendActionBar(player, message);
    }

    private ComponentText createNoPrefixComponent(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(message));
            return ComponentText.markup(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', message);
        String a = C.aura(t, IrisSettings.get().getGeneral().getSpinh(), IrisSettings.get().getGeneral().getSpins(), IrisSettings.get().getGeneral().getSpinb(), 0.36);
        return ComponentText.markup(a);
    }

    private ComponentText createNoPrefixComponentNoProcessing(String message) {
        return ComponentText.markup(C.mini(message));
    }

    private ComponentText createComponent(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return ComponentText.markup(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        String a = C.aura(t, IrisSettings.get().getGeneral().getSpinh(), IrisSettings.get().getGeneral().getSpins(), IrisSettings.get().getGeneral().getSpinb());
        return ComponentText.markup(a);
    }

    private ComponentText createComponentRaw(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return ComponentText.markup(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        return ComponentText.markup(C.mini(t));
    }

    @Override
    public void sendMessage(String message) {
        if ((!IrisSettings.get().getGeneral().isUseCustomColorsIngame() && s instanceof Player) || !IrisSettings.get().getGeneral().isUseConsoleCustomColors()) {
            ComponentMessenger.sendSection(s, C.translateAlternateColorCodes('&', getTag() + message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            ComponentMessenger.sendSection(
                    s,
                    C.translateAlternateColorCodes('&', getTag() + message.replace("<NOMINI>", "")));
            return;
        }

        deliver(createComponent(message));
    }

    public void sendMessageBasic(String message) {
        ComponentMessenger.sendSection(s, C.translateAlternateColorCodes('&', getTag() + message));
    }

    public void sendMessageRaw(String message) {
        if ((!IrisSettings.get().getGeneral().isUseCustomColorsIngame() && s instanceof Player) || !IrisSettings.get().getGeneral().isUseConsoleCustomColors()) {
            ComponentMessenger.sendSection(s, C.translateAlternateColorCodes('&', message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            ComponentMessenger.sendSection(s, message.replace("<NOMINI>", ""));
            return;
        }

        deliver(createComponentRaw(message));
    }

    public void sendComponent(Component component) {
        deliver(ComponentText.component(component));
    }

    private void deliver(ComponentText message) {
        ComponentMessenger.send(s, message);
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String str : messages)
            sendMessage(str);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(UUID uuid, String[] messages) {
        sendMessage(messages);
    }

    @Override
    public Server getServer() {
        return s.getServer();
    }

    @Override
    public String getName() {
        return s.getName();
    }

    @NotNull
    @Override
    public Component name() {
        return Component.text(getName());
    }

    @Override
    public Spigot spigot() {
        return s.spigot();
    }

    public void playSound(Sound sound, float volume, float pitch) {
        if (isPlayer()) {
            player().playSound(player().getLocation(), sound, volume, pitch);
        }
    }
}

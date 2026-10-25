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
package art.arcane.iris.studio.map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;

/**
 * Where the seed of a procedural object's variants is chosen: typed in, picked again from the seeds tried on this
 * object with the pack's own first, or rerolled at random. It keeps its size whatever it shows, so the bar it sits in
 * never shifts under the mouse.
 */
final class ObjectSeedChooser extends JPanel {
    private static final long serialVersionUID = 3127745038254406118L;
    private static final int FIELD_WIDTH = 120;
    private static final int TRIED_SEEDS_KEPT = 12;
    // Small enough to read back and type, and far more seeds than anyone tries.
    private static final int RANDOM_SEED_BOUND = 1_000_000;

    private final JComboBox<Long> seeds = new JComboBox<>();
    private final LongConsumer onSeedChosen;
    private final List<Long> triedSeedsNewestFirst = new ArrayList<>();
    private OptionalLong packSeed = OptionalLong.empty();
    private long shownSeed;
    private boolean showingSeeds;

    /**
     * @param onSeedChosen told the seed to bake the variants from whenever the user chooses another one
     */
    ObjectSeedChooser(LongConsumer onSeedChosen) {
        super(new BorderLayout(StudioTheme.scale(4), 0));
        this.onSeedChosen = onSeedChosen;
        setOpaque(false);
        JLabel label = new JLabel("Seed");
        label.setFont(StudioTheme.smallFont());
        label.setForeground(StudioTheme.SECONDARY_TEXT);
        seeds.setEditable(true);
        seeds.setFont(StudioTheme.smallFont());
        seeds.setToolTipText("Type a seed and press Enter, or pick one tried before");
        seeds.setRenderer(new PackSeedMarker());
        seeds.setPreferredSize(new Dimension(StudioTheme.scale(FIELD_WIDTH), seeds.getPreferredSize().height));
        seeds.addActionListener(event -> {
            if (!showingSeeds) {
                EventQueue.invokeLater(this::chooseEnteredSeed);
            }
        });
        add(label, BorderLayout.WEST);
        add(seeds, BorderLayout.CENTER);
        add(StudioToolbar.button("Reroll", "Bake the variants from a random seed", this::reroll), BorderLayout.EAST);
    }

    /** Starts a new object's list of tried seeds. */
    void forgetTriedSeeds() {
        triedSeedsNewestFirst.clear();
    }

    void showSeed(long shown, OptionalLong pack) {
        shownSeed = shown;
        packSeed = pack;
        if (!isPackSeed(shown)) {
            rememberTried(shown);
        }
        DefaultComboBoxModel<Long> choices = new DefaultComboBoxModel<>();
        pack.ifPresent(choices::addElement);
        triedSeedsNewestFirst.forEach(choices::addElement);
        showingSeeds = true;
        try {
            seeds.setModel(choices);
            seeds.setSelectedItem(shown);
        } finally {
            showingSeeds = false;
        }
    }

    private void rememberTried(long seed) {
        triedSeedsNewestFirst.remove(seed);
        triedSeedsNewestFirst.add(0, seed);
        while (triedSeedsNewestFirst.size() > TRIED_SEEDS_KEPT) {
            triedSeedsNewestFirst.remove(triedSeedsNewestFirst.size() - 1);
        }
    }

    // A pick from the list arrives as a seed, anything typed as text.
    private void chooseEnteredSeed() {
        OptionalLong entered = parse(seeds.getSelectedItem());
        if (entered.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(seeds);
            showSeed(shownSeed, packSeed);
            return;
        }
        if (entered.getAsLong() != shownSeed) {
            onSeedChosen.accept(entered.getAsLong());
        }
    }

    private static OptionalLong parse(Object entered) {
        if (entered instanceof Long seed) {
            return OptionalLong.of(seed);
        }
        try {
            return OptionalLong.of(Long.parseLong(String.valueOf(entered).trim()));
        } catch (NumberFormatException notASeed) {
            return OptionalLong.empty();
        }
    }

    private void reroll() {
        long seed;
        do {
            seed = ThreadLocalRandom.current().nextInt(RANDOM_SEED_BOUND);
        } while (seed == shownSeed);
        onSeedChosen.accept(seed);
    }

    private boolean isPackSeed(Object seed) {
        return packSeed.isPresent() && seed instanceof Long value && value == packSeed.getAsLong();
    }

    private final class PackSeedMarker extends DefaultListCellRenderer {
        private static final long serialVersionUID = -6061513307766393251L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (index >= 0 && isPackSeed(value)) {
                setText(value + "  (pack)");
            }
            return this;
        }
    }
}

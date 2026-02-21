package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.core.ExternalDataPackPipeline;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ExternalDatapackLocateHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Map<String, Set<String>> locateById = ExternalDataPackPipeline.snapshotLocateStructuresById();
        for (Map.Entry<String, Set<String>> entry : locateById.entrySet()) {
            if (entry == null) {
                continue;
            }

            String id = entry.getKey();
            if (id != null && !id.isBlank()) {
                tokens.add(id);
            }

            Set<String> structures = entry.getValue();
            if (structures == null || structures.isEmpty()) {
                continue;
            }

            for (String structure : structures) {
                if (structure != null && !structure.isBlank()) {
                    tokens.add(structure);
                }
            }
        }

        KList<String> possibilities = new KList<>();
        possibilities.add(tokens);
        return possibilities;
    }

    @Override
    public KList<String> getPossibilities(String input) {
        String rawInput = input == null ? "" : input;
        String[] split = rawInput.split(",", -1);
        String partial = split.length == 0 ? "" : split[split.length - 1].trim().toLowerCase(Locale.ROOT);
        StringBuilder prefixBuilder = new StringBuilder();
        if (split.length > 1) {
            for (int index = 0; index < split.length - 1; index++) {
                String value = split[index] == null ? "" : split[index].trim();
                if (value.isBlank()) {
                    continue;
                }
                if (!prefixBuilder.isEmpty()) {
                    prefixBuilder.append(',');
                }
                prefixBuilder.append(value);
            }
        }

        String prefix = prefixBuilder.toString();
        LinkedHashSet<String> completions = new LinkedHashSet<>();
        for (String possibility : getPossibilities()) {
            if (possibility == null || possibility.isBlank()) {
                continue;
            }
            String normalized = possibility.toLowerCase(Locale.ROOT);
            if (!partial.isBlank() && !normalized.startsWith(partial)) {
                continue;
            }

            if (prefix.isBlank()) {
                completions.add(possibility);
            } else {
                completions.add(prefix + "," + possibility);
            }
        }

        KList<String> results = new KList<>();
        results.add(completions);
        return results;
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        if (in == null || in.trim().isBlank()) {
            throw new DirectorParsingException("You must provide at least one external datapack id or structure id.");
        }

        return in.trim();
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        KList<String> possibilities = getPossibilities();
        String random = possibilities.getRandom();
        return random == null ? "external-datapack-id" : random;
    }
}

package jp.azisaba.lgw.ecplus.mythic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MMIDs this plugin must leave alone. An entry ending in {@code *} matches by prefix
 * ({@code Event_*}); any other entry must match the whole ID. Matching ignores case.
 * Immutable, so the async offline scan can share it.
 */
final class MmidExclusions {
    static final MmidExclusions NONE = new MmidExclusions(Set.of(), List.of());

    private final Set<String> exact;
    private final List<String> prefixes;

    private MmidExclusions(Set<String> exact, List<String> prefixes) {
        this.exact = exact;
        this.prefixes = prefixes;
    }

    static MmidExclusions parse(List<String> entries) {
        Set<String> exact = new HashSet<>();
        List<String> prefixes = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) continue;
            if (entry.endsWith("*")) {
                String prefix = entry.substring(0, entry.length() - 1);
                if (!prefix.isEmpty()) prefixes.add(prefix);
            } else {
                exact.add(entry);
            }
        }
        return new MmidExclusions(Set.copyOf(exact), List.copyOf(prefixes));
    }

    boolean isExcluded(String mmid) {
        if (mmid == null) return false;
        String id = mmid.trim().toLowerCase(Locale.ROOT);
        if (exact.contains(id)) return true;
        for (String prefix : prefixes) if (id.startsWith(prefix)) return true;
        return false;
    }

    String summary() {
        return "完全一致 " + exact.size() + "件・前方一致 " + prefixes.size() + "件";
    }
}

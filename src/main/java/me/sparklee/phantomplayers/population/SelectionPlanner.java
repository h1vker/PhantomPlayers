package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.identity.FakeIdentity;
import me.sparklee.phantomplayers.names.NameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class SelectionPlanner {

    private SelectionPlanner() {
    }

    public static List<FakeIdentity> selectSpawnAll(List<FakeIdentity> candidates, Set<String> activeKeys,
                                                    Set<String> recentKeys, int capacity,
                                                    Set<String> priorityKeys, Random random) {
        if (capacity <= 0) {
            return List.of();
        }
        List<FakeIdentity> priority = new ArrayList<>();
        List<FakeIdentity> fresh = new ArrayList<>();
        List<FakeIdentity> recent = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FakeIdentity identity : candidates) {
            if (!identity.enabled()) {
                continue;
            }
            String key = NameRules.key(identity.name());
            if (activeKeys.contains(key) || !seen.add(key)) {
                continue;
            }
            if (priorityKeys.contains(key)) {
                priority.add(identity);
            } else if (recentKeys.contains(key)) {
                recent.add(identity);
            } else {
                fresh.add(identity);
            }
        }
        Collections.shuffle(fresh, random);
        Collections.shuffle(recent, random);

        List<FakeIdentity> selected = new ArrayList<>(Math.min(capacity,
                priority.size() + fresh.size() + recent.size()));
        for (FakeIdentity identity : priority) {
            if (selected.size() >= capacity) {
                break;
            }
            selected.add(identity);
        }
        for (FakeIdentity identity : fresh) {
            if (selected.size() >= capacity) {
                break;
            }
            selected.add(identity);
        }
        for (FakeIdentity identity : recent) {
            if (selected.size() >= capacity) {
                break;
            }
            selected.add(identity);
        }
        return List.copyOf(selected);
    }
}

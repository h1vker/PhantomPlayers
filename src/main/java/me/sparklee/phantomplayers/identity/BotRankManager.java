package me.sparklee.phantomplayers.identity;

import me.sparklee.phantomplayers.names.NameRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Persists a small, randomly selected set of ranked fake-player identities.
 * The selected usernames are stable across despawn/spawn cycles and restarts.
 */
public final class BotRankManager {

    private static final Map<String, Integer> TARGETS = Map.of(
            "MASTER", 3,
            "IMMORTAL", 1,
            "PRINCE", 2,
            "WARRIOR", 4
    );

    private final Path file;
    private final Logger logger;
    private final Map<String, String> ranksByName = new LinkedHashMap<>();

    public BotRankManager(Path dataFolder, List<FakeIdentity> candidates, Logger logger) {
        this.file = dataFolder.resolve("bot-ranks.yml");
        this.logger = logger;
        boolean loaded = Files.exists(file) && load();
        if (!loaded) {
            assignRandom(candidates);
            save();
        }
    }

    public synchronized String rankOf(String name) {
        return ranksByName.getOrDefault(NameRules.key(name), "MEMBER");
    }

    public synchronized Set<String> rankedNameKeys() {
        return Set.copyOf(ranksByName.keySet());
    }

    public Component tagComponent(String name) {
        String rank = rankOf(name);
        NamedTextColor color = switch (rank) {
            case "MASTER" -> NamedTextColor.LIGHT_PURPLE;
            case "IMMORTAL" -> NamedTextColor.DARK_RED;
            case "PRINCE" -> NamedTextColor.GOLD;
            case "WARRIOR" -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
        return Component.text("[" + rank + "]", color).decorate(TextDecoration.BOLD);
    }

    private boolean load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        Map<String, String> loaded = new LinkedHashMap<>();
        for (String rank : TARGETS.keySet()) {
            for (String name : yaml.getStringList("ranks." + rank)) {
                if (!NameRules.isValid(name)) {
                    logger.warning("bot-ranks.yml: invalid username '" + name + "'; ignoring it.");
                    continue;
                }
                String key = NameRules.key(name);
                if (loaded.putIfAbsent(key, rank) != null) {
                    logger.warning("bot-ranks.yml: duplicate ranked username '" + name + "'; ignoring it.");
                }
            }
        }
        boolean valid = true;
        for (Map.Entry<String, Integer> target : TARGETS.entrySet()) {
            long count = loaded.values().stream().filter(target.getKey()::equals).count();
            if (count != target.getValue()) valid = false;
        }
        if (!valid) {
            logger.warning("bot-ranks.yml did not contain the required 3 Master, 1 Immortal, 2 Prince, and 4 Warrior bots; generating a fresh fixed assignment.");
            return false;
        }
        ranksByName.putAll(loaded);
        return true;
    }

    private void assignRandom(List<FakeIdentity> candidates) {
        List<FakeIdentity> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        int index = 0;
        for (Map.Entry<String, Integer> target : TARGETS.entrySet()) {
            for (int count = 0; count < target.getValue() && index < shuffled.size(); count++) {
                String name = shuffled.get(index++).name();
                ranksByName.put(NameRules.key(name), target.getKey());
            }
        }
        if (ranksByName.size() < 10) {
            logger.warning("Only " + candidates.size() + " names are available; could not assign all 10 special bot ranks.");
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ranks", null);
        for (String rank : TARGETS.keySet()) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, String> entry : ranksByName.entrySet()) {
                if (rank.equals(entry.getValue())) names.add(entry.getKey());
            }
            yaml.set("ranks." + rank, names);
        }
        try {
            Files.createDirectories(file.getParent());
            yaml.save(file.toFile());
        } catch (IOException exception) {
            logger.severe("Could not save bot-ranks.yml: " + exception.getMessage());
        }
    }
}

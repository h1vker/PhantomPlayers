package me.sparklee.phantomplayers.population;

import me.sparklee.phantomplayers.config.ConfigManager;
import me.sparklee.phantomplayers.identity.IdentityManager;
import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.SchedulerService;
import me.sparklee.phantomplayers.presentation.PresentationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rotates display-only fake players through the visible population.
 * Each identity gets one departure per shuffled cycle, preventing the same
 * small group from repeatedly appearing to join and leave.
 */
public final class PresenceRotationManager {

    private static final long MIN_NEXT_LEAVE_TICKS = 2_400L; // 2 minutes
    private static final long MAX_NEXT_LEAVE_TICKS = 6_000L; // 5 minutes
    private static final long MIN_AWAY_TICKS = 2_400L;       // 2 minutes
    private static final long MAX_AWAY_TICKS = 4_800L;       // 4 minutes
    private static final int MAX_SIMULTANEOUSLY_AWAY = 2;

    private final PopulationRegistry registry;
    private final IdentityManager identities;
    private final PresentationManager presentation;
    private final ConfigManager config;
    private final SchedulerService scheduler;
    private final Random random = new Random();
    private final List<String> cycle = new ArrayList<>();
    private final Set<String> away = new HashSet<>();
    private final Set<String> pending = new HashSet<>();

    private Cancellable nextLeave;
    private boolean running;
    private int cycleIndex;

    public PresenceRotationManager(PopulationRegistry registry, IdentityManager identities,
                                   PresentationManager presentation, ConfigManager config,
                                   SchedulerService scheduler) {
        this.registry = registry;
        this.identities = identities;
        this.presentation = presentation;
        this.config = config;
        this.scheduler = scheduler;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scheduleNextLeave();
    }

    public synchronized void stop() {
        running = false;
        if (nextLeave != null) {
            nextLeave.cancel();
            nextLeave = null;
        }
        for (String name : List.copyOf(pending)) {
            // Pending rejoin tasks are guarded by running and become harmless on shutdown.
        }
        pending.clear();
        away.clear();
        cycle.clear();
        cycleIndex = 0;
    }

    private synchronized void scheduleNextLeave() {
        if (!running) return;
        long delay = randomBetween(MIN_NEXT_LEAVE_TICKS, MAX_NEXT_LEAVE_TICKS);
        nextLeave = scheduler.runGlobalLater(delay, this::leaveOne);
    }

    private void leaveOne() {
        ActiveFakePlayer selected;
        synchronized (this) {
            if (!running) return;
            if (away.size() >= MAX_SIMULTANEOUSLY_AWAY) {
                scheduleNextLeave();
                return;
            }
            selected = selectNextActive();
            if (selected == null) {
                scheduleNextLeave();
                return;
            }
            String key = selected.identity().name().toLowerCase(java.util.Locale.ROOT);
            away.add(key);
            pending.add(key);
        }

        registry.remove(selected.identity().name()).ifPresent(removed -> {
            presentation.removeFakes(List.of(removed));
            Bukkit.getServer().sendMessage(formatPresenceMessage(removed.identity().name(), false));
            long awayTicks = randomBetween(MIN_AWAY_TICKS, MAX_AWAY_TICKS);
            scheduler.runGlobalLater(awayTicks, () -> rejoin(removed));
        });

        synchronized (this) {
            scheduleNextLeave();
        }
    }

    private ActiveFakePlayer selectNextActive() {
        List<ActiveFakePlayer> active = registry.snapshot();
        if (active.isEmpty()) return null;

        if (cycle.isEmpty() || cycleIndex >= cycle.size()) {
            cycle.clear();
            for (ActiveFakePlayer fake : active) {
                String key = fake.identity().name().toLowerCase(java.util.Locale.ROOT);
                if (!away.contains(key)) cycle.add(key);
            }
            Collections.shuffle(cycle, random);
            cycleIndex = 0;
        }

        while (cycleIndex < cycle.size()) {
            String candidate = cycle.get(cycleIndex++);
            for (ActiveFakePlayer fake : active) {
                if (fake.identity().name().equalsIgnoreCase(candidate)) return fake;
            }
        }

        // The active population may have changed since the cycle was shuffled.
        cycle.clear();
        cycleIndex = 0;
        return null;
    }

    private void rejoin(ActiveFakePlayer fake) {
        String key = fake.identity().name().toLowerCase(java.util.Locale.ROOT);
        synchronized (this) {
            if (!running) {
                pending.remove(key);
                away.remove(key);
                return;
            }
        }

        var restored = registry.activate(fake.identity(), fake.location(),
                config.settings().population().maxActive());
        if (restored.isPresent()) {
            presentation.addFakes(List.of(restored.get()));
            Bukkit.getServer().sendMessage(formatPresenceMessage(fake.identity().name(), true));
        }

        synchronized (this) {
            pending.remove(key);
            away.remove(key);
        }
    }

    private Component formatPresenceMessage(String name, boolean joining) {
        Component symbol = Component.text(joining ? "[+]" : "[-]",
                joining ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        return symbol
                .append(Component.space())
                .append(Component.text("[MEMBER]", NamedTextColor.GRAY)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(name, NamedTextColor.WHITE));
    }

    private long randomBetween(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min + 1));
    }
}

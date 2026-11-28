package me.sparklee.phantomplayers.chat;

import me.sparklee.phantomplayers.metrics.Metrics;
import me.sparklee.phantomplayers.identity.BotRankManager;
import me.sparklee.phantomplayers.platform.Cancellable;
import me.sparklee.phantomplayers.platform.SchedulerService;
import me.sparklee.phantomplayers.population.PopulationRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Random;

public final class ChatManager {

    private final Plugin plugin;
    private final SchedulerService scheduler;
    private final PopulationRegistry registry;
    private final Metrics metrics;
    private final BotRankManager botRanks;
    private final Path file;

    private ChatConfig config = ChatConfig.defaults();
    private ChatEngine engine;
    private boolean broadcastToConsole = true;

    public ChatManager(Plugin plugin, SchedulerService scheduler, PopulationRegistry registry, Metrics metrics,
                       BotRankManager botRanks) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.registry = registry;
        this.metrics = metrics;
        this.botRanks = botRanks;
        this.file = plugin.getDataFolder().toPath().resolve("chat.yml");
    }

    public ChatConfigLoader.Result load() {
        return ChatConfigLoader.load(file);
    }

    public void apply(ChatConfig next, boolean broadcastToConsole) {
        stop();
        this.config = next;
        this.broadcastToConsole = broadcastToConsole;
        if (!next.canRun()) {
            return;
        }
        engine = new ChatEngine(next,
                () -> registry.activeNames(),
                new BukkitDelivery(),
                new EngineScheduler(),
                new Random());
        engine.start();
    }

    public void stop() {
        if (engine != null) {
            engine.stop();
            engine = null;
        }
    }

    public ChatConfig config() {
        return config;
    }

    public boolean isRunning() {
        return engine != null && engine.isRunning();
    }

    private Component formatFakeChat(String line) {
        int separator = line.indexOf(": ");
        if (separator <= 0) {
            return Component.text(line);
        }
        String speaker = line.substring(0, separator);
        if (!registry.activeNames().contains(speaker)) {
            return Component.text(line);
        }
        String body = line.substring(separator + 2);
        return Component.text("[] ", NamedTextColor.GRAY)
                .append(botRanks.tagComponent(speaker))
                .append(Component.space())
                .append(Component.text(speaker, NamedTextColor.WHITE))
                .append(Component.text(" -> ", NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(body, NamedTextColor.WHITE));
    }

    private final class BukkitDelivery implements ChatEngine.Delivery {
        @Override
        public boolean hasAudience() {
            return !Bukkit.getOnlinePlayers().isEmpty();
        }

        @Override
        public void broadcast(String line) {
            Component message = formatFakeChat(line);
            if (broadcastToConsole) {
                Bukkit.getServer().sendMessage(message);
            } else {
                for (var player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
            }
            metrics.chatLine();
        }
    }

    private final class EngineScheduler implements ChatEngine.Scheduler {
        @Override
        public ChatEngine.Cancellable later(long delayTicks, Runnable task) {
            Cancellable handle = scheduler.runGlobalLater(delayTicks, task);
            return wrap(handle);
        }

        @Override
        public ChatEngine.Cancellable repeating(long periodTicks, Runnable task) {
            Cancellable handle = scheduler.runGlobalRepeating(periodTicks, periodTicks, task);
            return wrap(handle);
        }

        private ChatEngine.Cancellable wrap(Cancellable handle) {
            return handle::cancel;
        }
    }
}

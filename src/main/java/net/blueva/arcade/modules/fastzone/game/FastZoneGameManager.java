package net.blueva.arcade.modules.fastzone.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.fastzone.state.FastZoneArenaState;
import net.blueva.arcade.modules.fastzone.support.FastZoneDeathReason;
import net.blueva.arcade.modules.fastzone.support.FastZoneHazardService;
import net.blueva.arcade.modules.fastzone.support.FastZoneLoadoutService;
import net.blueva.arcade.modules.fastzone.support.FastZoneMessagingService;
import net.blueva.arcade.modules.fastzone.support.FastZoneStatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FastZoneGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final FastZoneStatsService statsService;
    private final FastZoneHazardService hazardService;
    private final FastZoneLoadoutService loadoutService;
    private final FastZoneMessagingService messagingService;

    private final Map<Integer, FastZoneArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArenas = new ConcurrentHashMap<>();

    public FastZoneGameManager(ModuleInfo moduleInfo,
                               ModuleConfigAPI moduleConfig,
                               CoreConfigAPI coreConfig,
                               FastZoneStatsService statsService,
                               FastZoneHazardService hazardService,
                               FastZoneLoadoutService loadoutService,
                               FastZoneMessagingService messagingService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.hazardService = hazardService;
        this.loadoutService = loadoutService;
        this.messagingService = messagingService;
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        FastZoneArenaState arenaState = new FastZoneArenaState(context);
        arenas.put(arenaId, arenaState);

        for (Player player : context.getPlayers()) {
            playerArenas.put(player, arenaId);
        }

        messagingService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int secondsLeft) {
        messagingService.sendCountdownTick(context, coreConfig, moduleInfo, secondsLeft);
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinish(context, coreConfig, moduleInfo);
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        startGameTimer(context);

        for (Player player : context.getPlayers()) {
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 60;
        }

        final int[] timeLeft = {gameTime};
        final int[] tickCount = {0};

        String taskId = "arena_" + arenaId + "_fast_zone_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            FastZoneArenaState arenaState = arenas.get(arenaId);
            if (arenaState == null || arenaState.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            tickCount[0]++;

            if (tickCount[0] % 2 == 0) {
                timeLeft[0]--;
            }

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();
            List<Player> spectators = context.getSpectators();

            if (shouldForceEnd(allPlayers, alivePlayers, spectators, timeLeft[0])) {
                endGameOnce(context);
                return;
            }

            String actionBarTemplate = coreConfig.getLanguage("action_bar.in_game.global");

            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                String actionBarMessage = formatActionBar(actionBarTemplate, context, timeLeft[0]);
                context.getMessagesAPI().sendActionBar(player, actionBarMessage);

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("round", String.valueOf(context.getCurrentRound()));
                customPlaceholders.put("round_max", String.valueOf(context.getMaxRounds()));

                List<Player> topPlayers = getTopPlayersByDistance(context);
                customPlaceholders.put("distance_1", topPlayers.size() >= 1 ? formatDistance(context, topPlayers.get(0)) : "-");
                customPlaceholders.put("distance_2", topPlayers.size() >= 2 ? formatDistance(context, topPlayers.get(1)) : "-");
                customPlaceholders.put("distance_3", topPlayers.size() >= 3 ? formatDistance(context, topPlayers.get(2)) : "-");
                customPlaceholders.put("distance_4", topPlayers.size() >= 4 ? formatDistance(context, topPlayers.get(3)) : "-");
                customPlaceholders.put("distance_5", topPlayers.size() >= 5 ? formatDistance(context, topPlayers.get(4)) : "-");

                context.getScoreboardAPI().update(player, customPlaceholders);
            }
        }, 0L, 10L);
    }

    private boolean shouldForceEnd(List<Player> allPlayers, List<Player> alivePlayers, List<Player> spectators, int timeLeft) {
        if (allPlayers.size() < 2) {
            return true;
        }

        if (alivePlayers.isEmpty()) {
            return true;
        }

        if (spectators.size() >= allPlayers.size()) {
            return true;
        }

        return timeLeft <= 0;
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        FastZoneArenaState arenaState = arenas.get(context.getArenaId());
        if (arenaState == null || !arenaState.markEnded()) {
            return;
        }

        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        if (alivePlayers.size() == 1 && !arenaState.hasWinner()) {
            recordWin(alivePlayers.get(0), context);
        } else if (alivePlayers.isEmpty() && !spectators.isEmpty() && !arenaState.hasWinner()) {
            recordWin(spectators.get(0), context);
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());
        context.endGame();
    }

    private String formatActionBar(String template, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int timeLeft) {
        if (template == null) {
            return "";
        }

        return template
                .replace("{time}", String.valueOf(timeLeft))
                .replace("{round}", String.valueOf(context.getCurrentRound()))
                .replace("{round_max}", String.valueOf(context.getMaxRounds()));
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        arenas.remove(arenaId);

        if (statsService.hasStatsApi()) {
            for (Player player : context.getPlayers()) {
                statsService.recordGamePlayed(player);
            }
        }

        playerArenas.entrySet().removeIf(entry -> entry.getValue().equals(arenaId));
    }

    public void handleDisable() {
        if (!arenas.isEmpty()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext = arenas.values().iterator().next().getContext();
            anyContext.getSchedulerAPI().cancelModuleTasks(moduleInfo.getId());
        }

        arenas.clear();
        playerArenas.clear();
    }

    public void handlePlayerFinish(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        if (statsService.hasStatsApi()) {
            statsService.recordFinishLineCross(player);
        }

        recordWin(player, context);
    }

    public void handlePlayerRespawn(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    private void recordWin(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        FastZoneArenaState arenaState = arenas.get(context.getArenaId());
        if (arenaState == null || arenaState.hasWinner()) {
            return;
        }

        arenaState.setWinnerId(player.getUniqueId());
        statsService.recordWin(player);
        context.setWinner(player);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            int position = calculateLivePosition(context, player);
            placeholders.put("fast_zone_position", String.valueOf(position));

            List<Player> topPlayers = getTopPlayersByDistance(context);
            placeholders.put("place_1", topPlayers.size() >= 1 ? topPlayers.get(0).getName() : "-");
            placeholders.put("place_2", topPlayers.size() >= 2 ? topPlayers.get(1).getName() : "-");
            placeholders.put("place_3", topPlayers.size() >= 3 ? topPlayers.get(2).getName() : "-");
            placeholders.put("place_4", topPlayers.size() >= 4 ? topPlayers.get(3).getName() : "-");
            placeholders.put("place_5", topPlayers.size() >= 5 ? topPlayers.get(4).getName() : "-");
        }

        return placeholders;
    }

    private int calculateLivePosition(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        if (spectators.contains(player)) {
            return spectators.indexOf(player) + 1;
        }

        Map<Player, Double> distances = new HashMap<>();
        for (Player candidate : alivePlayers) {
            if (!candidate.isOnline()) {
                continue;
            }

            double distance = getDistanceToFinish(context, candidate);
            distances.put(candidate, distance);
        }

        List<Map.Entry<Player, Double>> sorted = new ArrayList<>(distances.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(player)) {
                return spectators.size() + i + 1;
            }
        }

        return spectators.size() + alivePlayers.size();
    }

    private List<Player> getTopPlayersByDistance(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        List<Player> topPlayers = new ArrayList<>(spectators);

        Map<Player, Double> distances = new HashMap<>();
        for (Player candidate : alivePlayers) {
            if (!candidate.isOnline()) {
                continue;
            }

            double distance = getDistanceToFinish(context, candidate);
            distances.put(candidate, distance);
        }

        List<Map.Entry<Player, Double>> sorted = new ArrayList<>(distances.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        for (Map.Entry<Player, Double> entry : sorted) {
            topPlayers.add(entry.getKey());
        }

        return topPlayers;
    }

    private double getDistanceToFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        try {
            Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
            Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

            if (finishMin == null || finishMax == null) {
                return Double.MAX_VALUE;
            }

            double minX = Math.min(finishMin.getX(), finishMax.getX());
            double minY = Math.min(finishMin.getY(), finishMax.getY());
            double minZ = Math.min(finishMin.getZ(), finishMax.getZ());
            double maxX = Math.max(finishMin.getX(), finishMax.getX());
            double maxY = Math.max(finishMin.getY(), finishMax.getY());
            double maxZ = Math.max(finishMin.getZ(), finishMax.getZ());

            Location playerLoc = player.getLocation();

            double dx = 0;
            double dy = 0;
            double dz = 0;

            if (playerLoc.getX() < minX) dx = minX - playerLoc.getX();
            else if (playerLoc.getX() > maxX) dx = playerLoc.getX() - maxX;

            if (playerLoc.getY() < minY) dy = minY - playerLoc.getY();
            else if (playerLoc.getY() > maxY) dy = playerLoc.getY() - maxY;

            if (playerLoc.getZ() < minZ) dz = minZ - playerLoc.getZ();
            else if (playerLoc.getZ() > maxZ) dz = playerLoc.getZ() - maxZ;

            return Math.sqrt(dx * dx + dy * dy + dz * dz);

        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    private String formatDistance(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        if (context.getSpectators().contains(player)) {
            return "0";
        }

        double distance = getDistanceToFinish(context, player);
        if (distance == Double.MAX_VALUE) {
            return "?";
        }

        return String.format("%.0f", distance);
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, FastZoneDeathReason reason) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        messagingService.broadcastDeathMessage(context, player, getRandomMessage(reason.getMessagePath()));
    }

    public void handleWallCollision(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        playRespawnSound(context, player);
        playDeathEffect(player, player.getLocation());
        handlePlayerDeath(context, player, FastZoneDeathReason.WALL);
        context.respawnPlayer(player);
        handlePlayerRespawn(player);
    }

    public void playDeathEffect(Player player, Location location) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null || player == null) {
            return;
        }
        visualEffectsAPI.playDeathEffect(player, location != null ? location : player.getLocation());
    }

    public void broadcastFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, int position) {
        String message = getRandomMessage("messages.finish.crossed");
        messagingService.broadcastFinish(context, player, position, message);
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = playerArenas.get(player);
        if (arenaId == null) {
            return null;
        }

        FastZoneArenaState arenaState = arenas.get(arenaId);
        return arenaState != null ? arenaState.getContext() : null;
    }

    public boolean isWallCollisionEnabled() {
        return hazardService.isWallCollisionEnabled();
    }

    public double getWallCollisionDistance() {
        return hazardService.getWallCollisionDistance();
    }

    public Set<Material> getIgnoredCollisionBlocks() {
        return hazardService.getIgnoredCollisionBlocks();
    }

    public Material getDeathBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        return hazardService.getDeathBlock(context);
    }

    public boolean isInsideFinishLine(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) {
        return hazardService.isInsideFinishLine(context, location);
    }

    public void playRespawnSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));
    }

    public void playFinishSound(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.classified"));
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }
}

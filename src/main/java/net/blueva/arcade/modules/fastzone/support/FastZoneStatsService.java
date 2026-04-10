package net.blueva.arcade.modules.fastzone.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.entity.Player;

public class FastZoneStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;

    public FastZoneStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo, ModuleConfigAPI moduleConfig) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
    }

    public void registerStats() {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins", "Wins"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins", "Fast Zone wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played", "Games Played"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played", "Fast Zone games played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("finish_line_crosses", moduleConfig.getStringFrom("language.yml", "stats.labels.finish_line_crosses", "Zone finishes"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.finish_line_crosses", "Times you reached the fast zone finish line"), StatScope.MODULE));
    }

    public boolean hasStatsApi() {
        return statsAPI != null;
    }

    public void recordWin(Player player) {
        if (!hasStatsApi()) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
    }

    public void recordGamePlayed(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }

    public void recordFinishLineCross(Player player) {
        if (hasStatsApi()) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "finish_line_crosses", 1);
        }
    }
}

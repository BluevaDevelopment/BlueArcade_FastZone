package net.blueva.arcade.modules.fastzone.support;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class FastZoneMessagingService {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;

    public FastZoneMessagingService(ModuleConfigAPI moduleConfig, CoreConfigAPI coreConfig) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<String> description = moduleConfig.getStringListFrom("language.yml", "description");

        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public void sendCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, CoreConfigAPI coreConfig, ModuleInfo moduleInfo, int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage("titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage("titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void sendCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, CoreConfigAPI coreConfig, ModuleInfo moduleInfo) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            String title = coreConfig.getLanguage("titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage("titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void broadcastFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, int position, String message) {
        if (message == null) {
            return;
        }

        message = message
                .replace("{player}", player.getName())
                .replace("{position}", String.valueOf(position));

        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void broadcastDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, String message) {
        // Don't broadcast death messages for spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        if (message == null) {
            return;
        }

        message = message.replace("{player}", player.getName());
        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }
}

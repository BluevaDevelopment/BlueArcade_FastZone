package net.blueva.arcade.modules.fastzone.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.fastzone.game.FastZoneGameManager;
import net.blueva.arcade.modules.fastzone.support.FastZoneDeathReason;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Set;

public class FastZoneListener implements Listener {

    private final FastZoneGameManager gameManager;

    public FastZoneListener(FastZoneGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);
        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(player) || context.getSpectators().contains(player) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        if (context.getPhase() == GamePhase.COUNTDOWN) {
            event.setTo(from);
            return;
        }

        if (!context.isInsideBounds(to)) {
            Location deathLocation = player.getLocation();
            context.respawnPlayer(player);

            gameManager.playRespawnSound(context, player);
            gameManager.playDeathEffect(player, deathLocation);
            gameManager.handlePlayerDeath(context, player, FastZoneDeathReason.VOID);
            gameManager.handlePlayerRespawn(player);

            return;
        }

        Location blockAtHead = to.clone().add(0, player.getEyeHeight(), 0);
        Material blockAtHeadType = blockAtHead.getBlock().getType();

        Material deathBlock = gameManager.getDeathBlock(context);
        if (blockAtHeadType == deathBlock) {
            Location deathLocation = player.getLocation();
            context.respawnPlayer(player);

            gameManager.playRespawnSound(context, player);
            gameManager.playDeathEffect(player, deathLocation);
            gameManager.handlePlayerDeath(context, player, FastZoneDeathReason.DEATH_BLOCK);
            gameManager.handlePlayerRespawn(player);

            return;
        }

        if (gameManager.isWallCollisionEnabled() && hasCollidedWithWall(player, to)) {
            gameManager.handleWallCollision(context, player);
            return;
        }

        if (crossedFinishLine(context, from, to)) {
            if (!context.getSpectators().contains(player)) {
                context.finishPlayer(player);

                int position = context.getSpectators().indexOf(player) + 1;

                gameManager.handlePlayerFinish(player);
                gameManager.broadcastFinish(context, player, position);

                String title = gameManager.getModuleConfig().getStringFrom("language.yml", "titles.finished.title");
                String subtitle = gameManager.getModuleConfig().getStringFrom("language.yml", "titles.finished.subtitle")
                        .replace("{position}", String.valueOf(position));

                context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 80, 20);
                gameManager.playFinishSound(context, player);
            }
        }
    }

    private boolean hasCollidedWithWall(Player player, Location target) {
        Vector direction = target.getDirection();
        if (direction.lengthSquared() == 0) {
            return false;
        }

        double distance = Math.max(0.1, gameManager.getWallCollisionDistance());
        Location eyeLocation = target.clone().add(0, player.getEyeHeight(), 0);
        Location blockLocation = eyeLocation.clone().add(direction.normalize().multiply(distance));

        if (isBlocking(blockLocation.getBlock())) {
            return true;
        }

        // Only check head height, ignore feet to allow carpets and flowers
        return isBlocking(target.clone().add(0, player.getEyeHeight(), 0).getBlock());
    }

    private boolean isBlocking(Block block) {
        Material type = block.getType();
        Set<Material> ignoredCollisionBlocks = gameManager.getIgnoredCollisionBlocks();
        if (ignoredCollisionBlocks.contains(type)) {
            return false;
        }

        return !type.isAir() && !block.isPassable();
    }

    private boolean crossedFinishLine(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location from, Location to) {
        if (gameManager.isInsideFinishLine(context, to)) {
            return true;
        }

        double distance = from.distance(to);
        if (distance == 0) {
            return false;
        }

        int steps = Math.max(1, (int) Math.ceil(distance / 0.5));
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / steps);
        Location probe = from.clone();

        for (int i = 0; i <= steps; i++) {
            if (gameManager.isInsideFinishLine(context, probe)) {
                return true;
            }
            probe.add(step);
        }

        return false;
    }
}

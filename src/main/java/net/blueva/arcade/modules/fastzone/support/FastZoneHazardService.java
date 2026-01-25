package net.blueva.arcade.modules.fastzone.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FastZoneHazardService {

    private final ModuleConfigAPI moduleConfig;
    private final Set<Material> ignoredCollisionBlocks = new HashSet<>();

    public FastZoneHazardService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void loadIgnoredBlocks() {
        ignoredCollisionBlocks.clear();

        List<String> configured = moduleConfig.getStringList("hazards.wall_collision.ignore_blocks");
        if (configured != null) {
            for (String value : configured) {
                try {
                    ignoredCollisionBlocks.add(Material.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid materials to avoid breaking load
                }
            }
        }

        if (ignoredCollisionBlocks.isEmpty()) {
            ignoredCollisionBlocks.add(Material.AIR);
            ignoredCollisionBlocks.add(Material.CAVE_AIR);
            ignoredCollisionBlocks.add(Material.VOID_AIR);
        }
    }

    public boolean isWallCollisionEnabled() {
        return moduleConfig.getBoolean("hazards.wall_collision.enabled", true);
    }

    public double getWallCollisionDistance() {
        return moduleConfig.getDouble("hazards.wall_collision.check_distance", 1.0);
    }

    public Set<Material> getIgnoredCollisionBlocks() {
        return Collections.unmodifiableSet(ignoredCollisionBlocks);
    }

    public Material getDeathBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return Material.valueOf(deathBlockName.toUpperCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
            // Fallback
        }
        return Material.BARRIER;
    }

    public boolean isInsideFinishLine(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) {
        try {
            Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
            Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

            if (finishMin == null || finishMax == null) {
                return false;
            }

            return location.getX() >= Math.min(finishMin.getX(), finishMax.getX()) &&
                    location.getX() <= Math.max(finishMin.getX(), finishMax.getX()) &&
                    location.getY() >= Math.min(finishMin.getY(), finishMax.getY()) &&
                    location.getY() <= Math.max(finishMin.getY(), finishMax.getY()) &&
                    location.getZ() >= Math.min(finishMin.getZ(), finishMax.getZ()) &&
                    location.getZ() <= Math.max(finishMin.getZ(), finishMax.getZ());

        } catch (Exception e) {
            return false;
        }
    }
}

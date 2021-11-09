package me.william278.huskbungeertp.randomtp.processor;

import leafcraft.rtp.API.selection.TeleportRegion;
import leafcraft.rtp.RTP;
import me.william278.huskbungeertp.HuskBungeeRTP;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public class LeafRtp extends AbstractRtp {

    private final Plugin instance = HuskBungeeRTP.getInstance();

    @Override
    public void initialize() {}

    @Override
    public RandomResult getRandomLocation(World world, String targetBiomeString) {
        TeleportRegion region = this.getRegionFromWorld(world);
        Location location;

        if(region == null)
            return new RandomResult(null, false, 0);

        if (targetBiomeString.equalsIgnoreCase("ALL"))
            location = region.getRandomLocation(true);
        else {
            Biome targetBiome = Biome.valueOf(targetBiomeString.toUpperCase());
            location = region.getRandomLocation(true, targetBiome);
        }

        if(location != null)
            return new RandomResult(location, true, 1);
        else
            return new RandomResult(null, false, 0);
    }

    public TeleportRegion getRegionFromWorld(World world) {
        String WORLD_NAME = world.getName();

        if(!RTP.getConfigs().worlds.checkWorldExists(WORLD_NAME))
            return null;

        String findRegion = RTP.getConfigs().regions.getRegionNames().stream()
                .filter(regionName -> Objects.requireNonNull(RTP.getRegion(regionName)).world.getName().equals(WORLD_NAME))
                .findFirst()
                .orElse(null);

        if(findRegion == null)
            return null;

        return RTP.getRegion(findRegion);
    }
}

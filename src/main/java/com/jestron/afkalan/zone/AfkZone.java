package com.jestron.afkalan.zone;

/**
 * Değişmez AFK alanı. Ayar değişince yeni nesne oluşturulur (thread-safe okuma).
 * contains() saf aritmetik; nesne oluşturmaz.
 */
public final class AfkZone {

    private final String name;
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    // Blok sınırları dahil: [min, max+1)
    private final double bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ;
    private final int cooldown;
    private final double reward;
    private final double vipReward;
    private final String vipPermission;

    public AfkZone(String name, String world,
                   int x1, int y1, int z1, int x2, int y2, int z2,
                   int cooldown, double reward, double vipReward, String vipPermission) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.bMinX = minX;
        this.bMinY = minY;
        this.bMinZ = minZ;
        this.bMaxX = maxX + 1D;
        this.bMaxY = maxY + 1D;
        this.bMaxZ = maxZ + 1D;
        this.cooldown = Math.max(1, cooldown);
        this.reward = Math.max(0D, reward);
        this.vipReward = Math.max(0D, vipReward);
        this.vipPermission = vipPermission == null ? "" : vipPermission.trim();
    }

    /** Dünya kontrolü çağıran tarafta yapılır (dünya indeksinden gelir). */
    public boolean contains(double x, double y, double z) {
        return y >= bMinY && y < bMaxY
                && x >= bMinX && x < bMaxX
                && z >= bMinZ && z < bMaxZ;
    }

    public AfkZone with(int cooldown, double reward, double vipReward) {
        return new AfkZone(name, world, minX, minY, minZ, maxX, maxY, maxZ, cooldown, reward, vipReward, vipPermission);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public int getCooldown() { return cooldown; }
    public double getReward() { return reward; }
    public double getVipReward() { return vipReward; }
    public String getVipPermission() { return vipPermission; }
}

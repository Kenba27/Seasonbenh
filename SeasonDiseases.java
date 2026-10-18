package vn.seasonsdiseases;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SeasonsDiseases 1.1.0 - he thong benh tat theo mua cho plugin Seasons.
 */
public class SeasonDiseases extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ------------------------------------------------------------------ hang so
    static final Set<Material> RAW = EnumSet.of(Material.BEEF, Material.CHICKEN, Material.PORKCHOP,
            Material.MUTTON, Material.RABBIT, Material.COD, Material.SALMON);
    static final Set<Material> SPOILED = EnumSet.of(Material.ROTTEN_FLESH, Material.SPIDER_EYE,
            Material.POISONOUS_POTATO, Material.PUFFERFISH);
    static final Set<Material> BERRY = EnumSet.of(Material.SWEET_BERRIES, Material.GLOW_BERRIES);
    static final Set<Material> SWEET = EnumSet.of(Material.COOKIE, Material.PUMPKIN_PIE);
    static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS);
    static final Set<Material> DRINKS = EnumSet.of(Material.POTION, Material.MILK_BUCKET,
            Material.MELON_SLICE, Material.HONEY_BOTTLE);
    static final Set<Material> HEAT = EnumSet.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE,
            Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK, Material.FURNACE, Material.BLAST_FURNACE,
            Material.SMOKER);

    static final Map<String, String> SEASON_NAMES = Map.of(
            "SPRING", "Mùa Xuân", "SUMMER", "Mùa Hè", "AUTUMN", "Mùa Thu", "WINTER", "Mùa Đông",
            "ALL", "Quanh năm");

    static final Map<String, String> EFFECT_NAMES = Map.ofEntries(
            Map.entry("slowness", "Chậm chạp"), Map.entry("weakness", "Yếu ớt"),
            Map.entry("nausea", "Buồn nôn"), Map.entry("hunger", "Đói lả"),
            Map.entry("mining_fatigue", "Mệt mỏi (đào chậm)"), Map.entry("blindness", "Mờ mắt"),
            Map.entry("darkness", "Tối sầm"), Map.entry("poison", "Trúng độc"),
            Map.entry("wither", "Suy kiệt"));

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    // ------------------------------------------------------------------ trang thai
    private final Map<String, Disease> diseases = new LinkedHashMap<>();
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Set<String> warned = new HashSet<>();

    private File dataFile;
    private YamlConfiguration data;

    // config
    private int maxDiseases;
    private long immunityMs;
    private long eventWindowMs;
    private boolean cureOnDeath;
    private String fallbackSeason;
    private boolean hud;
    private boolean worsenEnabled;
    private boolean contagionEnabled;
    private double contagionRadius;
    private double contagionChance;
    private boolean maskRecipe;
    private boolean doctorEnabled;
    private int doctorCost;
    private boolean epidemicEnabled;
    private long epidemicCheckMs;
    private double epidemicChance;
    private long epidemicDurationMs;
    private double epidemicMultiplier;

    // dich benh
    private String epidemicId;
    private long epidemicEnd;
    private long epidemicTotal;
    private BossBar epidemicBar;
    private long lastEpidemicCheck;

    // Seasons (reflection, khong can jar Seasons de build)
    private Object seasonsPlugin;
    private boolean seasonsFailed;
    private Method mGetSeasonManager;
    private Method mGetCurrentSeason;
    private Method mGetPlayerManager;
    private Method mGetPlayerTemperature;
    private Method mGetTemperature;

    private NamespacedKey maskKey;

    static final class PlayerState {
        final Map<String, Long> active = new LinkedHashMap<>();
        final Map<String, Long> immune = new HashMap<>();
        final Map<String, Integer> counters = new HashMap<>();
        final Map<String, Long> eventTime = new HashMap<>();
        final Map<String, int[]> progress = new HashMap<>();
        final Set<String> worsened = new HashSet<>();
        long lastDrink = System.currentTimeMillis();
        long allImmune = 0L;
        int swim;
        int fire;
    }

    // ------------------------------------------------------------------ vong doi
    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadAll();

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("benh");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        registerMaskRecipe();
        lastEpidemicCheck = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) loadState(p);

        getServer().getScheduler().runTaskTimer(this, this::tickAll, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);
        getLogger().info("Da nap " + diseases.size() + " benh.");
    }

    @Override
    public void onDisable() {
        stopEpidemic(false);
        saveAll();
        if (maskKey != null) {
            try {
                Bukkit.removeRecipe(maskKey);
            } catch (Throwable ignored) {
                // bo qua
            }
        }
    }

    private void saveAll() {
        for (Player p : Bukkit.getOnlinePlayers()) storeState(p.getUniqueId());
        saveData();
    }

    // ------------------------------------------------------------------ nap cau hinh
    private void loadAll() {
        reloadConfig();
        FileConfiguration c = getConfig();
        maxDiseases = Math.max(1, c.getInt("max-diseases", 2));
        immunityMs = Math.max(0L, c.getLong("immunity-seconds", 180L)) * 1000L;
        eventWindowMs = Math.max(1L, c.getLong("event-window-seconds", 120L)) * 1000L;
        cureOnDeath = c.getBoolean("cure-on-death", true);
        fallbackSeason = c.getString("fallback-season", "SPRING").toUpperCase(Locale.ROOT);
        if (!SEASON_NAMES.containsKey(fallbackSeason) || fallbackSeason.equals("ALL")) fallbackSeason = "SPRING";
        hud = c.getBoolean("hud", true);
        worsenEnabled = c.getBoolean("worsen.enabled", true);
        contagionEnabled = c.getBoolean("contagion.enabled", true);
        contagionRadius = Math.max(1.0, c.getDouble("contagion.radius", 3.0));
        contagionChance = Math.max(0.0, Math.min(1.0, c.getDouble("contagion.chance", 0.12)));
        maskRecipe = c.getBoolean("mask.recipe", true);
        doctorEnabled = c.getBoolean("doctor.enabled", true);
        doctorCost = Math.max(0, c.getInt("doctor.cost-emeralds", 2));
        epidemicEnabled = c.getBoolean("epidemic.enabled", true);
        epidemicCheckMs = Math.max(1L, c.getLong("epidemic.check-minutes", 30L)) * 60000L;
        epidemicChance = Math.max(0.0, Math.min(1.0, c.getDouble("epidemic.chance", 0.25)));
        epidemicDurationMs = Math.max(1L, c.getLong("epidemic.duration-minutes", 15L)) * 60000L;
        epidemicMultiplier = Math.max(1.0, c.getDouble("epidemic.chance-multiplier", 2.5));
        seasonsFailed = false;
        seasonsPlugin = null;
        mGetSeasonManager = null;
        mGetCurrentSeason = null;
        mGetPlayerManager = null;
        mGetPlayerTemperature = null;
        mGetTemperature = null;

        // diseases.yml (neu la ban cu 1.0.x thi sao luu va tao ban moi)
        File f = new File(getDataFolder(), "diseases.yml");
        if (!f.exists()) {
            saveResource("diseases.yml", false);
        } else {
            YamlConfiguration probe = YamlConfiguration.loadConfiguration(f);
            if (probe.getInt("version", 1) < 2) {
                File old = new File(getDataFolder(), "diseases-1.0.yml.bak");
                if (old.exists()) old.delete();
                if (f.renameTo(old)) {
                    saveResource("diseases.yml", false);
                    getLogger().warning("diseases.yml cu (1.0.x) da duoc luu thanh diseases-1.0.yml.bak, "
                            + "da tao diseases.yml moi (31 benh cu + benh moi). Hay chep lai chinh sua cua ban neu can.");
                }
            }
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        diseases.clear();
        ConfigurationSection root = y.getConfigurationSection("diseases");
        if (root == null) {
            getLogger().warning("diseases.yml khong co muc 'diseases'.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Trigger trigger = Trigger.valueOf(s.getString("trigger", "").toUpperCase(Locale.ROOT));
                List<Disease.EffectSpec> effects = new ArrayList<>();
                for (String raw : s.getStringList("symptoms")) {
                    try {
                        effects.add(parseEffect(raw));
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Benh " + id + ": hieu ung khong hop le: " + raw);
                    }
                }
                List<Cure> cures = new ArrayList<>();
                for (String raw : s.getStringList("cures")) {
                    try {
                        cures.add(Cure.parse(raw));
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Benh " + id + ": cach chua khong hop le: " + ex.getMessage());
                    }
                }
                String season = s.getString("season", "ALL").toUpperCase(Locale.ROOT);
                diseases.put(id, new Disease(id, s.getString("name", id), season, trigger,
                        Math.max(1, s.getInt("threshold", 1)), s.getDouble("chance", 1.0),
                        s.getDouble("param", 0.0), Math.max(10, s.getInt("duration", 300)),
                        s.getString("description", ""), s.getString("symptom-message", ""),
                        s.getString("prevention", ""), effects, cures, s.getBoolean("contagious", false)));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bo qua benh " + id + ": " + ex.getMessage());
            }
        }
    }

    private Disease.EffectSpec parseEffect(String raw) {
        String[] a = raw.split(":");
        String key = a[0].trim().toLowerCase(Locale.ROOT);
        int amp = 0;
        if (a.length > 1) {
            try {
                amp = Integer.parseInt(a[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cap khong hop le: " + raw);
            }
        }
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (t == null) t = PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
        if (t == null) throw new IllegalArgumentException("khong co hieu ung " + key);
        return new Disease.EffectSpec(t, Math.max(0, amp), key);
    }

    // ------------------------------------------------------------------ luu tru
    private PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new PlayerState());
    }

    private void loadState(Player p) {
        PlayerState st = state(p);
        st.active.clear();
        st.immune.clear();
        st.worsened.clear();
        st.progress.clear();
        long now = System.currentTimeMillis();
        String base = "players." + p.getUniqueId();
        ConfigurationSection a = data.getConfigurationSection(base + ".active");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                long end = a.getLong(id);
                if (end > now && diseases.containsKey(id)) st.active.put(id, end);
            }
        }
        ConfigurationSection im = data.getConfigurationSection(base + ".immune");
        if (im != null) {
            for (String id : im.getKeys(false)) {
                long until = im.getLong(id);
                if (until > now) st.immune.put(id, until);
            }
        }
        st.allImmune = data.getLong(base + ".allImmune", 0L);
        st.lastDrink = now;
    }

    private void storeState(UUID uuid) {
        PlayerState st = states.get(uuid);
        if (st == null) return;
        String base = "players." + uuid;
        data.set(base, null);
        for (Map.Entry<String, Long> en : st.active.entrySet()) {
            data.set(base + ".active." + en.getKey(), en.getValue());
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : st.immune.entrySet()) {
            if (en.getValue() > now) data.set(base + ".immune." + en.getKey(), en.getValue());
        }
        if (st.allImmune > now) data.set(base + ".allImmune", st.allImmune);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Khong luu duoc data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ tien ich
    static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void actionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(c(msg)));
        } catch (Throwable t) {
            p.sendMessage(c(msg));
        }
    }

    private void warnOnce(String where, Throwable t) {
        if (warned.add(where)) {
            getLogger().warning("Loi o " + where + ": " + t);
        }
    }

    static String fmt(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    static String roman(int amp) {
        return amp >= 0 && amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp + 1);
    }

    static String seasonName(String key) {
        return SEASON_NAMES.getOrDefault(key, key);
    }

    private boolean seasonOk(Disease d, String season) {
        return d.season.equals("ALL") || d.season.equalsIgnoreCase(season);
    }

    private Disease find(String key) {
        Disease d = diseases.get(key.toLowerCase(Locale.ROOT));
        if (d != null) return d;
        for (Disease x : diseases.values()) {
            String stripped = ChatColor.stripColor(x.name);
            if (x.id.equalsIgnoreCase(key) || (stripped != null && stripped.equalsIgnoreCase(key))) return x;
        }
        return null;
    }

    private boolean exempt(Player p) {
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    // ------------------------------------------------------------------ Seasons (reflection)
    private String currentSeason() {
        try {
            if (seasonsPlugin == null) seasonsPlugin = getServer().getPluginManager().getPlugin("Seasons");
            if (seasonsPlugin != null && !seasonsFailed) {
                if (mGetSeasonManager == null) {
                    mGetSeasonManager = seasonsPlugin.getClass().getMethod("getSeasonManager");
                }
                Object sm = mGetSeasonManager.invoke(seasonsPlugin);
                if (sm != null) {
                    if (mGetCurrentSeason == null) mGetCurrentSeason = sm.getClass().getMethod("getCurrentSeason");
                    Object s = mGetCurrentSeason.invoke(sm);
                    if (s != null) {
                        String n = (s instanceof Enum<?> en) ? en.name() : String.valueOf(s);
                        n = n.toUpperCase(Locale.ROOT);
                        if (SEASON_NAMES.containsKey(n) && !n.equals("ALL")) return n;
                    }
                }
            }
        } catch (Throwable t) {
            if (!seasonsFailed) {
                seasonsFailed = true;
                getLogger().warning("Khong doc duoc mua tu plugin Seasons, dung fallback-season: " + t);
            }
        }
        return fallbackSeason;
    }

    private Double temperature(Player p) {
        try {
            if (seasonsPlugin == null || seasonsFailed) return null;
            if (mGetPlayerManager == null) {
                mGetPlayerManager = seasonsPlugin.getClass().getMethod("getPlayerManager");
            }
            Object pm = mGetPlayerManager.invoke(seasonsPlugin);
            if (pm == null) return null;
            if (mGetPlayerTemperature == null) {
                mGetPlayerTemperature = pm.getClass().getMethod("getPlayerTemperature", UUID.class);
            }
            Object pt = mGetPlayerTemperature.invoke(pm, p.getUniqueId());
            if (pt == null) return null;
            if (mGetTemperature == null) mGetTemperature = pt.getClass().getMethod("getTemperature");
            Object t = mGetTemperature.invoke(pt);
            return (t instanceof Number n) ? Double.valueOf(n.doubleValue()) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ngu canh kiem tra
    static final class Ctx {
        final Player p;
        final PlayerState st;
        final long now;
        final Double temp;
        private Boolean outside;
        private Boolean warm;
        private Boolean nearHeat;
        private String biome;

        Ctx(Player p, PlayerState st, long now, Double temp) {
            this.p = p;
            this.st = st;
            this.now = now;
            this.temp = temp;
        }

        boolean outside() {
            if (outside == null) {
                Location l = p.getLocation();
                outside = l.getWorld().getHighestBlockYAt(l) <= l.getBlockY();
            }
            return outside;
        }

        String biome() {
            if (biome == null) {
                try {
                    biome = String.valueOf(p.getLocation().getBlock().getBiome()).toLowerCase(Locale.ROOT);
                } catch (Throwable t) {
                    biome = "";
                }
            }
            return biome;
        }

        boolean snowy() {
            String b = biome();
            return b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove")
                    || b.contains("jagged");
        }

        boolean swampBiome() {
            String b = biome();
            return b.contains("swamp") || b.contains("mangrove");
        }

        boolean day() {
            long t = p.getWorld().getTime();
            return t < 12000L;
        }

        boolean night() {
            long t = p.getWorld().getTime();
            return t >= 13000L && t <= 23000L;
        }

        int leatherPieces() {
            int n = 0;
            for (ItemStack a : p.getInventory().getArmorContents()) {
                if (a != null && a.getType().name().startsWith("LEATHER_")) n++;
            }
            return n;
        }

        boolean nearHeat() {
            if (nearHeat == null) {
                nearHeat = false;
                Location l = p.getLocation();
                World w = l.getWorld();
                int bx = l.getBlockX();
                int by = l.getBlockY();
                int bz = l.getBlockZ();
                outer:
                for (int x = bx - 3; x <= bx + 3; x++) {
                    for (int y = by - 1; y <= by + 2; y++) {
                        for (int z = bz - 3; z <= bz + 3; z++) {
                            if (HEAT.contains(w.getBlockAt(x, y, z).getType())) {
                                nearHeat = true;
                                break outer;
                            }
                        }
                    }
                }
            }
            return nearHeat;
        }

        boolean warm() {
            if (warm == null) warm = leatherPieces() >= 3 || nearHeat();
            return warm;
        }

        int flowersAround() {
            Location l = p.getLocation();
            World w = l.getWorld();
            int n = 0;
            for (int x = l.getBlockX() - 2; x <= l.getBlockX() + 2; x++) {
                for (int y = l.getBlockY() - 1; y <= l.getBlockY() + 2; y++) {
                    for (int z = l.getBlockZ() - 2; z <= l.getBlockZ() + 2; z++) {
                        if (Tag.FLOWERS.isTagged(w.getBlockAt(x, y, z).getType())) n++;
                    }
                }
            }
            return n;
        }

        boolean noHelmet() {
            ItemStack h = p.getInventory().getHelmet();
            return h == null || h.getType() == Material.AIR;
        }

        boolean test(Disease d) {
            World w = p.getWorld();
            switch (d.trigger) {
                case RAIN:
                    return w.hasStorm() && outside() && !snowy();
                case COLD_TEMP:
                    return temp != null && temp < d.param && !warm();
                case HEAT_TEMP:
                    return temp != null && temp > d.param;
                case FLOWERS:
                    return flowersAround() >= 3;
                case NIGHT_OUTSIDE:
                    return night() && outside();
                case CROWD: {
                    int n = 0;
                    for (Entity e : p.getNearbyEntities(5, 3, 5)) {
                        if (e instanceof Villager) {
                            n++;
                        } else if (e instanceof Player o && o.getGameMode() != GameMode.SPECTATOR) {
                            n++;
                        }
                    }
                    return n >= 3;
                }
                case WATER_SWIM:
                    return p.isInWater();
                case SWEAT_ARMOR: {
                    ItemStack chest = p.getInventory().getChestplate();
                    if (chest == null || !day() || !outside()) return false;
                    String n = chest.getType().name();
                    return n.startsWith("IRON") || n.startsWith("DIAMOND") || n.startsWith("NETHERITE")
                            || n.startsWith("GOLDEN") || n.startsWith("CHAINMAIL");
                }
                case SUN:
                    return day() && outside() && noHelmet() && !w.hasStorm();
                case MOSQUITO: {
                    String b = biome();
                    return outside() && !w.hasStorm() && (swampBiome() || b.contains("jungle"));
                }
                case HUNGRY_LOW:
                    return p.getFoodLevel() <= 6;
                case DEHYDRATION:
                    return (now - st.lastDrink) / 1000.0 >= d.param;
                case SWAMP:
                    return swampBiome();
                case HIGH_ALTITUDE:
                    return p.getLocation().getY() >= d.param;
                case SNOW_BIOME:
                    return snowy() && outside();
                case SNOW_GLARE:
                    return snowy() && day() && outside() && noHelmet() && !w.hasStorm();
                case INSOMNIA:
                    try {
                        return p.getStatistic(Statistic.TIME_SINCE_REST) >= d.param;
                    } catch (Throwable t) {
                        return false;
                    }
                case THUNDER:
                    return w.isThundering() && outside();
                case UNDERGROUND:
                    return !outside() && p.getLocation().getY() <= d.param;
                default:
                    return false;
            }
        }
    }

    // ------------------------------------------------------------------ vong kiem tra 5 giay
    private void tickAll() {
        long now = System.currentTimeMillis();
        String season = currentSeason();
        try {
            tickEpidemic(now, season);
        } catch (Throwable t) {
            warnOnce("tickEpidemic", t);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(p, season, now);
            } catch (Throwable t) {
                warnOnce("tick " + p.getName(), t);
            }
        }
        if (contagionEnabled) {
            try {
                tickContagion(now);
            } catch (Throwable t) {
                warnOnce("tickContagion", t);
            }
        }
    }

    private int stage(PlayerState st, Disease d, long now) {
        Long end = st.active.get(d.id);
        if (!worsenEnabled || end == null) return 1;
        long total = d.durationSec * 1000L;
        return (end - now) < total / 2 ? 2 : 1;
    }

    private void tickPlayer(Player p, String season, long now) {
        PlayerState st = state(p);
        if (exempt(p)) return;

        // het han -> tu khoi
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> en : st.active.entrySet()) {
            if (now >= en.getValue()) expired.add(en.getKey());
        }
        for (String id : expired) {
            Disease d = diseases.get(id);
            if (d == null) {
                st.active.remove(id);
                continue;
            }
            finishDisease(p, d, "tự khỏi", true);
            p.sendMessage(c("&a💚 Bạn đã tự khỏi bệnh &e" + d.name + "&a."));
        }

        // cach chua bang moi truong
        boolean needSwim = false;
        boolean needFire = false;
        for (String id : st.active.keySet()) {
            Disease d = diseases.get(id);
            if (d == null) continue;
            if (d.hasCure(Cure.Kind.SWIM)) needSwim = true;
            if (d.hasCure(Cure.Kind.FIRE)) needFire = true;
        }
        Ctx ctx = new Ctx(p, st, now, temperature(p));
        if (needSwim) {
            if (p.isInWater()) {
                if (++st.swim >= 2) {
                    st.swim = 0;
                    cureByKind(p, Cure.Kind.SWIM, "đã tắm mát");
                }
            } else {
                st.swim = 0;
            }
        }
        if (needFire) {
            if (ctx.nearHeat()) {
                if (++st.fire >= 2) {
                    st.fire = 0;
                    cureByKind(p, Cure.Kind.FIRE, "đã sưởi ấm");
                }
            } else {
                st.fire = 0;
            }
        }

        // trieu chung + HUD
        boolean sentMsg = false;
        List<String> hudParts = new ArrayList<>();
        for (Map.Entry<String, Long> en : new ArrayList<>(st.active.entrySet())) {
            Disease d = diseases.get(en.getKey());
            if (d == null) continue;
            int stg = stage(st, d, now);
            if (stg == 2 && st.worsened.add(d.id)) {
                p.sendMessage(c("&4⚠ Bệnh &e" + d.name + " &4đang nặng hơn! Hãy chữa sớm (gõ &e/benh&4)."));
            }
            applySymptoms(p, d, stg);
            if (!sentMsg && !d.symptomMessage.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.25) {
                actionBar(p, d.symptomMessage);
                sentMsg = true;
            }
            hudParts.add((stg == 2 ? "&4" : "&c") + "🦠 " + d.name + " &7(" + fmt(en.getValue() - now) + ")");
        }
        if (hud && !sentMsg && !hudParts.isEmpty()) {
            actionBar(p, String.join("  ", hudParts));
        }

        // mac benh moi
        if (p.hasPermission("seasondiseases.immune")) return;
        if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        for (Disease d : diseases.values()) {
            if (d.trigger.event) continue;
            if (!seasonOk(d, season)) continue;
            if (st.active.containsKey(d.id)) continue;
            Long im = st.immune.get(d.id);
            if (im != null && im > now) continue;
            if (ctx.test(d)) {
                int cnt = st.counters.merge(d.id, 1, Integer::sum);
                int thr = d.threshold;
                double chance = d.chance;
                if (d.id.equals(epidemicId)) {
                    thr = Math.max(1, thr / 2);
                    chance = Math.min(1.0, chance * epidemicMultiplier);
                }
                if (cnt >= thr) {
                    st.counters.remove(d.id);
                    if (ThreadLocalRandom.current().nextDouble() < chance) infect(p, d, false);
                }
            } else {
                st.counters.remove(d.id);
            }
        }
    }

    // ------------------------------------------------------------------ lay lan
    private boolean hasMask(Player p) {
        ItemStack h = p.getInventory().getHelmet();
        if (h == null || h.getType() == Material.AIR) return false;
        ItemMeta m = h.getItemMeta();
        if (m == null || !m.hasDisplayName()) return false;
        String n = ChatColor.stripColor(m.getDisplayName());
        return n != null && n.toLowerCase(Locale.ROOT).contains("khẩu trang");
    }

    private void tickContagion(long now) {
        for (Player src : Bukkit.getOnlinePlayers()) {
            PlayerState st = states.get(src.getUniqueId());
            if (st == null || st.active.isEmpty() || exempt(src) || hasMask(src)) continue;
            List<Disease> spreaders = new ArrayList<>();
            for (Map.Entry<String, Long> en : st.active.entrySet()) {
                Disease d = diseases.get(en.getKey());
                if (d != null && d.contagious && en.getValue() > now) spreaders.add(d);
            }
            if (spreaders.isEmpty()) continue;
            for (Entity en : src.getNearbyEntities(contagionRadius, contagionRadius, contagionRadius)) {
                if (!(en instanceof Player other) || exempt(other) || hasMask(other)) continue;
                for (Disease d : spreaders) {
                    if (ThreadLocalRandom.current().nextDouble() < contagionChance) {
                        if (infect(other, d, false, true)) {
                            other.sendMessage(c("&e🤧 &f" + src.getName() + " &eđã lây bệnh cho bạn! "
                                    + "&7(Đeo khẩu trang để tránh lây)"));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dich benh
    private void tickEpidemic(long now, String season) {
        if (epidemicId != null) {
            if (now >= epidemicEnd) {
                stopEpidemic(true);
            } else if (epidemicBar != null) {
                double prog = (double) (epidemicEnd - now) / (double) Math.max(1L, epidemicTotal);
                epidemicBar.setProgress(Math.max(0.0, Math.min(1.0, prog)));
            }
            return;
        }
        if (!epidemicEnabled || now - lastEpidemicCheck < epidemicCheckMs) return;
        lastEpidemicCheck = now;
        if (ThreadLocalRandom.current().nextDouble() >= epidemicChance) return;
        List<Disease> pool = new ArrayList<>();
        for (Disease d : diseases.values()) {
            if (seasonOk(d, season)) pool.add(d);
        }
        if (pool.isEmpty()) return;
        startEpidemic(pool.get(ThreadLocalRandom.current().nextInt(pool.size())), (int) (epidemicDurationMs / 60000L));
    }

    private void startEpidemic(Disease d, int minutes) {
        stopEpidemic(false);
        epidemicId = d.id;
        epidemicTotal = Math.max(1, minutes) * 60000L;
        epidemicEnd = System.currentTimeMillis() + epidemicTotal;
        epidemicBar = Bukkit.createBossBar(c("&c🦠 DỊCH BỆNH: &e" + d.name + " &c- nguy cơ mắc tăng cao!"),
                BarColor.RED, BarStyle.SOLID);
        epidemicBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) epidemicBar.addPlayer(p);
        Bukkit.broadcastMessage(c("&c🦠 &lDỊCH BỆNH! &e" + d.name + " &cđang bùng phát trong "
                + minutes + " phút. Hãy cẩn thận và đeo khẩu trang!"));
    }

    private void stopEpidemic(boolean announce) {
        if (epidemicBar != null) {
            epidemicBar.removeAll();
            epidemicBar = null;
        }
        if (epidemicId != null && announce) {
            Disease d = diseases.get(epidemicId);
            Bukkit.broadcastMessage(c("&a💚 Dịch bệnh &e" + (d != null ? d.name : epidemicId) + " &ađã kết thúc."));
        }
        epidemicId = null;
    }

    // ------------------------------------------------------------------ mac / chua benh
    private void applySymptoms(Player p, Disease d, int stage) {
        for (Disease.EffectSpec spec : d.effects) {
            int amp = Math.min(4, spec.amplifier() + (stage >= 2 ? 1 : 0));
            p.addPotionEffect(new PotionEffect(spec.type(), 200, amp, true, false, true));
        }
    }

    public boolean infect(Player p, Disease d, boolean force) {
        return infect(p, d, force, false);
    }

    public boolean infect(Player p, Disease d, boolean force, boolean contagion) {
        PlayerState st = state(p);
        long now = System.currentTimeMillis();
        if (!force) {
            if (st.active.containsKey(d.id)) return false;
            if (st.active.size() >= maxDiseases) return false;
            Long im = st.immune.get(d.id);
            if (im != null && im > now) return false;
            if (now < st.allImmune) return false;
            if (p.hasPermission("seasondiseases.immune") || exempt(p)) return false;
        }
        DiseaseContractEvent ev = new DiseaseContractEvent(p, d, contagion);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() && !force) return false;

        st.active.put(d.id, now + d.durationSec * 1000L);
        st.worsened.remove(d.id);
        st.progress.keySet().removeIf(k -> k.startsWith(d.id + "#"));
        st.counters.remove(d.id);

        p.sendMessage(c("&c🦠 Bạn đã bị bệnh &e" + d.name + "&c!"));
        if (!d.description.isEmpty()) p.sendMessage(c("&c🦠 " + d.description));
        p.sendMessage(c("&7Gõ &e/benh &7để xem cách chữa."));
        p.sendTitle(c("&c🦠 " + d.name), c("&7Gõ /benh để xem cách chữa"), 10, 60, 20);
        p.playSound(p.getLocation(), "entity.villager.hurt", 1.0f, 0.8f);
        applySymptoms(p, d, 1);
        return true;
    }

    /** Don dep trang thai benh (khong gui tin nhan chua benh). */
    private void finishDisease(Player p, Disease d, String why, boolean immunity) {
        PlayerState st = state(p);
        if (st.active.remove(d.id) == null) return;
        st.worsened.remove(d.id);
        st.counters.remove(d.id);
        st.progress.keySet().removeIf(k -> k.startsWith(d.id + "#"));
        if (immunity) st.immune.put(d.id, System.currentTimeMillis() + immunityMs);
        for (Disease.EffectSpec s : d.effects) p.removePotionEffect(s.type());
        long now = System.currentTimeMillis();
        for (String id : st.active.keySet()) {
            Disease o = diseases.get(id);
            if (o != null) applySymptoms(p, o, stage(st, o, now));
        }
        Bukkit.getPluginManager().callEvent(new DiseaseCureEvent(p, d, why));
    }

    public void cure(Player p, Disease d, String why) {
        PlayerState st = state(p);
        if (!st.active.containsKey(d.id)) return;
        finishDisease(p, d, why, true);
        p.sendMessage(c("&a💊 Bạn đã khỏi bệnh &e" + d.name + "&a (" + why + ")."));
        p.playSound(p.getLocation(), "entity.player.levelup", 1.0f, 1.2f);
    }

    private void cureByKind(Player p, Cure.Kind kind, String why) {
        PlayerState st = state(p);
        for (String id : new ArrayList<>(st.active.keySet())) {
            Disease d = diseases.get(id);
            if (d != null && d.hasCure(kind)) cure(p, d, why);
        }
    }

    private void handleEatCure(Player p, PlayerState st, ItemStack item) {
        for (String id : new ArrayList<>(st.active.keySet())) {
            Disease d = diseases.get(id);
            if (d == null) continue;
            for (int i = 0; i < d.cures.size(); i++) {
                Cure cu = d.cures.get(i);
                if (cu.kind != Cure.Kind.ITEM) continue;
                int r = cu.matchIndex(item);
                if (r < 0) continue;
                String key = id + "#" + i;
                int[] pr = st.progress.computeIfAbsent(key, k -> new int[cu.reqs.size()]);
                if (pr[r] < cu.reqs.get(r).need) pr[r]++;
                if (cu.complete(pr)) {
                    st.progress.remove(key);
                    cure(p, d, "đã dùng " + cu.shortLabel());
                    break;
                } else {
                    actionBar(p, "&e🍽 " + d.name + ": tiến độ chữa " + cu.progressText(pr));
                }
            }
        }
    }

    // ------------------------------------------------------------------ su kien
    private void eventTrigger(Player p, Trigger t) {
        if (exempt(p) || p.hasPermission("seasondiseases.immune")) return;
        PlayerState st = state(p);
        long now = System.currentTimeMillis();
        String season = currentSeason();
        for (Disease d : diseases.values()) {
            if (d.trigger != t || !seasonOk(d, season)) continue;
            if (st.active.containsKey(d.id)) continue;
            Long im = st.immune.get(d.id);
            if (im != null && im > now) continue;
            long last = st.eventTime.getOrDefault(d.id, 0L);
            if (now - last > eventWindowMs) st.counters.put(d.id, 0);
            st.eventTime.put(d.id, now);
            int cnt = st.counters.merge(d.id, 1, Integer::sum);
            int thr = d.threshold;
            double chance = d.chance;
            if (d.id.equals(epidemicId)) {
                thr = Math.max(1, thr / 2);
                chance = Math.min(1.0, chance * epidemicMultiplier);
            }
            if (cnt >= thr) {
                st.counters.remove(d.id);
                if (ThreadLocalRandom.current().nextDouble() < chance) infect(p, d, false);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            Player p = e.getPlayer();
            loadState(p);
            if (epidemicBar != null) epidemicBar.addPlayer(p);
            PlayerState st = state(p);
            if (!st.active.isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (String id : st.active.keySet()) {
                    Disease d = diseases.get(id);
                    if (d == null) continue;
                    if (names.length() > 0) names.append(", ");
                    names.append(d.name);
                }
                p.sendMessage(c("&c🦠 Bạn vẫn đang bị bệnh: &e" + names + "&c. Gõ &e/benh &cđể xem cách chữa."));
            }
        } catch (Throwable t) {
            warnOnce("onJoin", t);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        try {
            UUID id = e.getPlayer().getUniqueId();
            storeState(id);
            states.remove(id);
        } catch (Throwable t) {
            warnOnce("onQuit", t);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        try {
            if (!cureOnDeath) return;
            Player p = e.getEntity();
            PlayerState st = state(p);
            st.active.clear();
            st.counters.clear();
            st.progress.clear();
            st.worsened.clear();
        } catch (Throwable t) {
            warnOnce("onDeath", t);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        try {
            Player p = e.getPlayer();
            ItemStack item = e.getItem();
            Material m = item.getType();
            PlayerState st = state(p);
            if (DRINKS.contains(m)) st.lastDrink = System.currentTimeMillis();
            if (RAW.contains(m)) eventTrigger(p, Trigger.EAT_RAW);
            if (SPOILED.contains(m)) eventTrigger(p, Trigger.EAT_SPOILED);
            if (BERRY.contains(m)) eventTrigger(p, Trigger.EAT_BERRY);
            if (SWEET.contains(m)) eventTrigger(p, Trigger.EAT_SWEET);
            handleEatCure(p, st, item);
        } catch (Throwable t) {
            warnOnce("onConsume", t);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        try {
            if (CROPS.contains(e.getBlock().getType())) eventTrigger(e.getPlayer(), Trigger.BREAK_CROP);
        } catch (Throwable t) {
            warnOnce("onBreak", t);
        }
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent e) {
        try {
            Player p = e.getPlayer();
            if (p.getSleepTicks() >= 100) cureByKind(p, Cure.Kind.SLEEP, "đã ngủ một giấc ngon");
        } catch (Throwable t) {
            warnOnce("onBedLeave", t);
        }
    }

    /**
     * Khong goi truc tiep TimeSkipEvent#getSkipReason() de tranh NoSuchMethodError tren cac ban Paper moi
     * (chu ky ham da doi). Dung reflection va chi dung de loc, nguoi dang ngu moi duoc chua.
     */
    @EventHandler
    public void onTimeSkip(TimeSkipEvent e) {
        try {
            String reason = "";
            try {
                Object r = e.getClass().getMethod("getSkipReason").invoke(e);
                reason = String.valueOf(r);
            } catch (Throwable ignored) {
                // khong doc duoc ly do -> van xu ly (chi nguoi dang ngu moi duoc chua)
            }
            if (!reason.isEmpty() && !reason.contains("NIGHT_SKIP")) return;
            for (Player p : e.getWorld().getPlayers()) {
                if (p.isSleeping()) cureByKind(p, Cure.Kind.SLEEP, "đã ngủ một giấc ngon");
            }
        } catch (Throwable t) {
            warnOnce("onTimeSkip", t);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        try {
            if (!doctorEnabled || e.getHand() != EquipmentSlot.HAND) return;
            if (!(e.getRightClicked() instanceof Villager v)) return;
            if (!String.valueOf(v.getProfession()).toLowerCase(Locale.ROOT).contains("cleric")) return;
            Player p = e.getPlayer();
            PlayerState st = state(p);
            Disease target = null;
            for (String id : st.active.keySet()) {
                Disease d = diseases.get(id);
                if (d != null && d.hasCure(Cure.Kind.DOCTOR)) {
                    target = d;
                    break;
                }
            }
            if (target == null) return; // khong benh nao can thay lang -> cho phep giao dich binh thuong
            e.setCancelled(true);
            if (doctorCost > 0) {
                if (!p.getInventory().contains(Material.EMERALD, doctorCost)) {
                    p.sendMessage(c("&c⚕ Thầy lang cần &e" + doctorCost + " ngọc lục bảo &cđể khám cho bạn."));
                    return;
                }
                p.getInventory().removeItem(new ItemStack(Material.EMERALD, doctorCost));
            }
            cure(p, target, "khám thầy lang");
        } catch (Throwable t) {
            warnOnce("onInteractEntity", t);
        }
    }

    // ------------------------------------------------------------------ khau trang
    private ItemStack createMask() {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c("&f😷 Khẩu trang"));
            meta.setLore(List.of(c("&7Đeo lên đầu để không bị lây bệnh"), c("&7và không lây cho người khác.")));
            if (meta instanceof LeatherArmorMeta lm) lm.setColor(Color.WHITE);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void registerMaskRecipe() {
        if (!maskRecipe) return;
        try {
            maskKey = new NamespacedKey(this, "khau_trang");
            ShapedRecipe r = new ShapedRecipe(maskKey, createMask());
            r.shape("SWS");
            r.setIngredient('S', Material.STRING);
            r.setIngredient('W', Material.WHITE_WOOL);
            Bukkit.addRecipe(r);
        } catch (Throwable t) {
            warnOnce("registerMaskRecipe", t);
        }
    }

    // ------------------------------------------------------------------ lenh
    private void showList(CommandSender s, String seasonArg) {
        String key = seasonArg == null ? currentSeason() : seasonArg.toUpperCase(Locale.ROOT);
        s.sendMessage(c("&6Các bệnh của &e" + seasonName(key) + "&6:"));
        for (Disease d : diseases.values()) {
            if (seasonOk(d, key)) {
                s.sendMessage(c("  &8▪ &f" + d.name + " &8(" + d.id + ")" + (d.contagious ? " &d[lây]" : "")));
            }
        }
        s.sendMessage(c("&7Xem chi tiết: &e/benh info <tên bệnh>"));
    }

    private void showDetail(CommandSender s, Disease d, Long end) {
        s.sendMessage(c("&8&m                                         "));
        String status = end == null ? "" : " &7(còn " + fmt(end - System.currentTimeMillis()) + ")";
        s.sendMessage(c("&c🦠 &e" + d.name + " &8[" + seasonName(d.season) + "]" + status));
        if (!d.description.isEmpty()) s.sendMessage(c("&7" + d.description));
        if (d.contagious) s.sendMessage(c("&dBệnh này lây giữa người với nhau (khẩu trang giúp phòng lây)."));
        if (!d.effects.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Disease.EffectSpec e : d.effects) {
                names.add(EFFECT_NAMES.getOrDefault(e.key(), e.key()) + " " + roman(e.amplifier()));
            }
            s.sendMessage(c("&7Triệu chứng: &f" + String.join(", ", names)));
        }
        s.sendMessage(c("&aCách chữa (chỉ cần một trong các cách):"));
        for (Cure cu : d.cures) s.sendMessage(c("  &8▪ &f" + cu.describe(doctorCost)));
        if (!d.prevention.isEmpty()) s.sendMessage(c("&7Phòng ngừa: &f" + d.prevention));
        if (end != null) s.sendMessage(c("&8Không chữa thì sẽ tự khỏi sau thời gian trên (bệnh nặng dần nếu để lâu)."));
    }

    private void showSelf(CommandSender s, Player target, boolean other) {
        PlayerState st = state(target);
        if (st.active.isEmpty()) {
            s.sendMessage(c(other ? "&a💚 " + target.getName() + " đang khỏe mạnh."
                    : "&a💚 Bạn đang khỏe mạnh."));
            if (!other) s.sendMessage(c("&7Xem tất cả: &e/benh list <spring|summer|autumn|winter>"));
            return;
        }
        for (Map.Entry<String, Long> en : st.active.entrySet()) {
            Disease d = diseases.get(en.getKey());
            if (d != null) showDetail(s, d, en.getValue());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return handleCommand(sender, args);
        } catch (Throwable t) {
            warnOnce("onCommand", t);
            sender.sendMessage(c("&cĐã có lỗi xảy ra, xem console."));
            return true;
        }
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission("seasondiseases.admin");
        if (args.length == 0) {
            if (sender instanceof Player p) showSelf(sender, p, false);
            else showList(sender, null);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list":
                showList(sender, args.length > 1 ? args[1] : null);
                return true;
            case "info": {
                if (args.length < 2) {
                    sender.sendMessage(c("&cDùng: /benh info <tên bệnh>"));
                    return true;
                }
                Disease d = find(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (d == null) {
                    sender.sendMessage(c("&cKhông tìm thấy bệnh '" + args[1] + "'. Dùng /benh list."));
                    return true;
                }
                Long end = null;
                if (sender instanceof Player p) end = state(p).active.get(d.id);
                showDetail(sender, d, end);
                return true;
            }
            case "chua": {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(c("&cChỉ người chơi mới dùng được lệnh này."));
                    return true;
                }
                PlayerState st = state(p);
                boolean any = false;
                for (String id : new ArrayList<>(st.active.keySet())) {
                    Disease d = diseases.get(id);
                    if (d == null) continue;
                    if (args.length > 1 && !d.id.equalsIgnoreCase(args[1])) continue;
                    for (Cure cu : d.cures) {
                        if (cu.kind != Cure.Kind.XP) continue;
                        any = true;
                        if (p.getLevel() >= cu.amount) {
                            p.setLevel(p.getLevel() - cu.amount);
                            cure(p, d, "trả " + cu.amount + " cấp kinh nghiệm");
                        } else {
                            p.sendMessage(c("&cCần &e" + cu.amount + " cấp kinh nghiệm &cđể chữa &e" + d.name + "&c."));
                        }
                        break;
                    }
                }
                if (!any) p.sendMessage(c("&7Bạn không có bệnh nào chữa được bằng kinh nghiệm."));
                return true;
            }
            default:
                break;
        }

        // ---- lenh quan tri ----
        boolean adminSub = sub.equals("give") || sub.equals("cure") || sub.equals("reload") || sub.equals("immune")
                || sub.equals("mask") || sub.equals("outbreak") || sub.equals("status");
        if (!adminSub) {
            sender.sendMessage(c("&cDùng: /benh [list|info <bệnh>|chua]"));
            return true;
        }
        if (!admin) {
            sender.sendMessage(c("&cBạn không có quyền."));
            return true;
        }
        switch (sub) {
            case "reload":
                loadAll();
                sender.sendMessage(c("&aĐã nạp lại " + diseases.size() + " bệnh."));
                return true;
            case "give": {
                if (args.length < 3) {
                    sender.sendMessage(c("&cDùng: /benh give <người chơi> <bệnh>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                Disease d = find(args[2]);
                if (t == null || d == null) {
                    sender.sendMessage(c("&cKhông tìm thấy người chơi hoặc bệnh."));
                    return true;
                }
                infect(t, d, true);
                sender.sendMessage(c("&aĐã cho " + t.getName() + " bị " + d.name + "."));
                return true;
            }
            case "cure": {
                if (args.length < 2) {
                    sender.sendMessage(c("&cDùng: /benh cure <người chơi> [bệnh]"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(c("&cKhông tìm thấy người chơi."));
                    return true;
                }
                PlayerState st = state(t);
                for (String id : new ArrayList<>(st.active.keySet())) {
                    Disease d = diseases.get(id);
                    if (d == null) continue;
                    if (args.length > 2 && !d.id.equalsIgnoreCase(args[2])) continue;
                    cure(t, d, "quản trị viên chữa");
                }
                sender.sendMessage(c("&aĐã chữa bệnh cho " + t.getName() + "."));
                return true;
            }
            case "immune": {
                if (args.length < 2) {
                    sender.sendMessage(c("&cDùng: /benh immune <người chơi> [giây|off]"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(c("&cKhông tìm thấy người chơi."));
                    return true;
                }
                PlayerState st = state(t);
                if (args.length > 2 && args[2].equalsIgnoreCase("off")) {
                    st.allImmune = 0L;
                    sender.sendMessage(c("&aĐã tắt miễn nhiễm cho " + t.getName() + "."));
                    return true;
                }
                long sec = 3600L;
                if (args.length > 2) {
                    try {
                        sec = Math.max(1L, Long.parseLong(args[2]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(c("&cSố giây không hợp lệ."));
                        return true;
                    }
                }
                st.allImmune = System.currentTimeMillis() + sec * 1000L;
                sender.sendMessage(c("&aĐã cho " + t.getName() + " miễn nhiễm mọi bệnh trong " + sec + " giây."));
                return true;
            }
            case "mask": {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : (sender instanceof Player sp ? sp : null);
                if (t == null) {
                    sender.sendMessage(c("&cDùng: /benh mask <người chơi>"));
                    return true;
                }
                t.getInventory().addItem(createMask());
                sender.sendMessage(c("&aĐã đưa khẩu trang cho " + t.getName() + "."));
                return true;
            }
            case "outbreak": {
                if (args.length < 2) {
                    sender.sendMessage(c("&cDùng: /benh outbreak <bệnh> [phút] | /benh outbreak stop"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("stop")) {
                    stopEpidemic(true);
                    sender.sendMessage(c("&aĐã dừng dịch bệnh."));
                    return true;
                }
                Disease d = find(args[1]);
                if (d == null) {
                    sender.sendMessage(c("&cKhông tìm thấy bệnh."));
                    return true;
                }
                int minutes = (int) (epidemicDurationMs / 60000L);
                if (args.length > 2) {
                    try {
                        minutes = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(c("&cSố phút không hợp lệ."));
                        return true;
                    }
                }
                startEpidemic(d, minutes);
                return true;
            }
            case "status": {
                if (args.length < 2) {
                    sender.sendMessage(c("&cDùng: /benh status <người chơi>"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    sender.sendMessage(c("&cKhông tìm thấy người chơi."));
                    return true;
                }
                showSelf(sender, t, true);
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("seasondiseases.admin");
        if (args.length == 1) {
            out.addAll(List.of("list", "info", "chua"));
            if (admin) out.addAll(List.of("give", "cure", "reload", "immune", "mask", "outbreak", "status"));
        } else if (args.length == 2) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("list")) {
                out.addAll(List.of("spring", "summer", "autumn", "winter"));
            } else if (a.equals("info") || a.equals("chua")) {
                out.addAll(diseases.keySet());
            } else if (admin && (a.equals("give") || a.equals("cure") || a.equals("immune") || a.equals("mask")
                    || a.equals("status"))) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (admin && a.equals("outbreak")) {
                out.add("stop");
                out.addAll(diseases.keySet());
            }
        } else if (args.length == 3 && admin) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("give") || a.equals("cure")) out.addAll(diseases.keySet());
            else if (a.equals("immune")) out.addAll(List.of("off", "300", "3600"));
            else if (a.equals("outbreak")) out.addAll(List.of("10", "15", "30"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    /** API cho plugin khac: cac benh dang mac cua nguoi choi. */
    public Collection<String> getActiveDiseaseIds(Player p) {
        PlayerState st = states.get(p.getUniqueId());
        return st == null ? List.of() : new ArrayList<>(st.active.keySet());
    }
}

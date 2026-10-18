package vn.seasonsdiseases;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mot cach chua benh. Cu phap trong diseases.yml:
 *  MATERIAL | MATERIAL~ten | MATERIAL~!ten | MATERIAL*N | A+B+C (an du ca) | WATER_BOTTLE
 *  SLEEP | SWIM | FIRE | DOCTOR | XP:N
 */
public final class Cure {

    public enum Kind { ITEM, SLEEP, SWIM, FIRE, XP, DOCTOR }

    /** Mot yeu cau an/uong: vat pham + (tuy chon) loc ten + so lan can dung. */
    public static final class Req {
        public final Material material;
        public final boolean water;
        public final String nameFilter;
        public final boolean negate;
        public final int need;

        Req(Material material, boolean water, String nameFilter, boolean negate, int need) {
            this.material = material;
            this.water = water;
            this.nameFilter = nameFilter;
            this.negate = negate;
            this.need = need;
        }

        public boolean matches(ItemStack item) {
            if (item == null) return false;
            if (water) {
                if (item.getType() != Material.POTION) return false;
                ItemMeta m = item.getItemMeta();
                if (m instanceof PotionMeta pm) {
                    try {
                        PotionType base = pm.getBasePotionType();
                        if (base != PotionType.WATER || pm.hasCustomEffects()) return false;
                    } catch (Throwable t) {
                        return false;
                    }
                }
                return true;
            }
            if (item.getType() != material) return false;
            if (nameFilter == null) return true;
            ItemMeta meta = item.getItemMeta();
            String display = "";
            if (meta != null && meta.hasDisplayName()) {
                display = ChatColor.stripColor(meta.getDisplayName());
                if (display == null) display = "";
                display = display.toLowerCase(Locale.ROOT);
            }
            boolean has = display.contains(nameFilter.toLowerCase(Locale.ROOT));
            return negate ? !has : has;
        }

        public String label() {
            String base;
            if (water) {
                base = "Chai nước";
            } else {
                base = NAMES.get(material);
                if (base == null) base = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            }
            if (nameFilter != null) {
                base = negate ? base + " (nguyên chất)" : base + " (" + nameFilter + ")";
            }
            return need > 1 ? base + " ×" + need : base;
        }
    }

    public static final Map<Material, String> NAMES = Map.ofEntries(
            Map.entry(Material.MILK_BUCKET, "Xô sữa"),
            Map.entry(Material.HONEY_BOTTLE, "Mật ong"),
            Map.entry(Material.GOLDEN_APPLE, "Táo vàng"),
            Map.entry(Material.ENCHANTED_GOLDEN_APPLE, "Táo vàng phù phép"),
            Map.entry(Material.MUSHROOM_STEW, "Súp nấm"),
            Map.entry(Material.RABBIT_STEW, "Súp thỏ"),
            Map.entry(Material.BEETROOT_SOUP, "Súp củ dền"),
            Map.entry(Material.CARROT, "Cà rốt"),
            Map.entry(Material.GOLDEN_CARROT, "Cà rốt vàng"),
            Map.entry(Material.MELON_SLICE, "Dưa hấu"),
            Map.entry(Material.SWEET_BERRIES, "Dâu ngọt"),
            Map.entry(Material.GLOW_BERRIES, "Quả phát sáng"),
            Map.entry(Material.BREAD, "Bánh mì"),
            Map.entry(Material.APPLE, "Táo"),
            Map.entry(Material.COOKIE, "Bánh quy"),
            Map.entry(Material.PUMPKIN_PIE, "Bánh bí ngô"),
            Map.entry(Material.COOKED_SALMON, "Cá hồi nướng"),
            Map.entry(Material.COOKED_COD, "Cá tuyết nướng"),
            Map.entry(Material.COOKED_BEEF, "Bò nướng"),
            Map.entry(Material.BAKED_POTATO, "Khoai tây nướng"));

    public final Kind kind;
    public final List<Req> reqs;
    /** So cap kinh nghiem (Kind.XP). */
    public final int amount;

    private Cure(Kind kind, List<Req> reqs, int amount) {
        this.kind = kind;
        this.reqs = reqs;
        this.amount = amount;
    }

    public static Cure parse(String raw) {
        String s = raw.trim();
        String up = s.toUpperCase(Locale.ROOT);
        switch (up) {
            case "SLEEP":
                return new Cure(Kind.SLEEP, List.of(), 0);
            case "SWIM":
                return new Cure(Kind.SWIM, List.of(), 0);
            case "FIRE":
                return new Cure(Kind.FIRE, List.of(), 0);
            case "DOCTOR":
                return new Cure(Kind.DOCTOR, List.of(), 0);
            default:
                break;
        }
        if (up.startsWith("XP:")) {
            int n;
            try {
                n = Integer.parseInt(s.substring(3).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("XP khong hop le: " + raw);
            }
            return new Cure(Kind.XP, List.of(), Math.max(1, n));
        }
        List<Req> reqs = new ArrayList<>();
        for (String tok : s.split("\\+")) {
            reqs.add(parseReq(tok.trim(), raw));
        }
        if (reqs.isEmpty()) throw new IllegalArgumentException("Cach chua rong: " + raw);
        return new Cure(Kind.ITEM, reqs, 0);
    }

    private static Req parseReq(String tok, String raw) {
        String t = tok;
        int need = 1;
        int star = t.lastIndexOf('*');
        if (star > 0) {
            String num = t.substring(star + 1).trim();
            if (num.matches("\\d+")) {
                need = Math.max(1, Integer.parseInt(num));
                t = t.substring(0, star).trim();
            }
        }
        String matName = t;
        String filter = null;
        boolean neg = false;
        int tilde = t.indexOf('~');
        if (tilde >= 0) {
            matName = t.substring(0, tilde).trim();
            filter = t.substring(tilde + 1).trim();
            if (filter.startsWith("!")) {
                neg = true;
                filter = filter.substring(1).trim();
            }
            if (filter.isEmpty()) filter = null;
        }
        if (matName.equalsIgnoreCase("WATER_BOTTLE")) {
            return new Req(null, true, null, false, need);
        }
        Material m = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
        if (m == null) throw new IllegalArgumentException("Vat pham khong hop le: " + matName + " (trong '" + raw + "')");
        return new Req(m, false, filter, neg, need);
    }

    /** Vi tri yeu cau khop voi vat pham, hoac -1. */
    public int matchIndex(ItemStack item) {
        for (int i = 0; i < reqs.size(); i++) {
            if (reqs.get(i).matches(item)) return i;
        }
        return -1;
    }

    public boolean complete(int[] progress) {
        for (int i = 0; i < reqs.size(); i++) {
            if (progress[i] < reqs.get(i).need) return false;
        }
        return true;
    }

    public String progressText(int[] progress) {
        int done = 0;
        int total = 0;
        for (int i = 0; i < reqs.size(); i++) {
            total += reqs.get(i).need;
            done += Math.min(progress[i], reqs.get(i).need);
        }
        return done + "/" + total;
    }

    public String shortLabel() {
        List<String> parts = new ArrayList<>();
        for (Req r : reqs) parts.add(r.label());
        return String.join(" + ", parts);
    }

    public String describe(int doctorCost) {
        switch (kind) {
            case SLEEP:
                return "Ngủ qua đêm trên giường";
            case SWIM:
                return "Xuống nước tắm/ngâm mình (10 giây)";
            case FIRE:
                return "Ngồi gần lửa/bếp lò (10 giây)";
            case DOCTOR:
                return "Khám thầy lang: chuột phải vào dân làng Mục sư (mất " + doctorCost + " ngọc lục bảo)";
            case XP:
                return "Trả " + amount + " cấp kinh nghiệm (gõ /benh chua)";
            default:
                break;
        }
        if (reqs.size() == 1) return reqs.get(0).label();
        return "Ăn/uống đủ cả: " + shortLabel();
    }
}

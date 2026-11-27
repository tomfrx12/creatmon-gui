package fr.creatmon.shop.client;

import fr.creatmon.shop.network.XpTablePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * « Valeurs XP » : ce que rapporte chaque Pokémon (métier Pokémon) et chaque item (métier Create).
 * Une catégorie par métier, recherche, tri et défilement.
 */
public class XpTableScreen extends Screen {

    private static final int W = 420, H = 262;
    private static final int HEADER_H = 28;
    private static final int NOTES_Y = HEADER_H + 4;
    private static final int TOOLS_Y = NOTES_Y + 24;
    private static final int LIST_Y = TOOLS_Y + 20, LIST_H = H - LIST_Y - 8;
    private static final int LIST_X = 6, LIST_W = W - 12 - 8;      // 8 px pour la barre de défilement
    private static final int COLS = 2, ROW_H = 20, COL_GAP = 4;
    private static final int COL_W = (LIST_W - COL_GAP) / COLS;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    private static final int BG = 0xFF0E1626, PANEL = 0xFF0A1520, DARK = 0xFF0C131E, CELL = 0xFF152235;
    private static final int GOLD = 0xFFD49E2A, GOLD_DIM = 0xFF7A5A18, WHITE = 0xFFFFFFFF;
    private static final int INV_LAB = 0xFF8899AA, LOCKED = 0xFF2A3445;
    private static final int POK = 0xFF4FA3FF, CRE = 0xFFE0A030;

    private static final String[] RARITY = {"Commun", "Peu commun", "Rare", "Très rare", "Légendaire"};
    private static final int[] RARITY_COLOR = {0xFF9AA5B1, 0xFF44BB44, 0xFF4499FF, 0xFFB57BFF, 0xFFFFAA00};

    private enum Tab { POKEMON, CREATE }

    private record Poke(int dex, String name, int rarity, int xp) {}
    private record Itm(ItemStack stack, String name, double xp) {}

    private final List<Poke> pokemon = new ArrayList<>();
    private final List<Itm> items = new ArrayList<>();
    private final List<String> pokeNotes, createNotes;
    private final double machineMult;

    private Tab tab = Tab.POKEMON;
    private boolean sortByXp = false;
    private int scroll;
    private EditBox search;
    private List<?> view = List.of();
    private Component tooltip;

    public XpTableScreen(XpTablePacket pkt) {
        super(Component.literal("Valeurs XP"));
        for (var e : pkt.pokemon()) {
            String name = e.langKey().isEmpty() ? e.name()
                : Component.translatableWithFallback(e.langKey(), e.name()).getString();
            pokemon.add(new Poke(e.dex(), name, Math.max(0, Math.min(4, e.rarity())), e.baseXp()));
        }
        for (var e : pkt.items()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.id());
            if (rl == null) continue;
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) continue; // item d'un mod absent
            ItemStack s = new ItemStack(item);
            items.add(new Itm(s, s.getHoverName().getString(), e.xp()));
        }
        this.pokeNotes = pkt.pokemonNotes();
        this.createNotes = pkt.createNotes();
        this.machineMult = pkt.machineMultiplier();
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - W) / 2, y = (height - H) / 2;
        String prev = search == null ? "" : search.getValue();
        search = new EditBox(font, x + LIST_X + 1, y + TOOLS_Y + 2, 150, 14, Component.literal("Rechercher"));
        search.setHint(Component.literal("Rechercher…").withStyle(s -> s.withColor(0x556677)));
        search.setValue(prev);
        search.setResponder(v -> { scroll = 0; rebuild(); });
        addRenderableWidget(search);
        rebuild();
    }

    private void rebuild() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (tab == Tab.POKEMON) {
            List<Poke> l = new ArrayList<>();
            for (Poke p : pokemon)
                if (q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q) || String.valueOf(p.dex()).equals(q)) l.add(p);
            l.sort(sortByXp
                ? Comparator.comparingInt(Poke::xp).reversed().thenComparingInt(Poke::dex)
                : Comparator.comparingInt(Poke::dex));
            view = l;
        } else {
            List<Itm> l = new ArrayList<>();
            for (Itm i : items)
                if (q.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(q)) l.add(i);
            l.sort(sortByXp
                ? Comparator.comparingDouble(Itm::xp).reversed().thenComparing(Itm::name)
                : Comparator.comparing(Itm::name));
            view = l;
        }
        scroll = Math.min(scroll, maxScroll());
    }

    private int rows() { return (view.size() + COLS - 1) / COLS; }
    private int maxScroll() { return Math.max(0, rows() - VISIBLE_ROWS); }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        tooltip = null;
        int ac = tab == Tab.POKEMON ? POK : CRE;

        g.fill(x, y, x + W, y + H, BG);
        border(g, x, y, W, H, GOLD);
        g.fill(x, y + HEADER_H, x + W, y + HEADER_H + 1, GOLD_DIM);

        // En-tête : retour, titre, onglets, fermer
        button(g, x + 6, y + 6, 46, 16, "◀ PASS", mx, my, WHITE);
        g.drawString(font, "VALEURS D'XP", x + 60, y + 10, GOLD, false);
        int tx = x + W - 28;
        for (Tab t : new Tab[]{Tab.CREATE, Tab.POKEMON}) {
            String label = t == Tab.POKEMON ? "POKÉMON" : "CREATE";
            int tw = font.width(label) + 16;
            tx -= tw;
            boolean sel = t == tab, hov = inside(mx, my, tx, y + 5, tw, 18);
            int tc = t == Tab.POKEMON ? POK : CRE;
            g.fill(tx, y + 5, tx + tw, y + 23, sel ? (tc & 0x00FFFFFF) | 0x55000000 : hov ? 0xFF1A2638 : CELL);
            border(g, tx, y + 5, tw, 18, sel ? tc : GOLD_DIM);
            if (sel) g.fill(tx + 1, y + 21, tx + tw - 1, y + 23, tc);
            g.drawString(font, label, tx + 8, y + 10, sel ? WHITE : INV_LAB, false);
            tx -= 4;
        }
        button(g, x + W - 22, y + 6, 16, 16, "✕", mx, my, WHITE);

        // Notes du métier
        List<String> notes = tab == Tab.POKEMON ? pokeNotes : createNotes;
        g.fill(x + 6, y + NOTES_Y, x + W - 6, y + NOTES_Y + 21, PANEL);
        g.fill(x + 6, y + NOTES_Y, x + 8, y + NOTES_Y + 21, ac);
        for (int i = 0; i < Math.min(2, notes.size()); i++)
            small(g, notes.get(i), x + 12, y + NOTES_Y + 3 + i * 9, i == 0 ? WHITE : INV_LAB);

        // Outils : tri + compteur
        String sort = "Tri : " + (sortByXp ? "XP ↓" : tab == Tab.POKEMON ? "N° Pokédex" : "Nom");
        int sw = font.width(sort) + 12;
        button(g, x + LIST_X + 158, y + TOOLS_Y + 1, sw, 16, sort, mx, my, INV_LAB);
        String count = view.size() + (tab == Tab.POKEMON ? " Pokémon" : " items");
        g.drawString(font, count, x + W - 6 - font.width(count), y + TOOLS_Y + 5, INV_LAB, false);

        // Liste
        int lx = x + LIST_X, ly = y + LIST_Y;
        g.fill(lx, ly, lx + LIST_W, ly + LIST_H, PANEL);
        g.enableScissor(lx, ly, lx + LIST_W, ly + LIST_H);
        for (int r = 0; r < VISIBLE_ROWS + 1; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= view.size()) break;
                int rx = lx + c * (COL_W + COL_GAP), ry = ly + r * ROW_H;
                boolean hov = inside(mx, my, rx, ry, COL_W, ROW_H - 2) && my < ly + LIST_H;
                g.fill(rx, ry, rx + COL_W, ry + ROW_H - 2, hov ? 0xFF1C2A40 : CELL);
                if (tab == Tab.POKEMON) drawPoke(g, (Poke) view.get(idx), rx, ry);
                else drawItem(g, (Itm) view.get(idx), rx, ry, hov);
            }
        }
        g.disableScissor();
        if (view.isEmpty())
            centered(g, "Aucun résultat", lx + LIST_W / 2, ly + LIST_H / 2 - 4, INV_LAB);

        // Barre de défilement
        int sx = x + W - 6 - 6;
        g.fill(sx, ly, sx + 6, ly + LIST_H, DARK);
        int ms = maxScroll();
        if (ms > 0) {
            int th = Math.max(16, LIST_H * VISIBLE_ROWS / rows());
            int ty = ly + (int) ((LIST_H - th) * (scroll / (float) ms));
            g.fill(sx, ty, sx + 6, ty + th, GOLD_DIM);
            g.fill(sx, ty, sx + 6, ty + 1, GOLD);
        }

        super.render(g, mx, my, pt);
        if (tooltip != null) g.renderTooltip(font, tooltip, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // le fond est dessiné au début de render(), sinon super.render() le repasserait par-dessus le panneau
    }

    private void drawPoke(GuiGraphics g, Poke p, int rx, int ry) {
        int rc = RARITY_COLOR[p.rarity()];
        g.fill(rx, ry, rx + 2, ry + ROW_H - 2, rc);
        small(g, String.format("#%04d", p.dex()), rx + 6, ry + 6, 0xFF556677);
        String name = ellipsize(p.name(), COL_W - 130);
        g.drawString(font, name, rx + 36, ry + 5, WHITE, false);
        String rar = RARITY[p.rarity()];
        int rw = (int) (font.width(rar) * 0.75f) + 6;
        int chipX = rx + COL_W - 52 - rw;
        g.fill(chipX, ry + 4, chipX + rw, ry + 14, (rc & 0x00FFFFFF) | 0x33000000);
        small(g, rar, chipX + 3, ry + 6, rc);
        String xp = p.xp() + " XP";
        g.drawString(font, xp, rx + COL_W - 6 - font.width(xp), ry + 5, GOLD, false);
    }

    private void drawItem(GuiGraphics g, Itm i, int rx, int ry, boolean hov) {
        g.fill(rx, ry, rx + 2, ry + ROW_H - 2, CRE);
        g.renderItem(i.stack(), rx + 4, ry + 1);
        String name = ellipsize(i.name(), COL_W - 100);
        g.drawString(font, name, rx + 24, ry + 5, WHITE, false);
        String hand = fmt(i.xp()) + " XP";
        g.drawString(font, hand, rx + COL_W - 6 - font.width(hand), ry + 2, GOLD, false);
        String mach = "machine " + fmt(i.xp() * machineMult);
        small(g, mach, rx + COL_W - 6 - (int) (font.width(mach) * 0.75f), ry + 11, INV_LAB);
        if (hov && !name.equals(i.name()))
            tooltip = Component.literal(i.name());
    }

    // ── Input ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (btn != 0) return false;
        int x = (width - W) / 2, y = (height - H) / 2;
        if (inside(mx, my, x + 6, y + 6, 46, 16)) { click(); minecraft.player.connection.sendCommand("pass"); return true; }
        if (inside(mx, my, x + W - 22, y + 6, 16, 16)) { onClose(); return true; }

        int tx = x + W - 28;
        for (Tab t : new Tab[]{Tab.CREATE, Tab.POKEMON}) {
            int tw = font.width(t == Tab.POKEMON ? "POKÉMON" : "CREATE") + 16;
            tx -= tw;
            if (inside(mx, my, tx, y + 5, tw, 18)) {
                if (tab != t) { tab = t; scroll = 0; rebuild(); click(); }
                return true;
            }
            tx -= 4;
        }

        String sort = "Tri : " + (sortByXp ? "XP ↓" : tab == Tab.POKEMON ? "N° Pokédex" : "Nom");
        if (inside(mx, my, x + LIST_X + 158, y + TOOLS_Y + 1, font.width(sort) + 12, 16)) {
            sortByXp = !sortByXp; rebuild(); click(); return true;
        }

        // Clic dans la barre de défilement
        int sx = x + W - 12;
        if (inside(mx, my, sx, y + LIST_Y, 6, LIST_H)) {
            scroll = Math.round((float) (my - y - LIST_Y) / LIST_H * maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        int x = (width - W) / 2, y = (height - H) / 2;
        if (btn == 0 && inside(mx, my, x + W - 16, y + LIST_Y - 4, 14, LIST_H + 8)) {
            scroll = Math.max(0, Math.min(maxScroll(), Math.round((float) (my - y - LIST_Y) / LIST_H * maxScroll())));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(dy) * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { onClose(); return true; }
        if (search.isFocused()) { search.keyPressed(keyCode, scanCode, mods); return true; }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, mods);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void button(GuiGraphics g, int bx, int by, int bw, int bh, String label, int mx, int my, int color) {
        boolean hov = inside(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF22324A : CELL);
        border(g, bx, by, bw, bh, hov ? GOLD : GOLD_DIM);
        centered(g, label, bx + bw / 2, by + (bh - 8) / 2, color);
    }

    private String ellipsize(String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 1 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void small(GuiGraphics g, String s, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    private void centered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

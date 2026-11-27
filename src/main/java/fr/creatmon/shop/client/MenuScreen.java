package fr.creatmon.shop.client;

import fr.creatmon.shop.network.MenuSyncPacket;
import fr.creatmon.shop.network.PassSyncPacket;
import fr.creatmon.shop.pass.PassConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * /menu — profil du joueur : skin, infos, niveaux Pokémon / Create,
 * boutons (île, warps, prestige, stats, comment XP).
 */
public class MenuScreen extends Screen {

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W = 420, H = 166;
    private static final int TOP_Y = 32, TOP_H = 128;
    private static final int SKIN_X = 6, SKIN_W = 92;
    private static final int INFO_X = 104, INFO_W = 196;
    private static final int BTN_X = 306, BTN_W = 108, BTN_H = 22, BTN_GAP = 4;
    // ── Couleurs (palette commune) ────────────────────────────────────
    private static final int BG = 0xFF0E1626, PANEL = 0xFF0A1520, DARK = 0xFF0C131E, CELL = 0xFF152235;
    private static final int GOLD = 0xFFD49E2A, GOLD_DIM = 0xFF7A5A18, WHITE = 0xFFFFFFFF;
    private static final int INV_LAB = 0xFF8899AA, GREEN = 0xFF44FF88, LOCKED = 0xFF2A3445;
    private static final int POK = 0xFF4FA3FF, CRE = 0xFFE0A030, PRESTIGE = 0xFFC77DFF;

    private static final String[] BTN_LABELS = {"ÎLE", "WARPS", "PRESTIGE", "STATISTIQUES", "COMMENT XP ?"};
    private static final int HOWTO = 4;

    // ── State ─────────────────────────────────────────────────────────
    private final String grade;
    private final int prestige;
    private final long playSeconds;
    private final List<String> commands;
    private final List<String> howToXp;
    private final Map<String, PassSyncPacket.TrackState> tracks = new LinkedHashMap<>();
    private boolean showHowTo;
    private List<Component> tooltip;

    public MenuScreen(MenuSyncPacket pkt) {
        super(Component.literal("Menu"));
        this.grade = pkt.grade();
        this.prestige = pkt.prestige();
        this.playSeconds = pkt.playSeconds();
        this.commands = pkt.buttonCommands();
        this.howToXp = pkt.howToXp();
        onPassSync(pkt.tracks());
    }

    public void onPassSync(List<PassSyncPacket.TrackState> states) {
        tracks.clear();
        for (var t : states) tracks.put(t.id(), t);
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    private int ox() { return (width - W) / 2; }
    private int oy() { return (height - H) / 2; }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = ox(), y = oy();
        tooltip = null;
        int hmx = showHowTo ? -1 : mx, hmy = showHowTo ? -1 : my;

        g.fill(x, y, x + W, y + H, BG);
        border(g, x, y, W, H, GOLD);
        int d = 3;
        g.fill(x+2, y+2, x+2+d, y+2+d, GOLD); g.fill(x+W-2-d, y+2, x+W-2, y+2+d, GOLD);
        g.fill(x+2, y+H-2-d, x+2+d, y+H-2, GOLD); g.fill(x+W-2-d, y+H-2-d, x+W-2, y+H-2, GOLD);
        g.fill(x, y + 27, x + W, y + 28, GOLD_DIM);

        drawHeader(g, x, y, hmx, hmy);
        drawSkin(g, x, y, mx, my);
        drawInfo(g, x, y);
        drawButtons(g, x, y, hmx, hmy);

        if (showHowTo) drawHowTo(g, x, y, mx, my);
        else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private void drawHeader(GuiGraphics g, int x, int y, int mx, int my) {
        String name = minecraft.player.getName().getString().toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().translate(x + 10, y + 8, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, name, 0, 0, GOLD, false);
        g.pose().popPose();

        String pr = "Prestige : " + prestige;
        int px = x + 16 + (int) (font.width(name) * 1.5f), pw = font.width(pr) + 12;
        g.fill(px, y + 7, px + pw, y + 21, 0xFF1E1433);
        border(g, px, y + 7, pw, 14, PRESTIGE);
        g.drawString(font, pr, px + 6, y + 10, PRESTIGE, false);

        // Fermer
        int cx = x + W - 22, cy = y + 6;
        boolean hov = inside(mx, my, cx, cy, 16, 16);
        g.fill(cx, cy, cx + 16, cy + 16, hov ? 0xFF3A1A1A : CELL);
        border(g, cx, cy, 16, 16, hov ? 0xFFFF5555 : GOLD_DIM);
        centered(g, "✕", cx + 8, cy + 4, hov ? 0xFFFF5555 : WHITE);
    }

    private void drawSkin(GuiGraphics g, int x, int y, int mx, int my) {
        int sx = x + SKIN_X, sy = y + TOP_Y;
        g.fill(sx, sy, sx + SKIN_W, sy + TOP_H, PANEL);
        border(g, sx, sy, SKIN_W, TOP_H, GOLD_DIM);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, sx + 2, sy + 2, sx + SKIN_W - 2, sy + TOP_H - 2,
            50, 0.0625f, mx, my, minecraft.player);
    }

    private void drawInfo(GuiGraphics g, int x, int y) {
        int ix = x + INFO_X, iy = y + TOP_Y;
        g.fill(ix, iy, ix + INFO_W, iy + TOP_H, PANEL);
        border(g, ix, iy, INFO_W, TOP_H, GOLD_DIM);
        g.drawString(font, "INFORMATIONS", ix + 6, iy + 5, GOLD, false);

        int half = (INFO_W - 15) / 2;
        cell(g, ix + 6, iy + 17, half, 26, "GRADE", grade, 0xFFFF6B6B);
        cell(g, ix + 9 + half, iy + 17, half, 26, "TEMPS DE JEU", playtime(), WHITE);
        level(g, ix + 6, iy + 47, INFO_W - 12, "NIVEAU POKÉMON", tracks.get(PassConfig.COBBLEMON), POK);
        level(g, ix + 6, iy + 86, INFO_W - 12, "NIVEAU CREATE", tracks.get(PassConfig.CREATE), CRE);
    }

    private void cell(GuiGraphics g, int cx, int cy, int w, int h, String label, String value, int color) {
        g.fill(cx, cy, cx + w, cy + h, CELL);
        small(g, label, cx + 4, cy + 3, INV_LAB);
        g.drawString(font, value, cx + 4, cy + 13, color, false);
    }

    private void level(GuiGraphics g, int cx, int cy, int w, String label, PassSyncPacket.TrackState t, int ac) {
        int h = 35;
        g.fill(cx, cy, cx + w, cy + h, CELL);
        g.fill(cx, cy, cx + 2, cy + h, ac);
        small(g, label, cx + 6, cy + 3, INV_LAB);
        if (t == null) { g.drawString(font, "—", cx + 6, cy + 13, INV_LAB, false); return; }
        g.drawString(font, "Niv. " + t.level(), cx + 6, cy + 12, ac, false);
        String rank = t.name();
        g.drawString(font, rank, cx + w - 6 - font.width(rank), cy + 12, INV_LAB, false);
        boolean maxed = t.level() >= t.maxLevel();
        float frac = maxed ? 1f : t.xpInLevel() / (float) Math.max(1, t.xpForNext());
        int bx = cx + 6, by = cy + 24, bw = w - 12;
        g.fill(bx, by, bx + bw, by + 5, DARK);
        g.fill(bx, by, bx + (int) (bw * frac), by + 5, ac);
        String xp = maxed ? "MAX" : t.xpInLevel() + " / " + t.xpForNext() + " XP";
        small(g, xp, bx + bw - (int) (font.width(xp) * 0.75f), cy + 3, INV_LAB);
    }

    private void drawButtons(GuiGraphics g, int x, int y, int mx, int my) {
        for (int i = 0; i < BTN_LABELS.length; i++) {
            int bx = x + BTN_X, by = y + TOP_Y + i * (BTN_H + BTN_GAP);
            boolean howTo = i >= HOWTO;
            boolean enabled = howTo || !command(i).isEmpty();
            boolean hov = inside(mx, my, bx, by, BTN_W, BTN_H);
            int fill = !enabled ? DARK : hov ? 0xFF22324A : CELL;
            g.fill(bx, by, bx + BTN_W, by + BTN_H, fill);
            border(g, bx, by, BTN_W, BTN_H, !enabled ? LOCKED : hov ? GOLD : GOLD_DIM);
            if (i == 2 && enabled) g.fill(bx, by, bx + 2, by + BTN_H, PRESTIGE);
            centered(g, BTN_LABELS[i], bx + BTN_W / 2, by + 7, enabled ? WHITE : 0xFF556677);
            if (hov && !enabled) tooltip = List.of(Component.literal("Bientôt disponible").withStyle(s -> s.withColor(0x8899AA)));
        }
    }

    private String command(int i) { return i < commands.size() ? commands.get(i) : ""; }

    // ── « Comment XP ? » ──────────────────────────────────────────────

    private void drawHowTo(GuiGraphics g, int x, int y, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y, x + W, y + H, 0xCC000000);
        int pw = W - 30, ph = 20 + howToXp.size() * 11 + 22;
        int px = x + 15, py = y + (H - ph) / 2;
        g.fill(px, py, px + pw, py + ph, BG);
        border(g, px, py, pw, ph, GOLD);
        g.drawString(font, "COMMENT GAGNER DE L'XP ?", px + 8, py + 7, GOLD, false);
        int ly = py + 22;
        for (String line : howToXp) {
            if (line.startsWith("#")) {
                String h = line.substring(1);
                g.drawString(font, h, px + 8, ly, h.contains("CREATE") ? CRE : POK, false);
            } else {
                g.drawString(font, line, px + 12, ly, line.startsWith("  ") ? INV_LAB : WHITE, false);
            }
            ly += 11;
        }
        centered(g, "Clic ou ESC pour fermer", px + pw / 2, py + ph - 13, 0xFF556677);
        g.pose().popPose();
    }

    // ── Input ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        if (showHowTo) { showHowTo = false; click(); return true; }
        int x = ox(), y = oy();

        if (inside(mx, my, x + W - 22, y + 6, 16, 16)) { onClose(); return true; }

        for (int i = 0; i < BTN_LABELS.length; i++) {
            if (!inside(mx, my, x + BTN_X, y + TOP_Y + i * (BTN_H + BTN_GAP), BTN_W, BTN_H)) continue;
            if (i == HOWTO) { showHowTo = true; click(); return true; }
            String cmd = command(i);
            if (!cmd.isEmpty()) {
                click();
                onClose();
                minecraft.player.connection.sendCommand(cmd);
            }
            return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) {
            if (showHowTo) { showHowTo = false; return true; }
            onClose(); return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, mods);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String playtime() {
        long h = playSeconds / 3600, m = (playSeconds % 3600) / 60;
        return h + "h " + String.format("%02d", m) + "m";
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

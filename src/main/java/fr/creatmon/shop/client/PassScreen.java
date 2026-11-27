package fr.creatmon.shop.client;

import fr.creatmon.shop.network.PassClaimPacket;
import fr.creatmon.shop.network.PassSyncPacket;
import fr.creatmon.shop.pass.PassConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
 * Pass de progression (/pass, /rangs) — deux voies : Cobblemon et Create.
 * Une seule ligne de récompenses, toutes gratuites.
 */
public class PassScreen extends Screen {

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W        = 400;
    private static final int H        = 232;
    private static final int HEADER_H = 32;
    private static final int PER_PAGE = 9;
    private static final int XP_BTN_X = 128;

    private static final int INFO_Y   = HEADER_H + 6;
    private static final int INFO_H   = 58;
    private static final int STRIP_Y  = INFO_Y + INFO_H + 6;
    private static final int STRIP_H  = 100;
    private static final int ARROW_W  = 16;
    private static final int STRIP_X  = 6;
    private static final int STRIP_W  = W - 12;
    private static final int SLOTS_X  = STRIP_X + ARROW_W + 4;
    private static final int SLOT_W   = (STRIP_W - 2 * (ARROW_W + 4)) / PER_PAGE;
    private static final int ICON     = 26;
    private static final int ICON_Y   = STRIP_Y + 16;
    private static final int LINE_Y   = ICON_Y + ICON + 14;
    private static final int SCRUB_Y  = H - 17;

    // ── Couleurs (même palette que les autres GUI) ────────────────────
    private static final int BG       = 0xFF0E1626;
    private static final int PANEL    = 0xFF0A1520;
    private static final int DARK     = 0xFF0C131E;
    private static final int GOLD     = 0xFFD49E2A;
    private static final int GOLD_DIM = 0xFF7A5A18;
    private static final int WHITE    = 0xFFFFFFFF;
    private static final int INV_LAB  = 0xFF8899AA;
    private static final int GREEN    = 0xFF44FF88;
    private static final int LOCKED   = 0xFF2A3445;

    private static int accent(String track) {
        return PassConfig.CREATE.equals(track) ? 0xFFE0A030 : 0xFF4FA3FF;
    }
    private static String label(String track) {
        return PassConfig.CREATE.equals(track) ? "CREATE" : "COBBLEMON";
    }

    // ── State ─────────────────────────────────────────────────────────
    private final Map<String, PassSyncPacket.TrackState> tracks = new LinkedHashMap<>();
    private final Map<String, ItemStack> stackCache = new HashMap<>();
    private String active;
    private int    page;

    public PassScreen(PassSyncPacket pkt) {
        super(Component.literal("Pass de progression"));
        apply(pkt);
        active = !pkt.focusTrack().isEmpty() && tracks.containsKey(pkt.focusTrack())
            ? pkt.focusTrack()
            : tracks.keySet().stream().findFirst().orElse(PassConfig.COBBLEMON);
        page = pageOfCurrent();
    }

    public void onSync(PassSyncPacket pkt) {
        apply(pkt);
        if (!pkt.focusTrack().isEmpty() && tracks.containsKey(pkt.focusTrack())) {
            active = pkt.focusTrack();
            page = pageOfCurrent();
        }
        page = Math.min(page, pageCount() - 1);
    }

    private void apply(PassSyncPacket pkt) {
        tracks.clear();
        for (var t : pkt.tracks()) tracks.put(t.id(), t);
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    private PassSyncPacket.TrackState cur() { return tracks.get(active); }
    private int pageCount() {
        var t = cur();
        return t == null ? 1 : Math.max(1, (t.maxLevel() + PER_PAGE - 1) / PER_PAGE);
    }
    private int pageOfCurrent() {
        var t = cur();
        return t == null ? 0 : Math.max(0, (t.level() - 1) / PER_PAGE);
    }

    private PassConfig.Reward rewardAt(int level) {
        for (var r : cur().rewards()) if (r.level() == level) return r;
        return null;
    }
    private boolean claimable(PassConfig.Reward r) {
        return r != null && r.level() <= cur().level() && !cur().claimed().contains(r.level());
    }
    private int claimableCount() {
        int n = 0;
        for (var r : cur().rewards()) if (claimable(r)) n++;
        return n;
    }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = (width - W) / 2, y = (height - H) / 2;
        drawChrome(g, x, y);
        drawHeader(g, x, y, mx, my);
        if (cur() == null) return;
        drawInfo(g, x, y, mx, my);
        drawStrip(g, x, y, mx, my);
        drawScrubber(g, x, y, mx, my);
    }

    private void drawChrome(GuiGraphics g, int x, int y) {
        g.fill(x, y, x+W, y+H, BG);
        border(g, x, y, W, H, GOLD);
        int d = 3;
        g.fill(x+2, y+2, x+2+d, y+2+d, GOLD); g.fill(x+W-2-d, y+2, x+W-2, y+2+d, GOLD);
        g.fill(x+2, y+H-2-d, x+2+d, y+H-2, GOLD); g.fill(x+W-2-d, y+H-2-d, x+W-2, y+H-2, GOLD);
        g.fill(x, y+HEADER_H, x+W, y+HEADER_H+1, GOLD_DIM);
        g.fill(x+1, y+H-22, x+W-1, y+H-21, GOLD_DIM);
    }

    // ── Header : titre + onglets ──────────────────────────────────────

    private void drawHeader(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(font, "PASS DE PROGRESSION", x + 10, y + 7, GOLD, false);
        g.drawString(font, "Tout est gratuit !", x + 10, y + 18, INV_LAB, false);

        // Bouton « Valeurs XP »
        int vx = x + XP_BTN_X, vw = font.width("VALEURS XP") + 16;
        boolean vh = inside(mx, my, vx, y + 7, vw, 18);
        g.fill(vx, y + 7, vx + vw, y + 25, vh ? 0xFF22324A : 0xFF152235);
        border(g, vx, y + 7, vw, 18, vh ? GOLD : GOLD_DIM);
        g.fill(vx, y + 7, vx + 2, y + 25, GOLD);
        g.drawString(font, "VALEURS XP", vx + 9, y + 12, vh ? GOLD : WHITE, false);

        int tx = x + W - 8;
        List<String> ids = new ArrayList<>(tracks.keySet());
        Collections.reverse(ids);
        for (String id : ids) {
            int tw = font.width(label(id)) + 16;
            tx -= tw;
            boolean sel = id.equals(active);
            boolean hov = inside(mx, my, tx, y + 7, tw, 18);
            int ac = accent(id);
            g.fill(tx, y+7, tx+tw, y+25, sel ? (ac & 0x00FFFFFF) | 0x55000000 : (hov ? 0xFF1A2638 : 0xFF152235));
            border(g, tx, y+7, tw, 18, sel ? ac : GOLD_DIM);
            if (sel) g.fill(tx+1, y+23, tx+tw-1, y+25, ac);
            g.drawString(font, label(id), tx + 8, y + 12, sel ? WHITE : INV_LAB, false);
            // pastille s'il reste des récompenses à récupérer sur l'autre voie
            var st = tracks.get(id);
            if (!sel && st != null && hasClaimable(st)) g.fill(tx+tw-5, y+9, tx+tw-2, y+12, GOLD);
            tx -= 4;
        }
    }

    private static boolean hasClaimable(PassSyncPacket.TrackState st) {
        for (var r : st.rewards()) if (r.level() <= st.level() && !st.claimed().contains(r.level())) return true;
        return false;
    }

    // ── Panneau d'informations ────────────────────────────────────────

    private void drawInfo(GuiGraphics g, int x, int y, int mx, int my) {
        var t = cur();
        int ac = accent(active);
        int px = x + 6, py = y + INFO_Y, pw = W - 12;
        g.fill(px, py, px+pw, py+INFO_H, PANEL);
        border(g, px, py, pw, INFO_H, GOLD_DIM);
        g.fill(px, py, px+2, py+INFO_H, ac);

        // Rang + niveau
        g.drawString(font, "RANG", px + 10, py + 6, INV_LAB, false);
        g.drawString(font, t.name().toUpperCase(Locale.ROOT), px + 10, py + 17, ac, false);
        g.drawString(font, "NIVEAU", px + 100, py + 6, INV_LAB, false);
        g.drawString(font, t.level() + " / " + t.maxLevel(), px + 100, py + 17, GOLD, false);
        g.drawString(font, "XP TOTALE", px + 170, py + 6, INV_LAB, false);
        g.drawString(font, fmt(t.totalXp()), px + 170, py + 17, WHITE, false);

        // Barre d'XP
        boolean maxed = t.level() >= t.maxLevel();
        int bx = px + 10, by = py + 34, bw = 230, bh = 7;
        g.fill(bx, by, bx+bw, by+bh, DARK);
        float frac = maxed ? 1f : t.xpInLevel() / (float) Math.max(1, t.xpForNext());
        g.fill(bx, by, bx + (int)(bw * frac), by+bh, ac);
        g.fill(bx, by, bx + (int)(bw * frac), by+1, 0x55FFFFFF);
        String xpTxt = maxed ? "Niveau maximum atteint !" : fmt(t.xpInLevel()) + " / " + fmt(t.xpForNext()) + " XP";
        g.drawString(font, xpTxt, bx, by + 11, INV_LAB, false);

        // Prochaine récompense
        PassConfig.Reward next = null;
        for (var r : t.rewards()) if (r.level() > t.level()) { next = r; break; }
        int nx = px + 252;
        g.drawString(font, "PROCHAINE", nx, py + 6, INV_LAB, false);
        if (next != null) {
            int bxI = nx + 6, byI = py + 18;
            g.fill(bxI, byI, bxI+22, byI+22, DARK);
            border(g, bxI, byI, 22, 22, GOLD);
            ItemStack s = stack(next);
            g.renderItem(s, bxI + 3, byI + 3);
            g.renderItemDecorations(font, s, bxI + 3, byI + 3);
            g.drawString(font, "Nv." + next.level(), bxI + 26, byI + 7, GOLD, false);
            if (inside(mx, my, bxI, byI, 22, 22)) pendingTooltip = tooltip(next, s);
        } else {
            g.drawString(font, "—", nx + 6, py + 24, INV_LAB, false);
        }

        // Bouton tout récupérer
        int n = claimableCount();
        int cw = 58, ch = 40, cx = px + pw - cw - 6, cy = py + 9;
        boolean hov = inside(mx, my, cx, cy, cw, ch);
        if (n > 0) {
            float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 250.0));
            g.fill(cx, cy, cx+cw, cy+ch, hov ? 0xFFE8B23A : GOLD);
            g.fill(cx, cy+ch-3, cx+cw, cy+ch, GOLD_DIM);
            border(g, cx-1, cy-1, cw+2, ch+2, ((int)(pulse * 0xAA) << 24) | 0xFFFFFF);
            centered(g, "TOUT", cx + cw/2, cy + 8, 0xFF1A1206);
            centered(g, "RÉCUPÉRER", cx + cw/2, cy + 18, 0xFF1A1206);
            centered(g, "(" + n + ")", cx + cw/2, cy + 28, 0xFF1A1206);
        } else {
            g.fill(cx, cy, cx+cw, cy+ch, 0xFF152235);
            border(g, cx, cy, cw, ch, LOCKED);
            centered(g, "RIEN À", cx + cw/2, cy + 12, INV_LAB);
            centered(g, "RÉCUPÉRER", cx + cw/2, cy + 23, INV_LAB);
        }
    }

    // ── Bande de récompenses ──────────────────────────────────────────

    private List<Component> pendingTooltip;

    private void drawStrip(GuiGraphics g, int x, int y, int mx, int my) {
        var t = cur();
        int ac = accent(active);
        int sx = x + STRIP_X, sy = y + STRIP_Y;
        g.fill(sx, sy, sx+STRIP_W, sy+STRIP_H, PANEL);
        border(g, sx, sy, STRIP_W, STRIP_H, GOLD_DIM);
        g.drawString(font, "RÉCOMPENSES " + label(active), sx + 6, sy + 4, INV_LAB, false);

        // Flèches de page
        drawArrow(g, x + STRIP_X + 2, mx, my, true,  page > 0);
        drawArrow(g, x + STRIP_X + STRIP_W - ARROW_W - 2, mx, my, false, page < pageCount() - 1);

        int first = page * PER_PAGE + 1;
        int lineL = x + SLOTS_X, lineR = x + SLOTS_X + SLOT_W * PER_PAGE;
        int ly = y + LINE_Y;

        // Ligne continue : fond sombre puis portion atteinte (avec avancement vers le niveau suivant)
        g.fill(lineL, ly - 2, lineR, ly + 2, LOCKED);
        float progLevel = t.level() + (t.level() >= t.maxLevel() ? 0f
            : t.xpInLevel() / (float) Math.max(1, t.xpForNext()));
        float col = progLevel - first; // position en colonnes depuis le centre du 1er slot
        int fillTo = (int)(lineL + SLOT_W / 2f + col * SLOT_W);
        fillTo = Math.max(lineL, Math.min(lineR, fillTo));
        if (first > t.level() + 1) fillTo = lineL;
        g.fill(lineL, ly - 2, fillTo, ly + 2, ac);
        g.fill(lineL, ly - 2, fillTo, ly - 1, 0x55FFFFFF);

        for (int i = 0; i < PER_PAGE; i++) {
            int level = first + i;
            if (level > t.maxLevel()) break;
            drawSlot(g, x + SLOTS_X + i * SLOT_W, y, level, mx, my);
        }

        if (pendingTooltip != null) {
            g.renderComponentTooltip(font, pendingTooltip, mx, my);
            pendingTooltip = null;
        }
    }

    private void drawSlot(GuiGraphics g, int colX, int y, int level, int mx, int my) {
        var t = cur();
        int ac = accent(active);
        int cx = colX + SLOT_W / 2;
        boolean reached = level <= t.level();
        boolean current = level == t.level();
        PassConfig.Reward r = rewardAt(level);
        boolean claimed = r != null && t.claimed().contains(level);
        boolean canClaim = claimable(r);

        // Surbrillance du niveau actuel
        if (current) {
            int hy = y + STRIP_Y + 13;
            g.fill(colX + 1, hy, colX + SLOT_W - 1, y + STRIP_Y + STRIP_H - 4, 0x22D49E2A);
            border(g, colX + 1, hy, SLOT_W - 2, STRIP_H - 17, 0x99D49E2A);
        }

        // Case de l'item
        int ix = cx - ICON / 2, iy = y + ICON_Y;
        boolean hov = inside(mx, my, ix, iy, ICON, ICON);
        int frame = canClaim ? GOLD : claimed ? 0xFF2E8B57 : reached ? ac : LOCKED;
        int fill  = canClaim ? 0xFF3A2C0E : claimed ? 0xFF10301F : reached ? 0xFF152235 : DARK;
        g.fill(ix, iy, ix+ICON, iy+ICON, fill);
        border(g, ix, iy, ICON, ICON, frame);
        if (canClaim) {
            float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0 + level));
            border(g, ix-1, iy-1, ICON+2, ICON+2, ((int)(pulse * 0xCC) << 24) | 0xFFD700);
        }
        if (hov) g.fill(ix+1, iy+1, ix+ICON-1, iy+ICON-1, 0x33FFFFFF);

        if (r != null) {
            ItemStack s = stack(r);
            g.renderItem(s, ix + 5, iy + 5);
            g.renderItemDecorations(font, s, ix + 5, iy + 5);
            if (!reached) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.fill(ix+1, iy+1, ix+ICON-1, iy+ICON-1, 0x88000000);
                g.pose().popPose();
            }
            if (claimed) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.drawString(font, "✔", ix + ICON - 7, iy + 1, GREEN, false);
                g.pose().popPose();
            }
            if (hov) pendingTooltip = tooltip(r, s);
        } else {
            centered(g, "—", cx, iy + 9, INV_LAB);
        }

        // Pastille du niveau sur la ligne
        String lv = String.valueOf(level);
        int bw = Math.max(14, font.width(lv) + 6), bh = 11;
        int bx = cx - bw / 2, by = y + LINE_Y - bh / 2;
        g.fill(bx, by, bx+bw, by+bh, reached ? ac : 0xFF1A2230);
        border(g, bx, by, bw, bh, current ? GOLD : reached ? 0xFFFFFFFF : LOCKED);
        centered(g, lv, cx, by + 2, reached ? WHITE : INV_LAB);

        // Statut
        String status = r == null ? "" : claimed ? "Obtenu" : canClaim ? "Récupérer" : "Verrouillé";
        int sc = claimed ? GREEN : canClaim ? GOLD : 0xFF556677;
        g.pose().pushPose();
        g.pose().translate(cx, y + LINE_Y + 12, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, status, -font.width(status) / 2, 0, sc, false);
        g.pose().popPose();
    }

    private void drawArrow(GuiGraphics g, int ax, int mx, int my, boolean left, boolean enabled) {
        int y = (height - H) / 2;
        int ay = y + STRIP_Y + 14, ah = STRIP_H - 18;
        boolean hov = enabled && inside(mx, my, ax, ay, ARROW_W, ah);
        g.fill(ax, ay, ax+ARROW_W, ay+ah, enabled ? (hov ? 0xFF22324A : 0xFF152235) : DARK);
        border(g, ax, ay, ARROW_W, ah, enabled ? (hov ? GOLD : GOLD_DIM) : LOCKED);
        centered(g, left ? "◀" : "▶", ax + ARROW_W / 2, ay + ah / 2 - 4, enabled ? GOLD : LOCKED);
    }

    // ── Pagination : barre de défilement par plage de niveaux ─────────

    private int scrubX(int x) { return x + 110; }
    private static final int SCRUB_W = 180;

    private void drawScrubber(GuiGraphics g, int x, int y, int mx, int my) {
        var t = cur();
        int first = page * PER_PAGE + 1;
        int last  = Math.min(t.maxLevel(), first + PER_PAGE - 1);
        String range = "Niv. " + first + " – " + last;
        g.drawString(font, range, x + 10, y + SCRUB_Y - 1, WHITE, false);

        int bx = scrubX(x), by = y + SCRUB_Y + 1, bh = 5;
        boolean hov = inside(mx, my, bx, by - 3, SCRUB_W, bh + 6);
        g.fill(bx, by, bx+SCRUB_W, by+bh, DARK);
        border(g, bx, by, SCRUB_W, bh, hov ? GOLD_DIM : LOCKED);
        // progression du joueur
        int prog = (int)(SCRUB_W * Math.min(1f, t.level() / (float) t.maxLevel()));
        g.fill(bx+1, by+1, bx + Math.max(1, prog) - 1, by+bh-1, (accent(active) & 0x00FFFFFF) | 0x88000000);
        // fenêtre visible
        int pc = pageCount();
        int ww = Math.max(8, SCRUB_W / pc);
        int wx = bx + (pc <= 1 ? 0 : (int)((SCRUB_W - ww) * (page / (float)(pc - 1))));
        g.fill(wx, by - 2, wx + ww, by + bh + 2, GOLD);
        g.fill(wx, by - 2, wx + ww, by - 1, 0xFFFFE08A);

        String hint = "Molette / ← →  •  ESC";
        g.drawString(font, hint, x + W - 10 - font.width(hint), y + SCRUB_Y - 1, INV_LAB, false);
    }

    // ── Input ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int x = (width - W) / 2, y = (height - H) / 2;

        // Valeurs XP
        if (inside(mx, my, x + XP_BTN_X, y + 7, font.width("VALEURS XP") + 16, 18)) {
            click();
            PacketDistributor.sendToServer(new fr.creatmon.shop.network.XpTableRequestPacket());
            return true;
        }

        // Onglets
        int tx = x + W - 8;
        List<String> ids = new ArrayList<>(tracks.keySet());
        Collections.reverse(ids);
        for (String id : ids) {
            int tw = font.width(label(id)) + 16;
            tx -= tw;
            if (inside(mx, my, tx, y + 7, tw, 18)) {
                if (!id.equals(active)) { active = id; page = pageOfCurrent(); click(); }
                return true;
            }
            tx -= 4;
        }
        if (cur() == null) return super.mouseClicked(mx, my, btn);

        // Tout récupérer
        int pw = W - 12, cw = 58;
        if (inside(mx, my, x + 6 + pw - cw - 6, y + INFO_Y + 9, cw, 40) && claimableCount() > 0) {
            claim(0);
            return true;
        }

        // Flèches
        int ay = y + STRIP_Y + 14, ah = STRIP_H - 18;
        if (inside(mx, my, x + STRIP_X + 2, ay, ARROW_W, ah)) { turn(-1); return true; }
        if (inside(mx, my, x + STRIP_X + STRIP_W - ARROW_W - 2, ay, ARROW_W, ah)) { turn(1); return true; }

        // Récompense individuelle
        int first = page * PER_PAGE + 1;
        for (int i = 0; i < PER_PAGE; i++) {
            int cx = x + SLOTS_X + i * SLOT_W + SLOT_W / 2;
            if (inside(mx, my, cx - ICON / 2, y + ICON_Y, ICON, ICON)) {
                PassConfig.Reward r = rewardAt(first + i);
                if (claimable(r)) claim(r.level());
                return true;
            }
        }

        // Barre de pagination
        int bx = scrubX(x);
        if (inside(mx, my, bx, y + SCRUB_Y - 2, SCRUB_W, 11)) {
            int target = (int)((mx - bx) / SCRUB_W * pageCount());
            setPage(target);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy != 0) { turn(dy > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { onClose(); return true; }
        if (keyCode == 263) { turn(-1); return true; } // ←
        if (keyCode == 262) { turn(1);  return true; } // →
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, mods);
    }

    private void turn(int d) { setPage(page + d); }

    private void setPage(int p) {
        int np = Math.max(0, Math.min(pageCount() - 1, p));
        if (np != page) { page = np; click(); }
    }

    private void claim(int level) {
        PacketDistributor.sendToServer(new PassClaimPacket(active, level));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private List<Component> tooltip(PassConfig.Reward r, ItemStack s) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(s.isEmpty() ? r.item() : s.getHoverName().getString())
            .withStyle(st -> st.withColor(0xFFD49E2A)));
        lines.add(Component.literal("Quantité : ×" + r.count()).withStyle(st -> st.withColor(0xCCCCCC)));
        lines.add(Component.literal("Niveau " + r.level()).withStyle(st -> st.withColor(0x8899AA)));
        var t = cur();
        if (t.claimed().contains(r.level()))
            lines.add(Component.literal("✔ Déjà récupéré").withStyle(st -> st.withColor(0x44FF88)));
        else if (r.level() <= t.level())
            lines.add(Component.literal("» Clic pour récupérer").withStyle(st -> st.withColor(0xFFD700)));
        else
            lines.add(Component.literal("Verrouillé").withStyle(st -> st.withColor(0xFF5555)));
        return lines;
    }

    private ItemStack stack(PassConfig.Reward r) {
        return stackCache.computeIfAbsent(r.item() + "#" + r.count(), k -> {
            ResourceLocation rl = ResourceLocation.tryParse(r.item());
            if (rl == null) return new ItemStack(Items.BARRIER);
            var item = BuiltInRegistries.ITEM.get(rl);
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item, Math.max(1, r.count()));
        });
    }

    private void centered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x+w, y+1, c); g.fill(x, y+h-1, x+w, y+h, c);
        g.fill(x, y, x+1, y+h, c); g.fill(x+w-1, y, x+w, y+h, c);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String fmt(int n) {
        String s = Integer.toString(n);
        var sb = new StringBuilder();
        int off = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

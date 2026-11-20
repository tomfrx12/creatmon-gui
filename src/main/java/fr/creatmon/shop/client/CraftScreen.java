package fr.creatmon.shop.client;

import fr.creatmon.shop.craft.CraftData;
import fr.creatmon.shop.craft.CraftTarget;
import fr.creatmon.shop.network.CraftSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class CraftScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("creatmon_craft_screen");

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W        = 380;
    private static final int H        = 250;
    private static final int HEADER_H = 36;
    private static final int SLOT_W   = 90;
    private static final int SLOT_H   = 85;
    private static final int COLS     = 3;
    private static final int ROWS     = 2;
    private static final int GAP      = 4;
    private static final int GRID_W   = COLS * SLOT_W + (COLS - 1) * GAP;
    private static final int GRID_X   = 6;
    private static final int GRID_Y   = HEADER_H + 6;
    private static final int TIMER_X  = GRID_X + GRID_W + 8;
    private static final int TIMER_W  = W - TIMER_X - 6;

    // ── Couleurs ──────────────────────────────────────────────────────
    private static final int BG       = 0xFF0E1626;
    private static final int GOLD     = 0xFFD49E2A;
    private static final int GOLD_DIM = 0xFF7A5A18;
    private static final int WHITE    = 0xFFFFFFFF;
    private static final int INV_LAB  = 0xFF8899AA;
    private static final int DONE_OV  = 0x9900CC44;

    private final Map<String, ItemStack> stackCache = new HashMap<>();

    // ── State ─────────────────────────────────────────────────────────
    private List<CraftTarget> targets;
    private Set<String>       completed;
    private long              timeRemainingMs;
    private long              openedAt;

    public CraftScreen(CraftSyncPacket pkt) {
        super(Component.literal("Atelier Create"));
        this.targets         = pkt.targets();
        this.completed       = pkt.completed();
        this.timeRemainingMs = pkt.timeRemainingMs();
        this.openedAt        = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0f));
    }

    public void onSync(CraftSyncPacket pkt) {
        this.targets         = pkt.targets();
        this.completed       = pkt.completed();
        this.timeRemainingMs = pkt.timeRemainingMs();
        this.openedAt        = System.currentTimeMillis();
        stackCache.clear();
    }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = (width - W) / 2, y = (height - H) / 2;
        long remaining = Math.max(0, timeRemainingMs - (System.currentTimeMillis() - openedAt));

        drawChrome(g, x, y);
        drawHeader(g, x, y);
        drawGrid(g, x, y, mx, my);
        drawTimer(g, x, y, remaining);
        drawFooter(g, x, y);
    }

    // ── Chrome ────────────────────────────────────────────────────────

    private void drawChrome(GuiGraphics g, int x, int y) {
        g.fill(x, y, x+W, y+H, BG);
        g.fill(x, y, x+W, y+1, GOLD); g.fill(x, y+H-1, x+W, y+H, GOLD);
        g.fill(x, y, x+1, y+H, GOLD); g.fill(x+W-1, y, x+W, y+H, GOLD);
        int d = 3;
        g.fill(x+2, y+2, x+2+d, y+2+d, GOLD); g.fill(x+W-2-d, y+2, x+W-2, y+2+d, GOLD);
        g.fill(x+2, y+H-2-d, x+2+d, y+H-2, GOLD); g.fill(x+W-2-d, y+H-2-d, x+W-2, y+H-2, GOLD);
        g.fill(x, y+HEADER_H, x+W, y+HEADER_H+1, GOLD_DIM);
        g.fill(x+TIMER_X-4, y+HEADER_H+1, x+TIMER_X-3, y+H-1, 0xFF0C131E);
    }

    // ── Header ────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int x, int y) {
        String title = "ATELIER CREATE";
        g.drawString(font, title, x + (W - font.width(title)) / 2, y + 5, GOLD, false);

        int done = completed.size(), total = targets.size();
        String prog = done + "/" + total + " objectifs";
        int pw = font.width(prog) + 10;
        int px = x + (W - pw) / 2;
        g.fill(px, y+16, px+pw, y+28, 0xFF152235);
        g.fill(px, y+16, px+pw, y+17, GOLD_DIM); g.fill(px, y+27, px+pw, y+28, GOLD_DIM);
        g.fill(px, y+16, px+1,  y+28, GOLD_DIM); g.fill(px+pw-1, y+16, px+pw, y+28, GOLD_DIM);
        int progColor = done == total ? 0xFF44FF88 : (done > 0 ? 0xFFFFCC44 : INV_LAB);
        g.drawString(font, prog, px + 5, y + 19, progColor, false);
    }

    // ── Grid ──────────────────────────────────────────────────────────

    private void drawGrid(GuiGraphics g, int x, int y, int mx, int my) {
        for (int i = 0; i < targets.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int sx = x + GRID_X + col * (SLOT_W + GAP);
            int sy = y + GRID_Y + row * (SLOT_H + GAP);
            drawSlot(g, sx, sy, targets.get(i), mx, my);
        }
    }

    private void drawSlot(GuiGraphics g, int sx, int sy, CraftTarget t, int mx, int my) {
        boolean done = completed.contains(t.itemId());
        boolean hov  = mx >= sx && mx < sx+SLOT_W && my >= sy && my < sy+SLOT_H;
        int diffColor = t.difficulty().color;
        int diffBg    = t.difficulty().bgColor;

        g.fill(sx, sy, sx+SLOT_W, sy+SLOT_H, 0xFF0C131E);
        g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, diffBg);
        g.fill(sx, sy, sx+SLOT_W, sy+2, diffColor);
        g.fill(sx, sy, sx+2, sy+SLOT_H, diffColor);

        if (done)         g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, DONE_OV);
        if (hov && !done) g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, 0x33FFFFFF);

        // ── Item icon 2× ─────────────────────────────────────────────
        ItemStack stack = getStack(t.itemId());
        if (!stack.isEmpty()) {
            int iconSize = 32; // 16 × 2
            int ix = sx + (SLOT_W - iconSize) / 2;
            int iy = sy + 14;
            g.pose().pushPose();
            g.pose().translate(ix, iy, 200);
            g.pose().scale(2.0f, 2.0f, 2.0f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }

        // ── Item name ─────────────────────────────────────────────────
        String rawName = stack.isEmpty()
            ? t.itemId().contains(":") ? t.itemId().split(":")[1] : t.itemId()
            : stack.getHoverName().getString();
        // capitalize first letter, shorten to fit slot width
        String name = rawName.isEmpty() ? "" : Character.toUpperCase(rawName.charAt(0)) + rawName.substring(1);
        if (font.width(name) > SLOT_W - 6) {
            while (name.length() > 1 && font.width(name + "…") > SLOT_W - 6) name = name.substring(0, name.length() - 1);
            name += "…";
        }
        int nameColor = done ? 0xFF44FF88 : WHITE;
        g.drawString(font, name, sx + (SLOT_W - font.width(name)) / 2, sy + 5, nameColor, false);

        // ── Quantity ──────────────────────────────────────────────────
        String qty = "×" + t.quantity();
        g.drawString(font, qty, sx + (SLOT_W - font.width(qty)) / 2, sy + 50, INV_LAB, false);

        // ── Difficulty ────────────────────────────────────────────────
        g.drawString(font, t.difficulty().label,
            sx + (SLOT_W - font.width(t.difficulty().label)) / 2, sy + 62, diffColor, false);

        // ── Reward ───────────────────────────────────────────────────
        String rew = "+" + t.difficulty().reward + " €";
        g.drawString(font, rew,
            sx + (SLOT_W - font.width(rew)) / 2, sy + 73, 0xFFD4A82A, false);

        if (done) {
            String check = "✔";
            g.drawString(font, check, sx + SLOT_W - font.width(check) - 4, sy + 4, 0xFF44FF88, false);
        }
    }

    // ── Item stack cache ──────────────────────────────────────────────

    private ItemStack getStack(String itemId) {
        return stackCache.computeIfAbsent(itemId, id -> {
            try {
                var rl = ResourceLocation.tryParse(id);
                if (rl == null) {
                    LOGGER.warn("[Atelier] ResourceLocation invalide: {}", id);
                    return ItemStack.EMPTY;
                }
                var key = ResourceKey.create(Registries.ITEM, rl);
                var optHolder = BuiltInRegistries.ITEM.getHolder(key);
                if (optHolder.isEmpty()) {
                    LOGGER.warn("[Atelier] Item introuvable dans le registry: {}", id);
                    return ItemStack.EMPTY;
                }
                var item = optHolder.get().value();
                if (item == Items.AIR) return ItemStack.EMPTY;
                return new ItemStack(item);
            } catch (Throwable t) {
                LOGGER.error("[Atelier] Exception pour {}: {}", id, t.getMessage());
                return ItemStack.EMPTY;
            }
        });
    }

    // ── Timer ─────────────────────────────────────────────────────────

    private void drawTimer(GuiGraphics g, int x, int y, long remainingMs) {
        int tx = x + TIMER_X, ty = y + GRID_Y;
        int tw = TIMER_W, th = H - GRID_Y - 26;

        g.fill(tx, ty, tx+tw, ty+th, 0xFF0A1520);
        g.fill(tx, ty, tx+tw, ty+1, GOLD_DIM); g.fill(tx, ty+th-1, tx+tw, ty+th, GOLD_DIM);
        g.fill(tx, ty, tx+1, ty+th, GOLD_DIM); g.fill(tx+tw-1, ty, tx+tw, ty+th, GOLD_DIM);

        String label = "TEMPS";
        g.drawString(font, label, tx + (tw - font.width(label)) / 2, ty + 6, INV_LAB, false);

        int timerColor = remainingMs < 5 * 60 * 1000 ? 0xFFFF5555 : 0xFF44CCFF;

        long h2 = remainingMs / 3_600_000;
        String hStr = h2 + "h";
        g.drawString(font, hStr, tx + (tw - font.width(hStr)) / 2, ty + 20, timerColor, false);

        long m = (remainingMs % 3_600_000) / 60_000;
        long s = (remainingMs % 60_000) / 1_000;
        String msStr = String.format("%02d:%02d", m, s);
        g.drawString(font, msStr, tx + (tw - font.width(msStr)) / 2, ty + 32, timerColor, false);

        float frac = Math.min(1f, remainingMs / (float) CraftData.DURATION_MS);
        int barW = tw - 4;
        int barY = ty + th - 14;
        g.fill(tx+2, barY, tx+2+barW, barY+6, 0xFF0C131E);
        g.fill(tx+2, barY, tx+2+(int)(barW*frac), barY+6, timerColor);
    }

    // ── Footer ────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics g, int x, int y) {
        g.fill(x+1, y+H-20, x+W-1, y+H-19, GOLD_DIM);
        String hint = "ESC pour fermer";
        g.drawString(font, hint, x + (W - font.width(hint)) / 2, y + H - 15, INV_LAB, false);
    }

    // ── Keyboard ──────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { onClose(); return true; }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

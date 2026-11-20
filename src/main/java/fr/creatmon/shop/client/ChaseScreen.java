package fr.creatmon.shop.client;

import fr.creatmon.shop.chase.ChaseData;
import fr.creatmon.shop.chase.ChaseTarget;
import fr.creatmon.shop.network.ChaseSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ChaseScreen extends Screen {

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

    // Cache ItemStack Pokémon (recréé à chaque ouverture)
    private final Map<String, ItemStack> spriteCache = new HashMap<>();

    // ── State ─────────────────────────────────────────────────────────
    private List<ChaseTarget> targets;
    private Set<String>       completed;
    private long              timeRemainingMs;
    private long              openedAt;

    public ChaseScreen(ChaseSyncPacket pkt) {
        super(Component.literal("Tableau de chasse"));
        this.targets         = pkt.targets();
        this.completed       = pkt.completed();
        this.timeRemainingMs = pkt.timeRemainingMs();
        this.openedAt        = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    public void onSync(ChaseSyncPacket pkt) {
        this.targets         = pkt.targets();
        this.completed       = pkt.completed();
        this.timeRemainingMs = pkt.timeRemainingMs();
        this.openedAt        = System.currentTimeMillis();
        spriteCache.clear();
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
        String title = "TABLEAU DE CHASSE";
        g.drawString(font, title, x + (W - font.width(title)) / 2, y + 5, GOLD, false);

        int done = completed.size(), total = targets.size();
        String prog = done + "/" + total + " objectifs";
        int pw = font.width(prog) + 10;
        int px = x + (W - pw) / 2;
        g.fill(px, y+16, px+pw, y+28, 0xFF152235);
        g.fill(px, y+16, px+pw, y+17, GOLD_DIM); g.fill(px, y+27, px+pw, y+28, GOLD_DIM);
        g.fill(px, y+16, px+1,  y+28, GOLD_DIM); g.fill(px+pw-1, y+16, px+pw, y+28, GOLD_DIM);
        int progColor = done == total ? 0xFF44FF88 : (done > 0 ? 0xFFFFCC44 : 0xFF8899AA);
        g.drawString(font, prog, px + 5, y + 19, progColor, false);
    }

    // ── Pokémon grid ──────────────────────────────────────────────────

    private void drawGrid(GuiGraphics g, int x, int y, int mx, int my) {
        for (int i = 0; i < targets.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int sx = x + GRID_X + col * (SLOT_W + GAP);
            int sy = y + GRID_Y + row * (SLOT_H + GAP);
            drawPokemonSlot(g, sx, sy, targets.get(i), mx, my);
        }
    }

    private void drawPokemonSlot(GuiGraphics g, int sx, int sy, ChaseTarget t, int mx, int my) {
        boolean done = completed.contains(t.speciesName());
        boolean hov  = mx >= sx && mx < sx+SLOT_W && my >= sy && my < sy+SLOT_H;
        int rarColor = t.rarity().color;
        int rarBg    = t.rarity().bgColor;

        g.fill(sx, sy, sx+SLOT_W, sy+SLOT_H, 0xFF0C131E);
        g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, rarBg);
        g.fill(sx, sy, sx+SLOT_W, sy+2, rarColor);
        g.fill(sx, sy, sx+2, sy+SLOT_H, rarColor);

        if (done)         g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, DONE_OV);
        if (hov && !done) g.fill(sx+1, sy+1, sx+SLOT_W-1, sy+SLOT_H-1, 0x33FFFFFF);

        // ── Sprite 3D (PokemonItem = rendu 3D natif Cobblemon) ────────
        // Taille : 2.5× = 40×40 px, centré dans la zone sprite (sy+15 → sy+58)
        ItemStack sprite = getSprite(t.speciesName());
        if (!sprite.isEmpty()) {
            int displaySize = 40;   // 16 * 2.5
            int ix = sx + (SLOT_W - displaySize) / 2;
            int iy = sy + 15 + (43 - displaySize) / 2; // = sy + 16
            g.pose().pushPose();
            g.pose().translate(ix, iy, 200);
            g.pose().scale(2.5f, 2.5f, 2.5f);
            g.renderItem(sprite, 0, 0);
            g.pose().popPose();
        }

        // ── Textes ────────────────────────────────────────────────────
        String name = Component.translatable("cobblemon.species." + t.speciesName() + ".name").getString();
        int nameColor = done ? 0xFF44FF88 : WHITE;
        g.drawString(font, name, sx + (SLOT_W - font.width(name)) / 2, sy + 6, nameColor, false);

        g.drawString(font, t.rarity().label,
            sx + (SLOT_W - font.width(t.rarity().label)) / 2, sy + 60, rarColor, false);
        String rew = "+" + t.rarity().reward + " €";
        g.drawString(font, rew,
            sx + (SLOT_W - font.width(rew)) / 2, sy + 72, 0xFFD4A82A, false);

        if (done) {
            String check = "✔";
            g.drawString(font, check, sx + SLOT_W - font.width(check) - 4, sy + 4, 0xFF44FF88, false);
        }
    }

    // ── Sprite cache ──────────────────────────────────────────────────

    private ItemStack getSprite(String speciesName) {
        return spriteCache.computeIfAbsent(speciesName, name -> {
            try {
                var species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE
                    .getByName(name);
                if (species == null) return ItemStack.EMPTY;
                var pokemon = new com.cobblemon.mod.common.pokemon.Pokemon();
                pokemon.setSpecies(species);
                return com.cobblemon.mod.common.item.PokemonItem.Companion.from(pokemon, 1);
            } catch (Throwable ignored) {
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

        float frac = Math.min(1f, remainingMs / (float) ChaseData.DURATION_MS);
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

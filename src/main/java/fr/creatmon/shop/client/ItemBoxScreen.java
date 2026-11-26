package fr.creatmon.shop.client;

import fr.creatmon.shop.lootbox.ItemPool;
import fr.creatmon.shop.network.ItemBoxResultPacket;
import fr.creatmon.shop.network.LootBoxClaimPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ItemBoxScreen extends Screen {

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W        = 480;
    private static final int HEADER_H = 48;
    private static final int FOOTER_H = 60;
    private static final int BODY_H   = 280;
    private static final int H        = HEADER_H + BODY_H + FOOTER_H; // 388

    // Slot
    private static final int SLOT_PAD    = 24;
    private static final int SLOT_VW     = W - SLOT_PAD * 2;      // 432
    private static final int CARD_W      = 48;
    private static final int SLOT_CENTER = SLOT_VW / 2;           // 216
    private static final int STRIDE      = CARD_W + 4;            // 52
    private static final int MAX_CARD_H  = 92;
    private static final int STRIP_GAP   = 5;
    private static final int MAX_N       = 5;

    // Animation quintic
    private static final int   LEAD_DECOYS   = 35;
    private static final int   TRAIL_DECOYS  = 8;
    private static final float TARGET_OFFSET =
        LEAD_DECOYS * (float) STRIDE + CARD_W / 2f - SLOT_CENTER;
    private static final int   ANIM_TICKS    = 160;
    private static final float SCROLL_IDLE   =
        4f * STRIDE + CARD_W / 2f - SLOT_CENTER;

    private static final String[] IDLE_SEEDS = {
        "minecraft:diamond","minecraft:iron_ingot","minecraft:gold_ingot",
        "minecraft:emerald","minecraft:arrow"
    };

    // ── Thème doré ────────────────────────────────────────────────────
    private static final int BG        = 0xFF0D0A04;
    private static final int HEADER_BG = 0xFF3A2800;
    private static final int GOLD      = 0xFFFFD700;
    private static final int GOLD_DIM  = 0xFFAA8800;
    private static final int SLOT_BG   = 0xFF090600;
    private static final int BTN_ON    = 0xFF3A2800;
    private static final int BTN_HOV   = 0xFF7A5500;
    private static final int BTN_OFF   = 0xFF1A1200;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int GRAY      = 0xFF887755;
    private static final int GREEN     = 0xFF44DD44;
    private static final int GOLD_LT   = 0xFFFFEE88;

    private static final int[] RARITY_COLORS  = {0xFFBBBBBB, 0xFF55DD55, 0xFF5599FF, 0xFFFFD700};
    private static final String[] RARITY_LABELS = {"COMMUN","PEU COMMUN","RARE","LÉGENDAIRE"};

    // ── Footer ────────────────────────────────────────────────────────
    private static final int FTR_PAD = 10;
    private static final int BTN_H   = 24;
    private static final int STEP_W  = 20;
    private static final int CNT_W   = 24;
    private static final int OUV_W   = 140;

    // ── State ─────────────────────────────────────────────────────────
    private enum Phase { IDLE, SPINNING, RESULT }

    private final String chestType;
    private       int    keyCount;
    private       int    selectedCount = 1;
    private       Phase  phase         = Phase.IDLE;
    private       int    animTick      = 0;
    private       float  scrollOffset  = SCROLL_IDLE;
    private       int    lastSoundIdx  = -1;

    @SuppressWarnings("unchecked")
    private final List<String>[] stripLists = new ArrayList[MAX_N];

    private List<String>  allItemIds   = new ArrayList<>();
    private List<Integer> allQuantities = new ArrayList<>();
    private List<Integer> allRarities  = new ArrayList<>();

    private final Map<String, ItemStack> spriteCache = new HashMap<>();
    private       ItemStack              keySprite   = null;
    private       int lastMX, lastMY;

    // ── Overlay "Tous les lots" ───────────────────────────────────────
    private boolean showPoolOverlay = false;
    private int     poolScroll      = 0;
    private static final int OV_W   = 460;
    private static final int OV_H   = 340;
    private static final int OV_PAD = 10;
    private static final int COLS   = 3;
    private static final int ENT_W  = (OV_W - OV_PAD * 2 - (COLS - 1) * 3) / COLS;
    private static final int ENT_H  = 24;

    // ── Constructeur ──────────────────────────────────────────────────

    public ItemBoxScreen(String chestType, int keyCount) {
        super(Component.literal("Caisse Poké Ball"));
        this.chestType = chestType;
        this.keyCount  = keyCount;
        for (int i = 0; i < MAX_N; i++) stripLists[i] = new ArrayList<>();
        resetIdle();
    }

    private void resetIdle() {
        scrollOffset = SCROLL_IDLE;
        animTick     = 0;
        lastSoundIdx = -1;
        long base = System.nanoTime();
        for (int i = 0; i < MAX_N; i++) {
            List<String> list = new ArrayList<>(ItemPool.decoys(IDLE_SEEDS[i % IDLE_SEEDS.length], 10));
            Collections.shuffle(list, new Random(base + i * 999983L));
            stripLists[i] = list;
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────

    private int getN() {
        if (phase == Phase.IDLE) return selectedCount;
        return Math.max(1, allItemIds.size());
    }

    private int stripH(int n) { return (BODY_H - (n - 1) * STRIP_GAP) / n; }
    private int cardH(int n)  { return Math.min(MAX_CARD_H, stripH(n) - 8); }

    private int stripTopY(int n, int stripIdx, int panelY) {
        int sH    = stripH(n);
        int total = n * sH + (n - 1) * STRIP_GAP;
        int bodyY = panelY + HEADER_H + (BODY_H - total) / 2;
        return bodyY + stripIdx * (sH + STRIP_GAP);
    }

    // ── Callbacks réseau ──────────────────────────────────────────────

    public void onOpen(int keyCount) { this.keyCount = keyCount; }

    public void onResult(ItemBoxResultPacket pkt) {
        if (phase != Phase.IDLE) return;

        allItemIds   = new ArrayList<>(pkt.allItemIds());
        allQuantities = new ArrayList<>(pkt.allQuantities());
        allRarities  = new ArrayList<>(pkt.allRarities());
        keyCount     = pkt.newKeyCount();

        int n = allItemIds.size();
        long base = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int need = LEAD_DECOYS + TRAIL_DECOYS + 2;
            List<String> pool = new ArrayList<>(ItemPool.decoys(allItemIds.get(i), need));
            Collections.shuffle(pool, new Random(base + i * 1000003L));
            stripLists[i] = new ArrayList<>();
            for (int j = 0; j < LEAD_DECOYS; j++)
                stripLists[i].add(pool.get(j % pool.size()));
            stripLists[i].add(allItemIds.get(i));
            for (int j = 0; j < TRAIL_DECOYS; j++)
                stripLists[i].add(pool.get((LEAD_DECOYS + j) % pool.size()));
        }
        scrollOffset = 0f;
        animTick     = 0;
        lastSoundIdx = -1;
        phase        = Phase.SPINNING;
    }

    // ── Tick ─────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (phase != Phase.SPINNING) return;

        animTick++;
        float t  = Math.min((float) animTick / ANIM_TICKS, 1f);
        double d = 1.0 - t;
        scrollOffset = (float)(1.0 - d * d * d * d * d) * TARGET_OFFSET;

        int centerIdx = (int)((scrollOffset + SLOT_CENTER - CARD_W / 2f) / STRIDE);
        if (centerIdx != lastSoundIdx) {
            lastSoundIdx = centerIdx;
            if (minecraft != null)
                minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f - 0.9f * t));
        }

        if (animTick >= ANIM_TICKS) {
            scrollOffset = TARGET_OFFSET;
            phase = Phase.RESULT;
            if (minecraft != null)
                minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.6f));
        }
    }

    // ── Render ───────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        lastMX = mx; lastMY = my;
        renderBackground(g, mx, my, pt);
        int x = (width - W) / 2, y = (height - H) / 2;
        drawPanel(g, x, y);
        drawHeader(g, x, y);
        drawBody(g, x, y);
        drawFooter(g, x, y);
        super.render(g, mx, my, pt);
        if (showPoolOverlay) drawPoolOverlay(g);
    }

    // ── Panel ─────────────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + W, y + H, BG);
        // Bordure dorée épaisse (2px)
        g.fill(x,         y,         x + W,     y + 2,     GOLD);
        g.fill(x,         y + H - 2, x + W,     y + H,     GOLD);
        g.fill(x,         y,         x + 2,     y + H,     GOLD);
        g.fill(x + W - 2, y,         x + W,     y + H,     GOLD);
        // Coins décorés
        g.fill(x + 2, y + 2, x + 8, y + 3, GOLD_DIM);
        g.fill(x + 2, y + 2, x + 3, y + 8, GOLD_DIM);
        g.fill(x + W - 8, y + 2, x + W - 2, y + 3, GOLD_DIM);
        g.fill(x + W - 3, y + 2, x + W - 2, y + 8, GOLD_DIM);
        g.fill(x + 2, y + H - 3, x + 8, y + H - 2, GOLD_DIM);
        g.fill(x + 2, y + H - 8, x + 3, y + H - 2, GOLD_DIM);
        g.fill(x + W - 8, y + H - 3, x + W - 2, y + H - 2, GOLD_DIM);
        g.fill(x + W - 3, y + H - 8, x + W - 2, y + H - 2, GOLD_DIM);
    }

    // ── Header ────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y + 2, x + W - 2, y + HEADER_H, HEADER_BG);
        g.fill(x + 2, y + HEADER_H, x + W - 2, y + HEADER_H + 1, GOLD_DIM);

        g.pose().pushPose();
        g.pose().translate(x + W / 2f, y + 8f, 0f);
        g.pose().scale(1.8f, 1.8f, 1f);
        g.drawCenteredString(font, "CAISSE POKÉ BALL", 0, 0, GOLD);
        g.pose().popPose();

        g.drawCenteredString(font, "Cobblemon Items", x + W / 2, y + 36, GOLD_LT);

        int cx = x + W - 14, cy = y + 5;
        g.fill(cx, cy, cx + 10, cy + 10, isHov(cx, cy, 10, 10) ? 0xFFBB2222 : 0xFF3A2000);
        g.drawCenteredString(font, "X", cx + 5, cy + 1, WHITE);
    }

    // ── Body ─────────────────────────────────────────────────────────

    private void drawBody(GuiGraphics g, int x, int y) {
        int n  = getN();
        int sx = x + SLOT_PAD;
        int cH = cardH(n);
        int sH = stripH(n);

        for (int i = 0; i < n; i++) {
            int sy   = stripTopY(n, i, y);
            int focY = sy + (sH - cH) / 2;
            drawStrip(g, sx, sy, sH, cH, i, n, focY);
        }
    }

    private void drawStrip(GuiGraphics g, int sx, int sy, int sH, int cH,
                           int stripIdx, int n, int focY) {
        g.fill(sx, sy, sx + SLOT_VW, sy + sH, SLOT_BG);
        // Bordure dorée fine
        g.fill(sx,               sy,          sx + SLOT_VW, sy + 1,       GOLD_DIM);
        g.fill(sx,               sy + sH - 1, sx + SLOT_VW, sy + sH,     GOLD_DIM);
        g.fill(sx,               sy,          sx + 1,        sy + sH,     GOLD_DIM);
        g.fill(sx + SLOT_VW - 1, sy,          sx + SLOT_VW,  sy + sH,    GOLD_DIM);

        // Zone focus (dorée)
        int focX = sx + SLOT_CENTER - CARD_W / 2;
        g.fill(focX - 2, focY - 2, focX + CARD_W + 2, focY + cH + 2, 0xFF1A1000);
        g.fill(focX - 2, focY - 2, focX + CARD_W + 2, focY - 1,      GOLD);
        g.fill(focX - 2, focY + cH + 1, focX + CARD_W + 2, focY + cH + 2, GOLD);
        g.fill(focX - 2, focY - 2, focX - 1, focY + cH + 2, GOLD);
        g.fill(focX + CARD_W + 1, focY - 2, focX + CARD_W + 2, focY + cH + 2, GOLD);

        // Label rareté en RESULT
        if (phase == Phase.RESULT && stripIdx < allRarities.size()) {
            int ri   = Math.min(allRarities.get(stripIdx), RARITY_LABELS.length - 1);
            g.drawString(font, RARITY_LABELS[ri], focX + 2, focY + 2, RARITY_COLORS[ri], false);
        }

        g.enableScissor(sx + 1, sy + 1, sx + SLOT_VW - 1, sy + sH - 1);
        List<String> list = stripLists[stripIdx];
        for (int i = 0; i < list.size(); i++) {
            float cardX = sx + i * (float) STRIDE - scrollOffset;
            if (cardX + CARD_W < sx || cardX > sx + SLOT_VW) continue;
            boolean focused = (phase == Phase.RESULT) && (i == LEAD_DECOYS);
            int rarity;
            if (focused && stripIdx < allRarities.size()) {
                rarity = allRarities.get(stripIdx);
            } else {
                int seed = Math.abs((list.get(i) + i).hashCode());
                int v = seed % 131;
                rarity = v < 100 ? 0 : v < 125 ? 1 : v < 130 ? 2 : 3;
            }
            drawCard(g, (int) cardX, focY, cH, list.get(i), rarity, focused);
        }
        g.disableScissor();

        // Flèches dorées
        int arrowMidY = sy + sH / 2;
        drawArrow(g, sx - 13, arrowMidY, false);
        drawArrow(g, sx + SLOT_VW + 2, arrowMidY, true);
    }

    private void drawCard(GuiGraphics g, int cx, int cy, int cH,
                          String itemId, int rarityIdx, boolean focused) {
        int ri   = Math.max(0, Math.min(rarityIdx, RARITY_COLORS.length - 1));
        int rcol = RARITY_COLORS[ri];
        int r  = ((rcol >> 16) & 0xFF) * 25 / 100 + 8;
        int gv = ((rcol >> 8)  & 0xFF) * 25 / 100 + 4;
        int b  = ( rcol        & 0xFF) * 25 / 100 + 2;
        int cardBg = 0xFF000000 | (r << 16) | (gv << 8) | b;

        g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + cH - 1, cardBg);
        if (focused) g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + cH - 1, 0x33FFFFFF);
        g.fill(cx,            cy,          cx + CARD_W, cy + 1,         rcol);
        g.fill(cx,            cy + cH - 1, cx + CARD_W, cy + cH,       rcol);
        g.fill(cx,            cy,          cx + 1,       cy + cH,       rcol);
        g.fill(cx + CARD_W - 1, cy,        cx + CARD_W, cy + cH,       rcol);

        // Sprite item (toujours 16×16, centré)
        ItemStack stack = getItemStack(itemId);
        if (!stack.isEmpty()) {
            int ix = cx + (CARD_W - 16) / 2;
            int iy = cy + (cH - 16) / 2 - (focused && cH >= 48 ? 4 : 0);
            g.renderItem(stack, ix, iy);
        }

        // Quantité si focalisé
        if (focused && cH >= 44) {
            int qty = getQuantity(itemId);
            if (qty > 1) {
                String qStr = "×" + qty;
                g.drawCenteredString(font, qStr, cx + CARD_W / 2, cy + cH - 10, GOLD_LT);
            }
        }
    }

    private void drawArrow(GuiGraphics g, int ax, int ay, boolean pointsRight) {
        int c = GOLD_DIM;
        for (int i = 0; i < 4; i++) {
            int px = pointsRight ? ax + i : ax + 3 - i;
            g.fill(px, ay - i, px + 1, ay + i + 1, c);
        }
    }

    // ── Footer ────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics g, int x, int y) {
        int fy    = y + H - FOOTER_H;
        int btnY  = fy + 8;
        int curX  = x + FTR_PAD;
        boolean canOpen = (keyCount > 0) && (phase == Phase.IDLE || phase == Phase.RESULT);

        g.fill(x + 2, fy,     x + W - 2, fy + 1,     GOLD_DIM);
        g.fill(x + 2, fy + 1, x + W - 2, y + H - 2, 0xFF080600);

        // [-]
        boolean hovM = canOpen && selectedCount > 1 && isHov(curX, btnY, STEP_W, BTN_H);
        g.fill(curX, btnY, curX + STEP_W, btnY + BTN_H, hovM ? BTN_HOV : BTN_ON);
        g.fill(curX, btnY, curX + STEP_W, btnY + 1, canOpen ? GOLD_DIM : 0xFF332200);
        g.drawCenteredString(font, "-", curX + STEP_W / 2, btnY + (BTN_H - 8) / 2, canOpen ? WHITE : GRAY);
        curX += STEP_W + 1;

        // [N]
        g.fill(curX, btnY, curX + CNT_W, btnY + BTN_H, 0xFF1A1000);
        g.drawCenteredString(font, String.valueOf(selectedCount), curX + CNT_W / 2, btnY + (BTN_H - 8) / 2, GOLD_LT);
        curX += CNT_W + 1;

        // [+]
        boolean hovP = canOpen && selectedCount < 5 && selectedCount < keyCount && isHov(curX, btnY, STEP_W, BTN_H);
        g.fill(curX, btnY, curX + STEP_W, btnY + BTN_H, hovP ? BTN_HOV : BTN_ON);
        g.fill(curX, btnY, curX + STEP_W, btnY + 1, canOpen ? GOLD_DIM : 0xFF332200);
        g.drawCenteredString(font, "+", curX + STEP_W / 2, btnY + (BTN_H - 8) / 2, canOpen ? WHITE : GRAY);
        curX += STEP_W + 8;

        // [OUVRIR]
        int ouvX = curX;
        boolean hovO = canOpen && isHov(ouvX, btnY, OUV_W, BTN_H);
        int btnCol = !canOpen ? BTN_OFF : hovO ? BTN_HOV : BTN_ON;
        g.fill(ouvX, btnY, ouvX + OUV_W, btnY + BTN_H, btnCol);
        g.fill(ouvX, btnY, ouvX + OUV_W, btnY + 1, canOpen ? GOLD : 0xFF332200);
        g.fill(ouvX, btnY + BTN_H - 1, ouvX + OUV_W, btnY + BTN_H, canOpen ? GOLD : 0xFF332200);
        String ouvTxt = "OUVRIR" + (selectedCount > 1 ? " ×" + selectedCount : "");
        g.drawCenteredString(font, ouvTxt, ouvX + OUV_W / 2, btnY + (BTN_H - 8) / 2, !canOpen ? GRAY : WHITE);
        curX += OUV_W + 8;

        // [🔑 ×K]
        int kx = curX, kw = x + W - FTR_PAD - kx;
        g.fill(kx, btnY, kx + kw, btnY + BTN_H, 0xFF100A00);
        g.fill(kx, btnY, kx + kw, btnY + 1, 0xFF554400);
        g.fill(kx, btnY + BTN_H - 1, kx + kw, btnY + BTN_H, 0xFF554400);
        ItemStack ks = getKeySprite();
        if (!ks.isEmpty()) g.renderItem(ks, kx + 4, btnY + (BTN_H - 16) / 2);
        g.drawString(font, "×" + keyCount, kx + (ks.isEmpty() ? kw / 2 : 22), btnY + (BTN_H - 8) / 2, GOLD_LT, false);

        // Bouton "Tous les lots"
        int lotsBtnW = 100, lotsBtnH = 14;
        int lotsBtnX = x + W - FTR_PAD - lotsBtnW;
        int lotsBtnY = y + H - FTR_PAD - lotsBtnH - 1;
        boolean hovLots = isHov(lotsBtnX, lotsBtnY, lotsBtnW, lotsBtnH);
        g.fill(lotsBtnX, lotsBtnY, lotsBtnX + lotsBtnW, lotsBtnY + lotsBtnH,
            showPoolOverlay ? GOLD_DIM : hovLots ? BTN_HOV : BTN_ON);
        g.drawCenteredString(font, "Tous les lots ›", lotsBtnX + lotsBtnW / 2, lotsBtnY + (lotsBtnH - 8) / 2, WHITE);

        // Hint
        int hintY = btnY + BTN_H + 6;
        if (phase == Phase.SPINNING) {
            g.drawCenteredString(font, "En cours...", x + W / 2, hintY, GRAY);
        } else if (phase == Phase.RESULT && !allItemIds.isEmpty()) {
            if (allItemIds.size() == 1) {
                String itemName = getItemName(allItemIds.get(0));
                int qty = allQuantities.isEmpty() ? 1 : allQuantities.get(0);
                String msg = "Vous avez reçu : " + (qty > 1 ? qty + "× " : "") + itemName + " !";
                g.drawCenteredString(font, msg, x + W / 2, hintY, GREEN);
            } else {
                List<String> parts = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                for (int i = 0; i < allItemIds.size(); i++) {
                    int col = RARITY_COLORS[Math.min(allRarities.get(i), RARITY_COLORS.length - 1)];
                    int qty = allQuantities.size() > i ? allQuantities.get(i) : 1;
                    String s = (qty > 1 ? qty + "× " : "") + getItemName(allItemIds.get(i));
                    parts.add(i < allItemIds.size() - 1 ? s + "  " : s);
                    cols.add(col);
                }
                int totalW = parts.stream().mapToInt(font::width).sum();
                int drawX  = x + W / 2 - totalW / 2;
                for (int i = 0; i < parts.size(); i++) {
                    g.drawString(font, parts.get(i), drawX, hintY, cols.get(i), false);
                    drawX += font.width(parts.get(i));
                }
            }
        } else if (keyCount == 0) {
            g.drawCenteredString(font, "Pas de clé dans votre inventaire", x + W / 2, hintY, GRAY);
        } else {
            g.drawCenteredString(font, "Choisissez le nombre de clés à utiliser", x + W / 2, hintY, GRAY);
        }
    }

    // ── Overlay pool ──────────────────────────────────────────────────

    private void drawPoolOverlay(GuiGraphics g) {
        int ox = (width - OV_W) / 2;
        int oy = (height - OV_H) / 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        g.fill(0, 0, width, height, 0x88000000);
        g.fill(ox, oy, ox + OV_W, oy + OV_H, 0xFF0C0800);
        g.fill(ox,          oy,          ox + OV_W, oy + 2,          GOLD);
        g.fill(ox,          oy + OV_H - 2, ox + OV_W, oy + OV_H,    GOLD);
        g.fill(ox,          oy,          ox + 2,        oy + OV_H,   GOLD);
        g.fill(ox + OV_W - 2, oy,        ox + OV_W,    oy + OV_H,   GOLD);

        g.fill(ox + 2, oy + 2, ox + OV_W - 2, oy + 26, HEADER_BG);
        g.fill(ox + 2, oy + 26, ox + OV_W - 2, oy + 27, GOLD_DIM);
        g.drawCenteredString(font, "Tous les lots", ox + OV_W / 2, oy + 9, GOLD);

        int cx = ox + OV_W - 16, cy = oy + 7;
        g.fill(cx, cy, cx + 10, cy + 10, isHov(cx, cy, 10, 10) ? 0xFFBB2222 : 0xFF3A2000);
        g.drawCenteredString(font, "X", cx + 5, cy + 1, WHITE);

        int contentX = ox + OV_PAD;
        int contentY = oy + 28;
        int visibleH = OV_H - 28 - 2;

        g.enableScissor(ox + 2, contentY, ox + OV_W - 2, contentY + visibleH);

        int drawY = contentY - poolScroll;
        ItemPool.Rarity[] order = {
            ItemPool.Rarity.LEGENDAIRE, ItemPool.Rarity.RARE,
            ItemPool.Rarity.PEU_COMMUN, ItemPool.Rarity.COMMUN
        };

        for (ItemPool.Rarity rarity : order) {
            List<ItemPool.PoolEntry> entries = ItemPool.getPool().stream()
                .filter(e -> e.rarity() == rarity).toList();
            if (entries.isEmpty()) continue;

            g.fill(ox + 2, drawY, ox + OV_W - 2, drawY + 18, 0xFF120E00);
            g.fill(ox + 2, drawY, ox + OV_W - 2, drawY + 1, rarity.color);
            g.drawString(font, rarity.label, contentX, drawY + 5, rarity.color, false);
            int total = ItemPool.getPool().stream().mapToInt(ItemPool.PoolEntry::weight).sum();
            float pct = (float) entries.get(0).weight() / total * 100f;
            String pctStr = String.format("%.2f%%/entrée — %d lots", pct, entries.size());
            g.drawString(font, pctStr, contentX + 70, drawY + 5, GRAY, false);
            drawY += 20;

            for (int i = 0; i < entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int ex  = contentX + col * (ENT_W + 3);
                int ey  = drawY + row * ENT_H;

                int rc = rarity.color;
                int er = ((rc >> 16) & 0xFF) * 20 / 100 + 6;
                int eg = ((rc >> 8)  & 0xFF) * 20 / 100 + 4;
                int eb = ( rc        & 0xFF) * 20 / 100 + 2;
                g.fill(ex, ey, ex + ENT_W, ey + ENT_H - 2, 0xFF000000 | (er << 16) | (eg << 8) | eb);

                ItemStack sp = getItemStack(entries.get(i).itemId());
                if (!sp.isEmpty()) g.renderItem(sp, ex + 1, ey + (ENT_H - 16) / 2);

                String name = getItemName(entries.get(i).itemId());
                int maxW = ENT_W - 22 - 30;
                while (font.width(name) > maxW && name.length() > 3)
                    name = name.substring(0, name.length() - 1);
                g.drawString(font, name, ex + 20, ey + (ENT_H - 8) / 2, WHITE, false);

                String qStr = "×" + entries.get(i).quantity();
                g.drawString(font, qStr, ex + ENT_W - font.width(qStr) - 2, ey + (ENT_H - 8) / 2, GOLD_LT, false);
            }
            drawY += ((entries.size() + COLS - 1) / COLS) * ENT_H + 4;
        }

        int totalH   = drawY - (contentY - poolScroll);
        int maxScroll = Math.max(0, totalH - visibleH);
        if (poolScroll > maxScroll) poolScroll = maxScroll;
        g.disableScissor();

        if (totalH > visibleH) {
            int sbX = ox + OV_W - 6;
            int sbH = visibleH * visibleH / totalH;
            int sbY = contentY + poolScroll * (visibleH - sbH) / maxScroll;
            g.fill(sbX, contentY, sbX + 4, contentY + visibleH, 0xFF120E00);
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, GOLD_DIM);
        }

        g.pose().popPose();
    }

    // ── Interactions ──────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (showPoolOverlay) { poolScroll = Math.max(0, poolScroll - (int)(dy * 12)); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = (width - W) / 2, y = (height - H) / 2;

        if (showPoolOverlay) {
            int ox = (width - OV_W) / 2, oy = (height - OV_H) / 2;
            if (isHov(ox + OV_W - 16, oy + 7, 10, 10)) { showPoolOverlay = false; return true; }
            if (!isHov(ox, oy, OV_W, OV_H)) { showPoolOverlay = false; return true; }
            return true;
        }

        int lotsBtnW = 100, lotsBtnH = 14;
        int lotsBtnX = x + W - FTR_PAD - lotsBtnW;
        int lotsBtnY = y + H - FTR_PAD - lotsBtnH - 1;
        if (isHov(lotsBtnX, lotsBtnY, lotsBtnW, lotsBtnH)) {
            showPoolOverlay = true; poolScroll = 0; return true;
        }

        if (isHov(x + W - 14, y + 5, 10, 10)) { onClose(); return true; }

        boolean canOpen = (keyCount > 0) && (phase == Phase.IDLE || phase == Phase.RESULT);
        int fy   = y + H - FOOTER_H;
        int btnY = fy + 8;
        int curX = x + FTR_PAD;

        if (canOpen && selectedCount > 1 && isHov(curX, btnY, STEP_W, BTN_H)) { selectedCount--; return true; }
        curX += STEP_W + 1 + CNT_W + 1;
        if (canOpen && selectedCount < 5 && selectedCount < keyCount && isHov(curX, btnY, STEP_W, BTN_H)) { selectedCount++; return true; }
        curX += STEP_W + 8;
        if (canOpen && isHov(curX, btnY, OUV_W, BTN_H)) {
            if (phase == Phase.RESULT) resetIdle();
            phase = Phase.IDLE;
            PacketDistributor.sendToServer(new LootBoxClaimPacket(chestType, selectedCount));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ── Utilitaires ───────────────────────────────────────────────────

    private boolean isHov(int bx, int by, int bw, int bh) {
        return lastMX >= bx && lastMX < bx + bw && lastMY >= by && lastMY < by + bh;
    }

    private int getQuantity(String itemId) {
        return ItemPool.findEntry(itemId).map(ItemPool.PoolEntry::quantity).orElse(1);
    }

    private String getItemName(String itemId) {
        try {
            var loc = ResourceLocation.parse(itemId);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
            if (item != null) return item.getDescription().getString();
        } catch (Throwable ignored) {}
        // Fallback: last segment, capitalize
        String raw = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return raw.replace('_', ' ');
    }

    private ItemStack getItemStack(String itemId) {
        return spriteCache.computeIfAbsent(itemId, id -> {
            try {
                var loc = ResourceLocation.parse(id);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
                if (item == null) return ItemStack.EMPTY;
                return new ItemStack(item);
            } catch (Throwable ignored) { return ItemStack.EMPTY; }
        });
    }

    private ItemStack getKeySprite() {
        if (keySprite == null) {
            try {
                var item = fr.creatmon.shop.ModItems.POKE_BALL_KEY.get();
                keySprite = new ItemStack(item);
            } catch (Throwable ignored) { keySprite = ItemStack.EMPTY; }
        }
        return keySprite;
    }
}

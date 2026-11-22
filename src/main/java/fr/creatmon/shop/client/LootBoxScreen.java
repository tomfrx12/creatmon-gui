package fr.creatmon.shop.client;

import fr.creatmon.shop.network.LootBoxClaimPacket;
import fr.creatmon.shop.network.LootBoxResultPacket;
import fr.creatmon.shop.lootbox.PokemonPool;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LootBoxScreen extends Screen {

    // ── Dimensions fixes ──────────────────────────────────────────────
    private static final int W          = 500;
    private static final int HEADER_H   = 56;
    private static final int FOOTER_H   = 68;
    private static final int BODY_H     = 316;
    private static final int H          = HEADER_H + BODY_H + FOOTER_H; // 440

    // Slot
    private static final int SLOT_PAD    = 28;
    private static final int SLOT_VW     = W - SLOT_PAD * 2; // 444
    private static final int CARD_W      = 86;
    private static final int SLOT_CENTER = SLOT_VW / 2;      // 222
    private static final int STRIDE      = CARD_W + 4;        // 90
    private static final int MAX_CARD_H  = 108;
    private static final int STRIP_GAP   = 6;
    private static final int MAX_N       = 5;

    // Animation CS:GO quintic
    private static final int   LEAD_DECOYS   = 35;
    private static final int   TRAIL_DECOYS  = 8;
    private static final float TARGET_OFFSET =
        LEAD_DECOYS * (float) STRIDE + CARD_W / 2f - SLOT_CENTER; // ≈ 2971
    private static final int   ANIM_TICKS    = 160;
    private static final float SCROLL_IDLE   =
        4f * STRIDE + CARD_W / 2f - SLOT_CENTER; // ≈ 181

    // Pokémon à afficher dans chaque strip en mode IDLE
    private static final String[] IDLE_SEEDS =
        {"pikachu", "eevee", "mewtwo", "bulbasaur", "rayquaza"};

    // ── Couleurs violet Master Ball (thème sombre) ───────────────────
    private static final int BG        = 0xFF0A0612;
    private static final int HEADER_BG = 0xFF4A0070;
    private static final int HDR_BDR   = 0xFF9933CC;
    private static final int SLOT_BG   = 0xFF06030F;
    private static final int CARD_BG   = 0xFF1A0830;  // fallback si pas de rareté
    private static final int FOCUS_OVL = 0x33FFFFFF;
    private static final int BTN_ON    = 0xFF3A1A5A;
    private static final int BTN_HOV   = 0xFF6A3A8A;
    private static final int BTN_OFF   = 0xFF1A0A2A;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int TEXT_DARK = 0xFF220044;
    private static final int GRAY      = 0xFF8878A0;
    private static final int GREEN     = 0xFF44DD44;
    private static final int PURPLE_LT = 0xFFCCAAFF;

    private static final int[] RARITY_COLORS  = {0xFFBBBBBB, 0xFF55DD55, 0xFF5599FF, 0xFFFFD700};
    private static final String[] RARITY_LABELS = {"COMMUN", "PEU COMMUN", "RARE", "LÉGENDAIRE"};

    // ── Footer ────────────────────────────────────────────────────────
    private static final int FTR_PAD = 10;
    private static final int BTN_H   = 26;
    private static final int STEP_W  = 22;
    private static final int CNT_W   = 26;
    private static final int OUV_W   = 150;

    // ── State ─────────────────────────────────────────────────────────
    private enum Phase { IDLE, SPINNING, RESULT }

    private final String  chestType;
    private       int     keyCount;
    private       int     selectedCount = 1;
    private       Phase   phase         = Phase.IDLE;
    private       int     animTick      = 0;
    private       float   scrollOffset  = SCROLL_IDLE;
    private       int     lastSoundIdx  = -1;

    @SuppressWarnings("unchecked")
    private final List<String>[] stripLists = new ArrayList[MAX_N];

    private List<String>  allSpecies  = new ArrayList<>();
    private List<Integer> allRarities = new ArrayList<>();

    private final Map<String, ItemStack> spriteCache = new HashMap<>();
    private       ItemStack              keySprite   = null;
    private       int lastMX, lastMY;

    // ── Overlay "Tous les lots" ───────────────────────────────────────
    private boolean showPoolOverlay = false;
    private int     poolScroll      = 0;
    private static final int OV_W   = 480;
    private static final int OV_H   = 360;
    private static final int OV_PAD = 10;
    private static final int COLS   = 4;
    private static final int ENT_W  = (OV_W - OV_PAD * 2 - (COLS - 1) * 3) / COLS; // ≈113
    private static final int ENT_H  = 24;

    // ── Constructeur ──────────────────────────────────────────────────

    public LootBoxScreen(String chestType, int keyCount) {
        super(Component.literal("Caisse Pokémon"));
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
            List<String> list = new ArrayList<>(PokemonPool.decoys(IDLE_SEEDS[i], 10));
            Collections.shuffle(list, new Random(base + i * 999983L));
            stripLists[i] = list;
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────

    /** Nombre de strips actives. */
    private int getN() {
        if (phase == Phase.IDLE) return selectedCount;
        return Math.max(1, allSpecies.size());
    }

    /** Hauteur allouée à chaque strip (slot + padding vertical). */
    private int stripH(int n) {
        return (BODY_H - (n - 1) * STRIP_GAP) / n;
    }

    /** Hauteur de la carte dans une strip pour N strips. */
    private int cardH(int n) {
        return Math.min(MAX_CARD_H, stripH(n) - 10);
    }

    /** Scale du sprite (3→48px, 2→32px). */
    private int spriteScale(int n) { return n <= 2 ? 3 : 2; }

    /** Y absolu du haut d'une strip (panelY = y du panel). */
    private int stripTopY(int n, int stripIdx, int panelY) {
        int sH    = stripH(n);
        int total = n * sH + (n - 1) * STRIP_GAP;
        int bodyY = panelY + HEADER_H + (BODY_H - total) / 2;
        return bodyY + stripIdx * (sH + STRIP_GAP);
    }

    // ── Callbacks réseau ──────────────────────────────────────────────

    public void onOpen(int keyCount) { this.keyCount = keyCount; }

    public void onResult(LootBoxResultPacket pkt) {
        if (phase != Phase.IDLE) return;

        allSpecies  = new ArrayList<>(pkt.allSpecies());
        allRarities = new ArrayList<>(pkt.allRarities());
        keyCount    = pkt.newKeyCount();

        // Chaque strip génère ses propres décoys indépendants + shuffle unique
        int n    = allSpecies.size();
        long base = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int need = LEAD_DECOYS + TRAIL_DECOYS + 2;
            List<String> pool = new ArrayList<>(PokemonPool.decoys(allSpecies.get(i), need));
            Collections.shuffle(pool, new Random(base + i * 1000003L));
            stripLists[i] = new ArrayList<>();
            for (int j = 0; j < LEAD_DECOYS; j++)
                stripLists[i].add(pool.get(j % pool.size()));
            stripLists[i].add(allSpecies.get(i));
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
        scrollOffset = (float)(1.0 - d * d * d * d * d) * TARGET_OFFSET; // quintic

        // Son par carte qui passe au centre (commun à toutes les strips)
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
        g.fill(x,         y,         x + W,     y + 1,     HDR_BDR);
        g.fill(x,         y + H - 1, x + W,     y + H,     HDR_BDR);
        g.fill(x,         y,         x + 1,     y + H,     HDR_BDR);
        g.fill(x + W - 1, y,         x + W,     y + H,     HDR_BDR);
    }

    // ── Header ────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + W - 1, y + HEADER_H, HEADER_BG);
        g.fill(x + 1, y + HEADER_H, x + W - 1, y + HEADER_H + 1, HDR_BDR);

        g.pose().pushPose();
        g.pose().translate(x + W / 2f, y + 10f, 0f);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, "CAISSE POKÉMON", 0, 0, WHITE);
        g.pose().popPose();

        String sub = "master_ball".equals(chestType) ? "Tout le Pokédex" : "Pokédex basique";
        g.drawCenteredString(font, sub, x + W / 2, y + 42, PURPLE_LT);

        int cx = x + W - 16, cy = y + 5;
        g.fill(cx, cy, cx + 12, cy + 12, isHov(cx, cy, 12, 12) ? 0xFFBB2222 : 0xFF5A1A6A);
        g.drawCenteredString(font, "X", cx + 6, cy + 2, WHITE);
    }

    // ── Body (N strips) ───────────────────────────────────────────────

    private void drawBody(GuiGraphics g, int x, int y) {
        int n  = getN();
        int sx = x + SLOT_PAD;
        int cH = cardH(n);
        int sH = stripH(n);
        int ss = spriteScale(n);

        for (int i = 0; i < n; i++) {
            int sy   = stripTopY(n, i, y);
            int focY = sy + (sH - cH) / 2; // y de la carte dans la strip

            drawStrip(g, sx, sy, sH, cH, ss, i, n, focY);
        }
    }

    private void drawStrip(GuiGraphics g, int sx, int sy, int sH, int cH, int ss,
                           int stripIdx, int n, int focY) {
        // Fond + bordure slot
        g.fill(sx, sy, sx + SLOT_VW, sy + sH, SLOT_BG);
        g.fill(sx,               sy,          sx + SLOT_VW, sy + 1,       HDR_BDR);
        g.fill(sx,               sy + sH - 1, sx + SLOT_VW, sy + sH,     HDR_BDR);
        g.fill(sx,               sy,          sx + 1,        sy + sH,     HDR_BDR);
        g.fill(sx + SLOT_VW - 1, sy,          sx + SLOT_VW,  sy + sH,    HDR_BDR);

        // Zone focus
        int focX = sx + SLOT_CENTER - CARD_W / 2;
        g.fill(focX - 2, focY - 2, focX + CARD_W + 2, focY + cH + 2, 0xFF2A0A40);
        g.fill(focX - 2, focY - 2, focX + CARD_W + 2, focY - 1,      HDR_BDR);
        g.fill(focX - 2, focY + cH + 1, focX + CARD_W + 2, focY + cH + 2, HDR_BDR);
        g.fill(focX - 2, focY - 2, focX - 1, focY + cH + 2, HDR_BDR);
        g.fill(focX + CARD_W + 1, focY - 2, focX + CARD_W + 2, focY + cH + 2, HDR_BDR);

        // Rareté (petite étiquette à gauche dans la zone focus) en RESULT
        if (phase == Phase.RESULT && stripIdx < allRarities.size()) {
            int ri   = Math.min(allRarities.get(stripIdx), RARITY_LABELS.length - 1);
            int rcol = RARITY_COLORS[ri];
            String lbl = RARITY_LABELS[ri];
            g.drawString(font, lbl, focX + 2, focY + 2, rcol, false);
        }

        // Cartes avec scissor
        g.enableScissor(sx + 1, sy + 1, sx + SLOT_VW - 1, sy + sH - 1);
        List<String> list = stripLists[stripIdx];
        for (int i = 0; i < list.size(); i++) {
            float cardX = sx + i * (float) STRIDE - scrollOffset;
            if (cardX + CARD_W < sx || cardX > sx + SLOT_VW) continue;
            boolean focused = (phase == Phase.RESULT) && (i == LEAD_DECOYS);
            // Rareté : réelle pour le résultat, pseudo-aléatoire (pondérée) pour les décoys
            int rarity;
            if (focused && stripIdx < allRarities.size()) {
                rarity = allRarities.get(stripIdx);
            } else {
                // Distribution pondérée identique au vrai pool : 100 commun, 25 peu commun, 5 rare, 1 légendaire
                int seed = Math.abs((list.get(i) + i).hashCode());
                int v = seed % 131;
                rarity = v < 100 ? 0 : v < 125 ? 1 : v < 130 ? 2 : 3;
            }
            drawCard(g, (int) cardX, focY, cH, ss, list.get(i), rarity, focused);
        }
        g.disableScissor();

        // Flèches
        int arrowMidY = sy + sH / 2;
        drawArrow(g, sx - 15, arrowMidY, false);
        drawArrow(g, sx + SLOT_VW + 3, arrowMidY, true);
    }

    private void drawCard(GuiGraphics g, int cx, int cy, int cH, int ss,
                          String species, int rarityIdx, boolean focused) {
        int ri   = Math.max(0, Math.min(rarityIdx, RARITY_COLORS.length - 1));
        int rcol = RARITY_COLORS[ri];
        // Fond sombre teinté par la rareté (mix 25% couleur + 75% noir)
        int r = ((rcol >> 16) & 0xFF) * 25 / 100 + 10;
        int gv = ((rcol >> 8)  & 0xFF) * 25 / 100 + 5;
        int b = ( rcol         & 0xFF) * 25 / 100 + 20;
        int cardBgColor = 0xFF000000 | (r << 16) | (gv << 8) | b;
        int bord = rcol;
        g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + cH - 1, cardBgColor);
        if (focused) g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + cH - 1, FOCUS_OVL);
        g.fill(cx,            cy,          cx + CARD_W, cy + 1,         bord);
        g.fill(cx,            cy + cH - 1, cx + CARD_W, cy + cH,       bord);
        g.fill(cx,            cy,          cx + 1,       cy + cH,       bord);
        g.fill(cx + CARD_W - 1, cy,        cx + CARD_W, cy + cH,       bord);

        ItemStack sprite = getSprite(species);
        if (!sprite.isEmpty()) {
            int iw = 16 * ss;
            int ix = cx + (CARD_W - iw) / 2;
            int iy = cy + (cH - iw) / 2 - (focused && cH >= 60 ? 5 : 0);
            g.pose().pushPose();
            g.pose().scale(ss, ss, 1f);
            g.renderItem(sprite, ix / ss, iy / ss);
            g.pose().popPose();
        }

        // Nom sous le sprite si la carte est assez haute
        if (focused && cH >= 58) {
            g.drawCenteredString(font, pokemonName(species), cx + CARD_W / 2, cy + cH - 11, WHITE);
        }
    }

    private void drawArrow(GuiGraphics g, int ax, int ay, boolean pointsRight) {
        int c = 0xFFAA88CC;
        for (int i = 0; i < 5; i++) {
            int px = pointsRight ? ax + i : ax + 4 - i;
            g.fill(px, ay - i, px + 1, ay + i + 1, c);
        }
    }

    // ── Footer ────────────────────────────────────────────────────────

    private void drawFooter(GuiGraphics g, int x, int y) {
        int fy    = y + H - FOOTER_H;
        int btnY  = fy + 8;
        int curX  = x + FTR_PAD;
        boolean canOpen = (keyCount > 0) && (phase == Phase.IDLE || phase == Phase.RESULT);

        g.fill(x + 1, fy,     x + W - 1, fy + 1,     0xFF3A1A5A);
        g.fill(x + 1, fy + 1, x + W - 1, y + H - 1, 0xFF0A050F);

        // [-]
        boolean hovM = canOpen && selectedCount > 1 && isHov(curX, btnY, STEP_W, BTN_H);
        g.fill(curX, btnY, curX + STEP_W, btnY + BTN_H, hovM ? BTN_HOV : BTN_ON);
        g.drawCenteredString(font, "-", curX + STEP_W / 2, btnY + (BTN_H - 8) / 2, canOpen ? WHITE : GRAY);
        curX += STEP_W + 1;

        // [N]
        g.fill(curX, btnY, curX + CNT_W, btnY + BTN_H, 0xFF220A3A);
        g.drawCenteredString(font, String.valueOf(selectedCount), curX + CNT_W / 2, btnY + (BTN_H - 8) / 2, PURPLE_LT);
        curX += CNT_W + 1;

        // [+]
        boolean hovP = canOpen && selectedCount < 5 && selectedCount < keyCount && isHov(curX, btnY, STEP_W, BTN_H);
        g.fill(curX, btnY, curX + STEP_W, btnY + BTN_H, hovP ? BTN_HOV : BTN_ON);
        g.drawCenteredString(font, "+", curX + STEP_W / 2, btnY + (BTN_H - 8) / 2, canOpen ? WHITE : GRAY);
        curX += STEP_W + 8;

        // [OUVRIR]
        int ouvX = curX;
        boolean hovO = canOpen && isHov(ouvX, btnY, OUV_W, BTN_H);
        int btnCol = !canOpen ? BTN_OFF : hovO ? BTN_HOV : BTN_ON;
        g.fill(ouvX, btnY, ouvX + OUV_W, btnY + BTN_H, btnCol);
        g.fill(ouvX, btnY, ouvX + OUV_W, btnY + 1, canOpen ? HDR_BDR : 0xFF332243);
        g.fill(ouvX, btnY + BTN_H - 1, ouvX + OUV_W, btnY + BTN_H, canOpen ? HDR_BDR : 0xFF332243);
        String ouvTxt = "OUVRIR" + (selectedCount > 1 ? " ×" + selectedCount : "");
        g.drawCenteredString(font, ouvTxt, ouvX + OUV_W / 2, btnY + (BTN_H - 8) / 2, !canOpen ? GRAY : WHITE);
        curX += OUV_W + 8;

        // [🔑 ×K]
        int kx = curX, kw = x + W - FTR_PAD - kx;
        g.fill(kx, btnY, kx + kw, btnY + BTN_H, 0xFF160822);
        g.fill(kx, btnY, kx + kw, btnY + 1, 0xFF332243);
        g.fill(kx, btnY + BTN_H - 1, kx + kw, btnY + BTN_H, 0xFF332243);
        ItemStack ks = getKeySprite();
        if (!ks.isEmpty()) g.renderItem(ks, kx + 4, btnY + (BTN_H - 16) / 2);
        g.drawString(font, "×" + keyCount, kx + (ks.isEmpty() ? kw / 2 : 22), btnY + (BTN_H - 8) / 2, PURPLE_LT, false);

        // Bouton "Tous les lots" — bas droite du footer
        int lotsBtnW = 100, lotsBtnH = 14;
        int lotsBtnX = x + W - FTR_PAD - lotsBtnW;
        int lotsBtnY = y + H - FTR_PAD - lotsBtnH - 1;
        boolean hovLots = isHov(lotsBtnX, lotsBtnY, lotsBtnW, lotsBtnH);
        g.fill(lotsBtnX, lotsBtnY, lotsBtnX + lotsBtnW, lotsBtnY + lotsBtnH,
            showPoolOverlay ? HDR_BDR : hovLots ? BTN_HOV : BTN_ON);
        g.drawCenteredString(font, "Tous les lots ›",
            lotsBtnX + lotsBtnW / 2, lotsBtnY + (lotsBtnH - 8) / 2, WHITE);

        // Hint / résultats
        int hintY = btnY + BTN_H + 6;
        if (phase == Phase.SPINNING) {
            g.drawCenteredString(font, "En cours...", x + W / 2, hintY, GRAY);
        } else if (phase == Phase.RESULT && !allSpecies.isEmpty()) {
            if (allSpecies.size() == 1) {
                g.drawCenteredString(font, "Pokémon ajouté à votre équipe !", x + W / 2, hintY, GREEN);
            } else {
                // Noms colorés par rareté
                List<String> parts  = new ArrayList<>();
                List<Integer> cols  = new ArrayList<>();
                for (int i = 0; i < allSpecies.size(); i++) {
                    int col = RARITY_COLORS[Math.min(allRarities.get(i), RARITY_COLORS.length - 1)];
                    parts.add(i < allSpecies.size() - 1 ? pokemonName(allSpecies.get(i)) + ", " : pokemonName(allSpecies.get(i)));
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

        // Passe au-dessus des items (z=150) et de leur pose stack
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);

        // Fond semi-transparent + panel
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(ox, oy, ox + OV_W, oy + OV_H, 0xFF0E0618);
        g.fill(ox,          oy,          ox + OV_W, oy + 1,          HDR_BDR);
        g.fill(ox,          oy + OV_H - 1, ox + OV_W, oy + OV_H,    HDR_BDR);
        g.fill(ox,          oy,          ox + 1,        oy + OV_H,   HDR_BDR);
        g.fill(ox + OV_W - 1, oy,        ox + OV_W,    oy + OV_H,   HDR_BDR);

        // Header
        g.fill(ox + 1, oy + 1, ox + OV_W - 1, oy + 26, HEADER_BG);
        g.fill(ox + 1, oy + 26, ox + OV_W - 1, oy + 27, HDR_BDR);
        g.drawCenteredString(font, "Tous les lots", ox + OV_W / 2, oy + 9, WHITE);

        // Bouton fermer
        int cx = ox + OV_W - 18, cy = oy + 7;
        g.fill(cx, cy, cx + 12, cy + 12, isHov(cx, cy, 12, 12) ? 0xFFBB2222 : 0xFF5A1A6A);
        g.drawCenteredString(font, "X", cx + 6, cy + 2, WHITE);

        // Zone scrollable
        int contentX  = ox + OV_PAD;
        int contentY  = oy + 28;
        int visibleH  = OV_H - 28 - 2;

        g.enableScissor(ox + 1, contentY, ox + OV_W - 1, contentY + visibleH);

        int drawY = contentY - poolScroll;
        List<PokemonPool.PoolEntry> pool = PokemonPool.getFullPool();

        // Grouper par rareté (ordre décroissant : LÉGENDAIRE → COMMUN)
        PokemonPool.Rarity[] order = {
            PokemonPool.Rarity.LEGENDAIRE,
            PokemonPool.Rarity.RARE,
            PokemonPool.Rarity.PEU_COMMUN,
            PokemonPool.Rarity.COMMUN
        };

        for (PokemonPool.Rarity rarity : order) {
            List<PokemonPool.PoolEntry> entries = pool.stream()
                .filter(e -> e.rarity() == rarity)
                .toList();
            if (entries.isEmpty()) continue;

            // Header de section
            g.fill(ox + 1, drawY, ox + OV_W - 1, drawY + 18, 0xFF1A0828);
            g.fill(ox + 1, drawY, ox + OV_W - 1, drawY + 1, rarity.color);
            g.drawString(font, rarity.label, contentX, drawY + 5, rarity.color, false);
            String pct = String.format("%.3f%%/Pokémon  —  %d espèces", entries.get(0).percent(), entries.size());
            g.drawString(font, pct, contentX + 80, drawY + 5, GRAY, false);
            drawY += 20;

            // Grille
            for (int i = 0; i < entries.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int ex  = contentX + col * (ENT_W + 3);
                int ey  = drawY + row * ENT_H;

                // Fond teinté
                int rc = rarity.color;
                int er = ((rc >> 16) & 0xFF) * 20 / 100 + 8;
                int eg = ((rc >> 8)  & 0xFF) * 20 / 100 + 4;
                int eb = ( rc        & 0xFF) * 20 / 100 + 18;
                g.fill(ex, ey, ex + ENT_W, ey + ENT_H - 2, 0xFF000000 | (er << 16) | (eg << 8) | eb);

                // Sprite
                ItemStack sp = getSprite(entries.get(i).species());
                if (!sp.isEmpty()) g.renderItem(sp, ex + 1, ey + (ENT_H - 18) / 2);

                // Nom (tronqué)
                String name = pokemonName(entries.get(i).species());
                int maxW = ENT_W - 22 - 30;
                while (font.width(name) > maxW && name.length() > 3)
                    name = name.substring(0, name.length() - 1);
                g.drawString(font, name, ex + 20, ey + (ENT_H - 8) / 2, WHITE, false);

                // Probabilité
                String p = String.format("%.2f%%", entries.get(i).percent());
                g.drawString(font, p, ex + ENT_W - font.width(p) - 2, ey + (ENT_H - 8) / 2, rarity.color, false);
            }
            drawY += ((entries.size() + COLS - 1) / COLS) * ENT_H + 4;
        }

        // Calcul du scroll max
        int totalH = drawY - (contentY - poolScroll);
        int maxScroll = Math.max(0, totalH - visibleH);
        if (poolScroll > maxScroll) poolScroll = maxScroll;

        g.disableScissor();

        // Scrollbar
        if (totalH > visibleH) {
            int sbX = ox + OV_W - 6;
            int sbH = visibleH * visibleH / totalH;
            int sbY = contentY + poolScroll * (visibleH - sbH) / maxScroll;
            g.fill(sbX, contentY, sbX + 4, contentY + visibleH, 0xFF1A0A2A);
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, HDR_BDR);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (showPoolOverlay) {
            poolScroll = Math.max(0, poolScroll - (int)(dy * 12));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    // ── Interactions ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = (width - W) / 2, y = (height - H) / 2;

        // Gestion de l'overlay "Tous les lots"
        if (showPoolOverlay) {
            int ox = (width - OV_W) / 2, oy = (height - OV_H) / 2;
            // Bouton fermer X
            if (isHov(ox + OV_W - 18, oy + 7, 12, 12)) { showPoolOverlay = false; return true; }
            // Clic hors du panel → fermer
            if (!isHov(ox, oy, OV_W, OV_H)) { showPoolOverlay = false; return true; }
            return true; // absorber tous les clics dans l'overlay
        }

        // Bouton "Tous les lots"
        int lotsBtnW = 100, lotsBtnH = 14;
        int lotsBtnX = x + W - FTR_PAD - lotsBtnW;
        int lotsBtnY = y + H - FTR_PAD - lotsBtnH - 1;
        if (isHov(lotsBtnX, lotsBtnY, lotsBtnW, lotsBtnH)) {
            showPoolOverlay = true;
            poolScroll = 0;
            return true;
        }

        if (isHov(x + W - 16, y + 5, 12, 12)) { onClose(); return true; }

        boolean canOpen = (keyCount > 0) && (phase == Phase.IDLE || phase == Phase.RESULT);
        int fy   = y + H - FOOTER_H;
        int btnY = fy + 8;
        int curX = x + FTR_PAD;

        if (canOpen && selectedCount > 1 && isHov(curX, btnY, STEP_W, BTN_H)) {
            selectedCount--; return true;
        }
        curX += STEP_W + 1 + CNT_W + 1;
        if (canOpen && selectedCount < 5 && selectedCount < keyCount && isHov(curX, btnY, STEP_W, BTN_H)) {
            selectedCount++; return true;
        }
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

    private String pokemonName(String species) {
        return Component.translatable("cobblemon.species." + species + ".name").getString();
    }

    private ItemStack getSprite(String s) {
        return spriteCache.computeIfAbsent(s, name -> {
            try {
                var sp = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(name);
                if (sp == null) return ItemStack.EMPTY;
                var pk = new com.cobblemon.mod.common.pokemon.Pokemon();
                pk.setSpecies(sp);
                return com.cobblemon.mod.common.item.PokemonItem.Companion.from(pk, 1);
            } catch (Throwable ignored) { return ItemStack.EMPTY; }
        });
    }

    private ItemStack getKeySprite() {
        if (keySprite == null) {
            try {
                var item = "master_ball".equals(chestType)
                    ? fr.creatmon.shop.ModItems.MASTER_BALL_KEY.get()
                    : fr.creatmon.shop.ModItems.POKE_BALL_KEY.get();
                keySprite = new ItemStack(item);
            } catch (Throwable ignored) { keySprite = ItemStack.EMPTY; }
        }
        return keySprite;
    }
}

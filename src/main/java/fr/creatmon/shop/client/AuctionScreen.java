package fr.creatmon.shop.client;

import fr.creatmon.shop.auction.AHListingData;
import fr.creatmon.shop.auction.AuctionMenu;
import fr.creatmon.shop.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AuctionScreen extends AbstractContainerScreen<AuctionMenu> {

    // ── Dimensions ────────────────────────────────────────────────────
    private static final int W          = 256;
    private static final int H          = 280;
    private static final int HEADER_H   = 36;
    private static final int SUBHEAD_H  = 28;
    private static final int SIDEBAR_W  = 26;
    private static final int SLOT_SIZE  = 18;
    private static final int GRID_X     = SIDEBAR_W + 4;
    private static final int GRID_Y     = HEADER_H + SUBHEAD_H + 2;   // = 66
    private static final int FOOTER_Y   = 190;
    private static final int INV_X      = (W - 9 * SLOT_SIZE) / 2;    // = 47
    private static final int COLS       = 10;
    private static final int ROWS       = 4;
    private static final int PAGE_SIZE  = COLS * ROWS;

    // ── Overlay : confirmation achat ──────────────────────────────────
    private static final int BUY_CONF_W  = 150;
    private static final int BUY_CONF_H  = 72;
    private static final int BUY_CONF_OX = (W - BUY_CONF_W) / 2;
    private static final int BUY_CONF_OY = GRID_Y + (FOOTER_Y - GRID_Y - BUY_CONF_H) / 2;

    // ── Overlay : confirmation vente ──────────────────────────────────
    private static final int SELL_CONF_W  = 156;
    private static final int SELL_CONF_H  = 96;
    private static final int SELL_CONF_OX = (W - SELL_CONF_W) / 2;
    private static final int SELL_CONF_OY = GRID_Y + (FOOTER_Y - GRID_Y - SELL_CONF_H) / 2;

    // ── Couleurs ──────────────────────────────────────────────────────
    private static final int BG          = 0xFF0E1626;
    private static final int SIDEBAR_BG  = 0xFF0A1020;
    private static final int SLOT_BG     = 0xFF1A2B40;
    private static final int SLOT_BDR    = 0xFF0C131E;
    private static final int GOLD        = 0xFFD49E2A;
    private static final int GOLD_DIM    = 0xFF7A5A18;
    private static final int WHITE       = 0xFFFFFFFF;
    private static final int INV_LABEL   = 0xFF8899AA;
    private static final int FOOTER_BG   = 0xFF07101A;
    private static final int PILL_BG     = 0xFF152235;
    private static final int SEARCH_BG   = 0xFF0A1520;
    private static final int BTN_DEC_BDR = 0xFF8B2222;
    private static final int BTN_DEC_BG  = 0xFF3D1010;
    private static final int BTN_DEC_HOV = 0xFF5A1818;
    private static final int BTN_INC_BDR = 0xFF228B22;
    private static final int BTN_INC_BG  = 0xFF103D10;
    private static final int BTN_INC_HOV = 0xFF185A18;
    private static final int QB_W = 18, QB_H = 14;
    private static final int ACT_W = 68, ACT_H = 14;

    // ── Mode ──────────────────────────────────────────────────────────
    enum Mode { BROWSE, MY_LISTINGS, SELL, INBOX }

    // ── State ─────────────────────────────────────────────────────────
    private Mode mode        = Mode.BROWSE;
    private int  page        = 0;
    private int  hoveredSlot = -1;
    private int  hoveredInv  = -1;

    // Buy confirmation
    private AHListingData confirmListing = null;

    // Sell confirmation
    private ItemStack sellItem   = null;
    private int       sellSlot   = -1;
    private int       sellQty    = 1;
    private int       sellMaxQty = 1;

    // These two EditBoxes are managed manually (not via addRenderableWidget)
    // so they can be rendered inside the z=400 pose block.
    private EditBox searchBox;
    private EditBox sellPriceBox;

    // ── Constructor ───────────────────────────────────────────────────

    public AuctionScreen(AuctionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        ClientBalance.value = menu.getBalance();
        ClientBalance.initialized = true;
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.3f));

        searchBox = new EditBox(font,
            leftPos + GRID_X,
            topPos  + HEADER_H + 16,
            W - SIDEBAR_W - 8, 10, Component.literal("Rechercher..."));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setBordered(false);
        addRenderableWidget(searchBox);

        sellPriceBox = new EditBox(font,
            leftPos + SELL_CONF_OX + 9,
            topPos  + SELL_CONF_OY + 67,
            SELL_CONF_W - 18, 10, Component.empty());
        sellPriceBox.setMaxLength(10);
        sellPriceBox.setFilter(s -> s.matches("\\d*"));
        sellPriceBox.setBordered(false);
    }

    // ── Background ────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;

        g.fill(x, y, x+W, y+H, BG);
        g.fill(x, y, x+SIDEBAR_W, y+FOOTER_Y, SIDEBAR_BG);
        g.fill(x+SIDEBAR_W, y, x+SIDEBAR_W+1, y+FOOTER_Y, GOLD_DIM);
        g.fill(x, y+HEADER_H, x+W, y+HEADER_H+1, GOLD_DIM);
        g.fill(x+SIDEBAR_W+1, y+HEADER_H+1,  x+W, y+HEADER_H+14, 0xFF0B1522);
        g.fill(x+SIDEBAR_W+1, y+HEADER_H+14, x+W, y+HEADER_H+15, SLOT_BDR);
        g.fill(x+SIDEBAR_W+1, y+HEADER_H+15, x+W, y+HEADER_H+SUBHEAD_H, SEARCH_BG);
        g.fill(x+SIDEBAR_W+1, y+HEADER_H+SUBHEAD_H, x+W, y+HEADER_H+SUBHEAD_H+1, SLOT_BDR);

        // Balance pill
        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20, px = x + (W - pw) / 2;
        g.fill(px, y+15, px+pw, y+28, PILL_BG);
        g.fill(px, y+15, px+pw, y+16, GOLD_DIM); g.fill(px, y+27, px+pw, y+28, GOLD_DIM);
        g.fill(px, y+15, px+1,  y+28, GOLD_DIM); g.fill(px+pw-1, y+15, px+pw, y+28, GOLD_DIM);

        // Footer
        g.fill(x, y+FOOTER_Y,    x+W, y+FOOTER_Y+1,  GOLD_DIM);
        g.fill(x, y+FOOTER_Y+1,  x+W, y+FOOTER_Y+15, FOOTER_BG);
        g.fill(x, y+FOOTER_Y+15, x+W, y+H,            BG);

        // Gold border + corner dots
        g.fill(x, y, x+W, y+1, GOLD); g.fill(x, y+H-1, x+W, y+H, GOLD);
        g.fill(x, y, x+1, y+H, GOLD); g.fill(x+W-1, y, x+W, y+H, GOLD);
        int d = 3;
        g.fill(x+2, y+2, x+2+d, y+2+d, GOLD); g.fill(x+W-2-d, y+2, x+W-2, y+2+d, GOLD);
        g.fill(x+2, y+H-2-d, x+2+d, y+H-2, GOLD); g.fill(x+W-2-d, y+H-2-d, x+W-2, y+H-2, GOLD);

        renderSidebar(g, x, y, mx, my);

        if (mode == Mode.SELL) {
            renderSellGridPrompt(g, x, y);
        } else {
            renderListingGrid(g, x, y, mx, my);
            renderNavBar(g, x, y, mx, my);
        }

        renderInventoryBg(g, x, y);
    }

    // ── Sidebar ───────────────────────────────────────────────────────

    private static final String[] SIDEBAR_ICONS = {
        "minecraft:written_book", "minecraft:chest", "minecraft:emerald", "minecraft:hopper"
    };
    private static final Mode[] MODES = { Mode.BROWSE, Mode.MY_LISTINGS, Mode.SELL, Mode.INBOX };

    private void renderSidebar(GuiGraphics g, int x, int y, int mx, int my) {
        int tabH = 26;
        for (int i = 0; i < 4; i++) {
            int ty  = y + HEADER_H + 4 + i * (tabH + 2);
            boolean act = mode == MODES[i];
            boolean hov = mx >= x+1 && mx < x+SIDEBAR_W-1 && my >= ty && my < ty+tabH;
            g.fill(x+1, ty, x+SIDEBAR_W-1, ty+tabH, act ? 0xFF1A2B40 : (hov ? 0xFF0F1D2E : SIDEBAR_BG));
            if (act) {
                g.fill(x+1, ty, x+3, ty+tabH, GOLD);
                g.fill(x+SIDEBAR_W-1, ty, x+SIDEBAR_W+1, ty+tabH, 0xFF1A2B40);
            }
            g.renderItem(resolveItem(SIDEBAR_ICONS[i]), x+4, ty+5);
            if (i == 3 && menu.getInboxCount() > 0) {
                String badge = String.valueOf(menu.getInboxCount());
                g.fill(x+15, ty+2, x+15+font.width(badge)+4, ty+10, 0xFFCC3333);
                g.drawString(font, badge, x+17, ty+3, WHITE, false);
            }
        }
    }

    // ── Listing grid ──────────────────────────────────────────────────

    private void renderListingGrid(GuiGraphics g, int x, int y, int mx, int my) {
        List<AHListingData> pg = currentPageItems();
        hoveredSlot = -1;
        for (int i = 0; i < pg.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int sx = x + GRID_X + col * SLOT_SIZE;
            int sy = y + GRID_Y  + row * SLOT_SIZE;
            boolean hov = (confirmListing == null && sellItem == null)
                && mx >= sx && mx < sx+SLOT_SIZE && my >= sy && my < sy+SLOT_SIZE;
            if (hov) hoveredSlot = i;
            g.fill(sx, sy, sx+SLOT_SIZE, sy+SLOT_SIZE, SLOT_BDR);
            g.fill(sx+1, sy+1, sx+SLOT_SIZE-1, sy+SLOT_SIZE-1, hov ? 0xFF223D5A : SLOT_BG);
            g.renderItem(pg.get(i).item(), sx+1, sy+1);
        }
        if (pg.isEmpty()) {
            String msg = mode == Mode.INBOX ? "Boîte vide" : "Aucune annonce";
            g.drawString(font, msg, x + (W - font.width(msg)) / 2, y + GRID_Y + 54, INV_LABEL, false);
        }
    }

    // ── SELL mode prompt ──────────────────────────────────────────────

    private void renderSellGridPrompt(GuiGraphics g, int x, int y) {
        hoveredSlot = -1;
        String[] lines = {
            "Survolez un item dans votre",
            "inventaire puis cliquez pour",
            "le mettre en vente."
        };
        int cy = y + GRID_Y + 44;
        for (int i = 0; i < lines.length; i++)
            g.drawString(font, lines[i], x + (W - font.width(lines[i])) / 2, cy + i * 11, INV_LABEL, false);
    }

    // ── Pagination bar ────────────────────────────────────────────────

    private void renderNavBar(GuiGraphics g, int x, int y, int mx, int my) {
        List<AHListingData> all = filteredListings();
        if (all.isEmpty()) return;
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        int cx = x + W / 2;
        int by = y + GRID_Y + ROWS * SLOT_SIZE + 10;

        boolean hPrev = page > 0 && mx >= cx-50 && mx < cx-36 && my >= by && my < by+14;
        g.fill(cx-50, by, cx-36, by+14, hPrev ? 0xFF223D5A : SLOT_BDR);
        g.fill(cx-49, by+1, cx-37, by+13, hPrev ? 0xFF2A4D6A : SLOT_BG);
        g.drawString(font, "◄", cx-46, by+3, page > 0 ? GOLD : GOLD_DIM, false);

        boolean hNext = page < maxPage && mx >= cx+36 && mx < cx+50 && my >= by && my < by+14;
        g.fill(cx+36, by, cx+50, by+14, hNext ? 0xFF223D5A : SLOT_BDR);
        g.fill(cx+37, by+1, cx+49, by+13, hNext ? 0xFF2A4D6A : SLOT_BG);
        g.drawString(font, "►", cx+40, by+3, page < maxPage ? GOLD : GOLD_DIM, false);

        String info = (page + 1) + "/" + (maxPage + 1);
        g.drawString(font, info, cx - font.width(info) / 2, by + 3, INV_LABEL, false);
    }

    // ── Inventory background ──────────────────────────────────────────

    private void renderInventoryBg(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) {
                int sx = x + INV_X + col * SLOT_SIZE, sy = y + 198 + row * SLOT_SIZE;
                g.fill(sx-1, sy-1, sx+17, sy+17, SLOT_BDR);
                g.fill(sx, sy, sx+16, sy+16, SLOT_BG);
            }
        for (int col = 0; col < 9; col++) {
            int sx = x + INV_X + col * SLOT_SIZE, sy = y + 256;
            g.fill(sx-1, sy-1, sx+17, sy+17, SLOT_BDR);
            g.fill(sx, sy, sx+16, sy+16, SLOT_BG);
        }
    }

    // ── Labels ────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        String title = "HÔTEL DES VENTES";
        g.drawString(font, title, (W - font.width(title)) / 2, 4, GOLD, false);

        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20, px = (W - pw) / 2;
        g.drawString(font, balStr, px + 6, 18, GOLD, false);

        String sub = switch (mode) {
            case BROWSE      -> "Toutes les annonces";
            case MY_LISTINGS -> "Mes annonces";
            case SELL        -> "Vendre un item";
            case INBOX       -> "Boîte de réception";
        };
        g.drawString(font, "■", GRID_X, HEADER_H + 3, GOLD, false);
        g.drawString(font, sub, GRID_X + font.width("■ "), HEADER_H + 3, 0xFFCCDDEE, false);

        if (mode == Mode.INBOX && !menu.getInboxItems().isEmpty()) {
            String claim = "Tout récupérer";
            int bw = font.width(claim) + 10;
            int bx = W - bw - 4, by = HEADER_H + 15;
            g.drawString(font, claim, bx + 5, by + 2, 0xFF88FF88, false);
        }

        g.drawString(font, "Inventaire",
            (W - font.width("Inventaire")) / 2, FOOTER_Y + 18, INV_LABEL, false);
    }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // Detect hovered inventory slot in SELL mode
        hoveredInv = -1;
        if (mode == Mode.SELL && sellItem == null && confirmListing == null) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                if (!s.getItem().isEmpty() && isHovering(s.x, s.y, 16, 16, mouseX, mouseY)) {
                    hoveredInv = i;
                    break;
                }
            }
        }

        // Hide searchBox when not in a browsing mode or when overlay is active
        searchBox.setVisible(confirmListing == null && sellItem == null
            && (mode == Mode.BROWSE || mode == Mode.MY_LISTINGS));

        super.render(g, mouseX, mouseY, pt);

        // Everything below must be above item icons (z=200)
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        if (confirmListing != null) {
            renderBuyOverlay(g, mouseX, mouseY);
        } else if (sellItem != null) {
            renderSellOverlay(g, mouseX, mouseY, pt);
        } else if (hoveredSlot >= 0) {
            List<AHListingData> pg = currentPageItems();
            if (hoveredSlot < pg.size()) {
                if (mode == Mode.INBOX)
                    renderInboxHover(g, pg.get(hoveredSlot).item(), mouseX, mouseY);
                else
                    renderListingHover(g, pg.get(hoveredSlot), mouseX, mouseY);
            }
        } else if (hoveredInv >= 0) {
            Slot s = menu.slots.get(hoveredInv);
            renderSellHover(g, s.getItem(), mouseX, mouseY);
        }

        g.pose().popPose();
    }

    // ── Buy confirmation overlay ──────────────────────────────────────

    private void renderBuyOverlay(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x+W, y+H, 0x99000000);

        int cx = x + BUY_CONF_OX, cy = y + BUY_CONF_OY;
        drawPanel(g, cx, cy, BUY_CONF_W, BUY_CONF_H);

        g.fill(cx+8, cy+8, cx+28, cy+28, SLOT_BDR);
        g.fill(cx+9, cy+9, cx+27, cy+27, SLOT_BG);
        g.renderItem(confirmListing.item(), cx+10, cy+10);

        String nameQty = confirmListing.item().getHoverName().getString() + " ×" + confirmListing.quantity();
        g.drawString(font, nameQty, cx+30, cy+14, WHITE, false);
        g.drawString(font, "Vendeur: " + confirmListing.sellerName(), cx+8, cy+32, INV_LABEL, false);
        g.drawString(font, "Prix: " + confirmListing.price() + " €",  cx+8, cy+42, GOLD,      false);

        int by = cy + 54;
        boolean hConf = mx>=cx+8  && mx<cx+8+ACT_W  && my>=by && my<by+ACT_H;
        boolean hCanc = mx>=cx+80 && mx<cx+80+ACT_W && my>=by && my<by+ACT_H;
        drawBtn(g, cx+8,  by, ACT_W, ACT_H, BTN_INC_BDR, BTN_INC_BG, BTN_INC_HOV, hConf, "Acheter",  0xFF88FF88);
        drawBtn(g, cx+80, by, ACT_W, ACT_H, BTN_DEC_BDR, BTN_DEC_BG, BTN_DEC_HOV, hCanc, "Annuler",  0xFFFF8888);
    }

    // ── Sell confirmation overlay ─────────────────────────────────────

    private void renderSellOverlay(GuiGraphics g, int mx, int my, float pt) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x+W, y+H, 0x99000000);

        int cx = x + SELL_CONF_OX, cy = y + SELL_CONF_OY;
        drawPanel(g, cx, cy, SELL_CONF_W, SELL_CONF_H);

        // Item slot
        int itemX = cx+68, itemY = cy+8;
        g.fill(itemX-1, itemY-1, itemX+21, itemY+21, GOLD_DIM);
        g.fill(itemX,   itemY,   itemX+20, itemY+20, SLOT_BG);
        g.renderItem(sellItem, itemX+2, itemY+2);

        // Qty buttons row
        int btnY = cy+6;
        int[] decOff = {8, 28, 48}, incOff = {90, 110, 130};
        String[] decLbl = {"-S", "-10", "-1"}, incLbl = {"+1", "+10", "+S"};
        for (int i = 0; i < 3; i++) {
            boolean hd = mx>=cx+decOff[i] && mx<cx+decOff[i]+QB_W && my>=btnY && my<btnY+QB_H;
            boolean hi = mx>=cx+incOff[i] && mx<cx+incOff[i]+QB_W && my>=btnY && my<btnY+QB_H;
            drawBtn(g, cx+decOff[i], btnY, QB_W, QB_H, BTN_DEC_BDR, BTN_DEC_BG, BTN_DEC_HOV, hd, decLbl[i], 0xFFFF8888);
            drawBtn(g, cx+incOff[i], btnY, QB_W, QB_H, BTN_INC_BDR, BTN_INC_BG, BTN_INC_HOV, hi, incLbl[i], 0xFF88FF88);
        }

        String qtyStr = "× " + sellQty + " / " + sellMaxQty;
        g.drawString(font, qtyStr, cx + SELL_CONF_W/2 - font.width(qtyStr)/2, cy+30, WHITE, false);

        String itemId = BuiltInRegistries.ITEM.getKey(sellItem.getItem()).toString();
        double avg = menu.getAvgPrice(itemId);
        String avgStr = avg > 0 ? "Prix moyen: " + (long)avg + " €" : "Pas d'historique";
        g.drawString(font, avgStr, cx + SELL_CONF_W/2 - font.width(avgStr)/2, cy+42, INV_LABEL, false);

        g.drawString(font, "Prix:", cx+8, cy+56, 0xFFCCDDEE, false);

        // EditBox background + render (inside z=400 block)
        g.fill(cx+8, cy+66, cx+SELL_CONF_W-8, cy+78, SLOT_BDR);
        g.fill(cx+9, cy+67, cx+SELL_CONF_W-9, cy+77, SEARCH_BG);
        sellPriceBox.setX(cx+9);
        sellPriceBox.setY(cy+67);
        sellPriceBox.render(g, mx, my, pt);

        // Action buttons
        int ay = cy+80;
        boolean hConf = mx>=cx+8  && mx<cx+8+ACT_W  && my>=ay && my<ay+ACT_H;
        boolean hCanc = mx>=cx+80 && mx<cx+80+ACT_W && my>=ay && my<ay+ACT_H;
        drawBtn(g, cx+8,  ay, ACT_W, ACT_H, BTN_INC_BDR, BTN_INC_BG, BTN_INC_HOV, hConf, "Mettre en vente", 0xFF88FF88);
        drawBtn(g, cx+80, ay, ACT_W, ACT_H, BTN_DEC_BDR, BTN_DEC_BG, BTN_DEC_HOV, hCanc, "Annuler",         0xFFFF8888);
    }

    // ── Hover panels ──────────────────────────────────────────────────

    private void renderListingHover(GuiGraphics g, AHListingData l, int mx, int my) {
        boolean isOwn    = l.sellerUUID().equals(minecraft.player.getUUID());
        boolean canCancel = isOwn && mode == Mode.MY_LISTINGS;
        boolean canBuy    = !isOwn && mode == Mode.BROWSE;
        final int PW = 130;
        int ph = (isOwn && !canBuy) ? 68 : 80;
        int px = mx+14, py = my - ph/2;
        if (px+PW > width) px = mx-14-PW;
        py = Math.max(0, Math.min(py, height-ph));

        g.fill(px, py, px+PW, py+ph, BG);
        g.fill(px, py, px+3,  py+ph, canCancel ? 0xFF4488CC : GOLD_DIM);
        g.fill(px, py, px+PW, py+1,  GOLD_DIM); g.fill(px, py+ph-1, px+PW, py+ph, GOLD_DIM);
        g.fill(px, py, px+1,  py+ph, GOLD_DIM); g.fill(px+PW-1, py, px+PW, py+ph, GOLD_DIM);

        int tx = px+7, ty = py+4;
        g.drawString(font, canCancel ? "Votre annonce" : "Annonce", tx, ty, canCancel ? 0xFF88BBFF : GOLD, false);
        ty += 10; g.fill(px+1, ty+1, px+PW-1, ty+2, SLOT_BDR); ty += 4;
        g.renderItem(l.item(), tx, ty);
        g.drawString(font, l.item().getHoverName().getString(), tx+20, ty+4, WHITE, false);
        ty += 18;
        g.drawString(font, "Qté: " + l.quantity(), tx, ty, INV_LABEL, false); ty += 10;
        if (!isOwn) { g.drawString(font, "Vendeur: " + l.sellerName(), tx, ty, INV_LABEL, false); ty += 10; }
        g.drawString(font, "Prix: " + l.price() + " €", tx, ty, GOLD, false); ty += 10;
        g.drawString(font, "Expire: " + l.formatTimeRemaining(), tx, ty, 0xFF888888, false); ty += 10;
        if (canCancel || canBuy) {
            g.fill(px+1, ty+2, px+PW-1, ty+3, SLOT_BDR); ty += 5;
            g.drawString(font, canCancel ? "Clic G: Annuler" : "Clic G: Acheter", tx, ty,
                canCancel ? 0xFFFF8888 : 0xFF88FF88, false);
        }
    }

    private void renderInboxHover(GuiGraphics g, ItemStack item, int mx, int my) {
        final int PW = 110;
        int ph = 48;
        int px = mx+14, py = my - ph/2;
        if (px+PW > width) px = mx-14-PW;
        py = Math.max(0, Math.min(py, height-ph));

        g.fill(px, py, px+PW, py+ph, BG);
        g.fill(px, py, px+3,  py+ph, 0xFF4488CC);
        g.fill(px, py, px+PW, py+1,  GOLD_DIM); g.fill(px, py+ph-1, px+PW, py+ph, GOLD_DIM);
        g.fill(px, py, px+1,  py+ph, GOLD_DIM); g.fill(px+PW-1, py, px+PW, py+ph, GOLD_DIM);

        int tx = px+7, ty = py+4;
        g.renderItem(item, tx, ty);
        g.drawString(font, item.getHoverName().getString(), tx+20, ty+4, WHITE, false);
        ty += 18;
        g.drawString(font, "Qté: " + item.getCount(), tx, ty, INV_LABEL, false); ty += 10;
        g.fill(px+1, ty+2, px+PW-1, ty+3, SLOT_BDR); ty += 5;
        g.drawString(font, "Clic G: Récupérer", tx, ty, 0xFF88FF88, false);
    }

    private void renderSellHover(GuiGraphics g, ItemStack item, int mx, int my) {
        final int PW = 120;
        String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        double avg = menu.getAvgPrice(itemId);
        int ph = 68;
        int px = mx+14, py = my - ph/2;
        if (px+PW > width) px = mx-14-PW;
        py = Math.max(0, Math.min(py, height-ph));

        g.fill(px, py, px+PW, py+ph, BG);
        g.fill(px, py, px+3,  py+ph, BTN_INC_BDR);
        g.fill(px, py, px+PW, py+1,  GOLD_DIM); g.fill(px, py+ph-1, px+PW, py+ph, GOLD_DIM);
        g.fill(px, py, px+1,  py+ph, GOLD_DIM); g.fill(px+PW-1, py, px+PW, py+ph, GOLD_DIM);

        int tx = px+7, ty = py+4;
        g.renderItem(item, tx, ty);
        g.drawString(font, item.getHoverName().getString(), tx+20, ty+4, WHITE, false);
        ty += 18;
        g.drawString(font, "Qté: " + item.getCount(), tx, ty, INV_LABEL, false); ty += 10;
        String avgStr = avg > 0 ? "Prix moyen: " + (long)avg + " €" : "Aucun historique";
        g.drawString(font, avgStr, tx, ty, avg > 0 ? GOLD : INV_LABEL, false); ty += 10;
        g.fill(px+1, ty+2, px+PW-1, ty+3, SLOT_BDR); ty += 5;
        g.drawString(font, "Clic G: Vendre", tx, ty, 0xFF88FF88, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = leftPos, y = topPos;

        // ── Buy overlay ───────────────────────────────────────────────
        if (confirmListing != null) {
            int cx = x + BUY_CONF_OX, cy = y + BUY_CONF_OY;
            int by = cy + 54;
            if (btn == 0 && mx>=cx+8 && mx<cx+8+ACT_W && my>=by && my<by+ACT_H) {
                PacketDistributor.sendToServer(new AHBuyPacket(confirmListing.id()));
                confirmListing = null;
                playClick();
            } else { confirmListing = null; }
            return true;
        }

        // ── Sell overlay ──────────────────────────────────────────────
        if (sellItem != null) {
            int cx = x + SELL_CONF_OX, cy = y + SELL_CONF_OY;
            int btnY = cy + 6;
            int[] decOff = {8, 28, 48}, incOff = {90, 110, 130};
            int[] decDelta = {-sellMaxQty, -10, -1}, incDelta = {1, 10, sellMaxQty};
            for (int i = 0; i < 3; i++) {
                if (mx>=cx+decOff[i] && mx<cx+decOff[i]+QB_W && my>=btnY && my<btnY+QB_H) {
                    sellQty = Math.max(1, Math.min(sellMaxQty, sellQty + decDelta[i]));
                    playClick(); return true;
                }
                if (mx>=cx+incOff[i] && mx<cx+incOff[i]+QB_W && my>=btnY && my<btnY+QB_H) {
                    sellQty = Math.max(1, Math.min(sellMaxQty, sellQty + incDelta[i]));
                    playClick(); return true;
                }
            }
            int ay = cy + 80;
            if (btn == 0 && mx>=cx+8 && mx<cx+8+ACT_W && my>=ay && my<ay+ACT_H) {
                String priceStr = sellPriceBox.getValue().trim();
                if (!priceStr.isEmpty()) {
                    try {
                        long price = Long.parseLong(priceStr);
                        if (price > 0) {
                            PacketDistributor.sendToServer(new AHListPacket(sellSlot, sellQty, price));
                            clearSell(); playClick();
                        }
                    } catch (NumberFormatException ignored) {}
                }
                return true;
            }
            if (mx>=cx+80 && mx<cx+80+ACT_W && my>=ay && my<ay+ACT_H) {
                clearSell(); playClick(); return true;
            }
            // Let EditBox handle its own click for focus/cursor
            return sellPriceBox.mouseClicked(mx, my, btn) || true;
        }

        // ── Sidebar ───────────────────────────────────────────────────
        int tabH = 26;
        for (int i = 0; i < 4; i++) {
            int ty = y + HEADER_H + 4 + i * (tabH + 2);
            if (mx>=x+1 && mx<x+SIDEBAR_W-1 && my>=ty && my<ty+tabH) {
                if (mode != MODES[i]) { mode = MODES[i]; page = 0; }
                playClick(); return true;
            }
        }

        // ── "Tout récupérer" (INBOX) ──────────────────────────────────
        if (mode == Mode.INBOX && !menu.getInboxItems().isEmpty()) {
            String claim = "Tout récupérer";
            int bw = font.width(claim) + 10;
            int bx = leftPos + W - bw - 4, by = topPos + HEADER_H + 15;
            if (mx>=bx && mx<bx+bw && my>=by && my<by+10) {
                PacketDistributor.sendToServer(new AHClaimPacket());
                playClick(); return true;
            }
        }

        // ── Listing grid ──────────────────────────────────────────────
        if (mode == Mode.INBOX) {
            List<AHListingData> pg = currentPageItems();
            for (int i = 0; i < pg.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int sx = x + GRID_X + col * SLOT_SIZE;
                int sy = y + GRID_Y  + row * SLOT_SIZE;
                if (mx>=sx && mx<sx+SLOT_SIZE && my>=sy && my<sy+SLOT_SIZE) {
                    if (btn == 0) {
                        int inboxIndex = page * PAGE_SIZE + i;
                        PacketDistributor.sendToServer(new AHClaimPacket(inboxIndex));
                        playClick();
                    }
                    return true;
                }
            }
        } else if (mode != Mode.SELL) {
            List<AHListingData> pg = currentPageItems();
            for (int i = 0; i < pg.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int sx = x + GRID_X + col * SLOT_SIZE;
                int sy = y + GRID_Y  + row * SLOT_SIZE;
                if (mx>=sx && mx<sx+SLOT_SIZE && my>=sy && my<sy+SLOT_SIZE) {
                    AHListingData l = pg.get(i);
                    boolean isOwn = l.sellerUUID().equals(minecraft.player.getUUID());
                    if (btn == 0) {
                        if (isOwn && mode == Mode.MY_LISTINGS) {
                            PacketDistributor.sendToServer(new AHCancelPacket(l.id()));
                            playClick();
                        } else if (!isOwn) {
                            confirmListing = l;
                            playClick();
                        }
                    }
                    return true;
                }
            }
        }

        // ── Pagination ────────────────────────────────────────────────
        if (mode != Mode.SELL) {
            List<AHListingData> all = filteredListings();
            if (!all.isEmpty()) {
                int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
                int cx = x + W / 2, by = y + GRID_Y + ROWS * SLOT_SIZE + 10;
                if (btn == 0 && mx>=cx-50 && mx<cx-36 && my>=by && my<by+14 && page > 0) {
                    page--; playClick(); return true;
                }
                if (btn == 0 && mx>=cx+36 && mx<cx+50 && my>=by && my<by+14 && page < maxPage) {
                    page++; playClick(); return true;
                }
            }
        }

        // ── Sell mode: inventory slot clicked ─────────────────────────
        if (mode == Mode.SELL) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                if (!s.getItem().isEmpty() && isHovering(s.x, s.y, 16, 16, mx, my)) {
                    openSell(s.getItem(), s.getContainerSlot());
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mode != Mode.SELL && confirmListing == null && sellItem == null) {
            List<AHListingData> all = filteredListings();
            if (!all.isEmpty()) {
                int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
                if (dy > 0 && page > 0)        { page--; return true; }
                if (dy < 0 && page < maxPage)  { page++; return true; }
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { // Escape
            if (confirmListing != null) { confirmListing = null; return true; }
            if (sellItem != null)       { clearSell();           return true; }
        }
        // Bloquer la touche inventaire (E) — seul ESC ferme l'AH
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        // Entrée sur la searchBox → simple défocus, ne ferme pas
        if ((keyCode == 257 || keyCode == 335) && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }
        if (sellItem != null) return sellPriceBox.keyPressed(keyCode, scanCode, mods);
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (sellItem != null) return sellPriceBox.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    // ── Public API ────────────────────────────────────────────────────

    public void onSync() {
        int max = Math.max(0, (filteredListings().size() - 1) / PAGE_SIZE);
        if (page > max) page = max;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void openSell(ItemStack item, int slot) {
        sellItem   = item.copy();
        sellSlot   = slot;
        sellQty    = 1;
        sellMaxQty = item.getCount();
        sellPriceBox.setValue("");
        sellPriceBox.setFocused(true);
    }

    private void clearSell() {
        sellItem = null;
        sellPriceBox.setFocused(false);
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private List<AHListingData> filteredListings() {
        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<AHListingData> base = switch (mode) {
            case MY_LISTINGS -> menu.getListings().stream()
                .filter(l -> l.sellerUUID().equals(minecraft.player.getUUID()))
                .collect(Collectors.toList());
            case INBOX -> menu.getInboxItems().stream()
                .map(item -> new AHListingData(UUID.randomUUID(), UUID.randomUUID(), "",
                    item, item.getCount(), 0, System.currentTimeMillis()))
                .collect(Collectors.toList());
            default -> menu.getListings();
        };
        if (q.isEmpty()) return base;
        return base.stream()
            .filter(l -> l.item().getHoverName().getString().toLowerCase().contains(q)
                      || l.sellerName().toLowerCase().contains(q))
            .collect(Collectors.toList());
    }

    private List<AHListingData> currentPageItems() {
        List<AHListingData> all = filteredListings();
        int from = page * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, all.size());
        return from < all.size() ? all.subList(from, to) : List.of();
    }

    private void drawPanel(GuiGraphics g, int cx, int cy, int pw, int ph) {
        g.fill(cx, cy, cx+pw, cy+ph, BG);
        g.fill(cx, cy, cx+pw, cy+1,  GOLD); g.fill(cx, cy+ph-1, cx+pw, cy+ph, GOLD);
        g.fill(cx, cy, cx+1,  cy+ph, GOLD); g.fill(cx+pw-1, cy, cx+pw, cy+ph, GOLD);
        g.fill(cx+2, cy+2, cx+4, cy+4, GOLD); g.fill(cx+pw-4, cy+2, cx+pw-2, cy+4, GOLD);
        g.fill(cx+2, cy+ph-4, cx+4, cy+ph-2, GOLD); g.fill(cx+pw-4, cy+ph-4, cx+pw-2, cy+ph-2, GOLD);
    }

    private void drawBtn(GuiGraphics g, int bx, int by, int bw, int bh,
                         int border, int bg, int hov, boolean isHov, String label, int textColor) {
        g.fill(bx, by, bx+bw, by+bh, border);
        g.fill(bx+1, by+1, bx+bw-1, by+bh-1, isHov ? hov : bg);
        int lw = font.width(label);
        g.drawString(font, label, bx + (bw - lw) / 2, by + (bh - 8) / 2, textColor, false);
    }

    private static ItemStack resolveItem(String itemId) {
        try {
            var loc = ResourceLocation.tryParse(itemId);
            return loc != null ? new ItemStack(BuiltInRegistries.ITEM.get(loc)) : new ItemStack(Items.BARRIER);
        } catch (Exception e) { return new ItemStack(Items.BARRIER); }
    }

    private static String formatBalance(int n) {
        String s = Integer.toString(n);
        var sb = new StringBuilder();
        int off = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}

package fr.creatmon.shop.client;

import fr.creatmon.shop.ShopData;
import fr.creatmon.shop.ShopMenu;
import fr.creatmon.shop.network.BuyItemPacket;
import fr.creatmon.shop.network.SellItemPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

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

    // ── Confirmation overlay ──────────────────────────────────────────
    // Layout: [-S][-10][-1] [item] [+1][+10][+S]  then  [Acheter/Vendre] [Annuler]
    private static final int CONF_W     = 156;
    private static final int CONF_H     = 76;
    private static final int CONF_OX    = (W - CONF_W) / 2;                              // = 50
    private static final int CONF_OY    = GRID_Y + (FOOTER_Y - GRID_Y - CONF_H) / 2;    // = 90
    // Small qty buttons: 18 × 14
    private static final int QB_W       = 18;
    private static final int QB_H       = 14;
    // Confirm/Cancel buttons: each 68 wide × 14 tall, gap 4, margin 8
    private static final int ACT_W      = 68;
    private static final int ACT_H      = 14;

    // Qty-button x offsets from CONF_OX (8 px panel padding):
    //   [-S]=8  [-10]=28  [-1]=48  [item]=68  [+1]=90  [+10]=110  [+S]=130
    // Row y inside panel = 6; item slot y = 8 (20px tall, centered +2)
    // Qty text y = 32, price text y = 44, action buttons y = 58

    // ── Colours ───────────────────────────────────────────────────────
    private static final int BG         = 0xFF0E1626;
    private static final int SIDEBAR_BG = 0xFF0A1020;
    private static final int SLOT_BG    = 0xFF1A2B40;
    private static final int SLOT_BDR   = 0xFF0C131E;
    private static final int GOLD       = 0xFFD49E2A;
    private static final int GOLD_DIM   = 0xFF7A5A18;
    private static final int ORANGE     = 0xFFFF8800;
    private static final int WHITE      = 0xFFFFFFFF;
    private static final int INV_LABEL  = 0xFF8899AA;
    private static final int FOOTER_BG  = 0xFF07101A;
    private static final int PILL_BG    = 0xFF152235;
    private static final int SEARCH_BG  = 0xFF0A1520;

    private static final int BTN_DEC_BORDER = 0xFF8B2222;
    private static final int BTN_DEC_BG     = 0xFF3D1010;
    private static final int BTN_DEC_HOV    = 0xFF5A1818;
    private static final int BTN_INC_BORDER = 0xFF228B22;
    private static final int BTN_INC_BG     = 0xFF103D10;
    private static final int BTN_INC_HOV    = 0xFF185A18;
    private static final int BTN_ACT_BG     = 0xFF0E1626;

    // ── State ─────────────────────────────────────────────────────────
    private EditBox searchBox;
    private int hoveredItem = -1;

    // Confirmation overlay
    private ShopData.ShopItem confirmingItem = null;
    private boolean confirmingIsBuy = false;
    private int confirmQty = 1;

    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (!ClientBalance.initialized) {
            // BalanceSyncPacket not yet received — seed from the menu (set via buf.readInt on open)
            ClientBalance.value = menu.getBalance();
            ClientBalance.initialized = true;
        } else {
            // BalanceSyncPacket is authoritative; push to menu to neutralize ContainerData overflow
            // (ContainerData syncs values as 16-bit shorts, which caps at 32 767)
            menu.updateBalance(ClientBalance.value);
        }
    }

    @Override
    protected void init() {
        super.init();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 1.3f));
        int searchX = leftPos + SIDEBAR_W + 4;
        int searchW = W - SIDEBAR_W - 8;
        searchBox = new EditBox(font, searchX, topPos + HEADER_H + 16, searchW, 10,
                Component.literal("Rechercher..."));
        searchBox.setMaxLength(32);
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setBordered(false);
        addRenderableWidget(searchBox);
    }

    // ── Background ────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        g.fill(x, y, x + W, y + H, BG);

        g.fill(x, y, x + SIDEBAR_W, y + FOOTER_Y, SIDEBAR_BG);
        g.fill(x + SIDEBAR_W, y, x + SIDEBAR_W + 1, y + FOOTER_Y, GOLD_DIM);

        g.fill(x, y + HEADER_H, x + W, y + HEADER_H + 1, GOLD_DIM);

        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 1,  x + W, y + HEADER_H + 14, 0xFF0B1522);
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 14, x + W, y + HEADER_H + 15, SLOT_BDR);

        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + 15, x + W, y + HEADER_H + SUBHEAD_H,     SEARCH_BG);
        g.fill(x + SIDEBAR_W + 1, y + HEADER_H + SUBHEAD_H, x + W, y + HEADER_H + SUBHEAD_H + 1, SLOT_BDR);

        // Balance pill
        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20;
        int px = x + (W - pw) / 2;
        g.fill(px,          y + 15, px + pw,     y + 28, PILL_BG);
        g.fill(px,          y + 15, px + pw,     y + 16, GOLD_DIM);
        g.fill(px,          y + 27, px + pw,     y + 28, GOLD_DIM);
        g.fill(px,          y + 15, px + 1,      y + 28, GOLD_DIM);
        g.fill(px + pw - 1, y + 15, px + pw,     y + 28, GOLD_DIM);

        // Footer
        g.fill(x, y + FOOTER_Y,      x + W, y + FOOTER_Y + 1,  GOLD_DIM);
        g.fill(x, y + FOOTER_Y + 1,  x + W, y + FOOTER_Y + 15, FOOTER_BG);
        g.fill(x, y + FOOTER_Y + 15, x + W, y + H,              BG);

        // Gold border
        g.fill(x,         y,         x + W, y + 1,     GOLD);
        g.fill(x,         y + H - 1, x + W, y + H,     GOLD);
        g.fill(x,         y,         x + 1, y + H,     GOLD);
        g.fill(x + W - 1, y,         x + W, y + H,     GOLD);

        int d = 3;
        g.fill(x + 2,         y + 2,         x + 2 + d, y + 2 + d,     GOLD);
        g.fill(x + W - 2 - d, y + 2,         x + W - 2, y + 2 + d,     GOLD);
        g.fill(x + 2,         y + H - 2 - d, x + 2 + d, y + H - 2,     GOLD);
        g.fill(x + W - 2 - d, y + H - 2 - d, x + W - 2, y + H - 2,     GOLD);

        renderSidebar(g, x, y, mouseX, mouseY);
        renderItemGrid(g, x, y, mouseX, mouseY);
        renderInventoryBg(g, x, y);
    }

    private void renderSidebar(GuiGraphics g, int x, int y, int mx, int my) {
        int tabH = 26;
        for (int i = 0; i < ShopData.CATEGORIES.size(); i++) {
            ShopData.ShopCategory cat = ShopData.CATEGORIES.get(i);
            int ty  = y + HEADER_H + 4 + i * (tabH + 2);
            boolean act = menu.getCategoryIndex() == i;
            boolean hov = mx >= x + 1 && mx < x + SIDEBAR_W - 1 && my >= ty && my < ty + tabH;
            g.fill(x + 1, ty, x + SIDEBAR_W - 1, ty + tabH, act ? 0xFF1A2B40 : (hov ? 0xFF0F1D2E : SIDEBAR_BG));
            if (act) {
                g.fill(x + 1,             ty, x + 3,              ty + tabH, cat.color());
                g.fill(x + SIDEBAR_W - 1, ty, x + SIDEBAR_W + 1,  ty + tabH, 0xFF1A2B40);
            }
            g.renderItem(resolveItem(cat.iconId()), x + 4, ty + 5);
        }
    }

    private void renderItemGrid(GuiGraphics g, int x, int y, int mx, int my) {
        List<ShopData.ShopItem> items = filteredItems();
        hoveredItem = -1;
        for (int i = 0; i < items.size(); i++) {
            int col = i % ShopMenu.COLS;
            int row = i / ShopMenu.COLS;
            int sx  = x + GRID_X + col * SLOT_SIZE;
            int sy  = y + GRID_Y  + row * SLOT_SIZE;
            boolean hov = confirmingItem == null && mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
            if (hov) hoveredItem = i;
            g.fill(sx,     sy,     sx + SLOT_SIZE,     sy + SLOT_SIZE,     SLOT_BDR);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, hov ? 0xFF223D5A : SLOT_BG);
            g.renderItem(resolveItem(items.get(i).itemId()), sx + 1, sy + 1);
        }
    }

    private void renderInventoryBg(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) {
                int sx = x + INV_X + col * SLOT_SIZE, sy = y + 198 + row * SLOT_SIZE;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BDR);
                g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
            }
        for (int col = 0; col < 9; col++) {
            int sx = x + INV_X + col * SLOT_SIZE, sy = y + 256;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BDR);
            g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
        }
    }

    // ── Labels ────────────────────────────────────────────────────────

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int titleW = font.width("SHOP");
        g.drawString(font, "SHOP", (W - titleW) / 2, 4, GOLD, false);

        String balStr = formatBalance(menu.getBalance()) + " €";
        int pw = font.width(balStr) + 20;
        int px = (W - pw) / 2;
        g.drawString(font, balStr, px + 6, 18, GOLD, false);

        ShopData.ShopCategory cat = menu.currentCategory();
        if (cat != null) {
            g.drawString(font, "■", GRID_X,                        HEADER_H + 3, cat.color(),  false);
            g.drawString(font, cat.label(), GRID_X + font.width("■ "), HEADER_H + 3, 0xFFCCDDEE, false);
        }

        int invLabelW = font.width("Inventaire");
        g.drawString(font, "Inventaire", (W - invLabelW) / 2, FOOTER_Y + 18, INV_LABEL, false);
    }

    // ── Render ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 400.0f);
        if (confirmingItem != null) {
            renderConfirmOverlay(g, mouseX, mouseY);
        } else if (hoveredItem >= 0) {
            List<ShopData.ShopItem> items = filteredItems();
            if (hoveredItem < items.size()) {
                ShopData.ShopCategory cat = ShopData.CATEGORIES.get(menu.getCategoryIndex());
                renderHoverPanel(g, items.get(hoveredItem), cat, mouseX, mouseY);
            }
        }
        g.pose().popPose();
    }

    // ── Hover panel ───────────────────────────────────────────────────

    private void renderHoverPanel(GuiGraphics g, ShopData.ShopItem item, ShopData.ShopCategory cat, int mx, int my) {
        final int PW = 128;
        // Compute height dynamically
        int ph = 4 + 10 + 2 + 1 + 2 + 16;  // top padding + cat label + gap + separator + gap + item row
        if (item.buyable())  ph += 10;
        if (item.sellable()) ph += 10;
        if (item.buyable() || item.sellable()) ph += 2 + 1 + 2;  // gap + separator + gap
        if (item.buyable())  ph += 10;
        if (item.sellable()) ph += 10;
        ph += 4;  // bottom padding

        // Position near cursor
        int px = mx + 14;
        int py = my - ph / 2;
        if (px + PW > width)  px = mx - 14 - PW;
        if (py < 0)            py = 0;
        if (py + ph > height)  py = height - ph;

        // Background
        g.fill(px, py, px + PW, py + ph, BG);
        // Left accent stripe (category color)
        g.fill(px, py, px + 3, py + ph, cat.color());
        // Border
        g.fill(px,          py,          px + PW, py + 1,      GOLD_DIM);
        g.fill(px,          py + ph - 1, px + PW, py + ph,     GOLD_DIM);
        g.fill(px,          py,          px + 1,  py + ph,     GOLD_DIM);
        g.fill(px + PW - 1, py,          px + PW, py + ph,     GOLD_DIM);

        int tx = px + 7;
        int ty = py + 4;

        // Category name
        g.drawString(font, cat.label(), tx, ty, cat.color(), false);
        ty += 10;
        g.fill(px + 1, ty + 1, px + PW - 1, ty + 2, SLOT_BDR);
        ty += 4;

        // Item icon + name (truncate if needed)
        g.renderItem(resolveItem(item.itemId()), tx, ty);
        String displayName = item.name();
        int maxNameW = PW - 7 - 20 - 2;
        if (font.width(displayName) > maxNameW) {
            while (font.width(displayName + "...") > maxNameW && !displayName.isEmpty())
                displayName = displayName.substring(0, displayName.length() - 1);
            displayName += "...";
        }
        g.drawString(font, displayName, tx + 20, ty + 4, WHITE, false);
        ty += 18;

        // Prices
        if (item.buyable()) {
            g.drawString(font, "Prix d'achat:  " + item.buy() + " €", tx, ty, 0xFF88FF88, false);
            ty += 10;
        }
        if (item.sellable()) {
            g.drawString(font, "Prix de vente: " + item.sell() + " €", tx, ty, 0xFFFFAA44, false);
            ty += 10;
        }

        // Separator + hints
        if (item.buyable() || item.sellable()) {
            ty += 2;
            g.fill(px + 1, ty, px + PW - 1, ty + 1, SLOT_BDR);
            ty += 3;
            if (item.buyable())
                g.drawString(font, "Clic G : Acheter", tx, ty, 0xFF88FF88, false);
            if (item.buyable() && item.sellable()) ty += 10;
            if (item.sellable())
                g.drawString(font, "Clic D : Vendre",  tx, ty, 0xFFFFAA44, false);
        }
    }

    // ── Confirmation overlay ──────────────────────────────────────────

    private void renderConfirmOverlay(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        // Dim the whole GUI
        g.fill(x, y, x + W, y + H, 0x99000000);

        int cx = x + CONF_OX;
        int cy = y + CONF_OY;

        // Panel
        g.fill(cx, cy, cx + CONF_W, cy + CONF_H, BG);
        // Gold border
        g.fill(cx,              cy,              cx + CONF_W, cy + 1,          GOLD);
        g.fill(cx,              cy + CONF_H - 1, cx + CONF_W, cy + CONF_H,     GOLD);
        g.fill(cx,              cy,              cx + 1,       cy + CONF_H,     GOLD);
        g.fill(cx + CONF_W - 1, cy,              cx + CONF_W,  cy + CONF_H,     GOLD);
        // Corner dots
        g.fill(cx + 2, cy + 2, cx + 4, cy + 4, GOLD);
        g.fill(cx + CONF_W - 4, cy + 2, cx + CONF_W - 2, cy + 4, GOLD);
        g.fill(cx + 2, cy + CONF_H - 4, cx + 4, cy + CONF_H - 2, GOLD);
        g.fill(cx + CONF_W - 4, cy + CONF_H - 4, cx + CONF_W - 2, cy + CONF_H - 2, GOLD);

        int btnRowY   = cy + 6;
        int itemSlotX = cx + 68;
        int itemSlotY = cy + 8;

        // Item slot (20×20 centered around x=68)
        g.fill(itemSlotX - 1, itemSlotY - 1, itemSlotX + 21, itemSlotY + 21, GOLD_DIM);
        g.fill(itemSlotX,     itemSlotY,     itemSlotX + 20, itemSlotY + 20, SLOT_BG);
        g.renderItem(resolveItem(confirmingItem.itemId()), itemSlotX + 2, itemSlotY + 2);

        // Decrease buttons: [-S]=8, [-10]=28, [-1]=48
        int[] decOffsets = {8, 28, 48};
        String[] decLabels = {"-S", "-10", "-1"};
        for (int i = 0; i < 3; i++) {
            int bx = cx + decOffsets[i];
            int by = btnRowY;
            boolean hov = mouseX >= bx && mouseX < bx + QB_W && mouseY >= by && mouseY < by + QB_H;
            g.fill(bx,     by,     bx + QB_W,     by + QB_H,     BTN_DEC_BORDER);
            g.fill(bx + 1, by + 1, bx + QB_W - 1, by + QB_H - 1, hov ? BTN_DEC_HOV : BTN_DEC_BG);
            int lw = font.width(decLabels[i]);
            g.drawString(font, decLabels[i], bx + (QB_W - lw) / 2, by + 3, 0xFFFF8888, false);
        }

        // Increase buttons: [+1]=90, [+10]=110, [+S]=130
        int[] incOffsets = {90, 110, 130};
        String[] incLabels = {"+1", "+10", "+S"};
        for (int i = 0; i < 3; i++) {
            int bx = cx + incOffsets[i];
            int by = btnRowY;
            boolean hov = mouseX >= bx && mouseX < bx + QB_W && mouseY >= by && mouseY < by + QB_H;
            g.fill(bx,     by,     bx + QB_W,     by + QB_H,     BTN_INC_BORDER);
            g.fill(bx + 1, by + 1, bx + QB_W - 1, by + QB_H - 1, hov ? BTN_INC_HOV : BTN_INC_BG);
            int lw = font.width(incLabels[i]);
            g.drawString(font, incLabels[i], bx + (QB_W - lw) / 2, by + 3, 0xFF88FF88, false);
        }

        // Quantity display
        String qtyStr = "× " + confirmQty;
        int qtw = font.width(qtyStr);
        g.drawString(font, qtyStr, cx + CONF_W / 2 - qtw / 2, cy + 32, WHITE, false);

        // Total price display
        int totalPrice = confirmingIsBuy
            ? confirmQty * confirmingItem.buy()
            : confirmQty * confirmingItem.sell();
        String priceStr = (confirmingIsBuy ? "Total: " : "Revenu: ") + totalPrice + " €";
        int ptw = font.width(priceStr);
        g.drawString(font, priceStr, cx + CONF_W / 2 - ptw / 2, cy + 44, confirmingIsBuy ? GOLD : ORANGE, false);

        // Action buttons row (y = 58)
        int actY = cy + 58;

        // Confirm button (green, left)
        boolean hovConf = mouseX >= cx + 8 && mouseX < cx + 8 + ACT_W && mouseY >= actY && mouseY < actY + ACT_H;
        g.fill(cx + 8,     actY,     cx + 8 + ACT_W,     actY + ACT_H,     BTN_INC_BORDER);
        g.fill(cx + 9,     actY + 1, cx + 7 + ACT_W,     actY + ACT_H - 1, hovConf ? BTN_INC_HOV : BTN_INC_BG);
        String confLabel = confirmingIsBuy ? "Acheter" : "Vendre";
        int clw = font.width(confLabel);
        g.drawString(font, confLabel, cx + 8 + (ACT_W - clw) / 2, actY + 3, 0xFF88FF88, false);

        // Cancel button (red, right)
        boolean hovCanc = mouseX >= cx + 80 && mouseX < cx + 80 + ACT_W && mouseY >= actY && mouseY < actY + ACT_H;
        g.fill(cx + 80,     actY,     cx + 80 + ACT_W,     actY + ACT_H,     BTN_DEC_BORDER);
        g.fill(cx + 81,     actY + 1, cx + 79 + ACT_W,     actY + ACT_H - 1, hovCanc ? BTN_DEC_HOV : BTN_DEC_BG);
        String cancLabel = "Annuler";
        int caw = font.width(cancLabel);
        g.drawString(font, cancLabel, cx + 80 + (ACT_W - caw) / 2, actY + 3, 0xFFFF8888, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;

        // ── Confirmation overlay ──────────────────────────────────────
        if (confirmingItem != null) {
            int cx = x + CONF_OX;
            int cy = y + CONF_OY;
            int btnRowY = cy + 6;

            // Decrease buttons
            int[] decOffsets = {8, 28, 48};
            int[] decDeltas  = {-64, -10, -1};
            for (int i = 0; i < 3; i++) {
                int bx = cx + decOffsets[i];
                if (mx >= bx && mx < bx + QB_W && my >= btnRowY && my < btnRowY + QB_H) {
                    confirmQty = Math.max(1, confirmQty + decDeltas[i]);
                    playClick();
                    return true;
                }
            }

            // Increase buttons
            int[] incOffsets = {90, 110, 130};
            int[] incDeltas  = {1, 10, 64};
            for (int i = 0; i < 3; i++) {
                int bx = cx + incOffsets[i];
                if (mx >= bx && mx < bx + QB_W && my >= btnRowY && my < btnRowY + QB_H) {
                    confirmQty = Math.min(64, confirmQty + incDeltas[i]);
                    playClick();
                    return true;
                }
            }

            // Action buttons
            int actY = cy + 58;

            // Confirm
            if (mx >= cx + 8 && mx < cx + 8 + ACT_W && my >= actY && my < actY + ACT_H) {
                if (confirmingIsBuy)
                    PacketDistributor.sendToServer(new BuyItemPacket(confirmingItem.id(), confirmQty));
                else
                    PacketDistributor.sendToServer(new SellItemPacket(confirmingItem.id(), confirmQty));
                confirmingItem = null;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
                return true;
            }

            // Cancel
            if (mx >= cx + 80 && mx < cx + 80 + ACT_W && my >= actY && my < actY + ACT_H) {
                confirmingItem = null;
                playClick();
                return true;
            }

            return true; // consume all clicks while overlay is open
        }

        // ── Category tabs ─────────────────────────────────────────────
        int tabH = 26;
        for (int i = 0; i < ShopData.CATEGORIES.size(); i++) {
            int ty = y + HEADER_H + 4 + i * (tabH + 2);
            if (mx >= x + 1 && mx < x + SIDEBAR_W - 1 && my >= ty && my < ty + tabH) {
                menu.setCategory(i);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
                return true;
            }
        }

        // ── Item grid ─────────────────────────────────────────────────
        List<ShopData.ShopItem> items = filteredItems();
        for (int i = 0; i < items.size(); i++) {
            int col = i % ShopMenu.COLS;
            int row = i / ShopMenu.COLS;
            int sx  = x + GRID_X + col * SLOT_SIZE;
            int sy  = y + GRID_Y  + row * SLOT_SIZE;
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                ShopData.ShopItem item = items.get(i);
                if (button == 0 && item.buyable()) {
                    confirmingItem  = item;
                    confirmingIsBuy = true;
                    confirmQty      = 1;
                    playClick();
                    return true;
                }
                if (button == 1 && item.sellable()) {
                    confirmingItem  = item;
                    confirmingIsBuy = false;
                    confirmQty      = 1;
                    playClick();
                    return true;
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && confirmingItem != null) {
            confirmingItem = null;
            return true;
        }
        // Bloquer la touche inventaire (E) — seul ESC ferme le shop
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private List<ShopData.ShopItem> filteredItems() {
        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        var items = menu.currentPageItems();
        if (q.isEmpty()) return items;
        return items.stream()
            .filter(it -> it.name().toLowerCase().contains(q) || it.desc().toLowerCase().contains(q))
            .toList();
    }

    private static ItemStack resolveItem(String itemId) {
        try {
            var loc = ResourceLocation.tryParse(itemId);
            if (loc == null) return new ItemStack(Items.BARRIER);
            return new ItemStack(BuiltInRegistries.ITEM.get(loc));
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private static String formatBalance(int n) {
        String s = Integer.toString(n);
        var sb   = new StringBuilder();
        int off  = s.length() % 3;
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (i - off) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}

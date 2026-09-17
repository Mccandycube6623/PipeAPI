package gd.rf.kongzhongtitian.PipeAPI;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class TransporterNodeScreen extends AbstractContainerScreen<TransporterNodeMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PipeAPI.MODID, "textures/screen/xiang.png");

    public TransporterNodeScreen(TransporterNodeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222; // 箱子高度
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        gui.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        for (int i = 0; i < 27; i++) {
            int x = leftPos + 8 + (i % 9) * 18;
            int y = topPos + 18 + (i / 9) * 18;
            if (i == 0) {
                // 正常显示（无覆盖）
                continue;
            } else if (i == 1) {
                // 升级槽高亮
                gui.fill(x, y, x + 16, y + 16, 0x40FFAA00);
                gui.renderOutline(x, y, 16, 16, 0xFFFFAA00);
            } else {
                // 其余锁定槽灰色
                gui.fill(x, y, x + 16, y + 16, 0x80AAAAAA);
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);
        this.renderTooltip(gui, mouseX, mouseY);

        // 在 render 方法中，原来的显示过滤信息改为显示物品图标（最多显示前几个）
        ItemStack upgrade = this.menu.getSlot(1).getItem();
        if (!upgrade.isEmpty() && upgrade.getItem() instanceof NodeUpgradeItemFilter) {
            List<ItemStack> filters = NodeUpgradeItemFilter.getFilterItems(upgrade);
            if (!filters.isEmpty()) {
                // 显示第一个物品的名称，或显示图标
                ItemStack first = filters.get(0);
                gui.drawString(this.font,
                        Component.literal("Filter: ").append(first.getHoverName()),
                        leftPos + 8, topPos + 6, 0xFFFFFF, false);
                // 还可以显示数量
                if (filters.size() > 1) {
                    gui.drawString(this.font,
                            Component.literal(" + " + (filters.size() - 1) + " more"),
                            leftPos + 8 + this.font.width("Filter: " + first.getHoverName()),
                            topPos + 6, 0xAAAAAA, false);
                }
            } else {
                gui.drawString(this.font,
                        Component.translatable("gui.pipe_api.no_filter"),
                        leftPos + 8, topPos + 6, 0xAAAAAA, false);
            }
        }
    }
}
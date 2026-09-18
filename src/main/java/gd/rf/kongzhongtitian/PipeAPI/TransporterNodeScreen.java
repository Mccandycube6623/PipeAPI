package gd.rf.kongzhongtitian.PipeAPI;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class TransporterNodeScreen extends AbstractContainerScreen<TransporterNodeMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PipeAPI.MODID, "textures/screen/xiang.png");

    private static final String[] DIRECTION_NAMES = {"East", "South", "West", "North", "Up", "Down"};
    private static final String[] DIRECTION_SHORT = {"E", "S", "W", "N", "U", "D"};

    public TransporterNodeScreen(TransporterNodeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        gui.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        for (int i = 0; i < 27; i++) {
            int x = leftPos + 8 + (i % 9) * 18;
            int y = topPos + 18 + (i / 9) * 18;

            if (i == 0) {
                continue;
            } else if (i == 1) {
                gui.fill(x, y, x + 16, y + 16, 0x40FFAA00);
                gui.renderOutline(x, y, 16, 16, 0xFFFFAA00);
            } else if (i == 2) {
                gui.fill(x, y, x + 16, y + 16, 0x4022AAFF);
                gui.renderOutline(x, y, 16, 16, 0xFF22AAFF);
            } else if (i >= TransporterNodeBlockEntity.SLOT_DIR_START
                    && i < TransporterNodeBlockEntity.SLOT_DIR_START + TransporterNodeBlockEntity.DIR_SLOT_COUNT) {
                ItemStack torch = this.menu.getSlot(i).getItem();
                if (!torch.isEmpty()) {
                    gui.fill(x, y, x + 16, y + 16, 0x40FF0000);
                    gui.renderOutline(x, y, 16, 16, 0xFFFF0000);
                } else {
                    gui.renderOutline(x, y, 16, 16, 0x80FF0000);
                }
            } else {
                gui.fill(x, y, x + 16, y + 16, 0x80AAAAAA);
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);
        this.renderTooltip(gui, mouseX, mouseY);

        for (int i = 0; i < TransporterNodeBlockEntity.DIR_SLOT_COUNT; i++) {
            int slotIndex = TransporterNodeBlockEntity.SLOT_DIR_START + i;
            int x = leftPos + 8 + (slotIndex % 9) * 18;
            int y = topPos + 18 + (slotIndex / 9) * 18;
            gui.drawString(this.font, DIRECTION_SHORT[i], x + 5, y + 18, 0xFF888888, false);
        }

        ItemStack upgrade = this.menu.getSlot(1).getItem();
        if (!upgrade.isEmpty() && upgrade.getItem() instanceof NodeUpgradeItemFilter) {
            List<ItemStack> filters = NodeUpgradeItemFilter.getFilterItems(upgrade);
            if (!filters.isEmpty()) {
                ItemStack first = filters.get(0);
                gui.drawString(this.font,
                        Component.literal("Filter: ").append(first.getHoverName()),
                        leftPos + 8, topPos + 6, 0xFFFFFF, false);
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

    private int getHoveredDirectionSlot(int mouseX, int mouseY) {
        for (int i = 0; i < TransporterNodeBlockEntity.DIR_SLOT_COUNT; i++) {
            int slotIndex = TransporterNodeBlockEntity.SLOT_DIR_START + i;
            int x = leftPos + 8 + (slotIndex % 9) * 18;
            int y = topPos + 18 + (slotIndex / 9) * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        int dirIdx = getHoveredDirectionSlot(mouseX, mouseY);
        if (dirIdx >= 0) {
            final Direction dir = TransporterNodeBlockEntity.DIRECTION_ORDER[dirIdx];
            final List<Component> lines = new ArrayList<>();
            MutableComponent dirIn=Component.literal("Unloaded");
            if(dirIdx==0){
                dirIn=Component.translatable("gui.pipe_api.east");
            } else if (dirIdx==1) {
                dirIn=Component.translatable("gui.pipe_api.south");
            } else if (dirIdx==2) {
                dirIn=Component.translatable("gui.pipe_api.west");
            } else if (dirIdx==3) {
                dirIn=Component.translatable("gui.pipe_api.north");
            } else if (dirIdx==4) {
                dirIn=Component.translatable("gui.pipe_api.up");
            } else if (dirIdx==5) {
                dirIn=Component.translatable("gui.pipe_api.down");
            }
            lines.add(dirIn);

            this.menu.getAccess().evaluate((level, pos) -> {
                BlockPos neighbor = pos.relative(dir);
                if (!level.isLoaded(neighbor)) {
                    lines.add(Component.literal("§7[unloaded]"));
                } else {
                    BlockState state = level.getBlockState(neighbor);
                    if (state.isAir()) {
                        lines.add(Component.literal("§7[empty]"));
                    } else {
                        lines.add(Component.literal("§f").append(state.getBlock().getName()));
                    }
                }
                return TRUE;
            }, FALSE);

            ItemStack torch = this.menu.getSlot(
                    TransporterNodeBlockEntity.SLOT_DIR_START + dirIdx).getItem();
            if (!torch.isEmpty()) {
                lines.add(Component.translatable("gui.pipe_api.enabled"));
            } else {
                lines.add(Component.translatable("gui.pipe_api.disabled"));
            }

            gui.renderTooltip(this.font, lines, Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
            return;
        }
        super.renderTooltip(gui, mouseX, mouseY);
    }
}
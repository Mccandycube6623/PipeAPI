package gd.rf.kongzhongtitian.PipeAPI;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class TransporterNodeMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    public TransporterNodeMenu(int windowId, Inventory playerInv,
                               IItemHandler nodeInventory,
                               ContainerLevelAccess access) {
        super(DTMenu.TRANSPORTER_NODE_MENU.get(), windowId);
        this.access = access;

        // 容器槽位：0=缓存，1=过滤器升级，2=速度升级，3~26=锁定槽
        for (int i = 0; i < 27; i++) {
            int x = 8 + (i % 9) * 18;
            int y = 18 + (i / 9) * 18;
            if (i == 0) {
                this.addSlot(new SlotItemHandler(nodeInventory, i, x, y));
            } else if (i == 1) {
                this.addSlot(new UpgradeSlot(nodeInventory, i, x, y));
            } else if (i == 2) {
                this.addSlot(new SpeedUpgradeSlot(nodeInventory, i, x, y));
            } else {
                this.addSlot(new LockedSlot(nodeInventory, i, x, y));
            }
        }

        // 玩家背包（27 格） + 快捷栏（9 格）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    // 过滤器升级槽（只能放 NodeUpgradeItemFilter，堆叠上限 1）
    private static class UpgradeSlot extends SlotItemHandler {
        public UpgradeSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof NodeUpgradeItemFilter;
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // 速度升级槽（只能放速度升级物品，允许堆叠多个）
    private static class SpeedUpgradeSlot extends SlotItemHandler {
        public SpeedUpgradeSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return TransporterNodeBlockEntity.isSpeedUpgrade(stack);
        }
        // 不重写 getMaxStackSize，使用默认堆叠上限
    }

    // 锁定槽位（不可放取）
    private static class LockedSlot extends SlotItemHandler {
        public LockedSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }

    // 快速移动物品
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();
            int containerSlots = 27; // 容器槽位总数

            if (index < containerSlots) {
                // 从容器槽移到玩家背包
                if (!this.moveItemStackTo(stackInSlot, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移到容器槽（按类型分配）
                boolean moved = false;
                if (TransporterNodeBlockEntity.isSpeedUpgrade(stackInSlot)) {
                    moved = this.moveItemStackTo(stackInSlot, 2, 3, false); // 只尝试速度槽
                } else if (stackInSlot.getItem() instanceof NodeUpgradeItemFilter) {
                    moved = this.moveItemStackTo(stackInSlot, 1, 2, false); // 只尝试过滤器槽
                } else {
                    moved = this.moveItemStackTo(stackInSlot, 0, 1, false); // 其他物品尝试缓存槽
                }
                if (!moved) return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) ->
                        level.getBlockState(pos).is(DTBlocks.TRANSPORTER_NODE.get())
                                && player.distanceToSqr(pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5) <= 64.0,
                true);
    }
}
package gd.rf.kongzhongtitian.PipeAPI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class NodeUpgradeItemFilter extends Item {
    public NodeUpgradeItemFilter(Properties properties) {
        super(properties);
    }

    // NBT 读写（保持不变）
    public static List<ItemStack> getFilterItems(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Filters")) {
            ListTag listTag = tag.getList("Filters", 10);
            List<ItemStack> list = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag itemTag = listTag.getCompound(i);
                ItemStack item = ItemStack.of(itemTag);
                if (!item.isEmpty()) {
                    list.add(item);
                }
            }
            return list;
        }
        return List.of();
    }

    public static void setFilterItems(ItemStack stack, List<ItemStack> filters) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (ItemStack filter : filters) {
            CompoundTag itemTag = new CompoundTag();
            filter.save(itemTag);
            listTag.add(itemTag);
        }
        tag.put("Filters", listTag);
    }

    // 修正：返回 InteractionResultHolder<ItemStack>
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuProvider menuProvider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.pipe_api.node_upgrade_item_filter");
                }

                @Nullable
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                    return new FilterMenu(id, inv, player.getItemInHand(hand));
                }
            };
            // 使用 NetworkHooks.openScreen 替代 serverPlayer.openMenu
            NetworkHooks.openScreen(serverPlayer, menuProvider, buf -> buf.writeInt(hand.ordinal()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
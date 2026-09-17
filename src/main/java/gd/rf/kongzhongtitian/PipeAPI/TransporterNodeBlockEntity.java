package gd.rf.kongzhongtitian.PipeAPI;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

public class TransporterNodeBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SIZE = 27;
    public static final int SLOT_CACHE = 0;
    public static final int SLOT_FILTER = 1;
    public static final int SLOT_SPEED = 2;

    private final ItemStackHandler itemHandler = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private LazyOptional<IItemHandler> lazyHandler = LazyOptional.empty();

    // 抽取冷却，单位 tick，受速度升级影响
    private double extractCooldown = 10.0;

    private List<BlockPos> outputTargets = new ArrayList<>();
    private Map<BlockPos, Integer> targetDistances = new HashMap<>();
    private int nextTargetIndex = 0;

    // 传输状态（仅表示正在传输的计时和目标，不持有物品）
    private boolean isTransitActive = false;
    private BlockPos currentTarget = null;
    private double currentRemainingTicks = 0.0;

    public TransporterNodeBlockEntity(BlockPos pos, BlockState state) {
        super(DTBlockEntity.TRANSPORTER_NODE.get(), pos, state);
    }

    // 每 tick 执行
    public static void tick(Level level, BlockPos pos, BlockState state, TransporterNodeBlockEntity node) {
        if (level.isClientSide) return;

        // 1. 处理传输延迟（每 tick 都执行）
        node.processTransit();

        // 2. 根据速度升级动态调整抽取频率
        node.extractCooldown -= 1.0;
        if (node.extractCooldown <= 0.0) {
            node.extractCooldown += node.getExtractInterval();
            node.refreshNetwork();
            node.tryExtractOneToCache();
            node.tryStartTransit();
        }
    }

    // 处理传输：减少延迟，到达后尝试将缓存中的所有物品发送到目标容器
    private void processTransit() {
        if (!isTransitActive) return;

        if (currentRemainingTicks > 0.0) {
            currentRemainingTicks -= 1.0;
            return;
        }

        // 延迟结束，检查缓存是否有物品
        ItemStack cacheStack = itemHandler.getStackInSlot(SLOT_CACHE);
        if (cacheStack.isEmpty()) {
            // 缓存为空（可能被外部取走），取消传输
            isTransitActive = false;
            currentTarget = null;
            return;
        }

        // 目标容器有效性检查
        if (currentTarget == null || !level.isLoaded(currentTarget)) {
            // 目标丢失，取消传输，物品保留在缓存中等待下次
            isTransitActive = false;
            currentTarget = null;
            return;
        }

        BlockEntity targetBe = level.getBlockEntity(currentTarget);
        if (targetBe == null) {
            isTransitActive = false;
            currentTarget = null;
            return;
        }

        // 尝试将所有缓存物品插入目标容器
        boolean fullyInserted = targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
            ItemStack leftover = ItemHandlerHelper.insertItem(handler, cacheStack.copy(), false);
            if (leftover.isEmpty()) {
                // 全部插入成功，清空缓存
                itemHandler.setStackInSlot(SLOT_CACHE, ItemStack.EMPTY);
                return true;
            } else {
                // 部分插入，更新缓存为剩余物品
                itemHandler.setStackInSlot(SLOT_CACHE, leftover);
                return false;
            }
        }).orElse(false);

        if (fullyInserted) {
            // 传输完成
            isTransitActive = false;
            currentTarget = null;
        }
        // 若未完全插入，保持传输状态（remainingTicks 仍为 0），下个 tick 继续尝试
    }

    // 刷新网络输出目标及其距离
    private void refreshNetwork() {
        Map<BlockPos, Integer> result = searchNetwork();
        outputTargets = new ArrayList<>(result.keySet());
        targetDistances = result;
        if (outputTargets.isEmpty()) {
            nextTargetIndex = 0;
        }
    }

    // 广度优先搜索网络中的存储方块，返回目标位置到节点距离（管道数）
    private Map<BlockPos, Integer> searchNetwork() {
        Map<BlockPos, Integer> containers = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Map<BlockPos, Integer> distances = new HashMap<>();

        // 从节点相邻的管道开始，距离为 1（经过一个管道）
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(DTBlocks.TRANSPORT_PIPE.get())
                    && TransportPipe.isConnected(neighborState, dir.getOpposite())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                    distances.put(neighbor, 1);
                }
            }
        }

        int maxSearch = 200;
        while (!queue.isEmpty() && maxSearch-- > 0) {
            BlockPos current = queue.poll();
            int currentDist = distances.get(current);
            BlockState currentState = level.getBlockState(current);
            for (Direction dir : Direction.values()) {
                if (!TransportPipe.isConnected(currentState, dir)) continue;
                BlockPos adjacent = current.relative(dir);
                if (adjacent.equals(worldPosition)) continue;

                BlockState adjState = level.getBlockState(adjacent);
                if (adjState.is(DTBlocks.TRANSPORT_PIPE.get())
                        && TransportPipe.isConnected(adjState, dir.getOpposite())) {
                    if (visited.add(adjacent)) {
                        queue.add(adjacent);
                        distances.put(adjacent, currentDist + 1);
                    }
                } else if (level.getBlockEntity(adjacent) != null) {
                    BlockEntity be = level.getBlockEntity(adjacent);
                    if (be != null && !containers.containsKey(adjacent)) {
                        LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite());
                        if (cap.isPresent()) {
                            containers.put(adjacent, currentDist);
                        }
                    }
                }
            }
        }
        return containers;
    }

    // 获取过滤器物品列表
    private List<ItemStack> getFilterItems() {
        ItemStack upgrade = itemHandler.getStackInSlot(SLOT_FILTER);
        if (!upgrade.isEmpty() && upgrade.getItem() instanceof NodeUpgradeItemFilter) {
            return NodeUpgradeItemFilter.getFilterItems(upgrade);
        }
        return List.of();
    }

    // 检查物品是否为速度升级
    public static boolean isSpeedUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) return false;
        String id = rl.toString();
        return "exura:upgrade_speed".equals(id)
                || "exura:upgrade_speed_enchanted".equals(id)
                || "exura:upgrade_speed_super".equals(id);
    }

    // 获取速度倍率（多个升级叠乘）
    private double getSpeedMultiplier() {
        ItemStack stack = itemHandler.getStackInSlot(SLOT_SPEED);
        if (stack.isEmpty() || !isSpeedUpgrade(stack)) return 1.0;

        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (rl == null) return 1.0;
        String id = rl.toString();

        double perItem;
        switch (id) {
            case "exura:upgrade_speed":
                perItem = 0.5;
                break;
            case "exura:upgrade_speed_enchanted":
                perItem = 0.49;
                break;
            case "exura:upgrade_speed_super":
                perItem = 0.48;
                break;
            default:
                return 1.0;
        }

        double multiplier = 1.0;
        int count = stack.getCount();
        for (int i = 0; i < count; i++) {
            multiplier *= perItem;
        }
        return multiplier;
    }

    // 获取每次抽取的间隔 tick（基础 10 tick × 速度倍率）
    private double getExtractInterval() {
        return 10.0 * getSpeedMultiplier();
    }

    // 尝试从相邻容器抽取一个物品到缓存槽（插槽 0）
    private void tryExtractOneToCache() {
        List<ItemStack> filters = getFilterItems();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = worldPosition.relative(dir);
            if (!level.isLoaded(neighbor)) continue;
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be == null) continue;
            if (level.getBlockState(neighbor).is(DTBlocks.TRANSPORTER_NODE.get())
                    || level.getBlockState(neighbor).is(DTBlocks.TRANSPORT_PIPE.get())) continue;

            LazyOptional<IItemHandler> capOptional = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite());
            if (capOptional.isPresent()) {
                IItemHandler handler = capOptional.orElse(null);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stackInSlot = handler.getStackInSlot(slot);
                    if (stackInSlot.isEmpty()) continue;
                    // 过滤器检查
                    if (!filters.isEmpty()) {
                        boolean matches = false;
                        for (ItemStack filter : filters) {
                            if (ItemStack.isSameItem(stackInSlot, filter)) {
                                matches = true;
                                break;
                            }
                        }
                        if (!matches) continue;
                    }
                    // 尝试提取 1 个物品
                    ItemStack extracted = handler.extractItem(slot, 1, true);
                    if (extracted.isEmpty()) continue;
                    extracted = handler.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) {
                        // 插入缓存槽（容量足够，不会失败）
                        itemHandler.insertItem(SLOT_CACHE, extracted, false);
                        return; // 成功抽取一个，结束本次抽取
                    }
                }
            }
        }
    }

    // 尝试启动新的传输计时：缓存非空且当前无活动传输时启动
    private void tryStartTransit() {
        if (isTransitActive) return;            // 已有传输在途
        if (outputTargets.isEmpty()) return;    // 没有目标

        ItemStack cacheStack = itemHandler.getStackInSlot(SLOT_CACHE);
        if (cacheStack.isEmpty()) return;       // 缓存为空，不启动

        // 选择目标（轮询）
        BlockPos target = outputTargets.get(nextTargetIndex % outputTargets.size());
        nextTargetIndex = (nextTargetIndex + 1) % outputTargets.size();

        int distance = targetDistances.getOrDefault(target, 0);
        currentRemainingTicks = distance * 10.0 * getSpeedMultiplier(); // 应用速度倍率

        // 启动传输计时，不清空缓存
        isTransitActive = true;
        currentTarget = target;
    }

    // 方块被破坏时掉落所有物品（缓存、过滤器和速度升级）
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        // 传输中的物品都还在缓存中，无需额外处理
        isTransitActive = false;
        currentTarget = null;
        currentRemainingTicks = 0.0;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyHandler.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyHandler = LazyOptional.of(() -> itemHandler);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putBoolean("isTransitActive", isTransitActive);
        if (currentTarget != null) {
            tag.putLong("currentTarget", currentTarget.asLong());
        }
        tag.putDouble("currentRemainingTicks", currentRemainingTicks);
        tag.putDouble("extractCooldown", extractCooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("inventory"));
        isTransitActive = tag.getBoolean("isTransitActive");
        if (tag.contains("currentTarget")) {
            currentTarget = BlockPos.of(tag.getLong("currentTarget"));
        }
        currentRemainingTicks = tag.getDouble("currentRemainingTicks");
        // 兼容旧存档：如果不存在 extractCooldown，则使用默认值 10.0
        extractCooldown = tag.contains("extractCooldown") ? tag.getDouble("extractCooldown") : 10.0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.exura.transporter_node");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TransporterNodeMenu(id, inv, this.itemHandler,
                ContainerLevelAccess.create(level, worldPosition));
    }
}
package gd.rf.kongzhongtitian.PipeAPI;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class DTCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PipeAPI.MODID);

    public static final RegistryObject<CreativeModeTab> DUCKTECH_TAB = CREATIVE_TABS.register("ducktech_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("item_group." + PipeAPI.MODID + ".example"))
                    .icon(() -> new ItemStack(DTBlocks.TRANSPORT_PIPE.get()))
                    .displayItems((params, output) -> {
                        DTItems.ITEMS.getEntries().forEach(entry -> entry.ifPresent(item -> output.accept(item.getDefaultInstance())));
                        DTBlocks.BLOCKS.getEntries().forEach(blockEntry -> {
                            blockEntry.ifPresent(block -> {
                                Item item = block.asItem();
                                if (item != Items.AIR) {
                                    output.accept(item);
                                }
                            });
                        });
                    })
                    .build()
    );

    private static void addItemsFromRegistry(CreativeModeTab.Output output,
                                             Iterable<RegistryObject<Item>> registry) {
        for (RegistryObject<Item> entry : registry) {
            entry.ifPresent(item -> output.accept(item.getDefaultInstance()));
        }
    }
}
package com.vomiter.farmersbp;

import com.vomiter.farmersbp.cutting.CuttingBoardContainer;
import com.vomiter.farmersbp.cutting.CuttingBoardUpgradeItem;
import com.vomiter.farmersbp.cutting.CuttingBoardWrapper;
import com.vomiter.farmersbp.network.ModNetwork;
import com.vomiter.farmersbp.pot.CookingPotContainer;
import com.vomiter.farmersbp.pot.CookingPotUpgradeItem;
import com.vomiter.farmersbp.pot.CookingPotWrapper;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.codec.ByteBufCodecs;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

@Mod(FarmerSBP.MOD_ID)
public final class FarmerSBP {
    public static final String MOD_ID = "farmersbp";
    public static ResourceLocation modLoc(String path){return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);};


    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CUTTING_INVENTORY =
            itemContents("cutting_inventory");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> POT_INVENTORY =
            itemContents("pot_inventory");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> POT_SETTINGS =
            itemContents("pot_settings");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> POT_OUTPUT_FLUSH =
            DATA_COMPONENTS.register("pot_output_flush", () -> new DataComponentType.Builder<Boolean>()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POT_COOK_TIME =
            DATA_COMPONENTS.register("pot_cook_time", () -> new DataComponentType.Builder<Integer>()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POT_TOTAL_COOK_TIME =
            DATA_COMPONENTS.register("pot_total_cook_time", () -> new DataComponentType.Builder<Integer>()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> POT_RECIPE =
            DATA_COMPONENTS.register("pot_recipe", () -> new DataComponentType.Builder<ResourceLocation>()
                    .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());

    private static DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> itemContents(String name) {
        return DATA_COMPONENTS.register(name, () -> new DataComponentType.Builder<ItemContainerContents>()
                .persistent(ItemContainerContents.CODEC)
                .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                .cacheEncoding()
                .build());
    }

    // Allow one of each station per backpack, independently of other upgrade groups.
    public static final IUpgradeCountLimitConfig LIMITS = new IUpgradeCountLimitConfig() {
        @Override
        public int getMaxUpgradesPerStorage(@NotNull String storageType, ResourceLocation upgradeName) {
            return "backpack".equals(storageType) ? 1 : 0;
        }

        @Override
        public int getMaxUpgradesInGroupPerStorage(@NotNull String storageType, @NotNull UpgradeGroup group) {
            return Integer.MAX_VALUE;
        }
    };

    public static final DeferredHolder<Item, CuttingBoardUpgradeItem> CUTTING_BOARD =
            ITEMS.register("cutting_board_upgrade", () -> new CuttingBoardUpgradeItem(LIMITS));
    public static final DeferredHolder<Item, CuttingBoardUpgradeItem> AUTOMATIC_CUTTING_BOARD =
            ITEMS.register("automatic_cutting_board_upgrade", () -> new CuttingBoardUpgradeItem(LIMITS, true));
    public static final DeferredHolder<Item, CookingPotUpgradeItem> COOKING_POT =
            ITEMS.register("cooking_pot_upgrade", () -> new CookingPotUpgradeItem(LIMITS));

    public static final DeferredHolder<Item, CookingPotUpgradeItem> AUTOMATIC_COOKING_POT =
            ITEMS.register("automatic_cooking_pot_upgrade", () -> new CookingPotUpgradeItem(LIMITS, true));

    public static final UpgradeContainerType<CuttingBoardWrapper, CuttingBoardContainer> CUTTING_TYPE =
            new UpgradeContainerType<>(CuttingBoardContainer::new);
    public static final UpgradeContainerType<CookingPotWrapper, CookingPotContainer> POT_TYPE =
            new UpgradeContainerType<>(CookingPotContainer::new);

    public FarmerSBP(IEventBus bus, Dist dist) {
        ITEMS.register(bus);
        DATA_COMPONENTS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModNetwork::register);
        bus.addListener(this::addCreativeTabContents);
        if (dist == Dist.CLIENT) {
            ClientRegistration.register(bus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            UpgradeContainerRegistry.register(CUTTING_BOARD.getId(), CUTTING_TYPE);
            UpgradeContainerRegistry.register(AUTOMATIC_CUTTING_BOARD.getId(), CUTTING_TYPE);
            UpgradeContainerRegistry.register(COOKING_POT.getId(), POT_TYPE);
            UpgradeContainerRegistry.register(AUTOMATIC_COOKING_POT.getId(), POT_TYPE);
        });
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ((DeferredHolder) net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.CREATIVE_TAB).getKey()) {
            event.accept(CUTTING_BOARD.get());
            event.accept(AUTOMATIC_CUTTING_BOARD.get());
            event.accept(COOKING_POT.get());
            event.accept(AUTOMATIC_COOKING_POT.get());
        }
    }

}

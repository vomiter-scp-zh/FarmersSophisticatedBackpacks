package com.vomiter.farmersbp;

import com.vomiter.farmersbp.cutting.CuttingBoardContainer;
import com.vomiter.farmersbp.cutting.CuttingBoardUpgradeItem;
import com.vomiter.farmersbp.cutting.CuttingBoardWrapper;
import com.vomiter.farmersbp.network.ModNetwork;
import com.vomiter.farmersbp.pot.CookingPotContainer;
import com.vomiter.farmersbp.pot.CookingPotUpgradeItem;
import com.vomiter.farmersbp.pot.CookingPotWrapper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerRegistry;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

@Mod(FarmerSBP.MOD_ID)
public final class FarmerSBP {
    public static final String MOD_ID = "farmersbp";
    public static ResourceLocation modLoc(String path){return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);};


    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

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

    public static final RegistryObject<CuttingBoardUpgradeItem> CUTTING_BOARD =
            ITEMS.register("cutting_board_upgrade", () -> new CuttingBoardUpgradeItem(LIMITS));
    public static final RegistryObject<CuttingBoardUpgradeItem> AUTOMATIC_CUTTING_BOARD =
            ITEMS.register("automatic_cutting_board_upgrade", () -> new CuttingBoardUpgradeItem(LIMITS, true));
    public static final RegistryObject<CookingPotUpgradeItem> COOKING_POT =
            ITEMS.register("cooking_pot_upgrade", () -> new CookingPotUpgradeItem(LIMITS));

    public static final RegistryObject<CookingPotUpgradeItem> AUTOMATIC_COOKING_POT =
            ITEMS.register("automatic_cooking_pot_upgrade", () -> new CookingPotUpgradeItem(LIMITS, true));

    public static final UpgradeContainerType<CuttingBoardWrapper, CuttingBoardContainer> CUTTING_TYPE =
            new UpgradeContainerType<>(CuttingBoardContainer::new);
    public static final UpgradeContainerType<CookingPotWrapper, CookingPotContainer> POT_TYPE =
            new UpgradeContainerType<>(CookingPotContainer::new);

    public FarmerSBP() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModNetwork::onCommonSetup);
        bus.addListener(this::addCreativeTabContents);
        if (FMLEnvironment.dist == Dist.CLIENT) {
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
        if (event.getTabKey() == net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems.CREATIVE_TAB.getKey()) {
            event.accept(CUTTING_BOARD);
            event.accept(AUTOMATIC_CUTTING_BOARD);
            event.accept(COOKING_POT);
            event.accept(AUTOMATIC_COOKING_POT);
        }
    }

}

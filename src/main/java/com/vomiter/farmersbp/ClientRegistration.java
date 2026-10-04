package com.vomiter.farmersbp;

import com.vomiter.farmersbp.cutting.CuttingBoardTab;
import com.vomiter.farmersbp.pot.CookingPotTab;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeGuiManager;

public final class ClientRegistration {
    private ClientRegistration() {}

    public static void register(IEventBus bus) {
        bus.addListener(ClientRegistration::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            UpgradeGuiManager.registerTab(FarmerSBP.CUTTING_TYPE, CuttingBoardTab::new);
            UpgradeGuiManager.registerTab(FarmerSBP.POT_TYPE, CookingPotTab::new);
        });
    }
}

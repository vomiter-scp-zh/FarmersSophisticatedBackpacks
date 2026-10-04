package com.vomiter.farmersbp.mixin;

import com.vomiter.farmersbp.pot.CookingPotWrapper;
import net.minecraft.client.gui.GuiGraphics;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StorageScreenBase.class)
public class StorageScreenBaseMixin {
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void fsbp$renderTooltip(GuiGraphics guiGraphics, int x, int y, CallbackInfo ci){
        if(!CookingPotWrapper.isShouldRenderOriginalTooltip()){
            ci.cancel();
        }
    }

}

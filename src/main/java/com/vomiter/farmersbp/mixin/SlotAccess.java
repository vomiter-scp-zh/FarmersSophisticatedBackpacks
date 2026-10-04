package com.vomiter.farmersbp.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotAccess {
    @Accessor("x")
    void setX(int i);

    @Accessor("y")
    void setY(int i);

}

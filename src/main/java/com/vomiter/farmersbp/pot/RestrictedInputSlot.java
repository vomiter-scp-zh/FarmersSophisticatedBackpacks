package com.vomiter.farmersbp.pot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

public class RestrictedInputSlot extends SlotSuppliedHandler {
    private Ingredient ingredient = Ingredient.EMPTY;

    public RestrictedInputSlot(Supplier<IItemHandler> handler, int slot, int x, int y) {
        super(handler, slot, x, y);
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = Objects.requireNonNull(ingredient);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return ingredient.test(stack) && super.mayPlace(stack);
    }
}

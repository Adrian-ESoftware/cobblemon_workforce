package com.nbp.cobblemon_workforce.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
    @Accessor("litTime")
    int cobblemon_workforce$getLitTime();

    @Accessor("litTime")
    void cobblemon_workforce$setLitTime(int litTime);

    @Accessor("litDuration")
    void cobblemon_workforce$setLitDuration(int litDuration);

    @Accessor("cookingProgress")
    int cobblemon_workforce$getCookingProgress();

    @Accessor("cookingProgress")
    void cobblemon_workforce$setCookingProgress(int cookingProgress);

    @Accessor("cookingTotalTime")
    int cobblemon_workforce$getCookingTotalTime();
}

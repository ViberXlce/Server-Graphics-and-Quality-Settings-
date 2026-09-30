package dev.sgqs.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets us set an option value without range validation (needed for Fullbright gamma). */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
	@Accessor("value")
	void sgqs$setRaw(T value);
}

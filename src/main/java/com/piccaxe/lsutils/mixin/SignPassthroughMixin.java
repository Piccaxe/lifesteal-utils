package com.piccaxe.lsutils.mixin;

import com.piccaxe.lsutils.config.Config;
import com.piccaxe.lsutils.config.ConfigManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes signs fully click-through while Anti-Sign is on: their outline shape becomes empty, so the
 * crosshair raycast passes straight through to the block behind. You can't select or open the sign,
 * and right/left-clicks land on whatever is behind it. Covers all sign types (standing/wall/hanging)
 * via the shared {@code AbstractBlockState.getOutlineShape} that delegates to the block.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class SignPassthroughMixin {
	@Inject(
		method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void piccaxelsutils$signPassthrough(BlockView world, BlockPos pos, ShapeContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		Config cfg = ConfigManager.get();
		if (cfg.masterEnabled && cfg.antiSign && ((BlockState) (Object) this).getBlock() instanceof AbstractSignBlock) {
			cir.setReturnValue(VoxelShapes.empty());
		}
	}
}

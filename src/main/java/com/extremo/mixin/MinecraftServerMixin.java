package com.extremo.mixin;

import com.extremo.WorldResetter;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	/**
	 * Al regenerar el mundo en caliente volvemos a llamar a createLevels(). El marcador ya
	 * esta cargado en memoria, asi que evitamos que se vuelva a cargar (lanzaria una
	 * excepcion por objetivos duplicados).
	 */
	@WrapWithCondition(
		method = "createLevels",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerScoreboard;load(Lnet/minecraft/world/scores/ScoreboardSaveData$Packed;)V")
	)
	private boolean extremo$skipScoreboardReload(ServerScoreboard scoreboard, ScoreboardSaveData.Packed data) {
		return !WorldResetter.isRegenerating();
	}
}

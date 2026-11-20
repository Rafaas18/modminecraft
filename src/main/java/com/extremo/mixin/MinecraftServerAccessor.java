package com.extremo.mixin;

import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("levels")
	Map<ResourceKey<Level>, ServerLevel> extremo$getLevels();

	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess extremo$getStorageSource();

	@Invoker("createLevels")
	void extremo$createLevels();

	@Invoker("updateEffectiveRespawnData")
	void extremo$updateEffectiveRespawnData();
}

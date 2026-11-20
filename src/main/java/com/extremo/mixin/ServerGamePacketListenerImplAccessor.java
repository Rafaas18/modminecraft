package com.extremo.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
	/** Lo mismo que hace vanilla tras pulsar "Reaparecer": el cliente vuelve a estar cargando. */
	@Invoker("restartClientLoadTimerAfterRespawn")
	void extremo$restartClientLoadTimerAfterRespawn();
}

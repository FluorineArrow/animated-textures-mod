package com.animatedtextures.mixin;

import com.animatedtextures.util.AnimatedTextureReloadAttempt;
import com.animatedtextures.util.AnimatedTextureReloadCoordinator;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReload;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.SimpleResourceReload;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Associates all apply work and overall completion with one resource reload attempt.
 */
@Mixin(ReloadableResourceManagerImpl.class)
public abstract class ReloadableResourceManagerMixin {

    @Redirect(
            method = "reload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/SimpleResourceReload;start(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/resource/ResourceReload;"
            )
    )
    private ResourceReload animatedTextures$trackReload(
            ResourceManager manager,
            List<ResourceReloader> reloaders,
            Executor prepareExecutor,
            Executor applyExecutor,
            CompletableFuture<Unit> initialStage,
            boolean profiled) {
        AnimatedTextureReloadAttempt attempt = AnimatedTextureReloadCoordinator.begin();
        ResourceReload reload = SimpleResourceReload.start(manager, reloaders, prepareExecutor,
                AnimatedTextureReloadCoordinator.wrapApplyExecutor(attempt, applyExecutor), initialStage, profiled);
        reload.whenComplete().whenComplete((ignored, failure) ->
                AnimatedTextureReloadCoordinator.complete(attempt, failure));
        return reload;
    }
}

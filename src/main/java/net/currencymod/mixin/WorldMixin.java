package net.currencymod.mixin;

import net.currencymod.shop.ShopAccess;
import net.currencymod.shop.ShopManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin implements ShopAccess {
    
    @Shadow public abstract RegistryKey<World> getRegistryKey();
    
    @Unique
    private ShopManager shopManager;
    
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        // Only initialize for server worlds
        if ((Object) this instanceof ServerWorld) {
            this.shopManager = new ShopManager();
        }
    }
    
    @Override
    public ShopManager getShopManager() {
        return shopManager;
    }
} 
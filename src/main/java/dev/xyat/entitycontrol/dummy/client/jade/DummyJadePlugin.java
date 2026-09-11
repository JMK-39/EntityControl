package dev.xyat.entitycontrol.dummy.client.jade;

import dev.xyat.entitycontrol.dummy.entity.DummyEntityTest;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 独立的 Jade 插件入口，专门处理假人模块
 */
@SuppressWarnings("unused")
@WailaPlugin
public class DummyJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 仅在客户端注册实体组件
        // 只有当准星指向 DummyEntityTest 时才会触发此 DummyProvider
        registration.registerEntityComponent(DummyProvider.INSTANCE, DummyEntityTest.class);
    }
}

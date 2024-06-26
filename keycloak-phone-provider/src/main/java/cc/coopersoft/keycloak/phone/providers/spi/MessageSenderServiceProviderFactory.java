package cc.coopersoft.keycloak.phone.providers.spi;

import org.keycloak.Config;
import org.keycloak.provider.ProviderFactory;

public interface MessageSenderServiceProviderFactory extends ProviderFactory<MessageSenderService> {
    void init(Config.Scope config);
}

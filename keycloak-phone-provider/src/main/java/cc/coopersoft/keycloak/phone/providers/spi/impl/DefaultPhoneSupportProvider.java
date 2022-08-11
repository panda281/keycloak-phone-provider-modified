package cc.coopersoft.keycloak.phone.providers.spi.impl;

import cc.coopersoft.keycloak.phone.providers.spi.PhoneSupportProvider;
import cc.coopersoft.keycloak.phone.providers.spi.PhoneVerificationCodeProvider;
import cc.coopersoft.keycloak.phone.providers.constants.TokenCodeType;
import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.representations.TokenCodeRepresentation;
import cc.coopersoft.keycloak.phone.providers.spi.MessageSenderService;
import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.ServiceUnavailableException;
import java.time.Instant;

public class DefaultPhoneSupportProvider implements PhoneSupportProvider {

    private static final Logger logger = Logger.getLogger(DefaultPhoneSupportProvider.class);
    private final KeycloakSession session;
    private final String service;
    private final int tokenExpiresIn;
    private final int hourMaximum;

    private final Scope config;

    DefaultPhoneSupportProvider(KeycloakSession session, Scope config) {
        this.session = session;
        this.config = config;
        logger.info("phone message service implement server name:" + config.get("service"));

        this.service = session.listProviderIds(MessageSenderService.class)
                .stream().filter(s -> s.equals(config.get("service")))
                .findFirst().orElse(
                        session.listProviderIds(MessageSenderService.class)
                                .stream().findFirst().orElse("")
                );
        this.tokenExpiresIn = config.getInt("tokenExpiresIn", 60);
        this.hourMaximum = config.getInt("hourMaximum",3);
    }



    @Override
    public void close() {
    }


    private PhoneVerificationCodeProvider getTokenCodeService() {
        return session.getProvider(PhoneVerificationCodeProvider.class);
    }

    @Override
    public boolean isDuplicatePhoneAllowed(String realm) {
        return config.getBoolean(realm + "-duplicate-phone-allowed",false);
    }

    @Override
    public int sendTokenCode(String phoneNumber,TokenCodeType type, String kind){
        logger.info("send code to:" + phoneNumber );

        if (getTokenCodeService().isAbusing(phoneNumber, type,hourMaximum)) {
            throw new ForbiddenException("You requested the maximum number of messages the last hour");
        }

        TokenCodeRepresentation ongoing = getTokenCodeService().ongoingProcess(phoneNumber, type);
        if (ongoing != null) {
            logger.info(String.format("No need of sending a new %s code for %s",type.getLabel(), phoneNumber));
            return (int) (ongoing.getExpiresAt().getTime() - Instant.now().toEpochMilli()) / 1000;
        }

        TokenCodeRepresentation token = TokenCodeRepresentation.forPhoneNumber(phoneNumber);

        try {
            session.getProvider(MessageSenderService.class, service).sendSmsMessage(type,phoneNumber,token.getCode(),tokenExpiresIn,kind);
            getTokenCodeService().persistCode(token, type, tokenExpiresIn);

            logger.info(String.format("Sent %s code to %s over %s",type.getLabel(), phoneNumber, service));

        } catch (MessageSendException e) {

            logger.error(String.format("Message sending to %s failed with %s: %s",
                    phoneNumber, e.getErrorCode(), e.getErrorMessage()));
            throw new ServiceUnavailableException("Internal server error");
        }

        return tokenExpiresIn;
    }

}
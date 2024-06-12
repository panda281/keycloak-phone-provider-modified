package cc.coopersoft.keycloak.phone.providers.sender;

import cc.coopersoft.keycloak.phone.providers.exception.MessageSendException;
import cc.coopersoft.keycloak.phone.providers.spi.FullSmsSenderAbstractService;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jboss.logging.Logger;
import org.keycloak.Config;

public class ExternalSmsSenderServiceProvider extends FullSmsSenderAbstractService {

    private static final Logger logger = Logger.getLogger(ExternalSmsSenderServiceProvider.class);
    private final String gatewayUrl;
    private final String id;
    private final String domain;

    ExternalSmsSenderServiceProvider(Config.Scope config, String realmDisplay) {
        super(realmDisplay);
        this.gatewayUrl = "https://sms.yegara.com/api3/send";
        this.id = config.get("id");
        this.domain = config.get("domain");
    }

    @Override
    public void sendMessage(String phoneNumber, String message) throws MessageSendException {
        try {
            JSONObject body = new JSONObject();
            body.put("id", this.id);
            body.put("domain", this.domain);
            body.put("to", phoneNumber);
            body.put("otp", message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Message send failed! Response code: " + response.statusCode());
                throw new MessageSendException(response.statusCode(), "Error", response.body());
            }

        } catch (Exception e) {
            logger.error("Exception while sending message", e);
            throw new MessageSendException(-1, "Exception", e.getMessage());
        }
    }

    @Override
    public void close() {
    }
}

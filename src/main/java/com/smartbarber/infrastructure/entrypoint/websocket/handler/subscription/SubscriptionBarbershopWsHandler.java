package com.smartbarber.infrastructure.entrypoint.websocket.handler.subscription;
import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.application.usecase.subscriptionbarber.FindSubscriptionBarberUC;
import com.smartbarber.application.usecase.transaction.WaitMessageResponseUC;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.infrastructure.entrypoint.utils.commons.ObjectMessageMapper;
import com.smartbarber.infrastructure.entrypoint.utils.commons.WebSocketConnectionManager;
import com.smartbarber.infrastructure.entrypoint.websocket.dto.event.MessageRsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionBarbershopWsHandler implements WebSocketHandler {
    private final WebSocketConnectionManager connectionManager;
    private final FindSubscriptionBarberUC findSubscriptionBarberUC;
    private final ObjectMessageMapper objectMapper;
    private final WaitMessageResponseUC messageResponseUC;
    private final AuthenticationUC authenticationUC;
    private final String TYPE = "Bearer";
    @Override
    public List<String> getSubProtocols() {
        return WebSocketHandler.super.getSubProtocols();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        log.info("ENTREEEEEE WSSSSSS");

        UUID orderId = extractOrderId(session);
        String token = extractIdToken(session);
        connectionManager.registerSession(orderId, session);
        log.info("JWTAAAA: {}", token);
       return  authenticationUC.authorizationSession(TYPE + " " + token)
               .then(
               session.send(
               messageResponseUC.waitForResponseAndSendMessage(orderId)
                       .doOnNext(transactionComplete -> log.info("SubscriptionBarbershopWsHandler.handle: transactionComplete: {}", transactionComplete))
                       .flatMap(transactionComplete ->
                               findSubscriptionBarberUC.findSubscriptionById(transactionComplete.getSubscriptionBarberId())
                                       .map(subscriptionBarbershop -> MessageRsDto.<SubscriptionBarbershop>builder()
                                               .type("SUBSCRIPTION_PROCESS")
                                               .data(subscriptionBarbershop)
                                               .build()
                                       )
                       ).map(message -> session.textMessage(objectMapper.writeValueAsString(message)))
       ));
    }

    private UUID extractOrderId(WebSocketSession session) {
        String orderId = UriComponentsBuilder
                .fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("orderId");
        assert orderId != null;
        return UUID.fromString(orderId);
    }

    private String extractIdToken(WebSocketSession session) {
        String idToken = UriComponentsBuilder
                .fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("token");
        assert idToken != null;
        return idToken;
    }
}




package com.cloudnative.ms_notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cloudnative.ms_notifications.DTO.Message;
import com.cloudnative.ms_notifications.Service.NotificationStreams;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class NotificationStreamTests {

    static final String URL = "/api/v1/notifications/stream";

    @Autowired
    MockMvc mvc;

    @Autowired
    NotificationStreams streams;

    @MockitoBean
    JwtDecoder decoder;

    @Test
    void streamRequiresToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void userOnlyReceivesOwnNotifications() throws Exception {
        when(decoder.decode("alice")).thenReturn(Jwt.withTokenValue("alice").header("alg", "RS256")
                .subject("alice").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        var result = mvc.perform(get(URL).header("Authorization", "Bearer alice"))
                .andExpect(request().asyncStarted()).andReturn();

        streams.send("bob", message("bob", "Pedido #1 recibido"));
        streams.send("alice", message("alice", "Pedido #2 recibido"));

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:notification").contains("Pedido #2 recibido")
                .doesNotContain("Pedido #1 recibido");
    }

    @Test
    void streamClosesCleanlyOnTimeout() throws Exception {
        when(decoder.decode("alice")).thenReturn(Jwt.withTokenValue("alice").header("alg", "RS256")
                .subject("alice").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        MvcResult result = mvc.perform(get(URL).header("Authorization", "Bearer alice"))
                .andExpect(request().asyncStarted()).andReturn();

        MockAsyncContext ctx = (MockAsyncContext) result.getRequest().getAsyncContext();
        for (AsyncListener listener : ctx.getListeners()) {
            listener.onTimeout(new AsyncEvent(ctx));
        }

        // Si el timeout termina en 503, API Gateway lo reenvia y el frontend lo ve como error.
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void streamClosesRightAfterNotification() throws Exception {
        when(decoder.decode("dave")).thenReturn(Jwt.withTokenValue("dave").header("alg", "RS256")
                .subject("dave").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        MvcResult result = mvc.perform(get(URL).header("Authorization", "Bearer dave"))
                .andExpect(request().asyncStarted()).andReturn();

        streams.send("dave", message("dave", "Pedido #3 recibido"));

        // Sin timeout: la respuesta ya termino y API Gateway puede entregarla.
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains("Pedido #3 recibido");
    }

    @Test
    void notificationSentWhileReconnectingIsDeliveredOnNextStream() throws Exception {
        when(decoder.decode("carol")).thenReturn(Jwt.withTokenValue("carol").header("alg", "RS256")
                .subject("carol").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        streams.send("carol", message("carol", "Pedido #4 recibido"));

        MvcResult result = mvc.perform(get(URL).header("Authorization", "Bearer carol"))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains("Pedido #4 recibido");
    }

    @Test
    void messageWithoutUserIsIgnored() {
        streams.send(null, message(null, "sin usuario"));
    }

    static Message message(String user, String content) {
        Message m = new Message();
        m.setUser(user);
        m.setContent(content);
        return m;
    }
}

package com.cloudnative.ms_notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

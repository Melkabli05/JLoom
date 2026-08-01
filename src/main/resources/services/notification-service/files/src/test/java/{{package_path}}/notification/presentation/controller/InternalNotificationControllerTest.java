package {{package}}.notification.presentation.controller;

import {{package}}.notification.application.service.NotificationService;
import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalNotificationController.class)
@TestPropertySource(properties = "internal.service-key=test-internal-key")
class InternalNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static String requestBody() {
        return """
                {"recipientEmail":"alice@example.com","subject":"hello","body":"world"}""";
    }

    @Test
    void correctKeyReturns202WithAssignedIdAndStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.submit(anyString(), anyString(), anyString(), any())).thenReturn(
                new Notification(id, "alice@example.com", "hello", "world", NotificationChannel.EMAIL,
                        NotificationStatus.PENDING, null, 0, null, Instant.now(), null));

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Service-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Service-Key", "not-the-right-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRecipientEmailIsRejected() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Service-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"not-an-email","subject":"hello","body":"world"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankSubjectIsRejected() throws Exception {
        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Service-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientEmail":"alice@example.com","subject":"","body":"world"}"""))
                .andExpect(status().isBadRequest());
    }
}

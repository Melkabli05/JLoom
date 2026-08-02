package {{package}}.notification.presentation.controller;

import {{package}}.infrastructure.configuration.SecurityConfig;
import {{package}}.notification.application.service.NotificationService;
import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import {{package}}.notification.presentation.mapper.NotificationMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NotificationMapperImpl.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class NotificationControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationService notificationService;
    @Test
    void anonymousListRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void listRequiresAdmin() throws Exception {
        when(notificationService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/notifications").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
    }
    @Test
    void listAsAdminOmitsBodyFromEachEntry() throws Exception {
        Notification notification = new Notification(UUID.randomUUID(), "alice@example.com", "subject", "sensitive body",
                NotificationChannel.EMAIL, NotificationStatus.SENT, null, 1, null, Instant.now(), Instant.now());
        when(notificationService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(notification)));
        mockMvc.perform(get("/notifications")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipientEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist());
    }
    @Test
    void getByIdRequiresAdmin() throws Exception {
        mockMvc.perform(get("/notifications/{id}", UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
    }
    @Test
    void getByIdAsAdminIncludesBody() throws Exception {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification(id, "alice@example.com", "subject", "sensitive body",
                NotificationChannel.EMAIL, NotificationStatus.SENT, null, 1, null, Instant.now(), Instant.now());
        when(notificationService.getById(id)).thenReturn(notification);
        mockMvc.perform(get("/notifications/{id}", id)
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("sensitive body"));
    }
}

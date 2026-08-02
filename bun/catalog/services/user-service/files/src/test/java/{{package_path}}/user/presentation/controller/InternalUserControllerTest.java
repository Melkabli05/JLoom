package {{package}}.user.presentation.controller;

import {{package}}.user.application.service.UserService;
import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import {{package}}.user.presentation.dto.VerifyCredentialsRequest;
import {{package}}.user.presentation.mapper.UserMapperImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(InternalUserController.class)
@Import(UserMapperImpl.class)
@TestPropertySource(properties = "internal.service-key=test-internal-key")
class InternalUserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserService userService;
    @Autowired
    private ObjectMapper objectMapper;
    @Test
    void correctKeyAndCredentialsReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.verifyCredentials(anyString(), anyString())).thenReturn(
                Optional.of(new User(id, "alice@example.com", "hash", Instant.now(), null, Role.USER, true)));
        mockMvc.perform(post("/internal/users/verify-credentials")
                        .header("X-Internal-Service-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCredentialsRequest("alice@example.com", "supersecret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("USER"));
    }
    @Test
    void missingKeyIsRejected() throws Exception {
        mockMvc.perform(post("/internal/users/verify-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCredentialsRequest("alice@example.com", "supersecret123"))))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void wrongKeyIsRejected() throws Exception {
        mockMvc.perform(post("/internal/users/verify-credentials")
                        .header("X-Internal-Service-Key", "not-the-right-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCredentialsRequest("alice@example.com", "supersecret123"))))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void correctKeyButWrongCredentialsReturns401() throws Exception {
        when(userService.verifyCredentials(anyString(), anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/internal/users/verify-credentials")
                        .header("X-Internal-Service-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyCredentialsRequest("alice@example.com", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }
}

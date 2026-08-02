package {{package}}.user.presentation.controller;
import {{package}}.user.application.service.UserService;
import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import {{package}}.user.presentation.dto.ChangeEmailRequest;
import {{package}}.user.presentation.dto.ChangePasswordRequest;
import {{package}}.user.presentation.dto.DeleteUserRequest;
import {{package}}.user.presentation.dto.RegistrationRequest;
import {{package}}.user.presentation.mapper.UserMapperImpl;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(UserController.class)
@Import(UserMapperImpl.class)
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserService userService;
    @Autowired
    private ObjectMapper objectMapper;
    @Test
    void registerWithValidBodyReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.register(anyString(), anyString())).thenReturn(id);
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest("alice@example.com", "supersecret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
    @Test
    void registerWithBlankEmailReturns400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest("", "supersecret123"))))
                .andExpect(status().isBadRequest());
    }
    @Test
    void registerWithShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest("alice@example.com", "short"))))
                .andExpect(status().isBadRequest());
    }
    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        when(userService.register(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "email already registered"));
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest("dup@example.com", "supersecret123"))))
                .andExpect(status().isConflict());
    }
    @Test
    void getExistingUserReturns200WithoutPasswordHash() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getById(any())).thenReturn(
                new User(id, "alice@example.com", "{bcrypt}shouldneverbeinresponse", Instant.now(), null, Role.USER, true));
        mockMvc.perform(get("/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test
    void getMissingUserReturns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(userService.getById(any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        mockMvc.perform(get("/users/{id}", missing))
                .andExpect(status().isNotFound());
    }
    @Test
    void listReturnsAPagedBody() throws Exception {
        UUID id = UUID.randomUUID();
        User user = new User(id, "alice@example.com", "{bcrypt}shouldneverbeinresponse", Instant.now(), null, Role.USER, true);
        when(userService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
    @Test
    void listPassesEmailAndRoleQueryParamsThrough() throws Exception {
        when(userService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/users").param("email", "alice").param("role", "ADMIN"))
                .andExpect(status().isOk());
        verify(userService).list(eq("alice"), eq(Role.ADMIN), any(Pageable.class));
    }
    @Test
    void changeEmailWithCorrectPasswordReturns200WithUpdatedView() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changeEmail(any(), anyString(), anyString())).thenReturn(
                new User(id, "new@example.com", "{bcrypt}hash", Instant.now(), null, Role.USER, true));
        mockMvc.perform(patch("/users/{id}/email", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeEmailRequest("new@example.com", "correctpassword"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }
    @Test
    void changeEmailWithWrongPasswordReturns403() throws Exception {
        when(userService.changeEmail(any(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "current password is incorrect"));
        mockMvc.perform(patch("/users/{id}/email", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeEmailRequest("new@example.com", "wrongpassword"))))
                .andExpect(status().isForbidden());
    }
    @Test
    void changeEmailWithMalformedEmailReturns400() throws Exception {
        mockMvc.perform(patch("/users/{id}/email", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeEmailRequest("not-an-email", "correctpassword"))))
                .andExpect(status().isBadRequest());
    }
    @Test
    void changePasswordWithCorrectCurrentPasswordReturns204() throws Exception {
        mockMvc.perform(patch("/users/{id}/password", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("oldpassword", "newpassword123"))))
                .andExpect(status().isNoContent());
    }
    @Test
    void changePasswordWithWrongCurrentPasswordReturns403() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "current password is incorrect"))
                .when(userService).changePassword(any(), anyString(), anyString());
        mockMvc.perform(patch("/users/{id}/password", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("wrongpassword", "newpassword123"))))
                .andExpect(status().isForbidden());
    }
    @Test
    void deleteWithCorrectPasswordReturns204() throws Exception {
        mockMvc.perform(delete("/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteUserRequest("correctpassword"))))
                .andExpect(status().isNoContent());
    }
    @Test
    void deleteWithWrongPasswordReturns403() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "current password is incorrect"))
                .when(userService).delete(any(), anyString());
        mockMvc.perform(delete("/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteUserRequest("wrongpassword"))))
                .andExpect(status().isForbidden());
    }
    @Test
    void deleteMissingUserReturns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"))
                .when(userService).delete(any(), anyString());
        mockMvc.perform(delete("/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteUserRequest("correctpassword"))))
                .andExpect(status().isNotFound());
    }
    @Test
    void deleteWithBlankPasswordReturns400() throws Exception {
        mockMvc.perform(delete("/users/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeleteUserRequest(""))))
                .andExpect(status().isBadRequest());
    }
}

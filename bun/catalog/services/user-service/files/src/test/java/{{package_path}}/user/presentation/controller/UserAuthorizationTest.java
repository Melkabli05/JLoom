package {{package}}.user.presentation.controller;

import {{package}}.infrastructure.configuration.SecurityConfig;
import {{package}}.user.application.service.UserService;
import {{package}}.user.domain.model.Role;
import {{package}}.user.domain.model.User;
import {{package}}.user.presentation.mapper.UserMapperImpl;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserMapperImpl.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class UserAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownIdIsAllowed() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getById(id)).thenReturn(
                new User(id, "alice@example.com", "hash", Instant.now(), null, Role.USER, true));

        mockMvc.perform(get("/users/{id}", id).with(jwt().jwt(j -> j.subject(id.toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void someoneElsesIdIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        UUID caller = UUID.randomUUID();

        mockMvc.perform(get("/users/{id}", id).with(jwt().jwt(j -> j.subject(caller.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadSomeoneElsesRecord() throws Exception {
        UUID id = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        when(userService.getById(id)).thenReturn(
                new User(id, "alice@example.com", "hash", Instant.now(), null, Role.USER, true));

        mockMvc.perform(get("/users/{id}", id)
                        .with(jwt().jwt(j -> j.subject(caller.toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void listRequiresAdmin() throws Exception {
        UUID caller = UUID.randomUUID();
        when(userService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users").with(jwt().jwt(j -> j.subject(caller.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWorksForAdmin() throws Exception {
        UUID caller = UUID.randomUUID();
        when(userService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                        .with(jwt().jwt(j -> j.subject(caller.toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteSomeoneElsesAccountIsForbiddenRegardlessOfPassword() throws Exception {
        UUID id = UUID.randomUUID();
        UUID caller = UUID.randomUUID();

        mockMvc.perform(delete("/users/{id}", id)
                        .with(jwt().jwt(j -> j.subject(caller.toString())))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"whatever\"}"))
                .andExpect(status().isForbidden());
    }
}

package {{package}}.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityController.class)
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtIssuer issuer;

    @Test
    void missingAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonBearerAuthorizationHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", "Basic xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorizedWithoutLeakingTheVerificationFailureReason() throws Exception {
        when(issuer.verify("bad-token")).thenThrow(new IllegalArgumentException("signature mismatch"));

        mockMvc.perform(get("/me").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.subject").doesNotExist());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        when(issuer.verify("expired-token")).thenThrow(new IllegalArgumentException("JWT expired"));

        mockMvc.perform(get("/me").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReturnsSubject() throws Exception {
        when(issuer.verify("good-token")).thenReturn("alice");

        mockMvc.perform(get("/me").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("alice"));
    }
}

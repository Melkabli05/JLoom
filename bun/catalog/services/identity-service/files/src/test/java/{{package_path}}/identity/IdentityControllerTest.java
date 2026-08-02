package {{package}}.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import {{package}}.infrastructure.configuration.SecurityConfig;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest(IdentityController.class)
@Import({SecurityConfig.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class IdentityControllerTest {
    @Autowired
    private MockMvc mockMvc;




    @MockitoBean
    private JwtIssuer jwtIssuer;
    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void validFabricatedJwtReturnsSubject() throws Exception {
        mockMvc.perform(get("/me").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("alice"));
    }
}

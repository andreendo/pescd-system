package br.ufscar.pescd.config;

import br.ufscar.pescd.controller.AdminUserApiController;
import br.ufscar.pescd.dto.PublicUserResponse;
import br.ufscar.pescd.security.CustomUserDetailsService;
import br.ufscar.pescd.security.RoleBasedAuthenticationSuccessHandler;
import br.ufscar.pescd.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserApiController.class)
@Import(SecurityConfig.class)
class CsrfApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private RoleBasedAuthenticationSuccessHandler successHandler;

    @Test
    void requisicaoDaApiQueAlteraDadosSemTokenCsrfERecusada() throws Exception {
        mockMvc.perform(post("/api/admin/users/5/block")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateEnabled(anyLong(), anyBoolean(), anyString());
    }

    @Test
    void requisicaoDaApiQueAlteraDadosComTokenCsrfDoCookieNoCabecalhoEAceita() throws Exception {
        when(userService.updateEnabled(5L, false, "admin")).thenReturn(
                new PublicUserResponse(5L, "Prof. Naldi", "mnaldi@ufscar.br", "mnaldi", "PROFESSOR", false));
        Cookie token = mockMvc.perform(get("/api/admin/users")
                        .with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/admin/users/5/block")
                        .with(user("admin").roles("ADMIN"))
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk());

        verify(userService).updateEnabled(5L, false, "admin");
    }

    @Test
    void respostaDaApiEntregaOTokenCsrfNoCookieParaOFront() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}

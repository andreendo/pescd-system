package br.ufscar.pescd.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.*;

class RoleBasedAuthenticationSuccessHandlerTest {

    RoleRedirectService roleRedirectService;
    Authentication authentication;
    HttpServletRequest request;
    HttpServletResponse response;
    RoleBasedAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        roleRedirectService = mock(RoleRedirectService.class);
        authentication = mock(Authentication.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new RoleBasedAuthenticationSuccessHandler(roleRedirectService);
    }

    // Caminho: contexto vazio -> redireciona para o caminho resolvido pelo service
    @Test
    @DisplayName("Redireciona para o caminho resolvido pelo papel")
    void contextoVazio_redirecionaParaCaminhoResolvido() throws Exception {

        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(authentication).getAuthorities();
        when(request.getContextPath()).thenReturn("");
        when(roleRedirectService.resolveRedirectPath(authorities)).thenReturn("/admin/users");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response, times(1)).sendRedirect("/admin/users");
    }

    // Caminho: contexto "/pescd" -> caminho resolvido recebe o prefixo do contexto
    @Test
    @DisplayName("Redirecionamento recebe o prefixo do context path")
    void contextoComPrefixo_redirecionaComPrefixo() throws Exception {

        Collection<? extends GrantedAuthority> authorities = List.of();
        doReturn(authorities).when(authentication).getAuthorities();
        when(request.getContextPath()).thenReturn("/pescd");
        when(roleRedirectService.resolveRedirectPath(authorities)).thenReturn("/dashboard");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response, times(1)).sendRedirect("/pescd/dashboard");
    }
}

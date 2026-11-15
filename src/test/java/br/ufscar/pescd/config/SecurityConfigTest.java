package br.ufscar.pescd.config;

import br.ufscar.pescd.security.CustomUserDetailsService;
import br.ufscar.pescd.security.RoleBasedAuthenticationSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class SecurityConfigTest {

    CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    RoleBasedAuthenticationSuccessHandler successHandler = mock(RoleBasedAuthenticationSuccessHandler.class);
    SecurityConfig config = new SecurityConfig(userDetailsService, successHandler);

    // HttpSecurity falso: cada Customizer e executado contra um configurer falso (RETURNS_SELF)
    HttpSecurity http = mock(HttpSecurity.class, Answers.RETURNS_SELF);
    DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
    CsrfConfigurer csrf = mock(CsrfConfigurer.class, Answers.RETURNS_SELF);
    FormLoginConfigurer formLogin = mock(FormLoginConfigurer.class, Answers.RETURNS_SELF);
    LogoutConfigurer logout = mock(LogoutConfigurer.class, Answers.RETURNS_SELF);
    ExceptionHandlingConfigurer exceptionHandling = mock(ExceptionHandlingConfigurer.class, Answers.RETURNS_SELF);

    // lambdas que o SecurityConfig registra no exceptionHandling (guardados pelo stub abaixo)
    AuthenticationEntryPoint entryPoint;
    RequestMatcher entryMatcher;
    AccessDeniedHandler deniedHandler;
    RequestMatcher deniedMatcher;

    @BeforeEach
    void setUp() throws Exception {
        Object registry = mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class,
                Answers.RETURNS_DEEP_STUBS);

        when(http.authorizeHttpRequests(any())).thenAnswer(inv -> {
            ((Customizer) inv.getArgument(0)).customize(registry);
            return http;
        });
        when(http.csrf(any())).thenAnswer(inv -> aplicar(inv.getArgument(0), csrf));
        when(http.formLogin(any())).thenAnswer(inv -> aplicar(inv.getArgument(0), formLogin));
        when(http.logout(any())).thenAnswer(inv -> aplicar(inv.getArgument(0), logout));
        when(http.exceptionHandling(any())).thenAnswer(inv -> aplicar(inv.getArgument(0), exceptionHandling));
        when(http.build()).thenReturn(chain);

        when(exceptionHandling.defaultAuthenticationEntryPointFor(any(AuthenticationEntryPoint.class), any(RequestMatcher.class)))
                .thenAnswer(inv -> {
                    entryPoint = inv.getArgument(0);
                    entryMatcher = inv.getArgument(1);
                    return exceptionHandling;
                });
        when(exceptionHandling.defaultAccessDeniedHandlerFor(any(AccessDeniedHandler.class), any(RequestMatcher.class)))
                .thenAnswer(inv -> {
                    deniedHandler = inv.getArgument(0);
                    deniedMatcher = inv.getArgument(1);
                    return exceptionHandling;
                });
    }

    private HttpSecurity aplicar(Customizer customizer, Object configurer) {
        customizer.customize(configurer);
        return http;
    }

    // Caminho: bean passwordEncoder -> BCrypt (hash comeca com $2 e confere com a senha)
    @Test
    @DisplayName("passwordEncoder gera hash BCrypt que confere com a senha")
    void passwordEncoder_geraHashBCryptQueConfere() {

        PasswordEncoder encoder = config.passwordEncoder();

        String hash = encoder.encode("senha123");

        assertNotEquals("senha123", hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(encoder.matches("senha123", hash));
        assertFalse(encoder.matches("outra", hash));
    }

    // Caminho: bean authenticationManager -> devolve o manager da AuthenticationConfiguration
    @Test
    @DisplayName("authenticationManager devolve o manager da configuracao")
    void authenticationManager_devolveManagerDaConfiguracao() throws Exception {

        AuthenticationConfiguration authConfig = mock(AuthenticationConfiguration.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(authConfig.getAuthenticationManager()).thenReturn(manager);

        assertSame(manager, config.authenticationManager(authConfig));
    }

    // Caminho: filterChain -> configura login, logout, pagina de acesso negado, csrf e userDetailsService
    @Test
    @DisplayName("filterChain liga handler de sucesso e userDetailsService")
    void filterChain_ligaHandlerDeSucessoEUserDetailsService() throws Exception {

        DefaultSecurityFilterChain resultado = (DefaultSecurityFilterChain) config.filterChain(http);

        assertSame(chain, resultado);
        verify(http, times(1)).userDetailsService(userDetailsService);
        verify(formLogin, times(1)).loginPage("/login");
        verify(formLogin, times(1)).successHandler(successHandler);
        verify(logout, times(1)).logoutSuccessUrl("/login?logout");
        verify(exceptionHandling, times(1)).accessDeniedPage("/access-denied");
        verify(csrf, times(1)).ignoringRequestMatchers("/api/**");
    }

    // Caminho: entry point de /api sem autenticacao -> 401 com JSON
    @Test
    @DisplayName("API sem autenticacao responde 401 em JSON")
    void apiSemAutenticacao_respondeJson401() throws Exception {

        config.filterChain(http);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("GET", "/api/x"), response, new BadCredentialsException("x"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Autenticação necessária para acessar este recurso.\"}",
                response.getContentAsString());
    }

    // Caminho: access denied handler de /api sem permissao -> 403 com JSON
    @Test
    @DisplayName("API sem permissao responde 403 em JSON")
    void apiSemPermissao_respondeJson403() throws Exception {

        config.filterChain(http);
        MockHttpServletResponse response = new MockHttpServletResponse();

        deniedHandler.handle(new MockHttpServletRequest("GET", "/api/x"), response, new AccessDeniedException("x"));

        assertEquals(403, response.getStatus());
        assertEquals("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Você não tem permissão para acessar este recurso.\"}",
                response.getContentAsString());
    }

    // PCE: URI iniciada em /api/ (casa) x URI de pagina (nao casa) nos dois matchers
    @Test
    @DisplayName("Handlers JSON valem so para caminhos /api/")
    void handlersJson_valemSoParaCaminhosApi() throws Exception {

        config.filterChain(http);
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/offers");
        api.setRequestURI("/api/offers");
        MockHttpServletRequest pagina = new MockHttpServletRequest("GET", "/aluno/ofertas");
        pagina.setRequestURI("/aluno/ofertas");

        assertTrue(entryMatcher.matches(api));
        assertFalse(entryMatcher.matches(pagina));
        assertTrue(deniedMatcher.matches(api));
        assertFalse(deniedMatcher.matches(pagina));
    }
}

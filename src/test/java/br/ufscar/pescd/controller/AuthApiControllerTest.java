package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.AuthLoginRequest;
import br.ufscar.pescd.dto.AuthResponse;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.security.RoleRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthApiControllerTest {

    AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    UserRepository userRepository = mock(UserRepository.class);
    RoleRedirectService roleRedirectService = mock(RoleRedirectService.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);

    AuthApiController controller;
    Authentication auth = new TestingAuthenticationToken("maria", "pw", "ROLE_SECRETARIO");

    @BeforeEach
    void setUp() {
        controller = new AuthApiController(authenticationManager, userRepository, roleRedirectService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    User usuario() {
        User u = new User();
        u.setId(1L);
        u.setFullName("Maria");
        u.setEmail("m@x.com");
        u.setUsername("maria");
        u.setRole(UserRole.SECRETARIO);
        return u;
    }

    // Caminho: login valido -> cria sessao com o SecurityContext e devolve os dados do usuario
    @Test
    @DisplayName("Login valido cria a sessao e retorna os dados do usuario")
    void login_valido_criaSessaoERetornaDados() {

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(request.getSession(true)).thenReturn(session);
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(usuario()));
        when(roleRedirectService.resolveRedirectPath(auth.getAuthorities())).thenReturn("/secretary/offers");

        ResponseEntity<AuthResponse> resposta = controller.login(new AuthLoginRequest("maria", "pw"), request);

        AuthResponse corpo = resposta.getBody();
        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("maria", corpo.username());
        assertEquals("SECRETARIO", corpo.role());
        assertEquals(List.of("ROLE_SECRETARIO"), corpo.authorities());
        assertEquals("/secretary/offers", corpo.redirectPath());
        assertSame(auth, SecurityContextHolder.getContext().getAuthentication());
        verify(session, times(1)).setAttribute(eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY), any());
    }

    // Caminho: authenticationManager rejeita -> BadCredentialsException propaga e nenhuma sessao e criada
    @Test
    @DisplayName("Login invalido propaga BadCredentialsException")
    void login_credenciaisInvalidas_propagaBadCredentialsException() {

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class,
                () -> controller.login(new AuthLoginRequest("x", "y"), request));

        verify(request, never()).getSession(anyBoolean());
    }

    // Caminho: autenticou mas o usuario nao existe no banco -> IllegalArgumentException
    @Test
    @DisplayName("Login com usuario ausente no banco lanca IllegalArgumentException")
    void login_usuarioNaoExisteNoBanco_lancaIllegalArgumentException() {

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(request.getSession(true)).thenReturn(session);
        when(userRepository.findByUsername("maria")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.login(new AuthLoginRequest("maria", "pw"), request));

        assertEquals("Usuário autenticado não encontrado.", ex.getMessage());
    }

    // Caminho: GET /me com usuario existente -> dados da sessao atual
    @Test
    @DisplayName("me retorna os dados da sessao atual")
    void me_usuarioExiste_retornaDadosDaSessao() {

        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(usuario()));
        when(roleRedirectService.resolveRedirectPath(auth.getAuthorities())).thenReturn("/secretary/offers");

        ResponseEntity<AuthResponse> resposta = controller.me(auth);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("m@x.com", resposta.getBody().email());
    }

    // Caminho: GET /me com usuario ausente no banco -> IllegalArgumentException
    @Test
    @DisplayName("me com usuario ausente no banco lanca IllegalArgumentException")
    void me_usuarioNaoExiste_lancaIllegalArgumentException() {

        when(userRepository.findByUsername("maria")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.me(auth));
    }

    // Caminho: logout autenticado -> SecurityContextLogoutHandler invalida a sessao
    @Test
    @DisplayName("Logout autenticado invalida a sessao")
    void logout_autenticado_invalidaSessao() {

        when(request.getSession(false)).thenReturn(session);

        ResponseEntity<Void> resposta = controller.logout(request, response, auth);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        verify(session, times(1)).invalidate();
    }

    // Caminho: logout sem authentication mas com sessao existente -> invalida a sessao
    @Test
    @DisplayName("Logout sem autenticacao invalida a sessao existente")
    void logout_semAutenticacaoComSessao_invalidaSessao() {

        when(request.getSession(false)).thenReturn(session);

        ResponseEntity<Void> resposta = controller.logout(request, response, null);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        verify(session, times(1)).invalidate();
    }

    // Caminho: logout sem authentication e sem sessao -> 204 sem criar sessao
    @Test
    @DisplayName("Logout sem autenticacao e sem sessao responde 204")
    void logout_semAutenticacaoSemSessao_retorna204() {

        when(request.getSession(false)).thenReturn(null);

        ResponseEntity<Void> resposta = controller.logout(request, response, null);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        verify(request, never()).getSession(true);
    }
}

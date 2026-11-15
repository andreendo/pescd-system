package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.OfferView;
import br.ufscar.pescd.security.RoleRedirectService;
import br.ufscar.pescd.service.OfferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    OfferService offerService = mock(OfferService.class);
    RoleRedirectService roleRedirectService = mock(RoleRedirectService.class);
    AuthController controller = new AuthController(offerService, roleRedirectService);

    // Caminho: GET /login -> nome da view
    @Test
    @DisplayName("Login retorna a view de login")
    void login_retornaViewLogin() {

        String view = controller.login();

        assertEquals("login", view);
    }

    // Caminho: GET /access-denied -> nome da view
    @Test
    @DisplayName("Acesso negado retorna a view access-denied")
    void accessDenied_retornaViewAccessDenied() {

        String view = controller.accessDenied();

        assertEquals("access-denied", view);
    }

    // Caminho: GET / -> ofertas publicas no model
    @Test
    @DisplayName("Home mostra as ofertas publicas")
    void home_colocaOfertasPublicasNoModel() {

        List<OfferView> ofertas = List.of(mock(OfferView.class));
        when(offerService.findPublicOffers()).thenReturn(ofertas);
        Model model = new ExtendedModelMap();

        String view = controller.home(model);

        assertEquals("index", view);
        assertSame(ofertas, model.getAttribute("offers"));
    }

    // Caminho: dashboard com authentication nula -> redireciona para o login
    @Test
    @DisplayName("Dashboard sem autenticacao redireciona para o login")
    void dashboard_authenticationNula_redirecionaParaLogin() {

        String view = controller.dashboard(null);

        assertEquals("redirect:/login", view);
        verifyNoInteractions(roleRedirectService);
    }

    // Caminho: dashboard com authentication nao autenticada -> redireciona para o login
    @Test
    @DisplayName("Dashboard com autenticacao invalida redireciona para o login")
    void dashboard_naoAutenticado_redirecionaParaLogin() {

        Authentication auth = new TestingAuthenticationToken("u", "p");
        auth.setAuthenticated(false);

        String view = controller.dashboard(auth);

        assertEquals("redirect:/login", view);
    }

    // Caminho: dashboard autenticado -> redireciona para o caminho do papel
    @Test
    @DisplayName("Dashboard autenticado redireciona para o caminho do papel")
    void dashboard_autenticado_redirecionaParaCaminhoDoPapel() {

        Authentication auth = new TestingAuthenticationToken("sec", "p", "ROLE_SECRETARIO");
        when(roleRedirectService.resolveRedirectPath(auth.getAuthorities())).thenReturn("/secretary/offers");

        String view = controller.dashboard(auth);

        assertEquals("redirect:/secretary/offers", view);
    }

    // Caminho: GET /dashboard/secretario -> view dashboard
    @Test
    @DisplayName("Dashboard do secretario retorna a view dashboard")
    void secretarioDashboard_retornaViewDashboard() {

        assertEquals("dashboard", controller.secretarioDashboard());
    }

    // Caminho: GET /dashboard/professor -> view dashboard
    @Test
    @DisplayName("Dashboard do professor retorna a view dashboard")
    void professorDashboard_retornaViewDashboard() {

        assertEquals("dashboard", controller.professorDashboard());
    }
}

package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.ApiErrorResponse;
import br.ufscar.pescd.dto.PublicUserResponse;
import br.ufscar.pescd.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserBloqueioApiTest {

    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("admin", null);

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserApiController controller;

    @Test
    void bloquearRepassaOUsuarioLogadoAoServico() {
        PublicUserResponse bloqueado = response(false);
        when(userService.updateEnabled(5L, false, "admin")).thenReturn(bloqueado);

        assertThat(controller.block(5L, ADMIN)).isSameAs(bloqueado);
    }

    @Test
    void desbloquearRepassaOUsuarioLogadoAoServico() {
        PublicUserResponse ativo = response(true);
        when(userService.updateEnabled(5L, true, "admin")).thenReturn(ativo);

        assertThat(controller.unblock(5L, ADMIN)).isSameAs(ativo);
    }

    @Test
    void loginDeUsuarioBloqueadoRetorna401ComMensagemClara() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseEntity<ApiErrorResponse> response = new ApiExceptionHandler()
                .handleDisabled(new DisabledException("User is disabled"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Usuário bloqueado. Procure o administrador.");
        assertThat(response.getBody().path()).isEqualTo("/api/auth/login");
    }

    private static PublicUserResponse response(boolean enabled) {
        return new PublicUserResponse(5L, "Prof. Naldi", "mnaldi@ufscar.br", "mnaldi", "PROFESSOR", enabled);
    }
}

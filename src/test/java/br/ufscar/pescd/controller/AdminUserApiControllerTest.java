package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.PublicUserResponse;
import br.ufscar.pescd.dto.UserForm;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.exception.BusinessRuleException;
import br.ufscar.pescd.exception.DuplicateResourceException;
import br.ufscar.pescd.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserApiControllerTest {

    UserService userService = mock(UserService.class);
    Authentication auth = mock(Authentication.class);

    AdminUserApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserApiController(userService);
        when(auth.getName()).thenReturn("admin");
        // o create monta o header Location a partir da requisicao corrente
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", "/api/admin/users")));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    PublicUserResponse usuario(long id) {
        return new PublicUserResponse(id, "Fulano", "f@x.com", "fulano", "ADMIN", true);
    }

    // Caminho: GET /api/admin/users -> lista do service
    @Test
    @DisplayName("Lista os usuarios publicos")
    void list_retornaUsuariosDoService() {

        when(userService.findAllPublic()).thenReturn(List.of(usuario(1), usuario(2)));

        List<PublicUserResponse> resultado = controller.list();

        assertEquals(2, resultado.size());
    }

    // Caminho: GET /{id} existente -> usuario do service
    @Test
    @DisplayName("Busca usuario por id")
    void findById_existente_retornaUsuario() {

        when(userService.findPublicById(7L)).thenReturn(usuario(7));

        PublicUserResponse resultado = controller.findById(7L);

        assertEquals(7L, resultado.id());
    }

    // Caminho: GET /{id} inexistente -> EntityNotFoundException do service propaga
    @Test
    @DisplayName("Usuario inexistente propaga EntityNotFoundException")
    void findById_inexistente_propagaEntityNotFoundException() {

        when(userService.findPublicById(99L)).thenThrow(new EntityNotFoundException("não encontrado"));

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> controller.findById(99L));

        assertEquals("não encontrado", ex.getMessage());
    }

    // Caminho: POST valido -> 201 com Location /api/admin/users/{id} e o usuario no corpo
    @Test
    @DisplayName("Cria usuario e responde 201 com Location")
    void create_valido_retorna201ComLocation() {

        UserForm form = new UserForm();
        form.setRole(UserRole.SECRETARIO);
        when(userService.createFromApi(form)).thenReturn(usuario(5));

        ResponseEntity<PublicUserResponse> response = controller.create(form);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("http://localhost/api/admin/users/5", response.getHeaders().getLocation().toString());
        assertEquals(5L, response.getBody().id());
    }

    // Caminho: POST com e-mail ou username duplicado -> DuplicateResourceException propaga
    @Test
    @DisplayName("Usuario duplicado propaga DuplicateResourceException")
    void create_duplicado_propagaDuplicateResourceException() {

        UserForm form = new UserForm();
        when(userService.createFromApi(form)).thenThrow(new DuplicateResourceException("e-mail duplicado"));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () -> controller.create(form));

        assertEquals("e-mail duplicado", ex.getMessage());
    }

    // Caminho: PUT /{id} -> id do path e copiado para o form antes de chamar o service
    @Test
    @DisplayName("Atualiza usuario usando o id do path")
    void update_definePathIdNoForm() {

        UserForm form = new UserForm();
        when(userService.updateFromApi(form)).thenReturn(usuario(3));

        PublicUserResponse resultado = controller.update(3L, form);

        assertEquals(3L, form.getId());
        assertEquals(3L, resultado.id());
    }

    // Caminho: DELETE /{id} -> 204 e service chamado com o nome do usuario logado
    @Test
    @DisplayName("Remove usuario e responde 204")
    void delete_valido_retorna204() {

        ResponseEntity<Void> response = controller.delete(4L, auth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userService, times(1)).delete(4L, "admin");
    }

    // Caminho: DELETE da propria conta -> BusinessRuleException do service propaga
    @Test
    @DisplayName("Autoexclusao propaga BusinessRuleException")
    void delete_autoexclusao_propagaBusinessRuleException() {

        doThrow(new BusinessRuleException("Não é possível remover seu próprio usuário."))
                .when(userService).delete(1L, "admin");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> controller.delete(1L, auth));

        assertEquals("Não é possível remover seu próprio usuário.", ex.getMessage());
    }
}

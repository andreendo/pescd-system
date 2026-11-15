package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.UserForm;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {

    UserService userService = mock(UserService.class);
    Authentication auth = mock(Authentication.class);

    AdminUserController controller;
    Model model = new ExtendedModelMap();
    RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(userService);
        when(auth.getName()).thenReturn("admin");
    }

    // Caminho: @ModelAttribute roles -> apenas ADMIN, SECRETARIO e PROFESSOR (ALUNO nao e cadastrado aqui)
    @Test
    @DisplayName("Perfis oferecidos no cadastro excluem ALUNO")
    void roles_retornaPerfisPermitidos() {

        UserRole[] perfis = controller.roles();

        assertArrayEquals(new UserRole[]{UserRole.ADMIN, UserRole.SECRETARIO, UserRole.PROFESSOR}, perfis);
    }

    // Caminho: GET /admin/users -> usuarios e nome do usuario logado no model
    @Test
    @DisplayName("Lista usuarios com o nome do usuario logado")
    void list_colocaUsuariosENomeLogadoNoModel() {

        List<User> usuarios = List.of(new User());
        when(userService.findAll()).thenReturn(usuarios);

        String view = controller.list(model, auth);

        assertEquals("admin/users/list", view);
        assertSame(usuarios, model.getAttribute("users"));
        assertEquals("admin", model.getAttribute("loggedUsername"));
    }

    // Caminho: GET /new -> form vazio e isEdit = false
    @Test
    @DisplayName("Formulario de criacao vem vazio")
    void createForm_formVazioSemEdicao() {

        String view = controller.createForm(model);

        assertEquals("admin/users/form", view);
        assertInstanceOf(UserForm.class, model.getAttribute("userForm"));
        assertEquals(false, model.getAttribute("isEdit"));
    }

    // Caminho: POST create com regras de negocio ok -> cria e redireciona com mensagem
    @Test
    @DisplayName("Cria usuario quando as regras de negocio passam")
    void create_regrasValidas_criaERedireciona() {

        UserForm form = new UserForm();
        BindingResult br = new BeanPropertyBindingResult(form, "userForm");
        when(userService.validateBusinessRules(form, br, false)).thenReturn(true);

        String view = controller.create(form, br, model, redirect);

        assertEquals("redirect:/admin/users", view);
        assertEquals("Usuário cadastrado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(userService, times(1)).create(form);
    }

    // Caminho: POST create com regras de negocio violadas -> reexibe o form sem criar
    @Test
    @DisplayName("Regras de negocio violadas reexibem o formulario de criacao")
    void create_regrasViolam_reexibeFormulario() {

        UserForm form = new UserForm();
        BindingResult br = new BeanPropertyBindingResult(form, "userForm");
        when(userService.validateBusinessRules(form, br, false)).thenReturn(false);

        String view = controller.create(form, br, model, redirect);

        assertEquals("admin/users/form", view);
        assertEquals(false, model.getAttribute("isEdit"));
        verify(userService, never()).create(any());
    }

    // Caminho: GET /{id}/edit -> form preenchido a partir do usuario e isEdit = true
    @Test
    @DisplayName("Formulario de edicao vem preenchido")
    void editForm_formPreenchidoComEdicao() {

        User usuario = new User();
        UserForm form = new UserForm();
        when(userService.findById(2L)).thenReturn(usuario);
        when(userService.toForm(usuario)).thenReturn(form);

        String view = controller.editForm(2L, model);

        assertEquals("admin/users/form", view);
        assertSame(form, model.getAttribute("userForm"));
        assertEquals(true, model.getAttribute("isEdit"));
    }

    // Caminho: POST update com regras ok -> define o id do path no form, atualiza e redireciona
    @Test
    @DisplayName("Atualiza usuario quando as regras de negocio passam")
    void update_regrasValidas_atualizaERedireciona() {

        UserForm form = new UserForm();
        BindingResult br = new BeanPropertyBindingResult(form, "userForm");
        when(userService.validateBusinessRules(form, br, true)).thenReturn(true);

        String view = controller.update(8L, form, br, model, redirect);

        assertEquals("redirect:/admin/users", view);
        assertEquals(8L, form.getId());
        assertEquals("Usuário atualizado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(userService, times(1)).update(form);
    }

    // Caminho: POST update com regras violadas -> reexibe o form de edicao sem atualizar
    @Test
    @DisplayName("Regras de negocio violadas reexibem o formulario de edicao")
    void update_regrasViolam_reexibeFormulario() {

        UserForm form = new UserForm();
        BindingResult br = new BeanPropertyBindingResult(form, "userForm");
        when(userService.validateBusinessRules(form, br, true)).thenReturn(false);

        String view = controller.update(8L, form, br, model, redirect);

        assertEquals("admin/users/form", view);
        assertEquals(true, model.getAttribute("isEdit"));
        verify(userService, never()).update(any());
    }

    // Caminho: POST delete sem erro -> remove e mensagem de sucesso
    @Test
    @DisplayName("Remove usuario com mensagem de sucesso")
    void delete_semErro_removeComSucesso() {

        String view = controller.delete(3L, auth, redirect);

        assertEquals("redirect:/admin/users", view);
        assertEquals("Usuário removido com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(userService, times(1)).delete(3L, "admin");
    }

    // Caminho: POST delete e o service lanca IllegalArgumentException (autoexclusao) -> errorMessage
    @Test
    @DisplayName("Autoexclusao mostra mensagem de erro")
    void delete_autoexclusao_mostraMensagemDeErro() {

        doThrow(new IllegalArgumentException("Não é possível remover seu próprio usuário."))
                .when(userService).delete(1L, "admin");

        String view = controller.delete(1L, auth, redirect);

        assertEquals("redirect:/admin/users", view);
        assertEquals("Não é possível remover seu próprio usuário.", redirect.getFlashAttributes().get("errorMessage"));
        assertFalse(redirect.getFlashAttributes().containsKey("successMessage"));
    }
}

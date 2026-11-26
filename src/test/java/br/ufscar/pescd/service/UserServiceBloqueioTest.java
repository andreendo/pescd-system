package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.PublicUserResponse;
import br.ufscar.pescd.dto.UserForm;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceBloqueioTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void bloquearOutroUsuarioMarcaComoBloqueadoESalva() {
        User professor = user(5L, "mnaldi", true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(professor));
        when(userRepository.save(professor)).thenReturn(professor);

        PublicUserResponse response = userService.updateEnabled(5L, false, "admin");

        assertThat(professor.getEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
        verify(userRepository).save(professor);
    }

    @Test
    void desbloquearUsuarioBloqueadoMarcaComoAtivoESalva() {
        User professor = user(5L, "mnaldi", false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(professor));
        when(userRepository.save(professor)).thenReturn(professor);

        PublicUserResponse response = userService.updateEnabled(5L, true, "admin");

        assertThat(professor.getEnabled()).isTrue();
        assertThat(response.enabled()).isTrue();
        verify(userRepository).save(professor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bloquearOuDesbloquearOProprioUsuarioLancaExcecaoENaoSalva(boolean enabled) {
        User admin = user(1L, "admin", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateEnabled(1L, enabled, "admin"));

        assertThat(ex.getMessage()).isEqualTo("Você não pode bloquear ou desbloquear seu próprio usuário.");
        assertThat(admin.getEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void bloquearUsuarioInexistenteLancaExcecao() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.updateEnabled(99L, false, "admin"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void editarUsuarioBloqueadoNaoODesbloqueia() {
        User professor = user(5L, "mnaldi", false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(professor));
        when(userRepository.save(professor)).thenReturn(professor);

        PublicUserResponse response = userService.updateFromApi(form(5L, "mnaldi", null));

        assertThat(professor.getEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void usuarioCriadoComecaAtivo() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicUserResponse response = userService.createFromApi(form(null, "novo.professor", "123456"));

        assertThat(response.enabled()).isTrue();
    }

    private static User user(Long id, String username, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setFullName("Usuário " + username);
        user.setEmail(username + "@ufscar.br");
        user.setUsername(username);
        user.setRole(UserRole.PROFESSOR);
        user.setEnabled(enabled);
        return user;
    }

    private static UserForm form(Long id, String username, String password) {
        UserForm form = new UserForm();
        form.setId(id);
        form.setFullName("Usuário " + username);
        form.setEmail(username + "@ufscar.br");
        form.setUsername(username);
        form.setPassword(password);
        form.setRole(UserRole.PROFESSOR);
        return form;
    }
}

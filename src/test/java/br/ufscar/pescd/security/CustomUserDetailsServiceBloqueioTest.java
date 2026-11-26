package br.ufscar.pescd.security;

import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceBloqueioTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private DaoAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(ENCODER);
    }

    @Test
    void usuarioAtivoGeraUserDetailsHabilitado() {
        when(userRepository.findByUsername("mnaldi")).thenReturn(Optional.of(user("mnaldi", true)));

        UserDetails details = userDetailsService.loadUserByUsername("mnaldi");

        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void usuarioBloqueadoGeraUserDetailsDesabilitado() {
        when(userRepository.findByUsername("mnaldi")).thenReturn(Optional.of(user("mnaldi", false)));

        UserDetails details = userDetailsService.loadUserByUsername("mnaldi");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loginDeUsuarioBloqueadoComSenhaCorretaERecusado() {
        when(userRepository.findByUsername("mnaldi")).thenReturn(Optional.of(user("mnaldi", false)));

        assertThrows(DisabledException.class,
                () -> authenticationProvider.authenticate(credentials("mnaldi", "123456")));
    }

    @Test
    void loginDeUsuarioAtivoComSenhaCorretaAutentica() {
        when(userRepository.findByUsername("mnaldi")).thenReturn(Optional.of(user("mnaldi", true)));

        Authentication authentication = authenticationProvider.authenticate(credentials("mnaldi", "123456"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("mnaldi");
    }

    private static UsernamePasswordAuthenticationToken credentials(String username, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(username, password);
    }

    private static User user(String username, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(ENCODER.encode("123456"));
        user.setRole(UserRole.PROFESSOR);
        user.setEnabled(enabled);
        return user;
    }
}

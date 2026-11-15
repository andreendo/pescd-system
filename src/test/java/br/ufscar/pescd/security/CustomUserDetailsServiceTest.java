package br.ufscar.pescd.security;

import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    UserRepository userRepository;
    CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    // Caminho: usuario encontrado -> UserDetails com senha e papel com prefixo ROLE_
    @Test
    @DisplayName("Usuario existente vira UserDetails com papel ROLE_")
    void usuarioExistente_retornaUserDetailsComRole() {

        User user = new User();
        user.setUsername("admin");
        user.setPassword("hash");
        user.setRole(UserRole.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("admin");

        assertEquals("admin", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertEquals(1, details.getAuthorities().size());
        assertEquals("ROLE_ADMIN", details.getAuthorities().iterator().next().getAuthority());
    }

    // Caminho: usuario nao encontrado -> UsernameNotFoundException
    @Test
    @DisplayName("Usuario inexistente lanca UsernameNotFoundException")
    void usuarioInexistente_lancaUsernameNotFoundException() {

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));

        assertEquals("Usuário não encontrado", ex.getMessage());
    }
}

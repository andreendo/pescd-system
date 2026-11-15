package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    // Caminho: callback @PrePersist -> preenche createdAt com a data atual
    @Test
    @DisplayName("onCreate preenche createdAt")
    void onCreate_preencheCreatedAt() {

        User usuario = new User();

        usuario.onCreate();

        assertNotNull(usuario.getCreatedAt());
    }

    // Caminho: valor padrao do campo enabled sem chamar nenhum setter
    @Test
    @DisplayName("Usuario novo nasce habilitado")
    void usuarioNovo_nasceHabilitado() {

        User usuario = new User();

        assertTrue(usuario.getEnabled());
    }
}

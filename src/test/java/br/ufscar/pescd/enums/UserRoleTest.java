package br.ufscar.pescd.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleTest {

    // PCE: uma verificacao por papel
    @Test
    @DisplayName("Cada papel tem o nome de exibicao esperado")
    void cadaPapel_temNomeDeExibicao() {

        assertEquals("Administrador", UserRole.ADMIN.getDisplayName());
        assertEquals("Secretário", UserRole.SECRETARIO.getDisplayName());
        assertEquals("Aluno", UserRole.ALUNO.getDisplayName());
        assertEquals("Professor", UserRole.PROFESSOR.getDisplayName());
    }
}

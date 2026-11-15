package br.ufscar.pescd.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GradeOptionTest {

    // PCE: uma verificacao por valor do enum (A a E)
    @Test
    @DisplayName("Cada nota tem o nome de exibicao esperado")
    void cadaNota_temNomeDeExibicao() {

        assertEquals("A — Excelente", GradeOption.A.getDisplayName());
        assertEquals("B — Bom", GradeOption.B.getDisplayName());
        assertEquals("C — Regular", GradeOption.C.getDisplayName());
        assertEquals("D — Insuficiente", GradeOption.D.getDisplayName());
        assertEquals("E — Reprovado", GradeOption.E.getDisplayName());
    }
}

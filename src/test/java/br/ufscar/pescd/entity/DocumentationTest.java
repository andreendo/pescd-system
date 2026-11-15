package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentationTest {

    // Caminho: callback @PrePersist -> preenche submittedAt com a data atual
    @Test
    @DisplayName("onCreate preenche submittedAt")
    void onCreate_preencheSubmittedAt() {

        Documentation entidade = new Documentation();

        entidade.onCreate();

        assertNotNull(entidade.getSubmittedAt());
    }
}

package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReportTest {

    // Caminho: callback @PrePersist -> preenche submittedAt com a data atual
    @Test
    @DisplayName("onCreate preenche submittedAt")
    void onCreate_preencheSubmittedAt() {

        Report entidade = new Report();

        entidade.onCreate();

        assertNotNull(entidade.getSubmittedAt());
    }
}

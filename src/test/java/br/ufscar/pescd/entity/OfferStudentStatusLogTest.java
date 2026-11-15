package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferStudentStatusLogTest {

    // Caminho: callback @PrePersist -> preenche changedAt com a data atual
    @Test
    @DisplayName("onCreate preenche changedAt")
    void onCreate_preencheChangedAt() {

        OfferStudentStatusLog entidade = new OfferStudentStatusLog();

        entidade.onCreate();

        assertNotNull(entidade.getChangedAt());
    }
}

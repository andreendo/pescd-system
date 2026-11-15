package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusChangeLogTest {

    // Caminho: callback @PrePersist -> preenche changedAt com a data atual
    @Test
    @DisplayName("onCreate preenche changedAt")
    void onCreate_preencheChangedAt() {

        StatusChangeLog entidade = new StatusChangeLog();

        entidade.onCreate();

        assertNotNull(entidade.getChangedAt());
    }
}

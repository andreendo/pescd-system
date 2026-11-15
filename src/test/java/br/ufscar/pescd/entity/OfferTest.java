package br.ufscar.pescd.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferTest {

    // Caminho: callback @PrePersist -> preenche createdAt com a data atual
    @Test
    @DisplayName("onCreate preenche createdAt")
    void onCreate_preencheCreatedAt() {

        Offer oferta = new Offer();

        oferta.onCreate();

        assertNotNull(oferta.getCreatedAt());
    }
}

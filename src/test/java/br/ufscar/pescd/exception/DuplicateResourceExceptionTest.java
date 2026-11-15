package br.ufscar.pescd.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateResourceExceptionTest {

    // Caminho: construtor com mensagem -> excecao nao verificada que guarda a mensagem
    @Test
    @DisplayName("Excecao guarda a mensagem e e nao verificada")
    void comMensagem_guardaMensagemENaoEVerificada() {

        DuplicateResourceException ex = new DuplicateResourceException("já existe");

        assertEquals("já existe", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
}

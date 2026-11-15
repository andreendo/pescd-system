package br.ufscar.pescd.entity;

import br.ufscar.pescd.enums.StudentOfferStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferStudentTest {

    // Caminho: @PrePersist com status nulo -> assume NAO_ENVIADO e preenche createdAt
    @Test
    @DisplayName("onCreate com status nulo assume NAO_ENVIADO")
    void onCreate_statusNulo_assumeNaoEnviado() {

        OfferStudent matricula = new OfferStudent();

        matricula.onCreate();

        assertEquals(StudentOfferStatus.NAO_ENVIADO, matricula.getStatus());
        assertNotNull(matricula.getCreatedAt());
    }

    // Caminho: @PrePersist com status ja informado -> preserva o status
    @Test
    @DisplayName("onCreate com status informado preserva o status")
    void onCreate_statusInformado_preservaStatus() {

        OfferStudent matricula = new OfferStudent();
        matricula.setStatus(StudentOfferStatus.PLANO_APROVADO);

        matricula.onCreate();

        assertEquals(StudentOfferStatus.PLANO_APROVADO, matricula.getStatus());
    }
}

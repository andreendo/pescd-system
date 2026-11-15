package br.ufscar.pescd.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentOfferStatusTest {

    // PCE: uma verificacao por valor do enum
    @Test
    @DisplayName("Cada status do aluno tem o nome de exibicao esperado")
    void cadaStatus_temNomeDeExibicao() {

        assertEquals("Não enviado", StudentOfferStatus.NAO_ENVIADO.getDisplayName());
        assertEquals("Plano enviado", StudentOfferStatus.PLANO_ENVIADO.getDisplayName());
        assertEquals("Plano aprovado", StudentOfferStatus.PLANO_APROVADO.getDisplayName());
        assertEquals("Documentação enviada", StudentOfferStatus.DOCUMENTACAO_ENVIADA.getDisplayName());
        assertEquals("Relatório enviado", StudentOfferStatus.RELATORIO_ENVIADO.getDisplayName());
        assertEquals("Relatório aprovado pelo supervisor",
                StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR.getDisplayName());
        assertEquals("Concluído pelo responsável", StudentOfferStatus.CONCLUIDO_RESPONSAVEL.getDisplayName());
    }
}

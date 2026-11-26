package br.ufscar.pescd.enums;

public enum StudentOfferStatus {
    NAO_ENVIADO,
    PLANO_ENVIADO,
    PLANO_REPROVADO,
    PLANO_APROVADO,
    DOCUMENTACAO_ENVIADA,
    DOCUMENTACAO_DEVOLVIDA,
    RELATORIO_ENVIADO,
    RELATORIO_REPROVADO,
    RELATORIO_APROVADO_SUPERVISOR,
    CONCLUIDO_RESPONSAVEL;

    public String getDisplayName() {
        return switch (this) {
            case NAO_ENVIADO -> "Não enviado";
            case PLANO_ENVIADO -> "Plano enviado";
            case PLANO_REPROVADO -> "Plano reprovado";
            case PLANO_APROVADO -> "Plano aprovado";
            case DOCUMENTACAO_ENVIADA -> "Documentação enviada";
            case DOCUMENTACAO_DEVOLVIDA -> "Documentação devolvida";
            case RELATORIO_ENVIADO -> "Relatório enviado";
            case RELATORIO_REPROVADO -> "Relatório reprovado";
            case RELATORIO_APROVADO_SUPERVISOR -> "Relatório aprovado pelo supervisor";
            case CONCLUIDO_RESPONSAVEL -> "Concluído pelo responsável";
        };
    }
}

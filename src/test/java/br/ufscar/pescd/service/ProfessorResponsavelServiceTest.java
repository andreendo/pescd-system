package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.AnalyzeDocumentationFormDTO;
import br.ufscar.pescd.dto.ConcludeReportFormDTO;
import br.ufscar.pescd.dto.DocumentationDetailsResponse;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentSummaryResponse;
import br.ufscar.pescd.dto.OfferSummaryResponse;
import br.ufscar.pescd.dto.ReportDetailsResponse;
import br.ufscar.pescd.dto.ResponsavelDashboardDTO;
import br.ufscar.pescd.dto.ResponsibleCloseOfferSummaryResponse;
import br.ufscar.pescd.dto.ReturnDocumentationFormDTO;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.GradeOption;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.DocumentationRepository;
import br.ufscar.pescd.repository.OfferRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ProfessorResponsavelServiceTest {

    OfferRepository offerRepository = mock(OfferRepository.class);
    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    WorkPlanRepository workPlanRepository = mock(WorkPlanRepository.class);
    ReportRepository reportRepository = mock(ReportRepository.class);
    DocumentationRepository documentationRepository = mock(DocumentationRepository.class);
    StatusChangeLogRepository statusChangeLogRepository = mock(StatusChangeLogRepository.class);
    OfferApiMapper offerApiMapper = mock(OfferApiMapper.class);

    ProfessorResponsavelService service;

    User responsavel;
    Offer offer;
    OfferStudent os;

    @BeforeEach
    void setUp() {
        service = new ProfessorResponsavelService(offerStudentRepository, workPlanRepository,
                reportRepository, documentationRepository, statusChangeLogRepository,
                offerRepository, offerApiMapper);

        responsavel = new User();
        responsavel.setId(50L);
        responsavel.setRole(UserRole.PROFESSOR);

        offer = new Offer();
        offer.setId(10L);
        offer.setSemester("2025/1");
        offer.setStatus(OfferStatus.EM_ANDAMENTO);
        offer.setResponsibleProfessor(responsavel);

        os = criarInscricao(offer, StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR, "Maria Souza");
        os.setId(100L);
    }

    private OfferStudent criarInscricao(Offer o, StudentOfferStatus status, String nome) {
        OfferStudent s = new OfferStudent();
        s.setOffer(o);
        s.setStatus(status);
        User u = new User();
        u.setFullName(nome);
        s.setStudent(u);
        return s;
    }

    private User criarOutroProfessor() {
        User u = new User();
        u.setId(77L);
        return u;
    }

    private ConcludeReportFormDTO criarFormConclusao() {
        ConcludeReportFormDTO f = new ConcludeReportFormDTO();
        f.setParecer("Aprovado");
        f.setFrequencia(95);
        f.setNota(GradeOption.A);
        return f;
    }

    private AnalyzeDocumentationFormDTO criarFormAnalise() {
        AnalyzeDocumentationFormDTO f = new AnalyzeDocumentationFormDTO();
        f.setParecer("Documentação válida");
        f.setFrequencia(80);
        f.setNota(GradeOption.B);
        return f;
    }

    private void inscricaoExiste() {
        when(offerStudentRepository.findByOffer_IdAndStudent_Id(10L, 2L)).thenReturn(Optional.of(os));
    }

    private void ofertaExiste() {
        when(offerRepository.findById(10L)).thenReturn(Optional.of(offer));
    }

    // ---------- dashboard ----------

    @Test
    @DisplayName("Dashboard agrupa inscrições por oferta, semestre decrescente")
    void inscricoesDeDuasOfertas_agrupaOrdenandoPorSemestreDecrescente() {
        // Ramo: agrupamento por oferta + ordenação por semestre
        Offer o1 = new Offer();
        o1.setSemester("2024/1");
        Offer o2 = new Offer();
        o2.setSemester("2025/2");
        OfferStudent a = criarInscricao(o1, StudentOfferStatus.NAO_ENVIADO, "A");
        OfferStudent b = criarInscricao(o2, StudentOfferStatus.NAO_ENVIADO, "B");
        OfferStudent c = criarInscricao(o2, StudentOfferStatus.NAO_ENVIADO, "C");

        when(offerStudentRepository.findByOffer_ResponsibleProfessor(responsavel)).thenReturn(List.of(a, b, c));

        List<ResponsavelDashboardDTO> resultado = service.getDashboard(responsavel);

        assertEquals(2, resultado.size());
        assertEquals("2025/2", resultado.get(0).getOffer().getSemester());
        assertEquals("2024/1", resultado.get(1).getOffer().getSemester());
        assertEquals(2, resultado.get(0).getStudents().size());
        assertTrue(resultado.get(0).getStudents().contains(b));
        assertTrue(resultado.get(0).getStudents().contains(c));
    }

    @Test
    @DisplayName("Dashboard sem inscrições é vazio")
    void semInscricoes_dashboardRetornaListaVazia() {
        // Ramo: nenhuma inscrição -> lista vazia
        when(offerStudentRepository.findByOffer_ResponsibleProfessor(responsavel)).thenReturn(List.of());

        List<ResponsavelDashboardDTO> resultado = service.getDashboard(responsavel);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Dashboard para API usa o mapper")
    void dashboardParaApi_mapeiaCadaOferta() {
        // Ramo: getDashboardForApi delega ao mapper
        OfferStudent a = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "A");
        OfferDetailsResponse details = new OfferDetailsResponse(null, List.of());

        when(offerStudentRepository.findByOffer_ResponsibleProfessor(responsavel)).thenReturn(List.of(a));
        when(offerApiMapper.toOfferDetails(offer, List.of(a))).thenReturn(details);

        List<OfferDetailsResponse> resultado = service.getDashboardForApi(responsavel);

        assertEquals(1, resultado.size());
        assertSame(details, resultado.get(0));
    }

    // ---------- controle de acesso ----------

    @Test
    @DisplayName("Responsável da oferta acessa a inscrição")
    void professorResponsavel_retornaInscricao() {
        // Ramo: inscrição existe e professor é o responsável
        inscricaoExiste();

        OfferStudent resultado = service.getEnrollmentForResponsavel(10L, 2L, responsavel);

        assertSame(os, resultado);
    }

    @Test
    @DisplayName("Inscrição inexistente lança exceção")
    void inscricaoInexistente_lancaIllegalArgumentException() {
        // Ramo: inscrição não encontrada -> exceção
        when(offerStudentRepository.findByOffer_IdAndStudent_Id(10L, 2L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollmentForResponsavel(10L, 2L, responsavel));

        assertEquals("Inscrição não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Outro professor não acessa a inscrição")
    void professorNaoResponsavel_lancaAccessDeniedException() {
        // Ramo: professor diferente do responsável -> acesso negado
        inscricaoExiste();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.getEnrollmentForResponsavel(10L, 2L, criarOutroProfessor()));

        assertEquals("Você não é o professor responsável desta oferta.", ex.getMessage());
    }

    @Test
    @DisplayName("Detalhes da inscrição são mapeados para a API")
    void detalhesDaInscricaoParaApi_retornaDetalhesDoMapper() {
        // Ramo: getEnrollmentDetailsForApi delega ao mapper
        inscricaoExiste();
        OfferStudentDetailsResponse details = new OfferStudentDetailsResponse(null, null, List.of());

        when(offerApiMapper.toOfferStudentDetails(os)).thenReturn(details);

        OfferStudentDetailsResponse resultado = service.getEnrollmentDetailsForApi(10L, 2L, responsavel);

        assertSame(details, resultado);
    }

    // ---------- consultas da oferta ----------

    @Test
    @DisplayName("Busca oferta por id")
    void ofertaExistente_retornaOferta() {
        // Ramo: oferta encontrada
        ofertaExiste();

        Offer resultado = service.getOffer(10L);

        assertSame(offer, resultado);
    }

    @Test
    @DisplayName("Oferta inexistente lança exceção")
    void ofertaInexistente_lancaIllegalArgumentException() {
        // Ramo: oferta não encontrada -> exceção
        when(offerRepository.findById(10L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getOffer(10L));

        assertEquals("Oferta não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Lista os alunos da oferta")
    void ofertaComAluno_listaAlunosDaOferta() {
        // Ramo: getOfferStudents busca a oferta e depois as inscrições
        ofertaExiste();

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(os));

        List<OfferStudent> resultado = service.getOfferStudents(10L);

        assertEquals(1, resultado.size());
        assertSame(os, resultado.get(0));
    }

    @Test
    @DisplayName("Detalhes da oferta são mapeados para a API")
    void detalhesDaOfertaParaApi_retornaDetalhesDoMapper() {
        // Ramo: professor responsável -> mapeia detalhes
        ofertaExiste();
        OfferDetailsResponse details = new OfferDetailsResponse(null, List.of());

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(os));
        when(offerApiMapper.toOfferDetails(offer, List.of(os))).thenReturn(details);

        OfferDetailsResponse resultado = service.getOfferDetailsForApi(10L, responsavel);

        assertSame(details, resultado);
    }

    @Test
    @DisplayName("Outro professor não vê detalhes da oferta")
    void detalhesDaOfertaPorProfessorNaoResponsavel_lancaAccessDeniedException() {
        // Ramo: professor diferente do responsável -> acesso negado
        ofertaExiste();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.getOfferDetailsForApi(10L, criarOutroProfessor()));

        assertEquals("Você não é o professor responsável desta oferta.", ex.getMessage());
    }

    @Test
    @DisplayName("Oferta sem responsável nega acesso")
    void ofertaSemProfessorResponsavel_lancaAccessDeniedException() {
        // Ramo: responsável da oferta nulo -> acesso negado
        offer.setResponsibleProfessor(null);
        ofertaExiste();

        assertThrows(AccessDeniedException.class,
                () -> service.getOfferDetailsForApi(10L, responsavel));
    }

    @Test
    @DisplayName("Lista os logs de status da inscrição")
    void logsDeStatus_retornaListaDoRepositorio() {
        // Ramo: getStatusLogs delega ao repositório
        List<StatusChangeLog> logs = List.of(new StatusChangeLog());

        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(os)).thenReturn(logs);

        List<StatusChangeLog> resultado = service.getStatusLogs(os);

        assertSame(logs, resultado);
    }

    // ---------- busca de alunos por nome ----------

    private OfferStudentSummaryResponse prepararBuscaPorNome(OfferStudent ana, OfferStudent bruno) {
        ofertaExiste();
        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(ana, bruno));
        OfferStudentSummaryResponse sumAna = mock(OfferStudentSummaryResponse.class);
        OfferStudentSummaryResponse sumBruno = mock(OfferStudentSummaryResponse.class);
        when(offerApiMapper.toOfferStudentSummary(ana)).thenReturn(sumAna);
        when(offerApiMapper.toOfferStudentSummary(bruno)).thenReturn(sumBruno);
        return sumAna;
    }

    @Test
    @DisplayName("Busca por nome ignora caixa e espaços nas pontas")
    void nomeComCaixaEEspacos_filtraAlunoCorrespondente() {
        // Ramo: nome informado -> trim + case-insensitive
        OfferStudent ana = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Ana Paula");
        OfferStudent bruno = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Bruno Costa");
        prepararBuscaPorNome(ana, bruno);

        List<OfferStudentSummaryResponse> resultado =
                service.searchStudentsByNameForApi(10L, responsavel, "  BRUNO ");

        assertEquals(1, resultado.size());
        assertSame(offerApiMapper.toOfferStudentSummary(bruno), resultado.get(0));
    }

    @Test
    @DisplayName("Busca por parte do nome")
    void parteDoNome_filtraAlunoCorrespondente() {
        // Ramo: nome contido no nome completo
        OfferStudent ana = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Ana Paula");
        OfferStudent bruno = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Bruno Costa");
        OfferStudentSummaryResponse sumAna = prepararBuscaPorNome(ana, bruno);

        List<OfferStudentSummaryResponse> resultado =
                service.searchStudentsByNameForApi(10L, responsavel, "paula");

        assertEquals(1, resultado.size());
        assertSame(sumAna, resultado.get(0));
    }

    @Test
    @DisplayName("Nome nulo retorna todos os alunos")
    void nomeNulo_retornaTodosOsAlunos() {
        // Ramo: nome == null -> sem filtro
        OfferStudent ana = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Ana Paula");
        OfferStudent bruno = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Bruno Costa");
        prepararBuscaPorNome(ana, bruno);

        List<OfferStudentSummaryResponse> resultado =
                service.searchStudentsByNameForApi(10L, responsavel, null);

        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("Nome em branco retorna todos os alunos")
    void nomeEmBranco_retornaTodosOsAlunos() {
        // Ramo: nome em branco -> sem filtro
        OfferStudent ana = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Ana Paula");
        OfferStudent bruno = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Bruno Costa");
        prepararBuscaPorNome(ana, bruno);

        List<OfferStudentSummaryResponse> resultado =
                service.searchStudentsByNameForApi(10L, responsavel, "   ");

        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("Nome sem correspondência retorna lista vazia")
    void nomeSemCorrespondencia_retornaListaVazia() {
        // Ramo: filtro não casa com ninguém
        OfferStudent ana = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Ana Paula");
        OfferStudent bruno = criarInscricao(offer, StudentOfferStatus.NAO_ENVIADO, "Bruno Costa");
        prepararBuscaPorNome(ana, bruno);

        List<OfferStudentSummaryResponse> resultado =
                service.searchStudentsByNameForApi(10L, responsavel, "zzz");

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Outro professor não pode buscar alunos")
    void buscaPorProfessorNaoResponsavel_lancaAccessDeniedException() {
        // Ramo: professor diferente do responsável -> acesso negado
        ofertaExiste();

        assertThrows(AccessDeniedException.class,
                () -> service.searchStudentsByNameForApi(10L, criarOutroProfessor(), "a"));
    }

    // ---------- PR.01 concluir relatório ----------

    @Test
    @DisplayName("Conclui relatório registrando parecer, frequência, nota e log")
    void relatorioAprovadoPeloSupervisor_concluiRelatorio() {
        // Ramo: caminho feliz de concludeReport
        Report report = new Report();
        inscricaoExiste();

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(report));

        service.concludeReport(10L, 2L, responsavel, criarFormConclusao());

        assertEquals("Aprovado", report.getResponsavelParecer());
        assertEquals(95, report.getResponsavelFrequencia());
        assertEquals(GradeOption.A, report.getResponsavelNota());
        assertNotNull(report.getResponsavelApprovedAt());
        assertEquals(StudentOfferStatus.CONCLUIDO_RESPONSAVEL, os.getStatus());

        verify(reportRepository, times(1)).save(report);
        verify(offerStudentRepository, times(1)).save(os);
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR
                        && log.getNewStatus() == StudentOfferStatus.CONCLUIDO_RESPONSAVEL
                        && "Relatório concluído pelo professor responsável".equals(log.getDescription())));
    }

    @Test
    @DisplayName("Conclusão exige relatório aprovado pelo supervisor")
    void statusRelatorioEnviado_concluirLancaIllegalArgumentException() {
        // Ramo: status diferente de RELATORIO_APROVADO_SUPERVISOR -> exceção
        os.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        inscricaoExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.concludeReport(10L, 2L, responsavel, criarFormConclusao()));

        assertEquals("Ação indisponível: o status atual é \"Relatório enviado\".", ex.getMessage());

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conclusão sem relatório lança exceção")
    void relatorioInexistente_concluirLancaIllegalArgumentException() {
        // Ramo: relatório não encontrado -> exceção
        inscricaoExiste();

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.concludeReport(10L, 2L, responsavel, criarFormConclusao()));

        assertEquals("Relatório não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Outro professor não conclui relatório")
    void concluirPorProfessorNaoResponsavel_lancaAccessDeniedException() {
        // Ramo: professor diferente do responsável -> acesso negado
        inscricaoExiste();

        assertThrows(AccessDeniedException.class,
                () -> service.concludeReport(10L, 2L, criarOutroProfessor(), criarFormConclusao()));

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conclusão via API devolve o resumo atualizado")
    void concluirRelatorioParaApi_retornaResumoDoMapper() {
        // Ramo: concludeReportForApi delega ao mapper
        inscricaoExiste();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(new Report()));
        when(offerApiMapper.toOfferStudentSummary(os)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado =
                service.concludeReportForApi(10L, 2L, responsavel, criarFormConclusao());

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Busca o relatório da inscrição")
    void relatorioExistente_getReportRetornaRelatorio() {
        // Ramo: relatório encontrado
        Report report = new Report();

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(report));

        Report resultado = service.getReport(os);

        assertSame(report, resultado);
    }

    @Test
    @DisplayName("Detalhes do relatório são mapeados para a API")
    void detalhesDoRelatorioParaApi_retornaDetalhesDoMapper() {
        // Ramo: getReportDetailsForApi delega ao mapper
        Report report = new Report();
        ReportDetailsResponse detalhes = mock(ReportDetailsResponse.class);
        inscricaoExiste();

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(report));
        when(offerApiMapper.toReportDetails(report)).thenReturn(detalhes);

        ReportDetailsResponse resultado = service.getReportDetailsForApi(10L, 2L, responsavel);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Detalhes de relatório inexistente lança exceção")
    void detalhesDeRelatorioInexistente_lancaIllegalArgumentException() {
        // Ramo: relatório não encontrado -> exceção
        inscricaoExiste();

        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getReportDetailsForApi(10L, 2L, responsavel));
    }

    // ---------- PR.02 analisar documentação ----------

    @Test
    @DisplayName("Analisa documentação registrando parecer, frequência, nota e log")
    void documentacaoEnviada_analisaDocumentacao() {
        // Ramo: caminho feliz de analyzeDocumentation
        Documentation doc = new Documentation();
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(doc));

        service.analyzeDocumentation(10L, 2L, responsavel, criarFormAnalise());

        assertEquals("Documentação válida", doc.getResponsavelParecer());
        assertEquals(80, doc.getResponsavelFrequencia());
        assertEquals(GradeOption.B, doc.getResponsavelNota());
        assertNotNull(doc.getResponsavelApprovedAt());
        assertEquals(StudentOfferStatus.CONCLUIDO_RESPONSAVEL, os.getStatus());

        verify(documentationRepository, times(1)).save(doc);
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.DOCUMENTACAO_ENVIADA
                        && "Documentação analisada pelo professor responsável".equals(log.getDescription())));
    }

    @Test
    @DisplayName("Análise exige status DOCUMENTACAO_ENVIADA")
    void statusNaoEnviado_analisarLancaIllegalArgumentException() {
        // Ramo: status diferente de DOCUMENTACAO_ENVIADA -> exceção
        os.setStatus(StudentOfferStatus.NAO_ENVIADO);
        inscricaoExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyzeDocumentation(10L, 2L, responsavel, criarFormAnalise()));

        assertEquals("Ação indisponível: o status atual é \"Não enviado\".", ex.getMessage());

        verify(documentationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Análise sem documentação lança exceção")
    void documentacaoInexistente_analisarLancaIllegalArgumentException() {
        // Ramo: documentação não encontrada -> exceção
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.analyzeDocumentation(10L, 2L, responsavel, criarFormAnalise()));

        assertEquals("Documentação não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Análise via API devolve o resumo atualizado")
    void analisarDocumentacaoParaApi_retornaResumoDoMapper() {
        // Ramo: analyzeDocumentationForApi delega ao mapper
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(new Documentation()));
        when(offerApiMapper.toOfferStudentSummary(os)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado =
                service.analyzeDocumentationForApi(10L, 2L, responsavel, criarFormAnalise());

        assertSame(resumo, resultado);
    }

    // ---------- Mud4 devolver documentação ----------

    private ReturnDocumentationFormDTO criarFormDevolucao() {
        ReturnDocumentationFormDTO f = new ReturnDocumentationFormDTO();
        f.setParecer("Anexe o comprovante de carga horária");
        return f;
    }

    @Test
    @DisplayName("Devolve documentação registrando parecer e reabrindo o envio")
    void documentacaoEnviada_devolveReabrindoEnvio() {
        // Ramo: caminho feliz de returnDocumentation -> status volta para NAO_ENVIADO
        Documentation doc = new Documentation();
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(doc));

        service.returnDocumentation(10L, 2L, responsavel, criarFormDevolucao());

        assertEquals("Anexe o comprovante de carga horária", doc.getDevolucaoParecer());
        assertNotNull(doc.getDevolvidaAt());
        assertNull(doc.getResponsavelApprovedAt());
        assertEquals(StudentOfferStatus.NAO_ENVIADO, os.getStatus());

        verify(documentationRepository, times(1)).save(doc);
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.DOCUMENTACAO_ENVIADA
                        && log.getNewStatus() == StudentOfferStatus.NAO_ENVIADO
                        && "Documentação devolvida pelo professor responsável".equals(log.getDescription())));
    }

    @Test
    @DisplayName("Devolução exige status DOCUMENTACAO_ENVIADA")
    void statusPlanoEnviado_devolverLancaIllegalArgumentException() {
        // Ramo: status diferente de DOCUMENTACAO_ENVIADA -> exceção
        os.setStatus(StudentOfferStatus.PLANO_ENVIADO);
        inscricaoExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.returnDocumentation(10L, 2L, responsavel, criarFormDevolucao()));

        assertEquals("Ação indisponível: o status atual é \"Plano enviado\".", ex.getMessage());

        verify(documentationRepository, never()).save(any());
        verifyNoInteractions(statusChangeLogRepository);
    }

    @Test
    @DisplayName("Devolução sem documentação lança exceção")
    void documentacaoInexistente_devolverLancaIllegalArgumentException() {
        // Ramo: documentação não encontrada -> exceção
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.returnDocumentation(10L, 2L, responsavel, criarFormDevolucao()));

        assertEquals("Documentação não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Devolução por outro professor é negada")
    void professorNaoResponsavel_devolverLancaAccessDenied() {
        // Ramo: usuário não é o responsável da oferta -> AccessDeniedException
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();

        assertThrows(AccessDeniedException.class,
                () -> service.returnDocumentation(10L, 2L, criarOutroProfessor(), criarFormDevolucao()));

        verify(documentationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Devolução via API devolve o resumo atualizado")
    void devolverDocumentacaoParaApi_retornaResumoDoMapper() {
        // Ramo: returnDocumentationForApi delega ao mapper
        os.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);
        inscricaoExiste();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(new Documentation()));
        when(offerApiMapper.toOfferStudentSummary(os)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado =
                service.returnDocumentationForApi(10L, 2L, responsavel, criarFormDevolucao());

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Detalhes da documentação são mapeados para a API")
    void detalhesDaDocumentacaoParaApi_retornaDetalhesDoMapper() {
        // Ramo: getDocumentationDetailsForApi delega ao mapper
        Documentation doc = new Documentation();
        DocumentationDetailsResponse detalhes = mock(DocumentationDetailsResponse.class);
        inscricaoExiste();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(doc));
        when(offerApiMapper.toDocumentationDetails(doc)).thenReturn(detalhes);

        DocumentationDetailsResponse resultado = service.getDocumentationDetailsForApi(10L, 2L, responsavel);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Busca a documentação da inscrição")
    void documentacaoExistente_getDocumentationRetornaDocumentacao() {
        // Ramo: documentação encontrada
        Documentation doc = new Documentation();

        when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.of(doc));

        Documentation resultado = service.getDocumentation(os);

        assertSame(doc, resultado);
    }

    // ---------- plano de trabalho ----------

    @Test
    @DisplayName("Busca o plano de trabalho")
    void planoExistente_retornaPlanoDeTrabalho() {
        // Ramo: plano encontrado
        WorkPlan plan = new WorkPlan();

        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.of(plan));

        WorkPlan resultado = service.getWorkPlan(os);

        assertSame(plan, resultado);
    }

    @Test
    @DisplayName("Plano inexistente lança exceção")
    void planoInexistente_lancaIllegalArgumentException() {
        // Ramo: plano não encontrado -> exceção
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getWorkPlan(os));

        assertEquals("Plano de trabalho não encontrado.", ex.getMessage());
    }

    // ---------- encerramento da oferta ----------

    @Test
    @DisplayName("Solicita encerramento quando todos os alunos estão concluídos")
    void todosAlunosConcluidos_solicitaEncerramento() {
        // Ramo: allMatch(CONCLUIDO_RESPONSAVEL) verdadeiro
        ofertaExiste();
        OfferStudent a = criarInscricao(offer, StudentOfferStatus.CONCLUIDO_RESPONSAVEL, "A");
        OfferStudent b = criarInscricao(offer, StudentOfferStatus.CONCLUIDO_RESPONSAVEL, "B");

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(a, b));

        service.closeOffer(10L, responsavel, "Boas práticas");

        assertEquals(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO, offer.getStatus());
        assertEquals("Boas práticas", offer.getLessonsLearned());
        assertNotNull(offer.getClosureRequestedAt());
        assertSame(responsavel, offer.getClosureRequestedBy());

        verify(offerRepository, times(1)).save(offer);
    }

    @Test
    @DisplayName("Aluno pendente impede o encerramento")
    void alunoNaoConcluido_encerrarLancaIllegalArgumentException() {
        // Ramo: algum aluno não concluído -> exceção
        ofertaExiste();
        OfferStudent a = criarInscricao(offer, StudentOfferStatus.CONCLUIDO_RESPONSAVEL, "A");
        OfferStudent b = criarInscricao(offer, StudentOfferStatus.PLANO_ENVIADO, "B");

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(a, b));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(10L, responsavel, "x"));

        assertEquals("Todos os alunos devem estar concluídos antes do encerramento.", ex.getMessage());
        assertEquals(OfferStatus.EM_ANDAMENTO, offer.getStatus());

        verify(offerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Só o responsável pode solicitar encerramento")
    void encerrarPorProfessorNaoResponsavel_lancaIllegalArgumentException() {
        // Ramo: professor diferente do responsável -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(10L, criarOutroProfessor(), "x"));

        assertEquals("Você não é o responsável por esta oferta.", ex.getMessage());

        verify(offerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Oferta sem alunos pode ser encerrada")
    void ofertaSemAlunos_encerramentoEhPermitido() {
        // BUG: allMatch em lista vazia é true, então uma oferta sem nenhum aluno pode ser encerrada
        ofertaExiste();

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of());

        service.closeOffer(10L, responsavel, "x");

        assertEquals(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO, offer.getStatus());
    }

    @Test
    @DisplayName("Oferta já concluída volta a aguardar encerramento")
    void ofertaConcluida_encerramentoReabreOferta() {
        // BUG: closeOffer não checa o status atual da oferta; CONCLUIDA volta para AGUARDANDO_ENCERRAMENTO_SECRETARIO (viola RNG-4)
        offer.setStatus(OfferStatus.CONCLUIDA);
        ofertaExiste();

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of());

        service.closeOffer(10L, responsavel, "x");

        assertEquals(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO, offer.getStatus());
    }

    @Test
    @DisplayName("Encerramento via API devolve o resumo da oferta")
    void encerrarOfertaParaApi_retornaResumoDoMapper() {
        // Ramo: closeOfferForApi delega ao mapper
        ofertaExiste();
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of());
        when(offerApiMapper.toOfferSummary(offer)).thenReturn(resumo);

        OfferSummaryResponse resultado = service.closeOfferForApi(10L, responsavel, "x");

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Resumo indica que não pode encerrar com aluno pendente")
    void alunoPendente_resumoIndicaQueNaoPodeEncerrar() {
        // Ramo: canClose = false
        ofertaExiste();
        List<OfferStudent> alunos = List.of(criarInscricao(offer, StudentOfferStatus.PLANO_ENVIADO, "A"));
        ResponsibleCloseOfferSummaryResponse resposta = mock(ResponsibleCloseOfferSummaryResponse.class);

        when(offerStudentRepository.findByOffer(offer)).thenReturn(alunos);
        when(offerApiMapper.toResponsibleCloseSummary(offer, alunos, false)).thenReturn(resposta);

        ResponsibleCloseOfferSummaryResponse resultado = service.getCloseOfferSummaryForApi(10L, responsavel);

        assertSame(resposta, resultado);
    }

    @Test
    @DisplayName("Resumo indica que pode encerrar com todos concluídos")
    void todosConcluidos_resumoIndicaQuePodeEncerrar() {
        // Ramo: canClose = true
        ofertaExiste();
        List<OfferStudent> alunos = List.of(criarInscricao(offer, StudentOfferStatus.CONCLUIDO_RESPONSAVEL, "A"));
        ResponsibleCloseOfferSummaryResponse resposta = mock(ResponsibleCloseOfferSummaryResponse.class);

        when(offerStudentRepository.findByOffer(offer)).thenReturn(alunos);
        when(offerApiMapper.toResponsibleCloseSummary(offer, alunos, true)).thenReturn(resposta);

        ResponsibleCloseOfferSummaryResponse resultado = service.getCloseOfferSummaryForApi(10L, responsavel);

        assertSame(resposta, resultado);
    }

    @Test
    @DisplayName("Outro professor não vê o resumo de encerramento")
    void resumoPorProfessorNaoResponsavel_lancaAccessDeniedException() {
        // Ramo: professor diferente do responsável -> acesso negado
        ofertaExiste();

        assertThrows(AccessDeniedException.class,
                () -> service.getCloseOfferSummaryForApi(10L, criarOutroProfessor()));
    }
}

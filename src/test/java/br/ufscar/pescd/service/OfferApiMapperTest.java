package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.DocumentationDetailsResponse;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentSummaryResponse;
import br.ufscar.pescd.dto.OfferSummaryResponse;
import br.ufscar.pescd.dto.ReportDetailsResponse;
import br.ufscar.pescd.dto.ResponsibleCloseOfferSummaryResponse;
import br.ufscar.pescd.dto.StatusLogResponse;
import br.ufscar.pescd.dto.StudentEnrollmentDetailsResponse;
import br.ufscar.pescd.dto.StudentOfferSummaryResponse;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.dto.WorkPlanDetailsResponse;
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
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferApiMapperTest {

    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    WorkPlanRepository workPlanRepository = mock(WorkPlanRepository.class);
    DocumentationRepository documentationRepository = mock(DocumentationRepository.class);
    ReportRepository reportRepository = mock(ReportRepository.class);
    StatusChangeLogRepository statusChangeLogRepository = mock(StatusChangeLogRepository.class);

    OfferApiMapper mapper;

    User professor;
    User student;
    Offer offer;
    OfferStudent enrollment;

    @BeforeEach
    void setUp() {
        mapper = new OfferApiMapper(offerStudentRepository, workPlanRepository,
                documentationRepository, reportRepository, statusChangeLogRepository);

        professor = criarUsuario(1L, "prof");
        student = criarUsuario(2L, "aluno");

        offer = new Offer();
        offer.setId(10L);
        offer.setName("Oferta 2025/1");
        offer.setSemester("2025/1");
        offer.setStartDate(LocalDate.of(2025, 2, 1));
        offer.setEndDate(LocalDate.of(2025, 6, 30));
        offer.setStatus(OfferStatus.EM_ANDAMENTO);
        offer.setResponsibleProfessor(professor);

        enrollment = new OfferStudent();
        enrollment.setId(100L);
        enrollment.setOffer(offer);
        enrollment.setStudent(student);
        enrollment.setStatus(StudentOfferStatus.NAO_ENVIADO);

        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
    }

    private User criarUsuario(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setFullName("Nome " + username);
        u.setEmail(username + "@ufscar.br");
        u.setUsername(username);
        u.setRole(UserRole.PROFESSOR);
        return u;
    }

    private Report criarRelatorio(Integer freq, GradeOption nota, LocalDateTime aprovadoEm) {
        Report r = new Report();
        r.setId(7L);
        r.setFrequency(90);
        r.setFileName("rel.pdf");
        r.setContentType("application/pdf");
        r.setResponsavelFrequencia(freq);
        r.setResponsavelNota(nota);
        r.setResponsavelApprovedAt(aprovadoEm);
        return r;
    }

    private Documentation criarDocumentacao(Integer freq, GradeOption nota, LocalDateTime aprovadoEm) {
        Documentation d = new Documentation();
        d.setId(8L);
        d.setInstitutionName("USP");
        d.setDisciplineName("Algoritmos");
        d.setDisciplineCourse("BCC");
        d.setWorkloadHours(60);
        d.setFileName("doc.pdf");
        d.setContentType("application/pdf");
        d.setResponsavelFrequencia(freq);
        d.setResponsavelNota(nota);
        d.setResponsavelApprovedAt(aprovadoEm);
        return d;
    }

    // inscrição concluída pelo responsável, com relatório ou documentação aprovados
    private OfferStudent criarInscricaoConcluida(Long id, GradeOption nota, Integer freq, boolean viaRelatorio) {
        OfferStudent os = new OfferStudent();
        os.setId(id);
        os.setOffer(offer);
        os.setStudent(student);
        os.setStatus(StudentOfferStatus.CONCLUIDO_RESPONSAVEL);
        if (viaRelatorio) {
            when(reportRepository.findByOfferStudent(os))
                    .thenReturn(Optional.of(criarRelatorio(freq, nota, LocalDateTime.now())));
            when(documentationRepository.findByOfferStudent(os)).thenReturn(Optional.empty());
        } else {
            when(documentationRepository.findByOfferStudent(os))
                    .thenReturn(Optional.of(criarDocumentacao(freq, nota, LocalDateTime.now())));
            when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.empty());
        }
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.empty());
        return os;
    }

    // ---------------- toUserSummary ----------------

    @Test
    @DisplayName("Usuário nulo vira resumo nulo")
    void usuarioNulo_retornaNulo() {
        // Ramo: user == null -> null
        UserSummaryResponse resultado = mapper.toUserSummary(null);

        assertNull(resultado);
    }

    @Test
    @DisplayName("Mapeia os campos do usuário sem expor senha")
    void usuarioValido_mapeiaCampos() {
        // Ramo: user != null -> copia id, nome, e-mail e username
        UserSummaryResponse resultado = mapper.toUserSummary(professor);

        assertEquals(new UserSummaryResponse(1L, "Nome prof", "prof@ufscar.br", "prof"), resultado);
    }

    // ---------------- toOfferSummary / toOfferDetails ----------------

    @Test
    @DisplayName("Mapeia oferta com rótulo de status e contagem de alunos")
    void ofertaCompleta_mapeiaStatusEContagemDeAlunos() {
        // Ramo: oferta com responsável e solicitante de encerramento, sem closedBy
        when(offerStudentRepository.countByOffer(offer)).thenReturn(3L);
        offer.setLessonsLearned("Aprendizados");
        offer.setClosureRequestedBy(professor);

        OfferSummaryResponse resultado = mapper.toOfferSummary(offer);

        assertEquals(10L, resultado.id());
        assertEquals("Oferta 2025/1", resultado.name());
        assertEquals("2025/1", resultado.semester());
        assertEquals(OfferStatus.EM_ANDAMENTO, resultado.status());
        assertEquals("Em andamento", resultado.statusLabel());
        assertEquals("Aprendizados", resultado.lessonsLearned());
        assertEquals("prof", resultado.responsibleProfessor().username());
        assertEquals("prof", resultado.closureRequestedBy().username());
        assertNull(resultado.closedBy());
        assertEquals(3L, resultado.enrolledStudents());
    }

    @Test
    @DisplayName("Oferta sem professor responsável")
    void ofertaSemProfessorResponsavel_retornaResponsavelNulo() {
        // Ramo: responsibleProfessor == null -> toUserSummary(null)
        offer.setResponsibleProfessor(null);

        OfferSummaryResponse resultado = mapper.toOfferSummary(offer);

        assertNull(resultado.responsibleProfessor());
    }

    @Test
    @DisplayName("Mapeia detalhes da oferta com resumo dos alunos")
    void ofertaComUmAluno_retornaResumoDoAluno() {
        // Ramo: lista de alunos não vazia
        OfferDetailsResponse resultado = mapper.toOfferDetails(offer, List.of(enrollment));

        assertEquals(10L, resultado.offer().id());
        assertEquals(1, resultado.students().size());
        assertEquals(100L, resultado.students().get(0).enrollmentId());
    }

    @Test
    @DisplayName("Mapeia detalhes da oferta sem alunos")
    void ofertaSemAlunos_retornaListaVazia() {
        // Ramo: lista de alunos vazia
        OfferDetailsResponse resultado = mapper.toOfferDetails(offer, List.of());

        assertTrue(resultado.students().isEmpty());
    }

    // ---------------- toOfferStudentSummary ----------------

    @Test
    @DisplayName("Inscrição sem envios e sem nota")
    void inscricaoSemEnvios_retornaSemNotaEOrigemNula() {
        // Ramo: nenhum relatório/documentação/plano -> tudo nulo ou falso
        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertEquals(StudentOfferStatus.NAO_ENVIADO, resultado.status());
        assertEquals("Não enviado", resultado.statusLabel());
        assertFalse(resultado.hasWorkPlan());
        assertFalse(resultado.hasDocumentation());
        assertFalse(resultado.hasReport());
        assertNull(resultado.completionSource());
        assertNull(resultado.finalFrequency());
        assertNull(resultado.finalGrade());
        assertNull(resultado.finalGradeLabel());
        assertNull(resultado.supervisor());
        assertEquals("aluno", resultado.student().username());
    }

    @Test
    @DisplayName("Inscrição com plano de trabalho e supervisor")
    void inscricaoComPlanoDeTrabalho_indicaPlanoEnviado() {
        // Ramo: workPlan presente e supervisor definido
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(new WorkPlan()));
        enrollment.setSupervisor(professor);

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertTrue(resultado.hasWorkPlan());
        assertEquals("prof", resultado.supervisor().username());
    }

    @Test
    @DisplayName("Nota e frequência vêm do relatório (origem REPORT)")
    void relatorioAprovado_usaOrigemReport() {
        // Ramo: report com approvedAt -> completionSource REPORT
        when(reportRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarRelatorio(95, GradeOption.A, LocalDateTime.now())));

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertTrue(resultado.hasReport());
        assertEquals("REPORT", resultado.completionSource());
        assertEquals(95, resultado.finalFrequency());
        assertEquals(GradeOption.A, resultado.finalGrade());
        assertEquals("A — Excelente", resultado.finalGradeLabel());
    }

    @Test
    @DisplayName("Sem relatório usa a documentação (origem DOCUMENTATION)")
    void semRelatorioComDocumentacaoAprovada_usaOrigemDocumentation() {
        // Ramo: report vazio, documentação com approvedAt -> DOCUMENTATION
        when(documentationRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarDocumentacao(80, GradeOption.B, LocalDateTime.now())));

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertTrue(resultado.hasDocumentation());
        assertEquals("DOCUMENTATION", resultado.completionSource());
        assertEquals(80, resultado.finalFrequency());
        assertEquals(GradeOption.B, resultado.finalGrade());
    }

    @Test
    @DisplayName("Relatório tem prioridade sobre documentação")
    void relatorioEDocumentacaoAprovados_priorizaRelatorio() {
        // Ramo: ambos aprovados -> prevalece o relatório
        when(reportRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarRelatorio(70, GradeOption.C, LocalDateTime.now())));
        when(documentationRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarDocumentacao(99, GradeOption.A, LocalDateTime.now())));

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertEquals("REPORT", resultado.completionSource());
        assertEquals(70, resultado.finalFrequency());
        assertEquals(GradeOption.C, resultado.finalGrade());
    }

    @Test
    @DisplayName("Relatório sem nota do responsável cai para a documentação")
    void relatorioSemNotaComDocumentacao_usaDocumentacao() {
        // Ramo: report presente mas sem valores -> or(...) usa a documentação
        when(reportRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarRelatorio(null, null, null)));
        when(documentationRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarDocumentacao(88, GradeOption.D, LocalDateTime.now())));

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertEquals("DOCUMENTATION", resultado.completionSource());
        assertEquals(88, resultado.finalFrequency());
        assertEquals(GradeOption.D, resultado.finalGrade());
    }

    @Test
    @DisplayName("Relatório não aprovado pelo responsável mantém origem nula")
    void relatorioNaoAprovado_mantemOrigemNula() {
        // Ramo: report sem approvedAt e sem documentação -> completionSource null
        when(reportRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarRelatorio(null, null, null)));

        OfferStudentSummaryResponse resultado = mapper.toOfferStudentSummary(enrollment);

        assertTrue(resultado.hasReport());
        assertNull(resultado.completionSource());
    }

    // ---------------- composições por inscrição ----------------

    @Test
    @DisplayName("Mapeia detalhes da inscrição com logs de status")
    void inscricaoComUmLog_retornaDetalhesComLogs() {
        // Ramo: toOfferStudentDetails com um log de status
        StatusChangeLog log = new StatusChangeLog();
        log.setId(1L);
        log.setNewStatus(StudentOfferStatus.NAO_ENVIADO);
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(enrollment))
                .thenReturn(List.of(log));

        OfferStudentDetailsResponse resultado = mapper.toOfferStudentDetails(enrollment);

        assertEquals(10L, resultado.offer().id());
        assertEquals(100L, resultado.enrollment().enrollmentId());
        assertEquals(1, resultado.statusLogs().size());
    }

    @Test
    @DisplayName("Mapeia resumo da oferta do aluno")
    void resumoDaOfertaDoAluno_retornaOfertaEInscricao() {
        // Ramo: toStudentOfferSummary combina oferta e inscrição
        StudentOfferSummaryResponse resultado = mapper.toStudentOfferSummary(enrollment);

        assertEquals(10L, resultado.offer().id());
        assertEquals(100L, resultado.enrollment().enrollmentId());
    }

    @Test
    @DisplayName("Detalhes completos sem plano, documentação nem relatório")
    void detalhesSemEnvios_retornaNulosELogsVazios() {
        // Ramo: três Optional vazios -> orElse(null)
        StudentEnrollmentDetailsResponse resultado = mapper.toStudentEnrollmentDetails(enrollment);

        assertNull(resultado.workPlan());
        assertNull(resultado.documentation());
        assertNull(resultado.report());
        assertTrue(resultado.statusLogs().isEmpty());
    }

    @Test
    @DisplayName("Detalhes completos com plano, documentação e relatório")
    void detalhesComTodosOsEnvios_mapeiaOsTres() {
        // Ramo: três Optional presentes -> map(...)
        WorkPlan plano = new WorkPlan();
        plano.setId(5L);
        plano.setDisciplineCode("DC001");
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plano));
        when(documentationRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarDocumentacao(null, null, null)));
        when(reportRepository.findByOfferStudent(enrollment))
                .thenReturn(Optional.of(criarRelatorio(null, null, null)));

        StudentEnrollmentDetailsResponse resultado = mapper.toStudentEnrollmentDetails(enrollment);

        assertEquals("DC001", resultado.workPlan().disciplineCode());
        assertEquals("USP", resultado.documentation().institutionName());
        assertEquals(90, resultado.report().frequency());
    }

    // ---------------- logs de status ----------------

    @Test
    @DisplayName("Mapeia log com status anterior")
    void logComStatusAnterior_mapeiaRotulos() {
        // Ramo: previousStatus != null -> rótulo preenchido
        StatusChangeLog log = new StatusChangeLog();
        log.setId(3L);
        log.setPreviousStatus(StudentOfferStatus.NAO_ENVIADO);
        log.setNewStatus(StudentOfferStatus.PLANO_ENVIADO);
        log.setDescription("Plano de trabalho enviado");
        log.setChangedAt(LocalDateTime.of(2025, 3, 1, 10, 0));

        StatusLogResponse resultado = mapper.toStatusLog(log);

        assertEquals(StudentOfferStatus.NAO_ENVIADO, resultado.previousStatus());
        assertEquals("Não enviado", resultado.previousStatusLabel());
        assertEquals("Plano enviado", resultado.newStatusLabel());
        assertEquals("Plano de trabalho enviado", resultado.description());
    }

    @Test
    @DisplayName("Mapeia log sem status anterior")
    void logSemStatusAnterior_retornaRotuloAnteriorNulo() {
        // Ramo: previousStatus == null -> rótulo nulo
        StatusChangeLog log = new StatusChangeLog();
        log.setNewStatus(StudentOfferStatus.NAO_ENVIADO);

        StatusLogResponse resultado = mapper.toStatusLog(log);

        assertNull(resultado.previousStatus());
        assertNull(resultado.previousStatusLabel());
    }

    @Test
    @DisplayName("Lista logs na ordem do repositório")
    void doisLogs_mantemOrdemDoRepositorio() {
        // Ramo: toStatusLogs mapeia cada log preservando a ordem
        StatusChangeLog a = new StatusChangeLog();
        a.setId(1L);
        a.setNewStatus(StudentOfferStatus.NAO_ENVIADO);
        StatusChangeLog b = new StatusChangeLog();
        b.setId(2L);
        b.setNewStatus(StudentOfferStatus.PLANO_ENVIADO);
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(enrollment))
                .thenReturn(List.of(a, b));

        List<StatusLogResponse> resultado = mapper.toStatusLogs(enrollment);

        assertEquals(2, resultado.size());
        assertEquals(1L, resultado.get(0).id());
        assertEquals(2L, resultado.get(1).id());
    }

    // ---------------- detalhes ----------------

    @Test
    @DisplayName("Mapeia plano de trabalho")
    void planoDeTrabalho_mapeiaCampos() {
        // Ramo: toWorkPlanDetails copia os campos do plano
        WorkPlan plano = new WorkPlan();
        plano.setId(1L);
        plano.setDisciplineCode("DC1");
        plano.setDisciplineName("Algoritmos");
        plano.setDisciplineCourse("BCC");
        plano.setFileName("plano.pdf");
        plano.setContentType("application/pdf");
        plano.setSupervisorParecer("ok");
        plano.setSupervisorApprovedAt(LocalDateTime.of(2025, 4, 1, 0, 0));

        WorkPlanDetailsResponse resultado = mapper.toWorkPlanDetails(plano);

        assertEquals(1L, resultado.id());
        assertEquals("Algoritmos", resultado.disciplineName());
        assertEquals("plano.pdf", resultado.fileName());
        assertEquals("ok", resultado.supervisorParecer());
        assertNotNull(resultado.supervisorApprovedAt());
    }

    @Test
    @DisplayName("Mapeia relatório com notas de supervisor e responsável")
    void relatorioComNotas_mapeiaRotulos() {
        // Ramo: supervisorNotaSugestao e responsavelNota presentes -> rótulos
        Report relatorio = criarRelatorio(90, GradeOption.A, LocalDateTime.now());
        relatorio.setSupervisorNotaSugestao(GradeOption.B);
        relatorio.setSupervisorFrequencia(85);

        ReportDetailsResponse resultado = mapper.toReportDetails(relatorio);

        assertEquals("B — Bom", resultado.supervisorNotaSugestaoLabel());
        assertEquals("A — Excelente", resultado.responsavelNotaLabel());
        assertEquals(85, resultado.supervisorFrequencia());
    }

    @Test
    @DisplayName("Mapeia relatório sem notas com rótulos nulos")
    void relatorioSemNotas_retornaRotulosNulos() {
        // Ramo: notas nulas -> rótulos nulos
        ReportDetailsResponse resultado = mapper.toReportDetails(criarRelatorio(null, null, null));

        assertNull(resultado.supervisorNotaSugestaoLabel());
        assertNull(resultado.responsavelNotaLabel());
    }

    @Test
    @DisplayName("Mapeia documentação com nota")
    void documentacaoComNota_mapeiaRotulo() {
        // Ramo: responsavelNota presente -> rótulo preenchido
        DocumentationDetailsResponse resultado = mapper.toDocumentationDetails(
                criarDocumentacao(100, GradeOption.E, LocalDateTime.now()));

        assertEquals(60, resultado.workloadHours());
        assertEquals("E — Reprovado", resultado.responsavelNotaLabel());
    }

    @Test
    @DisplayName("Mapeia documentação sem nota com rótulo nulo")
    void documentacaoSemNota_retornaRotuloNulo() {
        // Ramo: responsavelNota nula -> rótulo nulo
        DocumentationDetailsResponse resultado =
                mapper.toDocumentationDetails(criarDocumentacao(null, null, null));

        assertNull(resultado.responsavelNota());
        assertNull(resultado.responsavelNotaLabel());
    }

    // ---------------- toResponsibleCloseSummary ----------------

    @Test
    @DisplayName("Sem frequências a média é nula")
    void semFrequencias_retornaMediaNula() {
        // Ramo: frequencies.isEmpty() -> média null
        ResponsibleCloseOfferSummaryResponse resultado =
                mapper.toResponsibleCloseSummary(offer, List.of(enrollment), false);

        assertNull(resultado.averageFrequency());
        assertTrue(resultado.gradeDistribution().isEmpty());
        assertEquals(0, resultado.documentationCompletions());
        assertEquals(0, resultado.reportCompletions());
        assertFalse(resultado.canClose());
    }

    @Test
    @DisplayName("Oferta sem alunos tem média nula")
    void ofertaSemAlunosNoFechamento_retornaMediaNula() {
        // Ramo: lista de alunos vazia
        ResponsibleCloseOfferSummaryResponse resultado =
                mapper.toResponsibleCloseSummary(offer, List.of(), true);

        assertTrue(resultado.students().isEmpty());
        assertNull(resultado.averageFrequency());
        assertTrue(resultado.canClose());
    }

    @Test
    @DisplayName("Calcula média, distribuição de notas e origens de conclusão")
    void tresAlunosConcluidos_calculaMediaDistribuicaoEOrigens() {
        // Ramo: frequências não vazias, notas agrupadas, contagem por origem
        OfferStudent a = criarInscricaoConcluida(1L, GradeOption.A, 100, true);
        OfferStudent b = criarInscricaoConcluida(2L, GradeOption.A, 75, false);
        OfferStudent c = criarInscricaoConcluida(3L, GradeOption.B, 80, true);

        ResponsibleCloseOfferSummaryResponse resultado =
                mapper.toResponsibleCloseSummary(offer, List.of(a, b, c), true);

        assertEquals(0, new BigDecimal("85.00").compareTo(resultado.averageFrequency()));
        assertEquals(2L, resultado.gradeDistribution().get("A"));
        assertEquals(1L, resultado.gradeDistribution().get("B"));
        assertEquals(2L, resultado.reportCompletions());
        assertEquals(1L, resultado.documentationCompletions());
        assertEquals(3, resultado.students().size());
    }

    @Test
    @DisplayName("Arredonda a média para duas casas decimais")
    void mediaDizima_arredondaParaDuasCasas() {
        // Ramo: setScale(2, HALF_UP) sobre 99,666...
        OfferStudent a = criarInscricaoConcluida(1L, GradeOption.A, 100, true);
        OfferStudent b = criarInscricaoConcluida(2L, GradeOption.A, 100, true);
        OfferStudent c = criarInscricaoConcluida(3L, GradeOption.A, 99, true);

        ResponsibleCloseOfferSummaryResponse resultado =
                mapper.toResponsibleCloseSummary(offer, List.of(a, b, c), true);

        assertEquals(0, new BigDecimal("99.67").compareTo(resultado.averageFrequency()));
    }

    @Test
    @DisplayName("Ignora alunos sem nota ou frequência nas estatísticas")
    void alunoSemNota_naoEntraNasEstatisticas() {
        // Ramo: filter(Objects::nonNull) descarta valores nulos
        OfferStudent concluido = criarInscricaoConcluida(1L, GradeOption.C, 60, true);

        ResponsibleCloseOfferSummaryResponse resultado =
                mapper.toResponsibleCloseSummary(offer, List.of(concluido, enrollment), false);

        assertEquals(0, new BigDecimal("60.00").compareTo(resultado.averageFrequency()));
        assertEquals(Set.of("C"), resultado.gradeDistribution().keySet());
    }
}

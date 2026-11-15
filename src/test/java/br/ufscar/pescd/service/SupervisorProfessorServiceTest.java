package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.ApprovePlanFormDTO;
import br.ufscar.pescd.dto.ApproveReportFormDTO;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentSummaryResponse;
import br.ufscar.pescd.dto.ReportDetailsResponse;
import br.ufscar.pescd.dto.SupervisorDashboardDTO;
import br.ufscar.pescd.dto.WorkPlanDetailsResponse;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.GradeOption;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class SupervisorProfessorServiceTest {

    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    WorkPlanRepository workPlanRepository = mock(WorkPlanRepository.class);
    ReportRepository reportRepository = mock(ReportRepository.class);
    StatusChangeLogRepository statusChangeLogRepository = mock(StatusChangeLogRepository.class);
    OfferApiMapper offerApiMapper = mock(OfferApiMapper.class);

    SupervisorProfessorService service;

    User supervisor;
    Offer offer;
    OfferStudent os;

    @BeforeEach
    void setUp() {
        service = new SupervisorProfessorService(offerStudentRepository, workPlanRepository,
                reportRepository, statusChangeLogRepository, offerApiMapper);

        supervisor = new User();
        supervisor.setId(50L);
        supervisor.setUsername("super");
        supervisor.setRole(UserRole.PROFESSOR);

        offer = new Offer();
        offer.setId(10L);
        offer.setSemester("2025/1");

        os = new OfferStudent();
        os.setId(100L);
        os.setOffer(offer);
        os.setSupervisor(supervisor);
        os.setStatus(StudentOfferStatus.PLANO_ENVIADO);

        when(offerStudentRepository.findByOffer_IdAndStudent_Id(10L, 2L)).thenReturn(Optional.of(os));
    }

    private Offer criarOferta(String semestre) {
        Offer o = new Offer();
        o.setSemester(semestre);
        return o;
    }

    private OfferStudent criarSupervisionado(Offer o) {
        OfferStudent s = new OfferStudent();
        s.setOffer(o);
        s.setSupervisor(supervisor);
        return s;
    }

    private ApproveReportFormDTO criarReportForm() {
        ApproveReportFormDTO f = new ApproveReportFormDTO();
        f.setParecer("Bom relatório");
        f.setFrequencia(88);
        f.setNotaSugestao(GradeOption.B);
        return f;
    }

    // ---------------- dashboard ----------------

    @Test
    @DisplayName("Agrupa alunos por oferta e ordena por semestre decrescente")
    void dashboardComVariasOfertas_agrupaEOrdenaPorSemestreDecrescente() {
        // Ramo: groupingBy por oferta + sorted com reverseOrder
        Offer o1 = criarOferta("2024/2");
        Offer o2 = criarOferta("2025/2");
        Offer o3 = criarOferta("2025/1");
        OfferStudent a = criarSupervisionado(o1);
        OfferStudent b = criarSupervisionado(o2);
        OfferStudent c = criarSupervisionado(o3);
        OfferStudent d = criarSupervisionado(o2);
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of(a, b, c, d));

        List<SupervisorDashboardDTO> resultado = service.getDashboard(supervisor);

        assertEquals(3, resultado.size());
        assertEquals("2025/2", resultado.get(0).getOffer().getSemester());
        assertEquals("2025/1", resultado.get(1).getOffer().getSemester());
        assertEquals("2024/2", resultado.get(2).getOffer().getSemester());
        assertEquals(Set.of(b, d), new HashSet<>(resultado.get(0).getSupervisedStudents()));
    }

    @Test
    @DisplayName("Professor sem alunos supervisionados")
    void semSupervisionados_retornaListaVazia() {
        // Ramo: lista vazia -> dashboard vazio
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of());

        List<SupervisorDashboardDTO> resultado = service.getDashboard(supervisor);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("Mapeia o dashboard para a API")
    void dashboardParaApi_usaMapper() {
        // Ramo: cada item do dashboard -> toOfferDetails
        OfferStudent a = criarSupervisionado(offer);
        OfferDetailsResponse detalhes = new OfferDetailsResponse(null, List.of());
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of(a));
        when(offerApiMapper.toOfferDetails(offer, List.of(a))).thenReturn(detalhes);

        List<OfferDetailsResponse> resultado = service.getDashboardForApi(supervisor);

        assertEquals(List.of(detalhes), resultado);
    }

    // ---------------- controle de acesso ----------------

    @Test
    @DisplayName("Supervisor acessa a inscrição do seu aluno")
    void professorSupervisor_retornaInscricao() {
        // Ramo: inscrição existe e professor é o supervisor
        OfferStudent resultado = service.getEnrollmentForSupervisor(10L, 2L, supervisor);

        assertSame(os, resultado);
    }

    @Test
    @DisplayName("Inscrição inexistente")
    void inscricaoInexistente_lancaIllegalArgumentException() {
        // Ramo: findByOffer_IdAndStudent_Id vazio -> exceção
        when(offerStudentRepository.findByOffer_IdAndStudent_Id(10L, 2L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollmentForSupervisor(10L, 2L, supervisor));

        assertEquals("Inscrição não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Professor que não é o supervisor do aluno")
    void professorNaoSupervisor_lancaAccessDeniedException() {
        // Ramo: professor diferente do supervisor -> acesso negado
        User outro = new User();
        outro.setId(99L);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.getEnrollmentForSupervisor(10L, 2L, outro));

        assertEquals("Você não é o supervisor deste aluno nesta oferta.", ex.getMessage());
    }

    @Test
    @DisplayName("Aluno ainda sem supervisor")
    void alunoSemSupervisor_lancaAccessDeniedException() {
        // Ramo: supervisor nulo na inscrição -> acesso negado
        os.setSupervisor(null);

        assertThrows(AccessDeniedException.class,
                () -> service.getEnrollmentForSupervisor(10L, 2L, supervisor));
    }

    @Test
    @DisplayName("Mapeia detalhes da inscrição para a API")
    void detalhesDaInscricaoParaApi_usaMapper() {
        // Ramo: inscrição do supervisor -> toOfferStudentDetails
        OfferStudentDetailsResponse detalhes = new OfferStudentDetailsResponse(null, null, List.of());
        when(offerApiMapper.toOfferStudentDetails(os)).thenReturn(detalhes);

        OfferStudentDetailsResponse resultado = service.getEnrollmentDetailsForApi(10L, 2L, supervisor);

        assertSame(detalhes, resultado);
    }

    // ---------------- aprovar plano ----------------

    @Test
    @DisplayName("Aprova o plano registrando parecer, data e log")
    void planoEnviado_aprovaERegistraLog() {
        // Ramo: status PLANO_ENVIADO e plano existe -> aprovado
        WorkPlan plano = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.of(plano));

        service.approvePlan(10L, 2L, supervisor, "Plano adequado");

        assertEquals("Plano adequado", plano.getSupervisorParecer());
        assertNotNull(plano.getSupervisorApprovedAt());
        assertEquals(StudentOfferStatus.PLANO_APROVADO, os.getStatus());
        verify(workPlanRepository, times(1)).save(plano);
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.PLANO_ENVIADO
                        && log.getNewStatus() == StudentOfferStatus.PLANO_APROVADO
                        && "Plano de trabalho aprovado pelo professor supervisor".equals(log.getDescription())));
        verify(offerStudentRepository, times(1)).save(os);
    }

    @Test
    @DisplayName("Aprovar plano com status diferente de plano enviado")
    void aprovarPlanoComStatusPlanoAprovado_lancaIllegalArgumentException() {
        // Ramo: status != PLANO_ENVIADO -> exceção, nada salvo
        os.setStatus(StudentOfferStatus.PLANO_APROVADO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approvePlan(10L, 2L, supervisor, "ok"));

        assertEquals("Ação indisponível: o status atual é \"Plano aprovado\".", ex.getMessage());
        verify(workPlanRepository, never()).save(any());
        verify(statusChangeLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aprovar plano inexistente")
    void aprovarPlanoInexistente_lancaIllegalArgumentException() {
        // Ramo: plano de trabalho não encontrado -> exceção, status inalterado
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approvePlan(10L, 2L, supervisor, "ok"));

        assertEquals("Plano de trabalho não encontrado.", ex.getMessage());
        assertEquals(StudentOfferStatus.PLANO_ENVIADO, os.getStatus());
    }

    @Test
    @DisplayName("Professor não supervisor tenta aprovar plano")
    void aprovarPlanoPorProfessorNaoSupervisor_lancaAccessDeniedException() {
        // Ramo: acesso negado antes de qualquer alteração
        User outro = new User();

        assertThrows(AccessDeniedException.class,
                () -> service.approvePlan(10L, 2L, outro, "ok"));

        verify(workPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aprovar plano pela API retorna o resumo atualizado")
    void aprovarPlanoViaApi_retornaResumo() {
        // Ramo: approvePlanForApi -> approvePlan + toOfferStudentSummary
        WorkPlan plano = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.of(plano));
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(offerApiMapper.toOfferStudentSummary(os)).thenReturn(resumo);
        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        form.setParecer("ok");

        OfferStudentSummaryResponse resultado = service.approvePlanForApi(10L, 2L, supervisor, form);

        assertSame(resumo, resultado);
        assertEquals("ok", plano.getSupervisorParecer());
    }

    @Test
    @DisplayName("Busca o plano de trabalho do aluno")
    void planoExistente_retornaPlano() {
        // Ramo: plano encontrado no repositório
        WorkPlan plano = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.of(plano));

        WorkPlan resultado = service.getWorkPlan(os);

        assertSame(plano, resultado);
    }

    @Test
    @DisplayName("Mapeia detalhes do plano para a API")
    void detalhesDoPlanoParaApi_usaMapper() {
        // Ramo: inscrição do supervisor + plano -> toWorkPlanDetails
        WorkPlan plano = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(os)).thenReturn(Optional.of(plano));
        WorkPlanDetailsResponse detalhes = mock(WorkPlanDetailsResponse.class);
        when(offerApiMapper.toWorkPlanDetails(plano)).thenReturn(detalhes);

        WorkPlanDetailsResponse resultado = service.getWorkPlanDetailsForApi(10L, 2L, supervisor);

        assertSame(detalhes, resultado);
    }

    // ---------------- aprovar relatório ----------------

    @Test
    @DisplayName("Aprova o relatório com parecer, frequência e nota")
    void relatorioEnviado_aprovaERegistraLog() {
        // Ramo: status RELATORIO_ENVIADO e relatório existe -> aprovado
        os.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        Report relatorio = new Report();
        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(relatorio));

        service.approveReport(10L, 2L, supervisor, criarReportForm());

        assertEquals("Bom relatório", relatorio.getSupervisorParecer());
        assertEquals(88, relatorio.getSupervisorFrequencia());
        assertEquals(GradeOption.B, relatorio.getSupervisorNotaSugestao());
        assertNotNull(relatorio.getSupervisorApprovedAt());
        assertEquals(StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR, os.getStatus());
        verify(reportRepository, times(1)).save(relatorio);
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.RELATORIO_ENVIADO
                        && "Relatório aprovado pelo professor supervisor".equals(log.getDescription())));
    }

    @Test
    @DisplayName("Aprovar relatório com status diferente de relatório enviado")
    void aprovarRelatorioComStatusPlanoAprovado_lancaIllegalArgumentException() {
        // Ramo: status != RELATORIO_ENVIADO -> exceção, nada salvo
        os.setStatus(StudentOfferStatus.PLANO_APROVADO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approveReport(10L, 2L, supervisor, criarReportForm()));

        assertEquals("Ação indisponível: o status atual é \"Plano aprovado\".", ex.getMessage());
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aprovar relatório inexistente")
    void aprovarRelatorioInexistente_lancaIllegalArgumentException() {
        // Ramo: relatório não encontrado -> exceção
        os.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approveReport(10L, 2L, supervisor, criarReportForm()));

        assertEquals("Relatório não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Aprovar relatório pela API retorna o resumo atualizado")
    void aprovarRelatorioViaApi_retornaResumo() {
        // Ramo: approveReportForApi -> approveReport + toOfferStudentSummary
        os.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        Report relatorio = new Report();
        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(relatorio));
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(offerApiMapper.toOfferStudentSummary(os)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado =
                service.approveReportForApi(10L, 2L, supervisor, criarReportForm());

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Busca o relatório do aluno")
    void relatorioExistente_retornaRelatorio() {
        // Ramo: relatório encontrado no repositório
        Report relatorio = new Report();
        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(relatorio));

        Report resultado = service.getReport(os);

        assertSame(relatorio, resultado);
    }

    @Test
    @DisplayName("Mapeia detalhes do relatório para a API")
    void detalhesDoRelatorioParaApi_usaMapper() {
        // Ramo: inscrição do supervisor + relatório -> toReportDetails
        Report relatorio = new Report();
        when(reportRepository.findByOfferStudent(os)).thenReturn(Optional.of(relatorio));
        ReportDetailsResponse detalhes = mock(ReportDetailsResponse.class);
        when(offerApiMapper.toReportDetails(relatorio)).thenReturn(detalhes);

        ReportDetailsResponse resultado = service.getReportDetailsForApi(10L, 2L, supervisor);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Lista os logs de status do aluno")
    void logsDeStatus_retornaListaDoRepositorio() {
        // Ramo: delega ao repositório
        List<StatusChangeLog> logs = List.of(new StatusChangeLog());
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(os)).thenReturn(logs);

        List<StatusChangeLog> resultado = service.getStatusLogs(os);

        assertSame(logs, resultado);
    }
}

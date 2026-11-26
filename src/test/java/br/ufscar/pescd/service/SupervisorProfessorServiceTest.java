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
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorProfessorServiceTest {

    private static final Long OFFER_ID = 1L;
    private static final Long STUDENT_ID = 2L;

    @Mock
    private OfferStudentRepository offerStudentRepository;

    @Mock
    private WorkPlanRepository workPlanRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private StatusChangeLogRepository statusChangeLogRepository;

    @Mock
    private OfferApiMapper offerApiMapper;

    @InjectMocks
    private SupervisorProfessorService service;

    private User supervisor;
    private OfferStudent enrollment;

    @BeforeEach
    void setUp() {
        supervisor = user(10L, "alevada");

        Offer offer = offer(OFFER_ID, "2025/1");
        enrollment = new OfferStudent();
        enrollment.setId(100L);
        enrollment.setOffer(offer);
        enrollment.setStudent(user(STUDENT_ID, "jlima"));
        enrollment.setSupervisor(supervisor);
    }

    // PS.01

    @Test
    void dashboardAgrupaAlunosPorOfertaEOrdenaDoSemestreMaisRecente() {
        Offer antiga = offer(1L, "2024/2");
        Offer recente = offer(2L, "2025/1");
        OfferStudent aluno1 = enrollmentIn(antiga);
        OfferStudent aluno2 = enrollmentIn(recente);
        OfferStudent aluno3 = enrollmentIn(recente);
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of(aluno1, aluno2, aluno3));

        List<SupervisorDashboardDTO> dashboard = service.getDashboard(supervisor);

        assertThat(dashboard).extracting(SupervisorDashboardDTO::getOffer).containsExactly(recente, antiga);
        assertThat(dashboard.get(0).getSupervisedStudents()).containsExactlyInAnyOrder(aluno2, aluno3);
        assertThat(dashboard.get(1).getSupervisedStudents()).containsExactly(aluno1);
    }

    @Test
    void dashboardSemAlunosSupervisionadosRetornaListaVazia() {
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of());

        assertThat(service.getDashboard(supervisor)).isEmpty();
    }

    @Test
    void dashboardDaApiConverteCadaOfertaComOsAlunosSupervisionados() {
        when(offerStudentRepository.findBySupervisor(supervisor)).thenReturn(List.of(enrollment));
        OfferDetailsResponse response = mock(OfferDetailsResponse.class);
        when(offerApiMapper.toOfferDetails(enrollment.getOffer(), List.of(enrollment))).thenReturn(response);

        assertThat(service.getDashboardForApi(supervisor)).containsExactly(response);
    }

    @Test
    void detalhesDaInscricaoNaApiUsamOMapper() {
        givenEnrollmentExists();
        OfferStudentDetailsResponse response = mock(OfferStudentDetailsResponse.class);
        when(offerApiMapper.toOfferStudentDetails(enrollment)).thenReturn(response);

        assertThat(service.getEnrollmentDetailsForApi(OFFER_ID, STUDENT_ID, supervisor)).isSameAs(response);
    }

    // Busca da inscrição (base de PS.02 e PS.03)

    @Test
    void inscricaoDoProprioSupervisorERetornada() {
        givenEnrollmentExists();

        assertThat(service.getEnrollmentForSupervisor(OFFER_ID, STUDENT_ID, supervisor)).isSameAs(enrollment);
    }

    @Test
    void inscricaoInexistenteLancaExcecao() {
        when(offerStudentRepository.findByOffer_IdAndStudent_Id(OFFER_ID, STUDENT_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollmentForSupervisor(OFFER_ID, STUDENT_ID, supervisor));

        assertThat(ex.getMessage()).isEqualTo("Inscrição não encontrada.");
    }

    @Test
    void professorQueNaoESupervisorDoAlunoTemAcessoNegado() {
        givenEnrollmentExists();
        User outroProfessor = user(11L, "mnaldi");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> service.getEnrollmentForSupervisor(OFFER_ID, STUDENT_ID, outroProfessor));

        assertThat(ex.getMessage()).isEqualTo("Você não é o supervisor deste aluno nesta oferta.");
    }

    @Test
    void alunoSemSupervisorTemAcessoNegado() {
        enrollment.setSupervisor(null);
        givenEnrollmentExists();

        assertThrows(AccessDeniedException.class,
                () -> service.getEnrollmentForSupervisor(OFFER_ID, STUDENT_ID, supervisor));
    }

    // PS.02

    @Test
    void aprovarPlanoEnviadoSalvaParecerETimestampEMudaStatusParaPlanoAprovado() {
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);
        givenEnrollmentExists();
        WorkPlan plan = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plan));

        service.approvePlan(OFFER_ID, STUDENT_ID, supervisor, "Plano adequado.");

        assertThat(plan.getSupervisorParecer()).isEqualTo("Plano adequado.");
        assertThat(plan.getSupervisorApprovedAt()).isNotNull();
        verify(workPlanRepository).save(plan);
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_APROVADO);
        verify(offerStudentRepository).save(enrollment);
        assertStatusLogged(StudentOfferStatus.PLANO_ENVIADO, StudentOfferStatus.PLANO_APROVADO,
                "Plano de trabalho aprovado pelo professor supervisor");
    }

    @ParameterizedTest
    @EnumSource(value = StudentOfferStatus.class, names = "PLANO_ENVIADO", mode = EnumSource.Mode.EXCLUDE)
    void aprovarPlanoComStatusDiferenteDePlanoEnviadoLancaExcecao(StudentOfferStatus status) {
        enrollment.setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approvePlan(OFFER_ID, STUDENT_ID, supervisor, "Parecer"));

        assertThat(ex.getMessage())
                .isEqualTo("Ação indisponível: o status atual é \"" + status.getDisplayName() + "\".");
        assertThat(enrollment.getStatus()).isEqualTo(status);
        verify(workPlanRepository, never()).save(any());
        verify(statusChangeLogRepository, never()).save(any());
        verify(offerStudentRepository, never()).save(any());
    }

    @Test
    void aprovarPlanoSemPlanoCadastradoLancaExcecaoENaoMudaStatus() {
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);
        givenEnrollmentExists();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approvePlan(OFFER_ID, STUDENT_ID, supervisor, "Parecer"));

        assertThat(ex.getMessage()).isEqualTo("Plano de trabalho não encontrado.");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_ENVIADO);
        verify(statusChangeLogRepository, never()).save(any());
    }

    @Test
    void aprovarPlanoDeAlunoDeOutroSupervisorTemAcessoNegadoENaoMudaStatus() {
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);
        givenEnrollmentExists();

        assertThrows(AccessDeniedException.class,
                () -> service.approvePlan(OFFER_ID, STUDENT_ID, user(11L, "mnaldi"), "Parecer"));

        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_ENVIADO);
        verify(workPlanRepository, never()).save(any());
    }

    @Test
    void aprovarPlanoPelaApiUsaOParecerDoFormularioERetornaOResumoDaInscricao() {
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);
        givenEnrollmentExists();
        WorkPlan plan = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plan));
        OfferStudentSummaryResponse response = mock(OfferStudentSummaryResponse.class);
        when(offerApiMapper.toOfferStudentSummary(enrollment)).thenReturn(response);
        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        form.setParecer("Aprovado.");

        OfferStudentSummaryResponse result = service.approvePlanForApi(OFFER_ID, STUDENT_ID, supervisor, form);

        assertThat(result).isSameAs(response);
        assertThat(plan.getSupervisorParecer()).isEqualTo("Aprovado.");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.PLANO_APROVADO);
    }

    @Test
    void detalhesDoPlanoNaApiUsamOMapper() {
        givenEnrollmentExists();
        WorkPlan plan = new WorkPlan();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plan));
        WorkPlanDetailsResponse response = mock(WorkPlanDetailsResponse.class);
        when(offerApiMapper.toWorkPlanDetails(plan)).thenReturn(response);

        assertThat(service.getWorkPlanDetailsForApi(OFFER_ID, STUDENT_ID, supervisor)).isSameAs(response);
    }

    // PS.03

    @Test
    void aprovarRelatorioEnviadoSalvaDadosDoSupervisorEMudaStatus() {
        enrollment.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        givenEnrollmentExists();
        Report report = new Report();
        report.setFrequency(90);
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(report));

        service.approveReport(OFFER_ID, STUDENT_ID, supervisor, reportForm("Bom trabalho.", 85, GradeOption.B));

        assertThat(report.getSupervisorParecer()).isEqualTo("Bom trabalho.");
        assertThat(report.getSupervisorFrequencia()).isEqualTo(85);
        assertThat(report.getSupervisorNotaSugestao()).isEqualTo(GradeOption.B);
        assertThat(report.getSupervisorApprovedAt()).isNotNull();
        assertThat(report.getFrequency()).isEqualTo(90);
        verify(reportRepository).save(report);
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR);
        verify(offerStudentRepository).save(enrollment);
        assertStatusLogged(StudentOfferStatus.RELATORIO_ENVIADO, StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR,
                "Relatório aprovado pelo professor supervisor");
    }

    @ParameterizedTest
    @EnumSource(value = StudentOfferStatus.class, names = "RELATORIO_ENVIADO", mode = EnumSource.Mode.EXCLUDE)
    void aprovarRelatorioComStatusDiferenteDeRelatorioEnviadoLancaExcecao(StudentOfferStatus status) {
        enrollment.setStatus(status);
        givenEnrollmentExists();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approveReport(OFFER_ID, STUDENT_ID, supervisor, reportForm("Parecer", 80, GradeOption.A)));

        assertThat(ex.getMessage())
                .isEqualTo("Ação indisponível: o status atual é \"" + status.getDisplayName() + "\".");
        assertThat(enrollment.getStatus()).isEqualTo(status);
        verify(reportRepository, never()).save(any());
        verify(statusChangeLogRepository, never()).save(any());
        verify(offerStudentRepository, never()).save(any());
    }

    @Test
    void aprovarRelatorioSemRelatorioCadastradoLancaExcecaoENaoMudaStatus() {
        enrollment.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        givenEnrollmentExists();
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.approveReport(OFFER_ID, STUDENT_ID, supervisor, reportForm("Parecer", 80, GradeOption.A)));

        assertThat(ex.getMessage()).isEqualTo("Relatório não encontrado.");
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.RELATORIO_ENVIADO);
        verify(statusChangeLogRepository, never()).save(any());
    }

    @Test
    void aprovarRelatorioPelaApiRetornaOResumoDaInscricao() {
        enrollment.setStatus(StudentOfferStatus.RELATORIO_ENVIADO);
        givenEnrollmentExists();
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(new Report()));
        OfferStudentSummaryResponse response = mock(OfferStudentSummaryResponse.class);
        when(offerApiMapper.toOfferStudentSummary(enrollment)).thenReturn(response);

        OfferStudentSummaryResponse result = service.approveReportForApi(
                OFFER_ID, STUDENT_ID, supervisor, reportForm("Parecer", 75, GradeOption.C));

        assertThat(result).isSameAs(response);
        assertThat(enrollment.getStatus()).isEqualTo(StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR);
    }

    @Test
    void detalhesDoRelatorioNaApiUsamOMapper() {
        givenEnrollmentExists();
        Report report = new Report();
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(report));
        ReportDetailsResponse response = mock(ReportDetailsResponse.class);
        when(offerApiMapper.toReportDetails(report)).thenReturn(response);

        assertThat(service.getReportDetailsForApi(OFFER_ID, STUDENT_ID, supervisor)).isSameAs(response);
    }

    @Test
    void historicoDeStatusVemDoRepositorioEmOrdemCronologica() {
        StatusChangeLog log = new StatusChangeLog();
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(enrollment)).thenReturn(List.of(log));

        assertThat(service.getStatusLogs(enrollment)).containsExactly(log);
    }

    private void givenEnrollmentExists() {
        when(offerStudentRepository.findByOffer_IdAndStudent_Id(OFFER_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));
    }

    private void assertStatusLogged(StudentOfferStatus previous, StudentOfferStatus next, String description) {
        ArgumentCaptor<StatusChangeLog> captor = ArgumentCaptor.forClass(StatusChangeLog.class);
        verify(statusChangeLogRepository).save(captor.capture());
        StatusChangeLog log = captor.getValue();
        assertThat(log.getOfferStudent()).isSameAs(enrollment);
        assertThat(log.getPreviousStatus()).isEqualTo(previous);
        assertThat(log.getNewStatus()).isEqualTo(next);
        assertThat(log.getDescription()).isEqualTo(description);
    }

    private OfferStudent enrollmentIn(Offer offer) {
        OfferStudent os = new OfferStudent();
        os.setOffer(offer);
        os.setSupervisor(supervisor);
        return os;
    }

    private static ApproveReportFormDTO reportForm(String parecer, int frequencia, GradeOption nota) {
        ApproveReportFormDTO form = new ApproveReportFormDTO();
        form.setParecer(parecer);
        form.setFrequencia(frequencia);
        form.setNotaSugestao(nota);
        return form;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Offer offer(Long id, String semester) {
        Offer offer = new Offer();
        offer.setId(id);
        offer.setSemester(semester);
        return offer;
    }
}

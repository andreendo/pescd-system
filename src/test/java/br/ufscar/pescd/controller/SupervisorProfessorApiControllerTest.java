package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.ApprovePlanFormDTO;
import br.ufscar.pescd.dto.ApproveReportFormDTO;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentSummaryResponse;
import br.ufscar.pescd.dto.ReportDetailsResponse;
import br.ufscar.pescd.dto.WorkPlanDetailsResponse;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.service.SupervisorProfessorService;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupervisorProfessorApiControllerTest {

    SupervisorProfessorService service = mock(SupervisorProfessorService.class);
    UserRepository userRepository = mock(UserRepository.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);
    Authentication auth = mock(Authentication.class);

    SupervisorProfessorApiController controller;
    User professor = new User();
    OfferStudent enrollment = new OfferStudent();

    @BeforeEach
    void setUp() {
        controller = new SupervisorProfessorApiController(service, userRepository, storageService);
        professor.setId(5L);
        when(auth.getName()).thenReturn("sup");
        when(userRepository.findByUsername("sup")).thenReturn(Optional.of(professor));
    }

    // Caminho: GET ofertas supervisionadas -> resolve o professor e delega ao service
    @Test
    @DisplayName("Lista as ofertas supervisionadas")
    void listSupervisedOffers_delegaAoService() {

        List<OfferDetailsResponse> ofertas = List.of(mock(OfferDetailsResponse.class));
        when(service.getDashboardForApi(professor)).thenReturn(ofertas);

        List<OfferDetailsResponse> resultado = controller.listSupervisedOffers(auth);

        assertSame(ofertas, resultado);
    }

    // Caminho: usuario autenticado nao existe no banco -> IllegalArgumentException
    @Test
    @DisplayName("Usuario autenticado inexistente lanca IllegalArgumentException")
    void listSupervisedOffers_usuarioNaoExiste_lancaIllegalArgumentException() {

        when(userRepository.findByUsername("sup")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.listSupervisedOffers(auth));

        assertEquals("Usuário autenticado não encontrado.", ex.getMessage());
        verifyNoInteractions(service);
    }

    // Caminho: GET detalhes da matricula -> delega ao service
    @Test
    @DisplayName("Retorna os detalhes da matricula")
    void enrollmentDetails_delegaAoService() {

        OfferStudentDetailsResponse detalhes = mock(OfferStudentDetailsResponse.class);
        when(service.getEnrollmentDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        OfferStudentDetailsResponse resultado = controller.enrollmentDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: GET plano de trabalho -> delega ao service
    @Test
    @DisplayName("Retorna os detalhes do plano de trabalho")
    void workPlanDetails_delegaAoService() {

        WorkPlanDetailsResponse detalhes = mock(WorkPlanDetailsResponse.class);
        when(service.getWorkPlanDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        WorkPlanDetailsResponse resultado = controller.workPlanDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: POST aprovar plano -> delega ao service com o form
    @Test
    @DisplayName("Aprova o plano de trabalho")
    void approvePlan_delegaAoService() {

        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(service.approvePlanForApi(1L, 2L, professor, form)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado = controller.approvePlan(1L, 2L, form, auth);

        assertSame(resumo, resultado);
    }

    // Caminho: GET relatorio -> delega ao service
    @Test
    @DisplayName("Retorna os detalhes do relatorio")
    void reportDetails_delegaAoService() {

        ReportDetailsResponse detalhes = mock(ReportDetailsResponse.class);
        when(service.getReportDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        ReportDetailsResponse resultado = controller.reportDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: POST aprovar relatorio -> delega ao service com o form
    @Test
    @DisplayName("Aprova o relatorio")
    void approveReport_delegaAoService() {

        ApproveReportFormDTO form = new ApproveReportFormDTO();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(service.approveReportForApi(1L, 2L, professor, form)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado = controller.approveReport(1L, 2L, form, auth);

        assertSame(resumo, resultado);
    }

    // Caminho: download do plano -> PDF inline com o conteudo lido do storage
    @Test
    @DisplayName("Baixa o plano de trabalho como PDF")
    void downloadWorkPlan_retornaPdfInline() {

        WorkPlan plano = new WorkPlan();
        plano.setContentType("application/pdf");
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,abc");
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(enrollment);
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(storageService.read("1,abc")).thenReturn(new byte[]{9});

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, 2L, auth);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{9}, response.getBody());
    }

    // Caminho: download do relatorio -> PDF inline com o conteudo lido do storage
    @Test
    @DisplayName("Baixa o relatorio como PDF")
    void downloadReport_retornaPdfInline() {

        Report relatorio = new Report();
        relatorio.setContentType("application/pdf");
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("2,def");
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(enrollment);
        when(service.getReport(enrollment)).thenReturn(relatorio);
        when(storageService.read("2,def")).thenReturn(new byte[]{8});

        ResponseEntity<byte[]> response = controller.downloadReport(1L, 2L, auth);

        assertEquals("inline; filename=\"rel.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{8}, response.getBody());
    }

    // Caminho: supervisor sem acesso -> excecao do service propaga e o storage nao e lido
    @Test
    @DisplayName("Download sem acesso propaga a excecao do service")
    void downloadReport_semAcesso_propagaIllegalArgumentException() {

        when(service.getEnrollmentForSupervisor(1L, 2L, professor))
                .thenThrow(new IllegalArgumentException("Você não supervisiona este aluno."));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.downloadReport(1L, 2L, auth));

        assertEquals("Você não supervisiona este aluno.", ex.getMessage());
        verifyNoInteractions(storageService);
    }
}

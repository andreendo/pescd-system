package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.ApprovePlanFormDTO;
import br.ufscar.pescd.dto.ApproveReportFormDTO;
import br.ufscar.pescd.dto.SupervisorDashboardDTO;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.GradeOption;
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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupervisorProfessorControllerTest {

    SupervisorProfessorService service = mock(SupervisorProfessorService.class);
    UserRepository userRepository = mock(UserRepository.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);
    Authentication auth = mock(Authentication.class);

    SupervisorProfessorController controller;
    User professor = new User();
    OfferStudent os = new OfferStudent();
    Model model;
    RedirectAttributesModelMap redirect;

    @BeforeEach
    void setUp() {
        controller = new SupervisorProfessorController(service, userRepository, storageService);
        model = new ExtendedModelMap();
        redirect = new RedirectAttributesModelMap();
        professor.setId(5L);
        when(auth.getName()).thenReturn("sup");
        when(userRepository.findByUsername("sup")).thenReturn(Optional.of(professor));
    }

    // Caminho: PS.01 usuario encontrado -> dashboard e professor no model
    @Test
    @DisplayName("Dashboard carrega a lista e o professor no model")
    void dashboard_usuarioExiste_colocaDashboardNoModel() {

        List<SupervisorDashboardDTO> dashboard = List.of();
        when(service.getDashboard(professor)).thenReturn(dashboard);

        String view = controller.dashboard(model, auth);

        assertEquals("professor/supervisor/dashboard", view);
        assertSame(dashboard, model.getAttribute("dashboard"));
        assertSame(professor, model.getAttribute("professor"));
    }

    // Caminho: usuario autenticado nao existe no banco -> RuntimeException
    @Test
    @DisplayName("Usuario nao encontrado lanca RuntimeException")
    void dashboard_usuarioNaoEncontrado_lancaRuntimeException() {

        when(userRepository.findByUsername("sup")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.dashboard(model, auth));

        assertEquals("Usuário não encontrado.", ex.getMessage());
    }

    // Caminho: PS.02 GET -> matricula, plano e form vazio no model
    @Test
    @DisplayName("Formulario de aprovacao do plano carrega matricula, plano e form")
    void approvePlanForm_carregaMatriculaPlanoEForm() {

        WorkPlan plano = new WorkPlan();
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);

        String view = controller.approvePlanForm(1L, 2L, model, auth);

        assertEquals("professor/supervisor/approve-plan", view);
        assertSame(os, model.getAttribute("offerStudent"));
        assertSame(plano, model.getAttribute("workPlan"));
        assertInstanceOf(ApprovePlanFormDTO.class, model.getAttribute("form"));
    }

    // Caminho: PS.02 POST valido -> service aprova o plano e redireciona ao dashboard
    @Test
    @DisplayName("Aprovacao valida do plano redireciona para o dashboard")
    void approvePlan_valido_redirecionaComSucesso() {

        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        form.setParecer("Aprovado");
        BindingResult br = new BeanPropertyBindingResult(form, "form");

        String view = controller.approvePlan(1L, 2L, form, br, model, auth, redirect);

        assertEquals("redirect:/professor/supervisor/dashboard", view);
        assertEquals("Plano de trabalho aprovado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).approvePlan(1L, 2L, professor, "Aprovado");
    }

    // Caminho: PS.02 POST com parecer invalido -> reexibe o formulario sem aprovar
    @Test
    @DisplayName("Parecer invalido reexibe o formulario do plano")
    void approvePlan_parecerInvalido_reexibeFormulario() {

        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        BindingResult br = new BeanPropertyBindingResult(form, "form");
        br.rejectValue("parecer", "NotBlank", "obrigatório");
        WorkPlan plano = new WorkPlan();
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);

        String view = controller.approvePlan(1L, 2L, form, br, model, auth, redirect);

        assertEquals("professor/supervisor/approve-plan", view);
        assertSame(plano, model.getAttribute("workPlan"));
        verify(service, never()).approvePlan(anyLong(), anyLong(), any(), anyString());
    }

    // Caminho: download do plano -> PDF inline com o conteudo lido do storage
    @Test
    @DisplayName("Baixa o plano como PDF inline")
    void downloadPlan_retornaPdfInline() {

        WorkPlan plano = new WorkPlan();
        plano.setContentType("application/pdf");
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,a");
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[]{7});

        ResponseEntity<byte[]> response = controller.downloadPlan(1L, 2L, auth);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{7}, response.getBody());
    }

    // Caminho: PS.03 GET -> form pre-preenchido com a frequencia informada pelo aluno
    @Test
    @DisplayName("Formulario de aprovacao do relatorio pre-preenche a frequencia")
    void approveReportForm_preenchePelaFrequenciaDoAluno() {

        WorkPlan plano = new WorkPlan();
        Report relatorio = new Report();
        relatorio.setFrequency(90);
        List<StatusChangeLog> logs = List.of();
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);
        when(service.getReport(os)).thenReturn(relatorio);
        when(service.getStatusLogs(os)).thenReturn(logs);

        String view = controller.approveReportForm(1L, 2L, model, auth);

        ApproveReportFormDTO form = (ApproveReportFormDTO) model.getAttribute("form");
        assertEquals("professor/supervisor/approve-report", view);
        assertEquals(90, form.getFrequencia());
        assertSame(relatorio, model.getAttribute("report"));
        assertSame(logs, model.getAttribute("statusLogs"));
        assertArrayEquals(GradeOption.values(), (GradeOption[]) model.getAttribute("gradeOptions"));
    }

    // Caminho: PS.03 POST valido -> service aprova o relatorio e redireciona ao dashboard
    @Test
    @DisplayName("Aprovacao valida do relatorio redireciona para o dashboard")
    void approveReport_valido_redirecionaComSucesso() {

        ApproveReportFormDTO form = new ApproveReportFormDTO();
        BindingResult br = new BeanPropertyBindingResult(form, "form");

        String view = controller.approveReport(1L, 2L, form, br, model, auth, redirect);

        assertEquals("redirect:/professor/supervisor/dashboard", view);
        assertEquals("Relatório aprovado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).approveReport(1L, 2L, professor, form);
    }

    // Caminho: PS.03 POST invalido -> reexibe o formulario sem aprovar
    @Test
    @DisplayName("Aprovacao invalida do relatorio reexibe o formulario")
    void approveReport_invalido_reexibeFormulario() {

        ApproveReportFormDTO form = new ApproveReportFormDTO();
        BindingResult br = new BeanPropertyBindingResult(form, "form");
        br.rejectValue("frequencia", "Max", "entre 0 e 100");
        Report relatorio = new Report();
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(new WorkPlan());
        when(service.getReport(os)).thenReturn(relatorio);
        when(service.getStatusLogs(os)).thenReturn(List.of());

        String view = controller.approveReport(1L, 2L, form, br, model, auth, redirect);

        assertEquals("professor/supervisor/approve-report", view);
        assertSame(relatorio, model.getAttribute("report"));
        verify(service, never()).approveReport(anyLong(), anyLong(), any(), any());
    }

    // Caminho: download do relatorio -> PDF inline com o conteudo lido do storage
    @Test
    @DisplayName("Baixa o relatorio como PDF inline")
    void downloadReport_retornaPdfInline() {

        Report relatorio = new Report();
        relatorio.setContentType("application/pdf");
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("2,b");
        when(service.getEnrollmentForSupervisor(1L, 2L, professor)).thenReturn(os);
        when(service.getReport(os)).thenReturn(relatorio);
        when(storageService.read("2,b")).thenReturn(new byte[]{6});

        ResponseEntity<byte[]> response = controller.downloadReport(1L, 2L, auth);

        assertEquals("inline; filename=\"rel.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{6}, response.getBody());
    }

    // Caminho: supervisor sem vinculo com o aluno -> excecao do service propaga e o storage nao e lido
    @Test
    @DisplayName("Download sem permissao propaga a excecao do service")
    void downloadPlan_semPermissao_propagaIllegalArgumentException() {

        when(service.getEnrollmentForSupervisor(1L, 2L, professor))
                .thenThrow(new IllegalArgumentException("Você não supervisiona este aluno."));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.downloadPlan(1L, 2L, auth));

        assertEquals("Você não supervisiona este aluno.", ex.getMessage());
        verifyNoInteractions(storageService);
    }
}

package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.DocumentationForm;
import br.ufscar.pescd.dto.ReportForm;
import br.ufscar.pescd.dto.WorkPlanForm;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.service.StudentOfferService;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentOfferControllerTest {

    StudentOfferService service = mock(StudentOfferService.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);
    Authentication auth = mock(Authentication.class);

    StudentOfferController controller;
    OfferStudent enrollment = new OfferStudent();
    Model model;
    RedirectAttributesModelMap redirect;

    @BeforeEach
    void setUp() {
        controller = new StudentOfferController(service, storageService);
        model = new ExtendedModelMap();
        redirect = new RedirectAttributesModelMap();
        when(auth.getName()).thenReturn("aluno");
        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
    }

    // ---------- listagem e detalhes ----------

    // Caminho: GET /aluno/ofertas -> coloca as matriculas no model
    @Test
    @DisplayName("Lista as matriculas do aluno")
    void listEnrollments_colocaMatriculasNoModel() {

        List<OfferStudent> lista = List.of(enrollment);
        when(service.findStudentEnrollments("aluno")).thenReturn(lista);

        String view = controller.listEnrollments(auth, model);

        assertEquals("aluno/offers/list", view);
        assertSame(lista, model.getAttribute("enrollments"));
    }

    // Caminho: GET /aluno/ofertas/1 -> matricula, plano, documentacao, relatorio e logs no model
    @Test
    @DisplayName("Detalhes da matricula carregam plano, documentacao, relatorio e logs")
    void enrollmentDetails_carregaTudoNoModel() {

        WorkPlan plano = new WorkPlan();
        Documentation doc = new Documentation();
        Report relatorio = new Report();
        List<StatusChangeLog> logs = List.of();
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(service.getDocumentation(enrollment)).thenReturn(doc);
        when(service.getReport(enrollment)).thenReturn(relatorio);
        when(service.getStatusLogs(enrollment)).thenReturn(logs);

        String view = controller.enrollmentDetails(1L, auth, model);

        assertEquals("aluno/offers/details", view);
        assertSame(enrollment, model.getAttribute("enrollment"));
        assertSame(plano, model.getAttribute("workPlan"));
        assertSame(doc, model.getAttribute("documentation"));
        assertSame(relatorio, model.getAttribute("report"));
        assertSame(logs, model.getAttribute("logs"));
    }

    // ---------- plano de trabalho ----------

    // Caminho: GET plano sem form no model -> cria WorkPlanForm vazio
    @Test
    @DisplayName("Formulario do plano cria form vazio quando ausente")
    void workPlanForm_semFormNoModel_criaFormVazio() {

        List<User> professores = List.of(new User());
        when(service.listProfessors()).thenReturn(professores);

        String view = controller.workPlanForm(1L, auth, model);

        assertEquals("aluno/offers/plano", view);
        assertInstanceOf(WorkPlanForm.class, model.getAttribute("workPlanForm"));
        assertSame(professores, model.getAttribute("professors"));
    }

    // Caminho: GET plano com form ja no model (flash apos erro) -> mantem o form
    @Test
    @DisplayName("Formulario do plano mantem o form ja presente")
    void workPlanForm_comFormNoModel_mantemForm() {

        WorkPlanForm existente = new WorkPlanForm();
        model.addAttribute("workPlanForm", existente);
        when(service.listProfessors()).thenReturn(List.of());

        controller.workPlanForm(1L, auth, model);

        assertSame(existente, model.getAttribute("workPlanForm"));
    }

    // Caminho: POST plano valido -> service envia o plano e redireciona com mensagem de sucesso
    @Test
    @DisplayName("Envio valido do plano redireciona para os detalhes")
    void submitWorkPlan_valido_redirecionaComSucesso() {

        WorkPlanForm form = new WorkPlanForm();
        BindingResult br = new BeanPropertyBindingResult(form, "workPlanForm");

        String view = controller.submitWorkPlan(1L, form, br, auth, model, redirect);

        assertEquals("redirect:/aluno/ofertas/1", view);
        assertEquals("Plano de trabalho enviado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).submitWorkPlan(1L, "aluno", form);
    }

    // Caminho: POST plano com erro de binding -> reexibe o formulario sem chamar o service
    @Test
    @DisplayName("Plano com erro de validacao reexibe o formulario")
    void submitWorkPlan_comErroDeBinding_reexibeFormulario() {

        WorkPlanForm form = new WorkPlanForm();
        BindingResult br = new BeanPropertyBindingResult(form, "workPlanForm");
        br.rejectValue("disciplineCode", "NotBlank", "obrigatório");
        when(service.listProfessors()).thenReturn(List.of());

        String view = controller.submitWorkPlan(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/plano", view);
        assertSame(enrollment, model.getAttribute("enrollment"));
        verify(service, never()).submitWorkPlan(anyLong(), anyString(), any());
    }

    // Caminho: POST plano e o service lanca IllegalArgumentException -> reexibe com errorMessage
    @Test
    @DisplayName("Plano rejeitado pelo service mostra a mensagem de erro")
    void submitWorkPlan_serviceRejeita_mostraMensagemDeErro() {

        WorkPlanForm form = new WorkPlanForm();
        BindingResult br = new BeanPropertyBindingResult(form, "workPlanForm");
        doThrow(new IllegalArgumentException("Arquivo vazio.")).when(service).submitWorkPlan(1L, "aluno", form);
        when(service.listProfessors()).thenReturn(List.of());

        String view = controller.submitWorkPlan(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/plano", view);
        assertEquals("Arquivo vazio.", model.getAttribute("errorMessage"));
    }

    // ---------- documentacao ----------

    // Caminho: GET documentacao sem form no model -> cria DocumentationForm vazio
    @Test
    @DisplayName("Formulario da documentacao cria form vazio quando ausente")
    void documentationForm_semFormNoModel_criaFormVazio() {

        String view = controller.documentationForm(1L, auth, model);

        assertEquals("aluno/offers/documentacao", view);
        assertInstanceOf(DocumentationForm.class, model.getAttribute("documentationForm"));
    }

    // Caminho: GET documentacao com form ja no model -> mantem o form
    @Test
    @DisplayName("Formulario da documentacao mantem o form ja presente")
    void documentationForm_comFormNoModel_mantemForm() {

        DocumentationForm existente = new DocumentationForm();
        model.addAttribute("documentationForm", existente);

        controller.documentationForm(1L, auth, model);

        assertSame(existente, model.getAttribute("documentationForm"));
    }

    // Caminho: POST documentacao valida -> envia e redireciona com sucesso
    @Test
    @DisplayName("Envio valido da documentacao redireciona para os detalhes")
    void submitDocumentation_valida_redirecionaComSucesso() {

        DocumentationForm form = new DocumentationForm();
        BindingResult br = new BeanPropertyBindingResult(form, "documentationForm");

        String view = controller.submitDocumentation(1L, form, br, auth, model, redirect);

        assertEquals("redirect:/aluno/ofertas/1", view);
        assertEquals("Documentação enviada com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).submitDocumentation(1L, "aluno", form);
    }

    // Caminho: POST documentacao com erro de binding -> reexibe o formulario sem chamar o service
    @Test
    @DisplayName("Documentacao com erro de validacao reexibe o formulario")
    void submitDocumentation_comErroDeBinding_reexibeFormulario() {

        DocumentationForm form = new DocumentationForm();
        BindingResult br = new BeanPropertyBindingResult(form, "documentationForm");
        br.rejectValue("institutionName", "NotBlank", "obrigatório");

        String view = controller.submitDocumentation(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/documentacao", view);
        assertSame(enrollment, model.getAttribute("enrollment"));
        verify(service, never()).submitDocumentation(anyLong(), anyString(), any());
    }

    // Caminho: POST documentacao e o service lanca IllegalArgumentException -> reexibe com errorMessage
    @Test
    @DisplayName("Documentacao rejeitada pelo service mostra a mensagem de erro")
    void submitDocumentation_serviceRejeita_mostraMensagemDeErro() {

        DocumentationForm form = new DocumentationForm();
        BindingResult br = new BeanPropertyBindingResult(form, "documentationForm");
        doThrow(new IllegalArgumentException("Plano ainda não aprovado."))
                .when(service).submitDocumentation(1L, "aluno", form);

        String view = controller.submitDocumentation(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/documentacao", view);
        assertEquals("Plano ainda não aprovado.", model.getAttribute("errorMessage"));
    }

    // ---------- relatorio ----------

    // Caminho: GET relatorio sem form no model -> cria ReportForm vazio
    @Test
    @DisplayName("Formulario do relatorio cria form vazio quando ausente")
    void reportForm_semFormNoModel_criaFormVazio() {

        WorkPlan plano = new WorkPlan();
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(service.getStatusLogs(enrollment)).thenReturn(List.of());

        String view = controller.reportForm(1L, auth, model);

        assertEquals("aluno/offers/relatorio", view);
        assertInstanceOf(ReportForm.class, model.getAttribute("reportForm"));
        assertSame(plano, model.getAttribute("workPlan"));
    }

    // Caminho: GET relatorio com form ja no model -> mantem o form
    @Test
    @DisplayName("Formulario do relatorio mantem o form ja presente")
    void reportForm_comFormNoModel_mantemForm() {

        ReportForm existente = new ReportForm();
        model.addAttribute("reportForm", existente);
        when(service.getWorkPlan(enrollment)).thenReturn(null);
        when(service.getStatusLogs(enrollment)).thenReturn(List.of());

        controller.reportForm(1L, auth, model);

        assertSame(existente, model.getAttribute("reportForm"));
    }

    // Caminho: POST relatorio valido -> envia e redireciona com sucesso
    @Test
    @DisplayName("Envio valido do relatorio redireciona para os detalhes")
    void submitReport_valido_redirecionaComSucesso() {

        ReportForm form = new ReportForm();
        BindingResult br = new BeanPropertyBindingResult(form, "reportForm");

        String view = controller.submitReport(1L, form, br, auth, model, redirect);

        assertEquals("redirect:/aluno/ofertas/1", view);
        assertEquals("Relatório final enviado com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).submitReport(1L, "aluno", form);
    }

    // Caminho: POST relatorio com erro de binding -> reexibe o formulario sem chamar o service
    @Test
    @DisplayName("Relatorio com erro de validacao reexibe o formulario")
    void submitReport_comErroDeBinding_reexibeFormulario() {

        ReportForm form = new ReportForm();
        BindingResult br = new BeanPropertyBindingResult(form, "reportForm");
        br.rejectValue("frequency", "NotNull", "obrigatória");
        when(service.getWorkPlan(enrollment)).thenReturn(new WorkPlan());
        when(service.getStatusLogs(enrollment)).thenReturn(List.of());

        String view = controller.submitReport(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/relatorio", view);
        verify(service, never()).submitReport(anyLong(), anyString(), any());
    }

    // Caminho: POST relatorio e o service lanca IllegalArgumentException -> reexibe com errorMessage
    @Test
    @DisplayName("Relatorio rejeitado pelo service mostra a mensagem de erro")
    void submitReport_serviceRejeita_mostraMensagemDeErro() {

        ReportForm form = new ReportForm();
        BindingResult br = new BeanPropertyBindingResult(form, "reportForm");
        doThrow(new IllegalArgumentException("Documentação pendente.")).when(service).submitReport(1L, "aluno", form);
        when(service.getWorkPlan(enrollment)).thenReturn(new WorkPlan());
        when(service.getStatusLogs(enrollment)).thenReturn(List.of());

        String view = controller.submitReport(1L, form, br, auth, model, redirect);

        assertEquals("aluno/offers/relatorio", view);
        assertEquals("Documentação pendente.", model.getAttribute("errorMessage"));
    }

    // ---------- downloads ----------

    // Caminho: plano existe -> PDF inline com o nome do arquivo e o conteudo lido do storage
    @Test
    @DisplayName("Baixa o plano como PDF inline")
    void downloadWorkPlan_existe_retornaPdfInline() {

        WorkPlan plano = new WorkPlan();
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,a");
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[]{1});

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1}, response.getBody());
    }

    // Caminho: arquivo sem nome -> usa "arquivo.pdf" no Content-Disposition
    @Test
    @DisplayName("Arquivo sem nome usa o nome padrao arquivo.pdf")
    void downloadWorkPlan_semNomeDeArquivo_usaNomePadrao() {

        WorkPlan plano = new WorkPlan();
        plano.setFileFid("1,a");
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[0]);

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals("inline; filename=\"arquivo.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    // Caminho: plano inexistente -> 404 sem ler o storage
    @Test
    @DisplayName("Plano inexistente responde 404")
    void downloadWorkPlan_naoExiste_retorna404() {

        when(service.getWorkPlan(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(storageService);
    }

    // Caminho: documentacao existe -> conteudo lido do storage
    @Test
    @DisplayName("Baixa a documentacao")
    void downloadDocumentation_existe_retornaConteudo() {

        Documentation doc = new Documentation();
        doc.setFileName("doc.pdf");
        doc.setFileFid("2,b");
        when(service.getDocumentation(enrollment)).thenReturn(doc);
        when(storageService.read("2,b")).thenReturn(new byte[]{2});

        ResponseEntity<byte[]> response = controller.downloadDocumentation(1L, auth);

        assertArrayEquals(new byte[]{2}, response.getBody());
    }

    // Caminho: documentacao inexistente -> 404
    @Test
    @DisplayName("Documentacao inexistente responde 404")
    void downloadDocumentation_naoExiste_retorna404() {

        when(service.getDocumentation(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadDocumentation(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // Caminho: relatorio existe -> conteudo lido do storage
    @Test
    @DisplayName("Baixa o relatorio")
    void downloadReport_existe_retornaConteudo() {

        Report relatorio = new Report();
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("3,c");
        when(service.getReport(enrollment)).thenReturn(relatorio);
        when(storageService.read("3,c")).thenReturn(new byte[]{3});

        ResponseEntity<byte[]> response = controller.downloadReport(1L, auth);

        assertArrayEquals(new byte[]{3}, response.getBody());
    }

    // Caminho: relatorio inexistente -> 404
    @Test
    @DisplayName("Relatorio inexistente responde 404")
    void downloadReport_naoExiste_retorna404() {

        when(service.getReport(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadReport(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}

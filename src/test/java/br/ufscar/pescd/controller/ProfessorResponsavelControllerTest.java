package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.AnalyzeDocumentationFormDTO;
import br.ufscar.pescd.dto.ReturnDocumentationFormDTO;
import br.ufscar.pescd.dto.CloseOfferFormDTO;
import br.ufscar.pescd.dto.ConcludeReportFormDTO;
import br.ufscar.pescd.dto.ResponsavelDashboardDTO;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.GradeOption;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.service.ProfessorResponsavelService;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProfessorResponsavelControllerTest {

    ProfessorResponsavelService service = mock(ProfessorResponsavelService.class);
    UserRepository userRepository = mock(UserRepository.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);

    ProfessorResponsavelController controller;

    Authentication auth = new TestingAuthenticationToken("resp", "pw");
    User professor = new User();
    OfferStudent os = new OfferStudent();
    Model model;
    RedirectAttributesModelMap redirect;

    @BeforeEach
    void setUp() {
        controller = new ProfessorResponsavelController(service, userRepository, storageService);
        model = new ExtendedModelMap();
        redirect = new RedirectAttributesModelMap();
        professor.setId(10L);
    }

    // helpers simples de montagem

    private void usuarioExiste() {
        when(userRepository.findByUsername("resp")).thenReturn(Optional.of(professor));
    }

    private OfferStudent matricula(String nomeAluno) {
        User aluno = new User();
        aluno.setFullName(nomeAluno);
        OfferStudent e = new OfferStudent();
        e.setStudent(aluno);
        return e;
    }

    private Offer ofertaDoProfessor(Long idProfessor) {
        User dono = new User();
        dono.setId(idProfessor);
        Offer oferta = new Offer();
        oferta.setResponsibleProfessor(dono);
        return oferta;
    }

    private BindingResult bindingComErro(boolean temErro) {
        BindingResult br = mock(BindingResult.class);
        when(br.hasErrors()).thenReturn(temErro);
        return br;
    }

    // ---------------------------------------------------------------- dashboard

    @Test
    @DisplayName("Usuário inexistente na sessão lança RuntimeException")
    void usuarioNaoEncontrado_lancaRuntimeException() {
        // Ramo: resolveUser -> orElseThrow
        when(userRepository.findByUsername("resp")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.dashboard(model, auth));

        assertEquals("Usuário não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Dashboard coloca lista e professor no model")
    void dashboard_retornaViewComDados() {
        // Ramo: caminho feliz do dashboard
        usuarioExiste();
        List<ResponsavelDashboardDTO> dashboard = List.of();
        when(service.getDashboard(professor)).thenReturn(dashboard);

        String view = controller.dashboard(model, auth);

        assertEquals("professor/responsavel/dashboard", view);
        assertSame(dashboard, model.getAttribute("dashboard"));
        assertSame(professor, model.getAttribute("professor"));
    }

    // ---------------------------------------------------------------- offerDetails

    @Test
    @DisplayName("Detalhes da oferta sem filtro mostra todos os alunos")
    void detalhesSemFiltro_retornaTodosOsAlunos() {
        // Ramo: nome == null -> não filtra
        usuarioExiste();
        Offer oferta = ofertaDoProfessor(10L);
        List<OfferStudent> alunos = List.of(matricula("Ana Souza"), matricula("Bruno Lima"));
        when(service.getOffer(1L)).thenReturn(oferta);
        when(service.getOfferStudents(1L)).thenReturn(alunos);

        String view = controller.offerDetails(1L, null, auth, model);

        assertEquals("professor/responsavel/offer-details", view);
        assertSame(oferta, model.getAttribute("offer"));
        assertEquals(alunos, model.getAttribute("students"));
        assertNull(model.getAttribute("nome"));
    }

    @Test
    @DisplayName("Nome em branco não filtra a lista de alunos")
    void detalhesComNomeEmBranco_naoFiltra() {
        // Ramo: nome != null mas isBlank -> não filtra
        usuarioExiste();
        List<OfferStudent> alunos = List.of(matricula("Ana"), matricula("Bruno"));
        when(service.getOffer(1L)).thenReturn(ofertaDoProfessor(10L));
        when(service.getOfferStudents(1L)).thenReturn(alunos);

        controller.offerDetails(1L, "   ", auth, model);

        assertEquals(alunos, model.getAttribute("students"));
        assertEquals("   ", model.getAttribute("nome"));
    }

    @Test
    @DisplayName("Nome vazio não filtra a lista de alunos")
    void detalhesComNomeVazio_naoFiltra() {
        // Ramo: nome vazio -> isBlank true
        usuarioExiste();
        List<OfferStudent> alunos = List.of(matricula("Ana"), matricula("Bruno"));
        when(service.getOffer(1L)).thenReturn(ofertaDoProfessor(10L));
        when(service.getOfferStudents(1L)).thenReturn(alunos);

        controller.offerDetails(1L, "", auth, model);

        assertEquals(alunos, model.getAttribute("students"));
    }

    @Test
    @DisplayName("Filtro por nome ignora maiúsculas e minúsculas")
    void detalhesComNome_filtraIgnorandoCaixa() {
        // Ramo: nome informado -> filtro por contains (case insensitive)
        usuarioExiste();
        OfferStudent ana = matricula("Ana Souza");
        when(service.getOffer(1L)).thenReturn(ofertaDoProfessor(10L));
        when(service.getOfferStudents(1L)).thenReturn(List.of(ana, matricula("Bruno Lima")));

        controller.offerDetails(1L, "ANA", auth, model);

        assertEquals(List.of(ana), model.getAttribute("students"));
        assertEquals("ANA", model.getAttribute("nome"));
    }

    @Test
    @DisplayName("Professor que não é responsável lança IllegalArgumentException")
    void detalhesDeOfertaDeOutroProfessor_lancaIllegalArgumentException() {
        // Ramo: id do responsável diferente do professor logado -> exceção
        usuarioExiste();
        when(service.getOffer(1L)).thenReturn(ofertaDoProfessor(99L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.offerDetails(1L, null, auth, model));

        assertEquals("Você não é responsável por esta oferta.", ex.getMessage());
        verify(service, never()).getOfferStudents(anyLong());
    }

    // ---------------------------------------------------------------- concluir relatório

    @Test
    @DisplayName("Formulário de relatório vem preenchido com sugestões do supervisor")
    void formularioConcluirRelatorio_preenchidoComSugestoesDoSupervisor() {
        // Ramo: GET concluir-relatório pré-preenche frequência e nota
        usuarioExiste();
        WorkPlan plano = new WorkPlan();
        Report relatorio = new Report();
        relatorio.setSupervisorFrequencia(85);
        relatorio.setSupervisorNotaSugestao(GradeOption.B);
        List<StatusChangeLog> logs = List.of();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);
        when(service.getReport(os)).thenReturn(relatorio);
        when(service.getStatusLogs(os)).thenReturn(logs);

        String view = controller.concludeReportForm(1L, 2L, model, auth);

        assertEquals("professor/responsavel/conclude-report", view);
        ConcludeReportFormDTO form = (ConcludeReportFormDTO) model.getAttribute("form");
        assertEquals(85, form.getFrequencia());
        assertEquals(GradeOption.B, form.getNota());
        assertSame(plano, model.getAttribute("workPlan"));
        assertSame(logs, model.getAttribute("statusLogs"));
        assertArrayEquals(GradeOption.values(), (GradeOption[]) model.getAttribute("gradeOptions"));
    }

    @Test
    @DisplayName("Concluir relatório válido redireciona com mensagem de sucesso")
    void concluirRelatorioValido_redirecionaParaDashboard() {
        // Ramo: sem erros de binding -> chama service e redireciona
        usuarioExiste();
        ConcludeReportFormDTO form = new ConcludeReportFormDTO();
        BindingResult br = bindingComErro(false);

        String view = controller.concludeReport(1L, 2L, form, br, model, auth, redirect);

        assertEquals("redirect:/professor/responsavel/dashboard", view);
        assertEquals("Relatório concluído com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).concludeReport(1L, 2L, professor, form);
    }

    @Test
    @DisplayName("Concluir relatório com erro de validação reexibe o formulário")
    void concluirRelatorioComErroDeBinding_reexibeFormulario() {
        // Ramo: bindingResult.hasErrors -> volta para a view sem concluir
        usuarioExiste();
        ConcludeReportFormDTO form = new ConcludeReportFormDTO();
        BindingResult br = bindingComErro(true);
        Report relatorio = new Report();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(new WorkPlan());
        when(service.getReport(os)).thenReturn(relatorio);
        when(service.getStatusLogs(os)).thenReturn(List.of());

        String view = controller.concludeReport(1L, 2L, form, br, model, auth, redirect);

        assertEquals("professor/responsavel/conclude-report", view);
        assertSame(relatorio, model.getAttribute("report"));
        verify(service, never()).concludeReport(anyLong(), anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- analisar documentação

    @Test
    @DisplayName("Formulário de análise traz a documentação e um form vazio")
    void formularioAnalisarDocumentacao_retornaView() {
        // Ramo: GET analisar-documentação
        usuarioExiste();
        Documentation doc = new Documentation();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getDocumentation(os)).thenReturn(doc);
        when(service.getStatusLogs(os)).thenReturn(List.of());

        String view = controller.analyzeDocumentationForm(1L, 2L, model, auth);

        assertEquals("professor/responsavel/analyze-documentation", view);
        assertSame(doc, model.getAttribute("documentation"));
        assertNotNull(model.getAttribute("form"));
    }

    @Test
    @DisplayName("Analisar documentação válida redireciona com mensagem de sucesso")
    void analisarDocumentacaoValida_redirecionaParaDashboard() {
        // Ramo: sem erros de binding -> chama service e redireciona
        usuarioExiste();
        AnalyzeDocumentationFormDTO form = new AnalyzeDocumentationFormDTO();
        BindingResult br = bindingComErro(false);

        String view = controller.analyzeDocumentation(1L, 2L, form, br, model, auth, redirect);

        assertEquals("redirect:/professor/responsavel/dashboard", view);
        assertEquals("Documentação analisada com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).analyzeDocumentation(1L, 2L, professor, form);
    }

    @Test
    @DisplayName("Analisar documentação com erro de validação reexibe o formulário")
    void analisarDocumentacaoComErroDeBinding_reexibeFormulario() {
        // Ramo: bindingResult.hasErrors -> volta para a view sem analisar
        usuarioExiste();
        AnalyzeDocumentationFormDTO form = new AnalyzeDocumentationFormDTO();
        BindingResult br = bindingComErro(true);
        Documentation doc = new Documentation();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getDocumentation(os)).thenReturn(doc);
        when(service.getStatusLogs(os)).thenReturn(List.of());

        String view = controller.analyzeDocumentation(1L, 2L, form, br, model, auth, redirect);

        assertEquals("professor/responsavel/analyze-documentation", view);
        assertSame(doc, model.getAttribute("documentation"));
        verify(service, never()).analyzeDocumentation(anyLong(), anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- Mud4 devolver documentação

    @Test
    @DisplayName("Devolver documentação válida redireciona com mensagem de sucesso")
    void devolverDocumentacaoValida_redirecionaParaDashboard() {
        // Ramo: sem erros de binding -> chama service e redireciona
        usuarioExiste();
        ReturnDocumentationFormDTO form = new ReturnDocumentationFormDTO();
        BindingResult br = bindingComErro(false);

        String view = controller.returnDocumentation(1L, 2L, form, br, model, auth, redirect);

        assertEquals("redirect:/professor/responsavel/dashboard", view);
        assertEquals("Documentação devolvida ao aluno para novo envio.",
                redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).returnDocumentation(1L, 2L, professor, form);
    }

    @Test
    @DisplayName("Devolver documentação sem parecer reexibe o formulário")
    void devolverDocumentacaoComErroDeBinding_reexibeFormulario() {
        // Ramo: bindingResult.hasErrors -> volta para a view sem devolver
        usuarioExiste();
        ReturnDocumentationFormDTO form = new ReturnDocumentationFormDTO();
        BindingResult br = bindingComErro(true);
        Documentation doc = new Documentation();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getDocumentation(os)).thenReturn(doc);
        when(service.getStatusLogs(os)).thenReturn(List.of());

        String view = controller.returnDocumentation(1L, 2L, form, br, model, auth, redirect);

        assertEquals("professor/responsavel/analyze-documentation", view);
        assertSame(doc, model.getAttribute("documentation"));
        assertNotNull(model.getAttribute("form"));
        verify(service, never()).returnDocumentation(anyLong(), anyLong(), any(), any());
    }

    // ---------------------------------------------------------------- encerrar oferta

    @Test
    @DisplayName("Formulário de encerramento traz a oferta e um form vazio")
    void formularioEncerrarOferta_retornaView() {
        // Ramo: GET encerrar
        usuarioExiste();
        Offer oferta = new Offer();
        when(service.getOffer(1L)).thenReturn(oferta);

        String view = controller.closeOfferForm(1L, model, auth);

        assertEquals("professor/responsavel/close-offer", view);
        assertSame(oferta, model.getAttribute("offer"));
        assertNotNull(model.getAttribute("form"));
    }

    @Test
    @DisplayName("Encerrar oferta válida redireciona com mensagem de sucesso")
    void encerrarOfertaValida_redirecionaParaDashboard() {
        // Ramo: sem erros, service aceita o encerramento
        usuarioExiste();
        CloseOfferFormDTO form = new CloseOfferFormDTO();
        form.setLessonsLearned("Lições");
        BindingResult br = bindingComErro(false);

        String view = controller.closeOffer(1L, form, br, auth, model, redirect);

        assertEquals("redirect:/professor/responsavel/dashboard", view);
        assertEquals("Oferta enviada para encerramento.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).closeOffer(1L, professor, "Lições");
    }

    @Test
    @DisplayName("Encerrar oferta com erro de validação reexibe o formulário")
    void encerrarOfertaComErroDeBinding_reexibeFormulario() {
        // Ramo: bindingResult.hasErrors -> volta para a view sem encerrar
        usuarioExiste();
        CloseOfferFormDTO form = new CloseOfferFormDTO();
        BindingResult br = bindingComErro(true);
        Offer oferta = new Offer();
        when(service.getOffer(1L)).thenReturn(oferta);

        String view = controller.closeOffer(1L, form, br, auth, model, redirect);

        assertEquals("professor/responsavel/close-offer", view);
        assertSame(oferta, model.getAttribute("offer"));
        verify(service, never()).closeOffer(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Service rejeitando o encerramento vira mensagem de erro")
    void encerrarOfertaRejeitadaPeloService_redirecionaComErro() {
        // Ramo: catch IllegalArgumentException -> flash errorMessage
        usuarioExiste();
        CloseOfferFormDTO form = new CloseOfferFormDTO();
        form.setLessonsLearned("x");
        BindingResult br = bindingComErro(false);
        doThrow(new IllegalArgumentException("Há alunos pendentes."))
                .when(service).closeOffer(1L, professor, "x");

        String view = controller.closeOffer(1L, form, br, auth, model, redirect);

        assertEquals("redirect:/professor/responsavel/dashboard", view);
        assertEquals("Há alunos pendentes.", redirect.getFlashAttributes().get("errorMessage"));
        assertNull(redirect.getFlashAttributes().get("successMessage"));
    }

    // ---------------------------------------------------------------- downloads

    @Test
    @DisplayName("Download do plano devolve o PDF inline")
    void downloadPlano_retornaPdfInline() {
        // Ramo: download do plano de trabalho
        usuarioExiste();
        WorkPlan plano = new WorkPlan();
        plano.setContentType("application/pdf");
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,a");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getWorkPlan(os)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[]{1});

        ResponseEntity<byte[]> resposta = controller.downloadPlan(1L, 2L, auth);

        assertEquals(MediaType.APPLICATION_PDF, resposta.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1}, resposta.getBody());
    }

    @Test
    @DisplayName("Download do relatório devolve os bytes do arquivo")
    void downloadRelatorio_retornaBytes() {
        // Ramo: download do relatório
        usuarioExiste();
        Report relatorio = new Report();
        relatorio.setContentType("application/pdf");
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("2,b");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getReport(os)).thenReturn(relatorio);
        when(storageService.read("2,b")).thenReturn(new byte[]{2});

        ResponseEntity<byte[]> resposta = controller.downloadReport(1L, 2L, auth);

        assertArrayEquals(new byte[]{2}, resposta.getBody());
        assertEquals("inline; filename=\"rel.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @DisplayName("Download da documentação devolve os bytes do arquivo")
    void downloadDocumentacao_retornaBytes() {
        // Ramo: download da documentação
        usuarioExiste();
        Documentation doc = new Documentation();
        doc.setContentType("application/pdf");
        doc.setFileName("doc.pdf");
        doc.setFileFid("3,c");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(os);
        when(service.getDocumentation(os)).thenReturn(doc);
        when(storageService.read("3,c")).thenReturn(new byte[]{3});

        ResponseEntity<byte[]> resposta = controller.downloadDocumentation(1L, 2L, auth);

        assertArrayEquals(new byte[]{3}, resposta.getBody());
        assertEquals("inline; filename=\"doc.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }
}

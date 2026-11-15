package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.AnalyzeDocumentationFormDTO;
import br.ufscar.pescd.dto.ReturnDocumentationFormDTO;
import br.ufscar.pescd.dto.CloseOfferFormDTO;
import br.ufscar.pescd.dto.ConcludeReportFormDTO;
import br.ufscar.pescd.dto.DocumentationDetailsResponse;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferStudentSummaryResponse;
import br.ufscar.pescd.dto.OfferSummaryResponse;
import br.ufscar.pescd.dto.ReportDetailsResponse;
import br.ufscar.pescd.dto.ResponsibleCloseOfferSummaryResponse;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.service.ProfessorResponsavelService;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfessorResponsavelApiControllerTest {

    ProfessorResponsavelService service = mock(ProfessorResponsavelService.class);
    UserRepository userRepository = mock(UserRepository.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);

    ProfessorResponsavelApiController controller;

    Authentication auth = new TestingAuthenticationToken("prof", "pw");
    User professor = new User();
    OfferStudent matricula = new OfferStudent();

    @BeforeEach
    void setUp() {
        controller = new ProfessorResponsavelApiController(service, userRepository, storageService);
        professor.setId(10L);
    }

    private void usuarioExiste() {
        when(userRepository.findByUsername("prof")).thenReturn(Optional.of(professor));
    }

    @Test
    @DisplayName("Lista as ofertas do professor responsável")
    void listarOfertas_retornaListaDoService() {
        // Ramo: caminho feliz de listResponsibleOffers
        usuarioExiste();
        List<OfferDetailsResponse> ofertas = List.of(mock(OfferDetailsResponse.class));
        when(service.getDashboardForApi(professor)).thenReturn(ofertas);

        List<OfferDetailsResponse> resultado = controller.listResponsibleOffers(auth);

        assertSame(ofertas, resultado);
        verify(service, times(1)).getDashboardForApi(professor);
    }

    @Test
    @DisplayName("Usuário autenticado inexistente lança IllegalArgumentException")
    void usuarioAutenticadoNaoExiste_lancaIllegalArgumentException() {
        // Ramo: resolveUser -> orElseThrow
        when(userRepository.findByUsername("prof")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.listResponsibleOffers(auth));

        assertEquals("Usuário autenticado não encontrado.", ex.getMessage());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Detalhes da oferta vêm do service")
    void detalhesDaOferta_retornaDetalhes() {
        // Ramo: delegação para getOfferDetailsForApi
        usuarioExiste();
        OfferDetailsResponse detalhes = mock(OfferDetailsResponse.class);
        when(service.getOfferDetailsForApi(1L, professor)).thenReturn(detalhes);

        OfferDetailsResponse resultado = controller.offerDetails(1L, auth);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Busca de alunos repassa o nome ao service")
    void buscarAlunosComNome_retornaAlunos() {
        // Ramo: filtro por nome informado
        usuarioExiste();
        List<OfferStudentSummaryResponse> alunos = List.of(mock(OfferStudentSummaryResponse.class));
        when(service.searchStudentsByNameForApi(1L, professor, "ana")).thenReturn(alunos);

        List<OfferStudentSummaryResponse> resultado = controller.searchStudents(1L, "ana", auth);

        assertSame(alunos, resultado);
    }

    @Test
    @DisplayName("Busca de alunos sem nome retorna lista vazia do service")
    void buscarAlunosSemNome_retornaListaVazia() {
        // Ramo: name == null repassado ao service
        usuarioExiste();
        when(service.searchStudentsByNameForApi(1L, professor, null)).thenReturn(List.of());

        List<OfferStudentSummaryResponse> resultado = controller.searchStudents(1L, null, auth);

        assertTrue(resultado.isEmpty());
        verify(service, times(1)).searchStudentsByNameForApi(1L, professor, null);
    }

    @Test
    @DisplayName("Resumo de encerramento vem do service")
    void resumoDeEncerramento_retornaResumo() {
        // Ramo: delegação para getCloseOfferSummaryForApi
        usuarioExiste();
        ResponsibleCloseOfferSummaryResponse resumo = mock(ResponsibleCloseOfferSummaryResponse.class);
        when(service.getCloseOfferSummaryForApi(1L, professor)).thenReturn(resumo);

        ResponsibleCloseOfferSummaryResponse resultado = controller.closeSummary(1L, auth);

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Encerrar oferta repassa as lições aprendidas")
    void encerrarOferta_repassaLicoesAprendidas() {
        // Ramo: closeOffer usa form.getLessonsLearned()
        usuarioExiste();
        CloseOfferFormDTO form = new CloseOfferFormDTO();
        form.setLessonsLearned("Aprendemos muito");
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);
        when(service.closeOfferForApi(1L, professor, "Aprendemos muito")).thenReturn(resumo);

        OfferSummaryResponse resultado = controller.closeOffer(1L, form, auth);

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Encerrar oferta propaga AccessDeniedException do service")
    void encerrarOfertaSemPermissao_propagaAccessDenied() {
        // Ramo: service nega acesso -> exceção sobe
        usuarioExiste();
        CloseOfferFormDTO form = new CloseOfferFormDTO();
        form.setLessonsLearned("x");
        when(service.closeOfferForApi(1L, professor, "x")).thenThrow(new AccessDeniedException("negado"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> controller.closeOffer(1L, form, auth));

        assertEquals("negado", ex.getMessage());
    }

    @Test
    @DisplayName("Detalhes da matrícula vêm do service")
    void detalhesDaMatricula_retornaDetalhes() {
        // Ramo: delegação para getEnrollmentDetailsForApi
        usuarioExiste();
        OfferStudentDetailsResponse detalhes = mock(OfferStudentDetailsResponse.class);
        when(service.getEnrollmentDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        OfferStudentDetailsResponse resultado = controller.enrollmentDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Detalhes do relatório vêm do service")
    void detalhesDoRelatorio_retornaDetalhes() {
        // Ramo: delegação para getReportDetailsForApi
        usuarioExiste();
        ReportDetailsResponse detalhes = mock(ReportDetailsResponse.class);
        when(service.getReportDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        ReportDetailsResponse resultado = controller.reportDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Concluir relatório devolve o resumo do aluno")
    void concluirRelatorio_retornaResumo() {
        // Ramo: delegação para concludeReportForApi
        usuarioExiste();
        ConcludeReportFormDTO form = new ConcludeReportFormDTO();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(service.concludeReportForApi(1L, 2L, professor, form)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado = controller.concludeReport(1L, 2L, form, auth);

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Detalhes da documentação vêm do service")
    void detalhesDaDocumentacao_retornaDetalhes() {
        // Ramo: delegação para getDocumentationDetailsForApi
        usuarioExiste();
        DocumentationDetailsResponse detalhes = mock(DocumentationDetailsResponse.class);
        when(service.getDocumentationDetailsForApi(1L, 2L, professor)).thenReturn(detalhes);

        DocumentationDetailsResponse resultado = controller.documentationDetails(1L, 2L, auth);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Analisar documentação devolve o resumo do aluno")
    void analisarDocumentacao_retornaResumo() {
        // Ramo: delegação para analyzeDocumentationForApi
        usuarioExiste();
        AnalyzeDocumentationFormDTO form = new AnalyzeDocumentationFormDTO();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(service.analyzeDocumentationForApi(1L, 2L, professor, form)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado = controller.analyzeDocumentation(1L, 2L, form, auth);

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Devolver documentação devolve o resumo do aluno")
    void devolverDocumentacao_retornaResumo() {
        // Ramo (Mud4): delegação para returnDocumentationForApi
        usuarioExiste();
        ReturnDocumentationFormDTO form = new ReturnDocumentationFormDTO();
        OfferStudentSummaryResponse resumo = mock(OfferStudentSummaryResponse.class);
        when(service.returnDocumentationForApi(1L, 2L, professor, form)).thenReturn(resumo);

        OfferStudentSummaryResponse resultado = controller.returnDocumentation(1L, 2L, form, auth);

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Download do plano de trabalho devolve PDF inline")
    void downloadPlanoDeTrabalho_retornaPdfInline() {
        // Ramo: download do plano
        usuarioExiste();
        WorkPlan plano = new WorkPlan();
        plano.setContentType("application/pdf");
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,abc");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(matricula);
        when(service.getWorkPlan(matricula)).thenReturn(plano);
        when(storageService.read("1,abc")).thenReturn(new byte[]{1, 2});

        ResponseEntity<byte[]> resposta = controller.downloadWorkPlan(1L, 2L, auth);

        assertEquals(200, resposta.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, resposta.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1, 2}, resposta.getBody());
    }

    @Test
    @DisplayName("Download do relatório devolve PDF inline")
    void downloadRelatorio_retornaPdfInline() {
        // Ramo: download do relatório
        usuarioExiste();
        Report relatorio = new Report();
        relatorio.setContentType("application/pdf");
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("2,def");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(matricula);
        when(service.getReport(matricula)).thenReturn(relatorio);
        when(storageService.read("2,def")).thenReturn(new byte[]{3});

        ResponseEntity<byte[]> resposta = controller.downloadReport(1L, 2L, auth);

        assertEquals("inline; filename=\"rel.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{3}, resposta.getBody());
    }

    @Test
    @DisplayName("Download da documentação devolve PDF inline")
    void downloadDocumentacao_retornaPdfInline() {
        // Ramo: download da documentação
        usuarioExiste();
        Documentation doc = new Documentation();
        doc.setContentType("application/pdf");
        doc.setFileName("doc.pdf");
        doc.setFileFid("3,ghi");
        when(service.getEnrollmentForResponsavel(1L, 2L, professor)).thenReturn(matricula);
        when(service.getDocumentation(matricula)).thenReturn(doc);
        when(storageService.read("3,ghi")).thenReturn(new byte[]{4});

        ResponseEntity<byte[]> resposta = controller.downloadDocumentation(1L, 2L, auth);

        assertEquals("inline; filename=\"doc.pdf\"",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{4}, resposta.getBody());
    }

    @Test
    @DisplayName("Download sem acesso propaga a exceção do service")
    void downloadSemAcesso_propagaIllegalArgumentException() {
        // Ramo: getEnrollmentForResponsavel lança -> nada é lido do storage
        usuarioExiste();
        when(service.getEnrollmentForResponsavel(1L, 2L, professor))
                .thenThrow(new IllegalArgumentException("Você não é responsável por esta oferta."));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.downloadWorkPlan(1L, 2L, auth));

        assertEquals("Você não é responsável por esta oferta.", ex.getMessage());
        verifyNoInteractions(storageService);
    }
}

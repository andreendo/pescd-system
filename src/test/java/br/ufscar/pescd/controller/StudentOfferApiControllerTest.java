package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.DocumentationForm;
import br.ufscar.pescd.dto.ReportForm;
import br.ufscar.pescd.dto.StudentEnrollmentDetailsResponse;
import br.ufscar.pescd.dto.StudentOfferSummaryResponse;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.dto.WorkPlanForm;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentOfferApiControllerTest {

    StudentOfferService service = mock(StudentOfferService.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);
    Authentication auth = mock(Authentication.class);

    StudentOfferApiController controller;
    OfferStudent enrollment = new OfferStudent();

    @BeforeEach
    void setUp() {
        controller = new StudentOfferApiController(service, storageService);
        when(auth.getName()).thenReturn("aluno");
    }

    // Caminho: GET /api/aluno/ofertas -> delega ao service com o nome do usuario autenticado
    @Test
    @DisplayName("Lista as matriculas do aluno")
    void listEnrollments_delegaAoService() {

        List<StudentOfferSummaryResponse> lista = List.of(mock(StudentOfferSummaryResponse.class));
        when(service.listEnrollmentsForApi("aluno")).thenReturn(lista);

        List<StudentOfferSummaryResponse> resultado = controller.listEnrollments(auth);

        assertSame(lista, resultado);
    }

    // Caminho: GET /api/aluno/ofertas/1 -> devolve os detalhes do service
    @Test
    @DisplayName("Retorna os detalhes da matricula")
    void enrollmentDetails_delegaAoService() {

        StudentEnrollmentDetailsResponse detalhes = mock(StudentEnrollmentDetailsResponse.class);
        when(service.getEnrollmentDetailsForApi(1L, "aluno")).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado = controller.enrollmentDetails(1L, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: matricula de outro aluno -> AccessDeniedException do service propaga
    @Test
    @DisplayName("Matricula de outro aluno propaga AccessDeniedException")
    void enrollmentDetails_matriculaDeOutroAluno_propagaAccessDenied() {

        when(service.getEnrollmentDetailsForApi(1L, "aluno")).thenThrow(new AccessDeniedException("negado"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> controller.enrollmentDetails(1L, auth));

        assertEquals("negado", ex.getMessage());
    }

    // Caminho: GET /professors -> lista do service
    @Test
    @DisplayName("Lista os professores")
    void listProfessors_delegaAoService() {

        List<UserSummaryResponse> professores = List.of(mock(UserSummaryResponse.class));
        when(service.listProfessorsForApi()).thenReturn(professores);

        List<UserSummaryResponse> resultado = controller.listProfessors();

        assertSame(professores, resultado);
    }

    // Caminho: POST plano valido -> detalhes devolvidos pelo service
    @Test
    @DisplayName("Envia o plano de trabalho")
    void submitWorkPlan_valido_retornaDetalhes() {

        WorkPlanForm form = new WorkPlanForm();
        StudentEnrollmentDetailsResponse detalhes = mock(StudentEnrollmentDetailsResponse.class);
        when(service.submitWorkPlanForApi(1L, "aluno", form)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado = controller.submitWorkPlan(1L, form, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: POST plano invalido -> IllegalArgumentException do service propaga
    @Test
    @DisplayName("Plano invalido propaga IllegalArgumentException")
    void submitWorkPlan_invalido_propagaIllegalArgumentException() {

        WorkPlanForm form = new WorkPlanForm();
        when(service.submitWorkPlanForApi(1L, "aluno", form)).thenThrow(new IllegalArgumentException("arquivo inválido"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.submitWorkPlan(1L, form, auth));

        assertEquals("arquivo inválido", ex.getMessage());
    }

    // Caminho: POST documentacao -> detalhes devolvidos pelo service
    @Test
    @DisplayName("Envia a documentacao")
    void submitDocumentation_valida_retornaDetalhes() {

        DocumentationForm form = new DocumentationForm();
        StudentEnrollmentDetailsResponse detalhes = mock(StudentEnrollmentDetailsResponse.class);
        when(service.submitDocumentationForApi(1L, "aluno", form)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado = controller.submitDocumentation(1L, form, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: POST relatorio -> detalhes devolvidos pelo service
    @Test
    @DisplayName("Envia o relatorio")
    void submitReport_valido_retornaDetalhes() {

        ReportForm form = new ReportForm();
        StudentEnrollmentDetailsResponse detalhes = mock(StudentEnrollmentDetailsResponse.class);
        when(service.submitReportForApi(1L, "aluno", form)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado = controller.submitReport(1L, form, auth);

        assertSame(detalhes, resultado);
    }

    // Caminho: plano existe -> 200 PDF inline com nome do arquivo e conteudo do storage
    @Test
    @DisplayName("Baixa o plano como PDF")
    void downloadWorkPlan_existe_retornaPdf() {

        WorkPlan plano = new WorkPlan();
        plano.setFileName("plano.pdf");
        plano.setFileFid("1,a");
        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[]{1});

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("inline; filename=\"plano.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(new byte[]{1}, response.getBody());
    }

    // Caminho: arquivo sem nome -> usa "arquivo.pdf"
    @Test
    @DisplayName("Plano sem nome de arquivo usa o nome padrao")
    void downloadWorkPlan_semNomeDeArquivo_usaNomePadrao() {

        WorkPlan plano = new WorkPlan();
        plano.setFileFid("1,a");
        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getWorkPlan(enrollment)).thenReturn(plano);
        when(storageService.read("1,a")).thenReturn(new byte[]{1});

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals("inline; filename=\"arquivo.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    // Caminho: plano inexistente -> 404 sem tocar no storage
    @Test
    @DisplayName("Plano inexistente responde 404")
    void downloadWorkPlan_naoExiste_retorna404() {

        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getWorkPlan(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadWorkPlan(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(storageService);
    }

    // Caminho: documentacao existe -> 200 com o conteudo
    @Test
    @DisplayName("Baixa a documentacao como PDF")
    void downloadDocumentation_existe_retornaPdf() {

        Documentation doc = new Documentation();
        doc.setFileName("doc.pdf");
        doc.setFileFid("2,b");
        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getDocumentation(enrollment)).thenReturn(doc);
        when(storageService.read("2,b")).thenReturn(new byte[]{2});

        ResponseEntity<byte[]> response = controller.downloadDocumentation(1L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{2}, response.getBody());
    }

    // Caminho: documentacao inexistente -> 404
    @Test
    @DisplayName("Documentacao inexistente responde 404")
    void downloadDocumentation_naoExiste_retorna404() {

        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getDocumentation(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadDocumentation(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // Caminho: relatorio existe -> 200 com o conteudo
    @Test
    @DisplayName("Baixa o relatorio como PDF")
    void downloadReport_existe_retornaPdf() {

        Report relatorio = new Report();
        relatorio.setFileName("rel.pdf");
        relatorio.setFileFid("3,c");
        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getReport(enrollment)).thenReturn(relatorio);
        when(storageService.read("3,c")).thenReturn(new byte[]{3});

        ResponseEntity<byte[]> response = controller.downloadReport(1L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{3}, response.getBody());
    }

    // Caminho: relatorio inexistente -> 404
    @Test
    @DisplayName("Relatorio inexistente responde 404")
    void downloadReport_naoExiste_retorna404() {

        when(service.getEnrollment(1L, "aluno")).thenReturn(enrollment);
        when(service.getReport(enrollment)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadReport(1L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}

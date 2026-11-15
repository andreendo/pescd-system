package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.ImportStudentsResponse;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferForm;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferSummaryResponse;
import br.ufscar.pescd.dto.SecretaryCloseOfferPreviewResponse;
import br.ufscar.pescd.dto.SecretaryCloseOfferRequest;
import br.ufscar.pescd.dto.StudentForm;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.exception.BusinessRuleException;
import br.ufscar.pescd.exception.DuplicateResourceException;
import br.ufscar.pescd.service.SecretaryOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretaryOfferApiControllerTest {

    SecretaryOfferService service = mock(SecretaryOfferService.class);

    SecretaryOfferApiController controller;

    TestingAuthenticationToken auth = new TestingAuthenticationToken("secretaria", "pw");

    @BeforeEach
    void setUp() {
        controller = new SecretaryOfferApiController(service);
    }

    @Test
    @DisplayName("Lista as ofertas da secretaria")
    void listarOfertas_retornaListaDoService() {
        // Ramo: delegação para listOffersForApi
        List<OfferSummaryResponse> ofertas = List.of(mock(OfferSummaryResponse.class));
        when(service.listOffersForApi()).thenReturn(ofertas);

        List<OfferSummaryResponse> resultado = controller.listOffers();

        assertSame(ofertas, resultado);
    }

    @Test
    @DisplayName("Lista os professores disponíveis")
    void listarProfessores_retornaListaDoService() {
        // Ramo: delegação para listProfessorsForApi
        List<UserSummaryResponse> professores = List.of(mock(UserSummaryResponse.class));
        when(service.listProfessorsForApi()).thenReturn(professores);

        List<UserSummaryResponse> resultado = controller.listProfessors();

        assertSame(professores, resultado);
    }

    @Test
    @DisplayName("Criar oferta usa o username de quem está autenticado")
    void criarOferta_usaUsernameDoCriador() {
        // Ramo: createOffer repassa authentication.getName()
        OfferForm form = new OfferForm();
        OfferSummaryResponse criada = mock(OfferSummaryResponse.class);
        when(service.createOfferForApi(form, "secretaria")).thenReturn(criada);

        OfferSummaryResponse resultado = controller.createOffer(form, auth);

        assertSame(criada, resultado);
        verify(service, times(1)).createOfferForApi(form, "secretaria");
    }

    @Test
    @DisplayName("Datas inválidas propagam BusinessRuleException")
    void criarOfertaComDatasInvalidas_lancaBusinessRuleException() {
        // Ramo: service rejeita a oferta -> exceção sobe
        OfferForm form = new OfferForm();
        when(service.createOfferForApi(form, "secretaria"))
                .thenThrow(new BusinessRuleException("A data de fim deve ser depois da data de início."));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> controller.createOffer(form, auth));

        assertEquals("A data de fim deve ser depois da data de início.", ex.getMessage());
    }

    @Test
    @DisplayName("Detalhes da oferta vêm do service")
    void detalhesDaOferta_retornaDetalhes() {
        // Ramo: delegação para getOfferDetailsForApi
        OfferDetailsResponse detalhes = mock(OfferDetailsResponse.class);
        when(service.getOfferDetailsForApi(1L)).thenReturn(detalhes);

        OfferDetailsResponse resultado = controller.offerDetails(1L);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Adicionar aluno devolve os detalhes da oferta")
    void adicionarAluno_retornaDetalhesDaOferta() {
        // Ramo: delegação para addStudentForApi
        StudentForm form = new StudentForm();
        OfferDetailsResponse detalhes = mock(OfferDetailsResponse.class);
        when(service.addStudentForApi(1L, form)).thenReturn(detalhes);

        OfferDetailsResponse resultado = controller.addStudent(1L, form);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Aluno duplicado propaga DuplicateResourceException")
    void adicionarAlunoDuplicado_lancaDuplicateResourceException() {
        // Ramo: service detecta duplicidade -> exceção sobe
        StudentForm form = new StudentForm();
        when(service.addStudentForApi(1L, form)).thenThrow(new DuplicateResourceException("duplicado"));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> controller.addStudent(1L, form));

        assertEquals("duplicado", ex.getMessage());
    }

    @Test
    @DisplayName("Importar CSV devolve o resultado do service")
    void importarCsv_retornaResultado() {
        // Ramo: delegação para importStudentsFromCsvForApi
        MockMultipartFile arquivo = new MockMultipartFile("file", "alunos.csv", "text/csv",
                "RA,NOME_COMPLETO,EMAIL\n1,Ana,a@x.com".getBytes());
        ImportStudentsResponse resultadoService = mock(ImportStudentsResponse.class);
        when(service.importStudentsFromCsvForApi(1L, arquivo)).thenReturn(resultadoService);

        ImportStudentsResponse resultado = controller.importStudents(1L, arquivo);

        assertSame(resultadoService, resultado);
    }

    @Test
    @DisplayName("CSV vazio propaga IllegalArgumentException")
    void importarCsvVazio_lancaIllegalArgumentException() {
        // Ramo: service rejeita arquivo vazio -> exceção sobe
        MockMultipartFile vazio = new MockMultipartFile("file", "alunos.csv", "text/csv", new byte[0]);
        when(service.importStudentsFromCsvForApi(1L, vazio))
                .thenThrow(new IllegalArgumentException("Arquivo CSV vazio."));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.importStudents(1L, vazio));

        assertEquals("Arquivo CSV vazio.", ex.getMessage());
    }

    @Test
    @DisplayName("Detalhes do aluno na oferta vêm do service")
    void detalhesDoAlunoNaOferta_retornaDetalhes() {
        // Ramo: delegação para getStudentDetailsForApi
        OfferStudentDetailsResponse detalhes = mock(OfferStudentDetailsResponse.class);
        when(service.getStudentDetailsForApi(1L, 2L)).thenReturn(detalhes);

        OfferStudentDetailsResponse resultado = controller.studentDetails(1L, 2L);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Prévia de encerramento vem do service")
    void previaDeEncerramento_retornaPrevia() {
        // Ramo: delegação para getCloseOfferPreviewForApi
        SecretaryCloseOfferPreviewResponse previa = mock(SecretaryCloseOfferPreviewResponse.class);
        when(service.getCloseOfferPreviewForApi(1L)).thenReturn(previa);

        SecretaryCloseOfferPreviewResponse resultado = controller.closePreview(1L);

        assertSame(previa, resultado);
    }

    @Test
    @DisplayName("Encerrar oferta usa o username da secretaria")
    void encerrarOferta_usaUsernameDaSecretaria() {
        // Ramo: closeOffer repassa authentication.getName()
        OfferSummaryResponse encerrada = mock(OfferSummaryResponse.class);
        when(service.closeOfferForApi(1L, "secretaria")).thenReturn(encerrada);

        OfferSummaryResponse resultado = controller.closeOffer(1L, new SecretaryCloseOfferRequest(true), auth);

        assertSame(encerrada, resultado);
        verify(service, times(1)).closeOfferForApi(1L, "secretaria");
    }

    @Test
    @DisplayName("Oferta fora de encerramento propaga BusinessRuleException")
    void encerrarOfertaForaDeEncerramento_lancaBusinessRuleException() {
        // Ramo: service rejeita o encerramento -> exceção sobe
        when(service.closeOfferForApi(1L, "secretaria"))
                .thenThrow(new BusinessRuleException("Oferta não está aguardando encerramento."));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> controller.closeOffer(1L, new SecretaryCloseOfferRequest(true), auth));

        assertEquals("Oferta não está aguardando encerramento.", ex.getMessage());
    }
}

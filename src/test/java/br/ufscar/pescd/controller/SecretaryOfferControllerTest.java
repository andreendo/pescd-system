package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.OfferForm;
import br.ufscar.pescd.dto.StudentForm;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.service.SecretaryOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecretaryOfferControllerTest {

    SecretaryOfferService service = mock(SecretaryOfferService.class);

    SecretaryOfferController controller;

    Model model;
    RedirectAttributesModelMap redirect;
    TestingAuthenticationToken auth = new TestingAuthenticationToken("secretaria", "pw");

    @BeforeEach
    void setUp() {
        controller = new SecretaryOfferController(service);
        model = new ExtendedModelMap();
        redirect = new RedirectAttributesModelMap();
    }

    private OfferForm formComDatas(LocalDate inicio, LocalDate fim) {
        OfferForm f = new OfferForm();
        f.setStartDate(inicio);
        f.setEndDate(fim);
        return f;
    }

    // ---------------------------------------------------------------- listagem / formulário

    @Test
    @DisplayName("Listagem de ofertas coloca a lista no model")
    void listarOfertas_retornaViewComOfertas() {
        // Ramo: caminho feliz de listOffers
        List<Offer> ofertas = List.of(new Offer());
        when(service.listOffers()).thenReturn(ofertas);

        String view = controller.listOffers(model);

        assertEquals("secretary/offers/list", view);
        assertSame(ofertas, model.getAttribute("offers"));
    }

    @Test
    @DisplayName("Formulário de nova oferta traz form vazio e professores")
    void formularioNovaOferta_retornaFormEProfessores() {
        // Ramo: GET /new
        List<User> professores = List.of(new User());
        when(service.listProfessors()).thenReturn(professores);

        String view = controller.createForm(model);

        assertEquals("secretary/offers/form", view);
        assertNotNull(model.getAttribute("offerForm"));
        assertSame(professores, model.getAttribute("professors"));
    }

    // ---------------------------------------------------------------- criar oferta

    @Test
    @DisplayName("Formulário válido cria a oferta e redireciona")
    void criarOfertaComFormularioValido_redirecionaParaLista() {
        // Ramo: datas ok, sem erros -> createOffer
        OfferForm f = formComDatas(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");

        String view = controller.create(f, br, auth, model, redirect);

        assertEquals("redirect:/secretary/offers", view);
        assertEquals("Oferta criada com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).createOffer(f, "secretaria");
    }

    @Test
    @DisplayName("Data de fim igual ao início é rejeitada")
    void dataFimIgualAoInicio_rejeitaEndDate() {
        // Ramo: !endDate.isAfter(startDate) com datas iguais -> rejectValue
        OfferForm f = formComDatas(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");

        String view = controller.create(f, br, auth, model, redirect);

        assertEquals("secretary/offers/form", view);
        assertEquals("A data de fim deve ser depois da data de início.",
                br.getFieldError("endDate").getDefaultMessage());
        assertTrue(model.containsAttribute("professors"));
        verify(service, never()).createOffer(any(), anyString());
    }

    @Test
    @DisplayName("Data de fim anterior ao início é rejeitada")
    void dataFimAnteriorAoInicio_rejeitaEndDate() {
        // Ramo: !endDate.isAfter(startDate) com fim < início -> rejectValue
        OfferForm f = formComDatas(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1));
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");

        String view = controller.create(f, br, auth, model, redirect);

        assertEquals("secretary/offers/form", view);
        assertTrue(br.hasFieldErrors("endDate"));
    }

    @Test
    @DisplayName("Binding já com erros reexibe o formulário sem criar")
    void criarOfertaComErroDeBinding_reexibeFormulario() {
        // Ramo: datas nulas (pula validação) e bindingResult.hasErrors
        OfferForm f = formComDatas(null, null);
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");
        br.rejectValue("semester", "NotBlank", "Semestre obrigatório.");

        String view = controller.create(f, br, auth, model, redirect);

        assertEquals("secretary/offers/form", view);
        verify(service, never()).createOffer(any(), anyString());
    }

    @Test
    @DisplayName("Só data de início informada não valida a ordem das datas")
    void apenasInicioInformado_naoValidaOrdemDasDatas() {
        // Ramo: startDate != null e endDate == null -> curto-circuito
        OfferForm f = formComDatas(LocalDate.of(2026, 1, 1), null);
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");
        br.rejectValue("endDate", "NotNull", "obrigatória");

        controller.create(f, br, auth, model, redirect);

        assertEquals(1, br.getFieldErrorCount("endDate"));
    }

    @Test
    @DisplayName("Só data de fim informada não valida a ordem das datas")
    void apenasFimInformado_naoValidaOrdemDasDatas() {
        // Ramo: startDate == null -> curto-circuito
        OfferForm f = formComDatas(null, LocalDate.of(2026, 1, 1));
        BindingResult br = new BeanPropertyBindingResult(f, "offerForm");
        br.rejectValue("startDate", "NotNull", "obrigatória");

        controller.create(f, br, auth, model, redirect);

        assertEquals(0, br.getFieldErrorCount("endDate"));
    }

    // ---------------------------------------------------------------- detalhes / alunos

    @Test
    @DisplayName("Detalhes da oferta trazem a oferta e seus alunos")
    void detalhesDaOferta_retornaOfertaEAlunos() {
        // Ramo: GET /{id}
        Offer oferta = new Offer();
        List<OfferStudent> alunos = List.of(new OfferStudent());
        when(service.getOffer(1L)).thenReturn(oferta);
        when(service.getOfferStudents(1L)).thenReturn(alunos);

        String view = controller.offerDetails(1L, model);

        assertEquals("secretary/offers/details", view);
        assertSame(oferta, model.getAttribute("offer"));
        assertSame(alunos, model.getAttribute("students"));
    }

    @Test
    @DisplayName("Gerenciar alunos cria o form quando ele não está no model")
    void gerenciarAlunosSemForm_criaStudentForm() {
        // Ramo: !model.containsAttribute("studentForm") -> cria novo
        when(service.getOffer(1L)).thenReturn(new Offer());
        when(service.getOfferStudents(1L)).thenReturn(List.of());

        String view = controller.manageStudents(1L, model);

        assertEquals("secretary/offers/students", view);
        assertNotNull(model.getAttribute("studentForm"));
    }

    @Test
    @DisplayName("Gerenciar alunos mantém o form que já estava no model")
    void gerenciarAlunosComFormNoModel_mantemOExistente() {
        // Ramo: model já contém studentForm (flash de erro) -> não recria
        StudentForm existente = new StudentForm();
        model.addAttribute("studentForm", existente);
        when(service.getOffer(1L)).thenReturn(new Offer());
        when(service.getOfferStudents(1L)).thenReturn(List.of());

        controller.manageStudents(1L, model);

        assertSame(existente, model.getAttribute("studentForm"));
    }

    // ---------------------------------------------------------------- adicionar aluno

    @Test
    @DisplayName("Adicionar aluno válido redireciona com mensagem de sucesso")
    void adicionarAlunoValido_redirecionaParaAlunos() {
        // Ramo: sem erros, service aceita
        StudentForm f = new StudentForm();
        BindingResult br = new BeanPropertyBindingResult(f, "studentForm");

        String view = controller.addStudent(1L, f, br, model, redirect);

        assertEquals("redirect:/secretary/offers/1/students", view);
        assertEquals("Aluno adicionado à oferta.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).addStudent(1L, f);
    }

    @Test
    @DisplayName("Adicionar aluno com erro de validação reexibe a tela")
    void adicionarAlunoComErroDeBinding_reexibeTela() {
        // Ramo: bindingResult.hasErrors
        StudentForm f = new StudentForm();
        BindingResult br = new BeanPropertyBindingResult(f, "studentForm");
        br.rejectValue("email", "Email", "e-mail inválido");
        Offer oferta = new Offer();
        when(service.getOffer(1L)).thenReturn(oferta);
        when(service.getOfferStudents(1L)).thenReturn(List.of());

        String view = controller.addStudent(1L, f, br, model, redirect);

        assertEquals("secretary/offers/students", view);
        assertSame(oferta, model.getAttribute("offer"));
        verify(service, never()).addStudent(any(), any());
    }

    @Test
    @DisplayName("Service rejeitando o aluno vira mensagem de erro")
    void adicionarAlunoRejeitadoPeloService_redirecionaComErro() {
        // Ramo: catch IllegalArgumentException
        StudentForm f = new StudentForm();
        BindingResult br = new BeanPropertyBindingResult(f, "studentForm");
        doThrow(new IllegalArgumentException("Aluno já está na oferta."))
                .when(service).addStudent(1L, f);

        String view = controller.addStudent(1L, f, br, model, redirect);

        assertEquals("redirect:/secretary/offers/1/students", view);
        assertEquals("Aluno já está na oferta.", redirect.getFlashAttributes().get("errorMessage"));
    }

    // ---------------------------------------------------------------- importar CSV

    @Test
    @DisplayName("Importar CSV informa a quantidade de alunos adicionados")
    void importarCsvValido_informaQuantidade() {
        // Ramo: importStudentsFromCsv retorna a contagem
        MockMultipartFile arquivo = new MockMultipartFile("file", "a.csv", "text/csv", "x".getBytes());
        when(service.importStudentsFromCsv(1L, arquivo)).thenReturn(3);

        String view = controller.importStudents(1L, arquivo, redirect);

        assertEquals("redirect:/secretary/offers/1/students", view);
        assertEquals("3 aluno(s) adicionado(s) à oferta a partir do CSV.",
                redirect.getFlashAttributes().get("successMessage"));
    }

    @Test
    @DisplayName("CSV inválido vira mensagem de erro")
    void importarCsvInvalido_redirecionaComErro() {
        // Ramo: catch IllegalArgumentException
        MockMultipartFile arquivo = new MockMultipartFile("file", "a.csv", "text/csv", new byte[0]);
        when(service.importStudentsFromCsv(1L, arquivo)).thenThrow(new IllegalArgumentException("CSV vazio."));

        String view = controller.importStudents(1L, arquivo, redirect);

        assertEquals("redirect:/secretary/offers/1/students", view);
        assertEquals("CSV vazio.", redirect.getFlashAttributes().get("errorMessage"));
    }

    // ---------------------------------------------------------------- aluno / encerrar

    @Test
    @DisplayName("Detalhes do aluno trazem a matrícula e os logs")
    void detalhesDoAluno_retornaMatriculaELogs() {
        // Ramo: GET /{offerId}/students/{offerStudentId}
        OfferStudent os = new OfferStudent();
        List<StatusChangeLog> logs = List.of();
        when(service.getOfferStudent(2L)).thenReturn(os);
        when(service.getStudentLogs(2L)).thenReturn(logs);

        String view = controller.studentDetails(1L, 2L, model);

        assertEquals("secretary/offers/student-details", view);
        assertSame(os, model.getAttribute("offerStudent"));
        assertSame(logs, model.getAttribute("logs"));
    }

    @Test
    @DisplayName("Encerrar oferta redireciona com mensagem de sucesso")
    void encerrarOferta_redirecionaComSucesso() {
        // Ramo: closeOffer sem exceção
        String view = controller.closeOffer(1L, redirect);

        assertEquals("redirect:/secretary/offers", view);
        assertEquals("Oferta encerrada com sucesso.", redirect.getFlashAttributes().get("successMessage"));
        verify(service, times(1)).closeOffer(1L);
    }

    @Test
    @DisplayName("Oferta que não pode ser encerrada vira mensagem de erro")
    void encerrarOfertaRejeitadaPeloService_redirecionaComErro() {
        // Ramo: catch IllegalArgumentException
        doThrow(new IllegalArgumentException("Oferta não aguarda encerramento."))
                .when(service).closeOffer(1L);

        String view = controller.closeOffer(1L, redirect);

        assertEquals("redirect:/secretary/offers", view);
        assertEquals("Oferta não aguarda encerramento.", redirect.getFlashAttributes().get("errorMessage"));
    }
}

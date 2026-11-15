package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.DocumentationForm;
import br.ufscar.pescd.dto.ReportForm;
import br.ufscar.pescd.dto.StudentEnrollmentDetailsResponse;
import br.ufscar.pescd.dto.StudentOfferSummaryResponse;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.dto.WorkPlanForm;
import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.DocumentationRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class StudentOfferServiceTest {

    static final byte[] PDF = "%PDF-1.4 conteudo".getBytes();

    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    WorkPlanRepository workPlanRepository = mock(WorkPlanRepository.class);
    DocumentationRepository documentationRepository = mock(DocumentationRepository.class);
    ReportRepository reportRepository = mock(ReportRepository.class);
    StatusChangeLogRepository statusChangeLogRepository = mock(StatusChangeLogRepository.class);
    OfferApiMapper offerApiMapper = mock(OfferApiMapper.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);

    StudentOfferService service;

    User student;
    Offer offer;
    OfferStudent enrollment;

    @BeforeEach
    void setUp() {
        service = new StudentOfferService(offerStudentRepository, userRepository, workPlanRepository,
                documentationRepository, reportRepository, statusChangeLogRepository,
                offerApiMapper, storageService);

        student = new User();
        student.setId(1L);
        student.setUsername("aluno");
        student.setRole(UserRole.ALUNO);

        offer = new Offer();
        offer.setId(10L);
        offer.setStatus(OfferStatus.EM_ANDAMENTO);

        enrollment = new OfferStudent();
        enrollment.setId(100L);
        enrollment.setOffer(offer);
        enrollment.setStudent(student);
        enrollment.setStatus(StudentOfferStatus.NAO_ENVIADO);

        when(offerStudentRepository.findById(100L)).thenReturn(Optional.of(enrollment));
    }

    private User criarProfessor(Long id) {
        User p = new User();
        p.setId(id);
        p.setUsername("prof" + id);
        p.setRole(UserRole.PROFESSOR);
        return p;
    }

    private MockMultipartFile criarPdf() {
        return new MockMultipartFile("file", "plano.pdf", "application/pdf", PDF);
    }

    private WorkPlanForm criarWorkPlanForm(MultipartFile file) {
        WorkPlanForm f = new WorkPlanForm();
        f.setDisciplineCode("DC001");
        f.setDisciplineName("Algoritmos");
        f.setDisciplineCourse("BCC");
        f.setSupervisorId(50L);
        f.setFile(file);
        return f;
    }

    private DocumentationForm criarDocumentationForm(MultipartFile file) {
        DocumentationForm f = new DocumentationForm();
        f.setInstitutionName("USP");
        f.setDisciplineName("Algoritmos");
        f.setDisciplineCourse("BCC");
        f.setWorkloadHours(60);
        f.setFile(file);
        return f;
    }

    private ReportForm criarReportForm(MultipartFile file) {
        ReportForm f = new ReportForm();
        f.setFrequency(90);
        f.setFile(file);
        return f;
    }

    private StudentEnrollmentDetailsResponse criarDetalhes() {
        return new StudentEnrollmentDetailsResponse(null, null, null, null, null, List.of());
    }

    // ---------------- consultas ----------------

    @Test
    @DisplayName("Lista as inscrições do aluno logado")
    void alunoExistente_listaInscricoes() {
        // Ramo: aluno encontrado -> retorna inscrições do repositório
        when(userRepository.findByUsername("aluno")).thenReturn(Optional.of(student));
        when(offerStudentRepository.findByStudent(student)).thenReturn(List.of(enrollment));

        List<OfferStudent> resultado = service.findStudentEnrollments("aluno");

        assertEquals(List.of(enrollment), resultado);
    }

    @Test
    @DisplayName("Aluno inexistente ao listar inscrições")
    void alunoNaoEncontrado_lancaIllegalArgumentException() {
        // Ramo: aluno não encontrado -> exceção
        when(userRepository.findByUsername("x")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.findStudentEnrollments("x"));

        assertEquals("Aluno não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Mapeia as inscrições para o resumo da API")
    void listaInscricoesParaApi_usaMapper() {
        // Ramo: lista de inscrições -> mapeada por toStudentOfferSummary
        StudentOfferSummaryResponse resumo = new StudentOfferSummaryResponse(null, null);
        when(userRepository.findByUsername("aluno")).thenReturn(Optional.of(student));
        when(offerStudentRepository.findByStudent(student)).thenReturn(List.of(enrollment));
        when(offerApiMapper.toStudentOfferSummary(enrollment)).thenReturn(resumo);

        List<StudentOfferSummaryResponse> resultado = service.listEnrollmentsForApi("aluno");

        assertEquals(List.of(resumo), resultado);
    }

    @Test
    @DisplayName("Retorna a inscrição do próprio aluno")
    void inscricaoDoProprioAluno_retornaInscricao() {
        // Ramo: inscrição existe e pertence ao aluno -> retorna
        OfferStudent resultado = service.getEnrollment(100L, "aluno");

        assertSame(enrollment, resultado);
    }

    @Test
    @DisplayName("Inscrição inexistente")
    void inscricaoInexistente_lancaIllegalArgumentException() {
        // Ramo: inscrição não encontrada -> exceção
        when(offerStudentRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollment(999L, "aluno"));

        assertEquals("Inscrição não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Inscrição pertence a outro aluno")
    void inscricaoDeOutroAluno_lancaIllegalArgumentException() {
        // Ramo: inscrição de outro aluno -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getEnrollment(100L, "outro"));

        assertEquals("Esta inscrição não pertence ao aluno logado.", ex.getMessage());
    }

    @Test
    @DisplayName("Mapeia os detalhes da inscrição para a API")
    void detalhesDaInscricaoParaApi_usaMapper() {
        // Ramo: inscrição do aluno -> toStudentEnrollmentDetails
        StudentEnrollmentDetailsResponse detalhes = criarDetalhes();
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado = service.getEnrollmentDetailsForApi(100L, "aluno");

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Lista professores para escolha de supervisor")
    void listaProfessores_retornaProfessoresEResumos() {
        // Ramo: listProfessors e listProfessorsForApi (mapeamento)
        User professor = criarProfessor(50L);
        UserSummaryResponse resumo = new UserSummaryResponse(50L, "n", "e", "prof50");
        when(userRepository.findByRole(UserRole.PROFESSOR)).thenReturn(List.of(professor));
        when(offerApiMapper.toUserSummary(professor)).thenReturn(resumo);

        List<User> professores = service.listProfessors();
        List<UserSummaryResponse> resumos = service.listProfessorsForApi();

        assertEquals(List.of(professor), professores);
        assertEquals(List.of(resumo), resumos);
    }

    @Test
    @DisplayName("Retorna plano, documentação e relatório existentes")
    void enviosExistentes_retornaPlanoDocumentacaoERelatorio() {
        // Ramo: Optional presente nos três getters
        WorkPlan plano = new WorkPlan();
        Documentation documentacao = new Documentation();
        Report relatorio = new Report();
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(plano));
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(documentacao));
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(relatorio));

        assertSame(plano, service.getWorkPlan(enrollment));
        assertSame(documentacao, service.getDocumentation(enrollment));
        assertSame(relatorio, service.getReport(enrollment));
    }

    @Test
    @DisplayName("Sem envios retorna nulo")
    void semEnvios_retornaNulo() {
        // Ramo: Optional vazio nos três getters -> null
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());

        assertNull(service.getWorkPlan(enrollment));
        assertNull(service.getDocumentation(enrollment));
        assertNull(service.getReport(enrollment));
    }

    @Test
    @DisplayName("Lista os logs de status da inscrição")
    void logsDeStatus_retornaListaDoRepositorio() {
        // Ramo: delega ao repositório
        List<StatusChangeLog> logs = List.of(new StatusChangeLog());
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(enrollment)).thenReturn(logs);

        List<StatusChangeLog> resultado = service.getStatusLogs(enrollment);

        assertSame(logs, resultado);
    }

    // ---------------- plano de trabalho ----------------

    @Test
    @DisplayName("Envia plano de trabalho novo e registra log")
    void planoNovo_salvaPlanoDefineSupervisorERegistraLog() {
        // Ramo: sem plano anterior -> cria, armazena, define supervisor e muda status
        when(userRepository.findById(50L)).thenReturn(Optional.of(criarProfessor(50L)));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(PDF, "plano.pdf", "application/pdf")).thenReturn("3,abc");

        service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf()));

        verify(workPlanRepository, times(1)).save(argThat(wp ->
                "3,abc".equals(wp.getFileFid())
                        && "DC001".equals(wp.getDisciplineCode())
                        && "Algoritmos".equals(wp.getDisciplineName())
                        && "BCC".equals(wp.getDisciplineCourse())
                        && "plano.pdf".equals(wp.getFileName())
                        && "application/pdf".equals(wp.getContentType())
                        && wp.getOfferStudent() == enrollment));
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.NAO_ENVIADO
                        && log.getNewStatus() == StudentOfferStatus.PLANO_ENVIADO
                        && "Plano de trabalho enviado".equals(log.getDescription())));
        verify(offerStudentRepository, times(1)).save(enrollment);
        assertEquals(50L, enrollment.getSupervisor().getId());
        assertEquals(StudentOfferStatus.PLANO_ENVIADO, enrollment.getStatus());
    }

    @Test
    @DisplayName("Substituir plano existente remove o arquivo antigo")
    void planoExistente_removeArquivoAnteriorEArmazenaNovo() {
        // Ramo: plano anterior existe -> delete do fid antigo antes do store
        WorkPlan existente = new WorkPlan();
        existente.setFileFid("1,old");
        when(userRepository.findById(50L)).thenReturn(Optional.of(criarProfessor(50L)));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existente));
        when(storageService.store(any(), any(), any())).thenReturn("2,new");

        service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf()));

        verify(storageService, times(1)).delete("1,old");
        verify(storageService, times(1)).store(PDF, "plano.pdf", "application/pdf");
        assertEquals("2,new", existente.getFileFid());
    }

    @Test
    @DisplayName("Plano com oferta fora de andamento")
    void planoComOfertaConcluida_lancaIllegalArgumentException() {
        // Ramo: oferta não está EM_ANDAMENTO -> exceção, nada armazenado
        offer.setStatus(OfferStatus.CONCLUIDA);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf())));

        assertEquals("Esta ação só é permitida em ofertas em andamento.", ex.getMessage());
        verify(storageService, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("Plano com status diferente de não enviado")
    void planoComStatusPlanoEnviado_lancaIllegalArgumentException() {
        // Ramo: status atual != NAO_ENVIADO -> exceção
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf())));

        assertEquals("Ação indisponível: o status atual é \"Plano enviado\".", ex.getMessage());
    }

    @Test
    @DisplayName("Supervisor inexistente")
    void supervisorInexistente_lancaIllegalArgumentException() {
        // Ramo: findById vazio -> supervisor inválido
        when(userRepository.findById(50L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf())));

        assertEquals("Professor supervisor inválido.", ex.getMessage());
    }

    @Test
    @DisplayName("Supervisor sem perfil de professor")
    void supervisorSecretario_lancaIllegalArgumentException() {
        // Ramo: usuário existe mas role != PROFESSOR -> supervisor inválido
        User secretario = criarProfessor(50L);
        secretario.setRole(UserRole.SECRETARIO);
        when(userRepository.findById(50L)).thenReturn(Optional.of(secretario));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(criarPdf())));

        assertEquals("Professor supervisor inválido.", ex.getMessage());
    }

    @Test
    @DisplayName("Plano com arquivo que não é PDF")
    void planoComArquivoTxt_lancaIllegalArgumentException() {
        // Ramo: nem content-type nem extensão de PDF -> exceção, nada salvo
        when(userRepository.findById(50L)).thenReturn(Optional.of(criarProfessor(50L)));
        MockMultipartFile txt = new MockMultipartFile("file", "plano.txt", "text/plain", PDF);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "aluno", criarWorkPlanForm(txt)));

        assertEquals("O arquivo deve estar no formato PDF.", ex.getMessage());
        verify(workPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("Plano de inscrição de outro aluno")
    void planoDeOutroAluno_lancaIllegalArgumentException() {
        // Ramo: inscrição não pertence ao usuário -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitWorkPlan(100L, "outro", criarWorkPlanForm(criarPdf())));

        assertEquals("Esta inscrição não pertence ao aluno logado.", ex.getMessage());
    }

    @Test
    @DisplayName("Envio de plano pela API retorna detalhes atualizados")
    void planoViaApi_retornaDetalhes() {
        // Ramo: submitWorkPlanForApi -> submit + getEnrollmentDetailsForApi
        when(userRepository.findById(50L)).thenReturn(Optional.of(criarProfessor(50L)));
        when(workPlanRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("3,abc");
        StudentEnrollmentDetailsResponse detalhes = criarDetalhes();
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado =
                service.submitWorkPlanForApi(100L, "aluno", criarWorkPlanForm(criarPdf()));

        assertSame(detalhes, resultado);
    }

    // ---------------- documentação ----------------

    @Test
    @DisplayName("Envia documentação nova e registra log")
    void documentacaoNova_salvaERegistraStatus() {
        // Ramo: sem documentação anterior -> cria e muda status
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(PDF, "plano.pdf", "application/pdf")).thenReturn("4,doc");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(criarPdf()));

        verify(documentationRepository, times(1)).save(argThat(d ->
                "4,doc".equals(d.getFileFid())
                        && "USP".equals(d.getInstitutionName())
                        && d.getWorkloadHours() == 60));
        assertEquals(StudentOfferStatus.DOCUMENTACAO_ENVIADA, enrollment.getStatus());
    }

    @Test
    @DisplayName("Reenvio de documentação devolvida limpa a devolução anterior")
    void documentacaoDevolvida_reenvioLimpaParecerDeDevolucao() {
        // Ramo (Mud4): novo envio apaga parecer e data da devolução
        Documentation devolvida = new Documentation();
        devolvida.setFileFid("1,old");
        devolvida.setDevolucaoParecer("Anexe o comprovante");
        devolvida.setDevolvidaAt(java.time.LocalDateTime.now());
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(devolvida));
        when(storageService.store(any(), any(), any())).thenReturn("9,new");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(criarPdf()));

        assertNull(devolvida.getDevolucaoParecer());
        assertNull(devolvida.getDevolvidaAt());
    }

    @Test
    @DisplayName("Substitui documentação existente removendo arquivo antigo")
    void documentacaoExistente_removeArquivoAntigo() {
        // Ramo: documentação anterior existe -> delete do fid antigo
        Documentation existente = new Documentation();
        existente.setFileFid("1,old");
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existente));
        when(storageService.store(any(), any(), any())).thenReturn("9,new");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(criarPdf()));

        verify(storageService, times(1)).delete("1,old");
        assertEquals("9,new", existente.getFileFid());
    }

    @Test
    @DisplayName("Documentação com oferta em atraso")
    void documentacaoComOfertaEmAtraso_lancaIllegalArgumentException() {
        // Ramo: oferta não está EM_ANDAMENTO -> exceção
        offer.setStatus(OfferStatus.EM_ATRASO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(criarPdf())));

        assertEquals("Esta ação só é permitida em ofertas em andamento.", ex.getMessage());
    }

    @Test
    @DisplayName("Documentação com status já enviado")
    void documentacaoJaEnviada_lancaIllegalArgumentException() {
        // Ramo: status != NAO_ENVIADO -> exceção
        enrollment.setStatus(StudentOfferStatus.DOCUMENTACAO_ENVIADA);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(criarPdf())));

        assertTrue(ex.getMessage().contains("Documentação enviada"));
    }

    @Test
    @DisplayName("Documentação sem arquivo")
    void documentacaoSemArquivo_lancaIllegalArgumentException() {
        // Ramo: file == null -> arquivo obrigatório
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(null)));

        assertEquals("O arquivo é obrigatório.", ex.getMessage());
    }

    @Test
    @DisplayName("Envio de documentação pela API retorna detalhes")
    void documentacaoViaApi_retornaDetalhes() {
        // Ramo: submitDocumentationForApi -> submit + detalhes
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("4,doc");
        StudentEnrollmentDetailsResponse detalhes = criarDetalhes();
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado =
                service.submitDocumentationForApi(100L, "aluno", criarDocumentationForm(criarPdf()));

        assertSame(detalhes, resultado);
    }

    // ---------------- relatório ----------------

    @Test
    @DisplayName("Envia relatório novo e registra log")
    void relatorioNovo_salvaERegistraLog() {
        // Ramo: sem relatório anterior -> cria e muda status
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(PDF, "plano.pdf", "application/pdf")).thenReturn("5,rel");

        service.submitReport(100L, "aluno", criarReportForm(criarPdf()));

        verify(reportRepository, times(1)).save(argThat(r ->
                "5,rel".equals(r.getFileFid()) && r.getFrequency() == 90));
        verify(statusChangeLogRepository, times(1)).save(argThat(log ->
                log.getPreviousStatus() == StudentOfferStatus.PLANO_APROVADO
                        && "Relatório final enviado".equals(log.getDescription())));
        assertEquals(StudentOfferStatus.RELATORIO_ENVIADO, enrollment.getStatus());
    }

    @Test
    @DisplayName("Substitui relatório existente removendo arquivo antigo")
    void relatorioExistente_removeArquivoAntigo() {
        // Ramo: relatório anterior existe -> delete do fid antigo
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        Report existente = new Report();
        existente.setFileFid("1,old");
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.of(existente));
        when(storageService.store(any(), any(), any())).thenReturn("8,new");

        service.submitReport(100L, "aluno", criarReportForm(criarPdf()));

        verify(storageService, times(1)).delete("1,old");
        assertEquals("8,new", existente.getFileFid());
    }

    @Test
    @DisplayName("Relatório exige plano aprovado")
    void relatorioComPlanoApenasEnviado_lancaIllegalArgumentException() {
        // Ramo: status != PLANO_APROVADO -> exceção
        enrollment.setStatus(StudentOfferStatus.PLANO_ENVIADO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(100L, "aluno", criarReportForm(criarPdf())));

        assertEquals("Ação indisponível: o status atual é \"Plano enviado\".", ex.getMessage());
    }

    @Test
    @DisplayName("Relatório com oferta aguardando encerramento")
    void relatorioComOfertaAguardandoEncerramento_lancaIllegalArgumentException() {
        // Ramo: oferta não está EM_ANDAMENTO -> exceção
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitReport(100L, "aluno", criarReportForm(criarPdf())));

        assertEquals("Esta ação só é permitida em ofertas em andamento.", ex.getMessage());
    }

    @Test
    @DisplayName("Envio de relatório pela API retorna detalhes")
    void relatorioViaApi_retornaDetalhes() {
        // Ramo: submitReportForApi -> submit + detalhes
        enrollment.setStatus(StudentOfferStatus.PLANO_APROVADO);
        when(reportRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("5,rel");
        StudentEnrollmentDetailsResponse detalhes = criarDetalhes();
        when(offerApiMapper.toStudentEnrollmentDetails(enrollment)).thenReturn(detalhes);

        StudentEnrollmentDetailsResponse resultado =
                service.submitReportForApi(100L, "aluno", criarReportForm(criarPdf()));

        assertSame(detalhes, resultado);
    }

    // ---------------- validação do PDF ----------------

    @Test
    @DisplayName("Arquivo vazio é rejeitado")
    void arquivoVazio_lancaIllegalArgumentException() {
        // Ramo: file.isEmpty() -> arquivo obrigatório
        MockMultipartFile vazio = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(vazio)));

        assertEquals("O arquivo é obrigatório.", ex.getMessage());
    }

    @Test
    @DisplayName("Arquivo maior que 5 MB é rejeitado")
    void arquivoMaiorQueCincoMb_lancaIllegalArgumentException() {
        // Ramo: size > MAX_FILE_SIZE -> exceção
        MultipartFile grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(grande)));

        assertEquals("O arquivo deve ter no máximo 5 MB.", ex.getMessage());
    }

    @Test
    @DisplayName("Arquivo com exatamente 5 MB é aceito")
    void arquivoComExatamenteCincoMb_eAceito() {
        // Ramo: size == MAX_FILE_SIZE (limite) -> aceito
        MultipartFile limite = new MockMultipartFile("file", "a.pdf", "application/pdf",
                new byte[5 * 1024 * 1024]);
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("1,x");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(limite));

        verify(documentationRepository, times(1)).save(any(Documentation.class));
    }

    @Test
    @DisplayName("PDF identificado só pela extensão em maiúsculas")
    void pdfSoPelaExtensaoMaiuscula_eAceito() {
        // Ramo: content-type não é PDF mas nome termina com .pdf (ignorando caixa)
        MockMultipartFile arquivo = new MockMultipartFile("file", "DOC.PDF", "application/octet-stream", PDF);
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("1,x");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(arquivo));

        verify(storageService, times(1)).store(PDF, "DOC.PDF", "application/octet-stream");
    }

    @Test
    @DisplayName("PDF identificado só pelo content-type sem extensão")
    void pdfSoPeloContentTypeSemExtensao_eAceito() {
        // Ramo: content-type PDF (caixa mista) e nome sem extensão -> aceito
        MockMultipartFile arquivo = new MockMultipartFile("file", "documento", "APPLICATION/PDF", PDF);
        when(documentationRepository.findByOfferStudent(enrollment)).thenReturn(Optional.empty());
        when(storageService.store(any(), any(), any())).thenReturn("1,x");

        service.submitDocumentation(100L, "aluno", criarDocumentationForm(arquivo));

        verify(documentationRepository, times(1)).save(any(Documentation.class));
    }

    @Test
    @DisplayName("Arquivo sem nome e com content-type não PDF")
    void arquivoSemNomeEContentTypePng_lancaIllegalArgumentException() {
        // Ramo: name == null e content-type não PDF -> formato inválido
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getSize()).thenReturn(10L);
        when(arquivo.getContentType()).thenReturn("image/png");
        when(arquivo.getOriginalFilename()).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(arquivo)));

        assertEquals("O arquivo deve estar no formato PDF.", ex.getMessage());
    }

    @Test
    @DisplayName("Falha de leitura vira IllegalArgumentException")
    void falhaDeLeituraDoArquivo_lancaIllegalArgumentException() throws IOException {
        // Ramo: getBytes lança IOException -> convertida com causa preservada
        MultipartFile arquivo = mock(MultipartFile.class);
        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getSize()).thenReturn(10L);
        when(arquivo.getContentType()).thenReturn("application/pdf");
        when(arquivo.getOriginalFilename()).thenReturn("a.pdf");
        when(arquivo.getBytes()).thenThrow(new IOException("disco"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitDocumentation(100L, "aluno", criarDocumentationForm(arquivo)));

        assertEquals("Não foi possível ler o arquivo enviado.", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }
}

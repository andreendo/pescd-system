package br.ufscar.pescd.config;

import br.ufscar.pescd.entity.Documentation;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.Report;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.entity.WorkPlan;
import br.ufscar.pescd.enums.GradeOption;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.DocumentationRepository;
import br.ufscar.pescd.repository.OfferRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.ReportRepository;
import br.ufscar.pescd.repository.UserRepository;
import br.ufscar.pescd.repository.WorkPlanRepository;
import br.ufscar.pescd.storage.SeaweedFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    UserRepository userRepository = mock(UserRepository.class);
    OfferRepository offerRepository = mock(OfferRepository.class);
    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    WorkPlanRepository workPlanRepository = mock(WorkPlanRepository.class);
    ReportRepository reportRepository = mock(ReportRepository.class);
    DocumentationRepository documentationRepository = mock(DocumentationRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    SeaweedFsStorageService storageService = mock(SeaweedFsStorageService.class);

    DataInitializer initializer;

    @BeforeEach
    void setUp() {
        // save devolve o proprio objeto; encode devolve "enc:" + senha; storage devolve um fid fixo
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(i -> i.getArgument(0));
        when(offerStudentRepository.save(any(OfferStudent.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "enc:" + i.getArgument(0));
        when(storageService.store(any(byte[].class), anyString(), anyString())).thenReturn("3,abc");

        initializer = new DataInitializer(userRepository, offerRepository, offerStudentRepository,
                workPlanRepository, reportRepository, documentationRepository, passwordEncoder, storageService);
    }

    // Caminho: banco vazio -> cria os 10 usuarios do seed (1 admin, 1 secretario, 4 professores, 4 alunos)
    @Test
    @DisplayName("Banco vazio cria os dez usuarios com senha codificada")
    void bancoVazio_criaDezUsuariosComSenhaCodificada() {

        initializer.run();

        verify(userRepository, times(10)).save(any(User.class));
        verify(userRepository, times(1)).save(argThat(u -> "admin".equals(u.getUsername())
                && u.getRole() == UserRole.ADMIN && "enc:admin123".equals(u.getPassword()) && u.getEnabled()));
        verify(userRepository, times(1)).save(argThat(u -> u.getRole() == UserRole.SECRETARIO));
        verify(userRepository, times(4)).save(argThat(u -> u.getRole() == UserRole.PROFESSOR));
        verify(userRepository, times(4)).save(argThat(u -> u.getRole() == UserRole.ALUNO));
        verify(passwordEncoder, times(10)).encode(anyString());
    }

    // Caminho: usuario ja existe (id != null) -> atualiza dados, nao recodifica a senha
    @Test
    @DisplayName("Usuario existente mantem a senha e tem os dados atualizados")
    void usuarioExistente_mantemSenhaEAtualizaDados() {

        User existente = new User();
        existente.setId(1L);
        existente.setUsername("admin");
        existente.setPassword("old-hash");
        existente.setFullName("Antigo");
        existente.setRole(UserRole.PROFESSOR);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existente));

        initializer.run();

        assertEquals("old-hash", existente.getPassword());
        assertEquals("Administrador do Sistema", existente.getFullName());
        assertEquals(UserRole.ADMIN, existente.getRole());
        verify(passwordEncoder, never()).encode("admin123");
        verify(passwordEncoder, times(9)).encode(anyString());
        verify(userRepository, times(10)).save(any(User.class));
    }

    // Caminho: oferta nova (id nulo) -> cria 4 ofertas EM_ANDAMENTO criadas pelo admin
    @Test
    @DisplayName("Banco vazio cria quatro ofertas em andamento")
    void bancoVazio_criaQuatroOfertasEmAndamento() {

        initializer.run();

        verify(offerRepository, times(4)).save(any(Offer.class));
        verify(offerRepository, times(4)).save(argThat(o -> o.getStatus() == OfferStatus.EM_ANDAMENTO
                && "admin".equals(o.getCreatedBy().getUsername())));
        verify(offerRepository, times(1)).save(argThat(o -> "Algoritmo e Estrutura de Dados I".equals(o.getName())
                && "2026/1".equals(o.getSemester())
                && "alevada".equals(o.getResponsibleProfessor().getUsername())));
    }

    // Caminho: oferta existente AGUARDANDO_ENCERRAMENTO_SECRETARIO -> volta para EM_ANDAMENTO
    @Test
    @DisplayName("Oferta aguardando encerramento volta para em andamento")
    void ofertaAguardandoEncerramento_voltaParaEmAndamento() {

        Offer existente = new Offer();
        existente.setId(10L);
        existente.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        when(offerRepository.findByName("Aprendizado de Máquina I")).thenReturn(Optional.of(existente));

        initializer.run();

        assertEquals(OfferStatus.EM_ANDAMENTO, existente.getStatus());
        assertEquals("2025/2", existente.getSemester());
    }

    // Caminho: oferta existente com outro status (CONCLUIDA) -> preserva o status
    @Test
    @DisplayName("Oferta existente concluida preserva o status")
    void ofertaExistenteConcluida_preservaStatus() {

        Offer existente = new Offer();
        existente.setId(11L);
        existente.setStatus(OfferStatus.CONCLUIDA);
        when(offerRepository.findByName("Desenvolvimento Web I")).thenReturn(Optional.of(existente));

        initializer.run();

        assertEquals(OfferStatus.CONCLUIDA, existente.getStatus());
    }

    // Caminho: matriculas novas -> 11 matriculas com os status e supervisores do seed
    @Test
    @DisplayName("Banco vazio cria onze matriculas com status e supervisores")
    void bancoVazio_criaOnzeMatriculasComStatusESupervisores() {

        initializer.run();

        verify(offerStudentRepository, times(11)).save(any(OfferStudent.class));
        verify(offerStudentRepository, times(5)).save(argThat(m -> m.getStatus() == StudentOfferStatus.NAO_ENVIADO));
        verify(offerStudentRepository, times(2)).save(argThat(m -> m.getStatus() == StudentOfferStatus.CONCLUIDO_RESPONSAVEL));
        verify(offerStudentRepository, times(1)).save(argThat(m -> m.getStatus() == StudentOfferStatus.PLANO_ENVIADO
                && "alevada".equals(m.getSupervisor().getUsername())));
        verify(offerStudentRepository, times(1)).save(argThat(m -> m.getStatus() == StudentOfferStatus.RELATORIO_ENVIADO
                && "aendo".equals(m.getSupervisor().getUsername())));
        verify(offerStudentRepository, times(1)).save(argThat(m -> m.getStatus() == StudentOfferStatus.RELATORIO_APROVADO_SUPERVISOR
                && "ccomin".equals(m.getSupervisor().getUsername())));
        verify(offerStudentRepository, times(1)).save(argThat(m -> m.getStatus() == StudentOfferStatus.DOCUMENTACAO_ENVIADA
                && m.getSupervisor() == null));
    }

    // Caminho: matricula ja existe -> reaproveita o mesmo objeto em vez de criar outro
    @Test
    @DisplayName("Matricula existente e reaproveitada")
    void matriculaExistente_reaproveitaMatricula() {

        OfferStudent existente = new OfferStudent();
        existente.setId(5L);
        when(offerStudentRepository.findByOfferAndStudent(any(Offer.class), any(User.class)))
                .thenReturn(Optional.of(existente));

        initializer.run();

        verify(offerStudentRepository, times(11)).save(existente);
    }

    // Caminho: nada existe e o storage funciona -> semeia 3 planos, 2 relatorios e 1 documentacao
    @Test
    @DisplayName("Banco vazio semeia planos, relatorios e documentacao")
    void bancoVazio_semeiaPlanosRelatoriosEDocumentacao() {

        initializer.run();

        verify(workPlanRepository, times(3)).save(argThat(p -> "1000123".equals(p.getDisciplineCode())
                && "plano-exemplo.pdf".equals(p.getFileName())
                && "application/pdf".equals(p.getContentType())
                && "3,abc".equals(p.getFileFid())));
        verify(reportRepository, times(2)).save(any(Report.class));
        verify(reportRepository, times(1)).save(argThat(r -> r.getFrequency() == 85
                && r.getSupervisorApprovedAt() == null));
        verify(reportRepository, times(1)).save(argThat(r -> r.getFrequency() == 90
                && r.getSupervisorFrequencia() == 88
                && r.getSupervisorNotaSugestao() == GradeOption.B
                && !r.getSupervisorParecer().isBlank()
                && r.getSupervisorApprovedAt() != null));
        verify(documentationRepository, times(1)).save(argThat(d -> d.getWorkloadHours() == 60
                && "documentacao-exemplo.pdf".equals(d.getFileName())));
        verify(storageService, times(6)).store(any(byte[].class), anyString(), eq("application/pdf"));
    }

    // Caminho: plano, relatorio e documentacao ja existem -> nao envia arquivo nem salva de novo
    @Test
    @DisplayName("Arquivos ja semeados sao ignorados")
    void arquivosJaExistem_naoSemeiaNovamente() {

        when(workPlanRepository.findByOfferStudent(any())).thenReturn(Optional.of(new WorkPlan()));
        when(reportRepository.findByOfferStudent(any())).thenReturn(Optional.of(new Report()));
        when(documentationRepository.findByOfferStudent(any())).thenReturn(Optional.of(new Documentation()));

        initializer.run();

        verify(workPlanRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
        verify(documentationRepository, never()).save(any());
        verifyNoInteractions(storageService);
    }

    // Caminho: storage lanca RuntimeException (fid nulo) -> ignora os arquivos e a aplicacao sobe
    @Test
    @DisplayName("Storage fora do ar ignora os arquivos e nao quebra")
    void storageForaDoAr_ignoraArquivosENaoLancaExcecao() {

        when(storageService.store(any(byte[].class), anyString(), anyString()))
                .thenThrow(new RuntimeException("SeaweedFS fora do ar"));

        assertDoesNotThrow(() -> initializer.run());

        verify(workPlanRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
        verify(documentationRepository, never()).save(any());
        verify(userRepository, times(10)).save(any(User.class));
    }

    // Caminho: existe oferta legada -> remove so ela e as suas matriculas
    @Test
    @DisplayName("Remove apenas as ofertas legadas e suas matriculas")
    void ofertaLegada_removeSoLegadaESuasMatriculas() {

        Offer legada = new Offer();
        legada.setName("Estágio Docência em Algoritmos");
        Offer atual = new Offer();
        atual.setName("Oferta Atual");
        List<OfferStudent> matriculasLegadas = List.of(new OfferStudent());
        when(offerRepository.findAll()).thenReturn(List.of(legada, atual));
        when(offerStudentRepository.findByOffer(legada)).thenReturn(matriculasLegadas);

        initializer.run();

        verify(offerStudentRepository, times(1)).deleteAll(matriculasLegadas);
        verify(offerRepository, times(1)).delete(legada);
        verify(offerRepository, never()).delete(atual);
    }

    // Caminho: nenhuma oferta no banco -> nada e removido
    @Test
    @DisplayName("Sem ofertas legadas nada e removido")
    void semOfertas_naoRemoveNada() {

        when(offerRepository.findAll()).thenReturn(List.of());

        initializer.run();

        verify(offerRepository, never()).delete(any());
        verify(offerStudentRepository, never()).deleteAll(any());
    }
}

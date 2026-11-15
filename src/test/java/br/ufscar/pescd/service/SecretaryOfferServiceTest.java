package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.ImportStudentsResponse;
import br.ufscar.pescd.dto.OfferDetailsResponse;
import br.ufscar.pescd.dto.OfferForm;
import br.ufscar.pescd.dto.OfferStudentDetailsResponse;
import br.ufscar.pescd.dto.OfferSummaryResponse;
import br.ufscar.pescd.dto.SecretaryCloseOfferPreviewResponse;
import br.ufscar.pescd.dto.StudentForm;
import br.ufscar.pescd.dto.UserSummaryResponse;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.StatusChangeLog;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.repository.OfferRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import br.ufscar.pescd.repository.StatusChangeLogRepository;
import br.ufscar.pescd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class SecretaryOfferServiceTest {

    OfferRepository offerRepository = mock(OfferRepository.class);
    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    StatusChangeLogRepository statusChangeLogRepository = mock(StatusChangeLogRepository.class);
    OfferApiMapper offerApiMapper = mock(OfferApiMapper.class);

    SecretaryOfferService service;

    Offer offer;

    @BeforeEach
    void setUp() {
        service = new SecretaryOfferService(offerRepository, offerStudentRepository, userRepository,
                passwordEncoder, statusChangeLogRepository, offerApiMapper);

        offer = new Offer();
        offer.setId(1L);
        offer.setName("Oferta");
        offer.setSemester("2025/1");
        offer.setStatus(OfferStatus.EM_ANDAMENTO);
    }

    private User criarUsuario(Long id, String username, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@ufscar.br");
        u.setFullName("Nome " + username);
        u.setRole(role);
        return u;
    }

    private OfferForm criarFormOferta(String nome, LocalDate inicio, LocalDate fim) {
        OfferForm f = new OfferForm();
        f.setName(nome);
        f.setSemester("2025/2");
        f.setStartDate(inicio);
        f.setEndDate(fim);
        f.setResponsibleProfessorId(5L);
        return f;
    }

    private StudentForm criarFormAluno() {
        StudentForm f = new StudentForm();
        f.setFullName("Aluno Novo");
        f.setEmail("novo@ufscar.br");
        f.setRa("123456");
        return f;
    }

    private MockMultipartFile criarCsv(String conteudo) {
        return new MockMultipartFile("file", "alunos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private void ofertaExiste() {
        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
    }

    private void professorECriadorExistem() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(criarUsuario(5L, "prof", UserRole.PROFESSOR)));
        when(userRepository.findByUsername("sec")).thenReturn(Optional.of(criarUsuario(6L, "sec", UserRole.SECRETARIO)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- consultas ----------

    @Test
    @DisplayName("Lista ofertas ordenadas por semestre decrescente")
    void listarOfertas_retornaListaDoRepositorio() {
        // Ramo: listOffers delega ao repositório
        List<Offer> ofertas = List.of(offer);

        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(ofertas);

        List<Offer> resultado = service.listOffers();

        assertSame(ofertas, resultado);
    }

    @Test
    @DisplayName("Ofertas são mapeadas para a API")
    void listarOfertasParaApi_mapeiaCadaOferta() {
        // Ramo: listOffersForApi usa o mapper
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);

        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(List.of(offer));
        when(offerApiMapper.toOfferSummary(offer)).thenReturn(resumo);

        List<OfferSummaryResponse> resultado = service.listOffersForApi();

        assertEquals(1, resultado.size());
        assertSame(resumo, resultado.get(0));
    }

    @Test
    @DisplayName("Busca oferta por id")
    void ofertaExistente_retornaOferta() {
        // Ramo: oferta encontrada
        ofertaExiste();

        Offer resultado = service.getOffer(1L);

        assertSame(offer, resultado);
    }

    @Test
    @DisplayName("Oferta inexistente lança exceção")
    void ofertaInexistente_lancaIllegalArgumentException() {
        // Ramo: oferta não encontrada -> exceção
        when(offerRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getOffer(1L));

        assertEquals("Oferta não encontrada.", ex.getMessage());
    }

    @Test
    @DisplayName("Lista os alunos da oferta")
    void ofertaComAluno_listaAlunosDaOferta() {
        // Ramo: getOfferStudents busca a oferta e depois as inscrições
        ofertaExiste();
        OfferStudent os = new OfferStudent();

        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(os));

        List<OfferStudent> resultado = service.getOfferStudents(1L);

        assertEquals(1, resultado.size());
        assertSame(os, resultado.get(0));
    }

    @Test
    @DisplayName("Detalhes da oferta são montados para a API")
    void detalhesDaOfertaParaApi_retornaDetalhesDoMapper() {
        // Ramo: getOfferDetailsForApi delega ao mapper
        ofertaExiste();
        List<OfferStudent> alunos = List.of(new OfferStudent());
        OfferDetailsResponse detalhes = new OfferDetailsResponse(null, List.of());

        when(offerStudentRepository.findByOffer(offer)).thenReturn(alunos);
        when(offerApiMapper.toOfferDetails(offer, alunos)).thenReturn(detalhes);

        OfferDetailsResponse resultado = service.getOfferDetailsForApi(1L);

        assertSame(detalhes, resultado);
    }

    @Test
    @DisplayName("Busca aluno da oferta por id")
    void alunoDaOfertaExistente_retornaInscricao() {
        // Ramo: inscrição encontrada
        OfferStudent os = new OfferStudent();

        when(offerStudentRepository.findById(9L)).thenReturn(Optional.of(os));

        OfferStudent resultado = service.getOfferStudent(9L);

        assertSame(os, resultado);
    }

    @Test
    @DisplayName("Aluno da oferta inexistente lança exceção")
    void alunoDaOfertaInexistente_lancaIllegalArgumentException() {
        // Ramo: inscrição não encontrada -> exceção
        when(offerStudentRepository.findById(9L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getOfferStudent(9L));

        assertEquals("Aluno da oferta não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Lista logs de status do aluno")
    void logsDoAluno_retornaListaDoRepositorio() {
        // Ramo: getStudentLogs busca a inscrição e depois os logs
        OfferStudent os = new OfferStudent();
        List<StatusChangeLog> logs = List.of(new StatusChangeLog());

        when(offerStudentRepository.findById(9L)).thenReturn(Optional.of(os));
        when(statusChangeLogRepository.findByOfferStudentOrderByChangedAtAsc(os)).thenReturn(logs);

        List<StatusChangeLog> resultado = service.getStudentLogs(9L);

        assertSame(logs, resultado);
    }

    @Test
    @DisplayName("Lista apenas professores")
    void listarProfessores_buscaPeloPerfilProfessor() {
        // Ramo: listProfessors filtra por UserRole.PROFESSOR
        List<User> professores = List.of(criarUsuario(1L, "p", UserRole.PROFESSOR));

        when(userRepository.findByRole(UserRole.PROFESSOR)).thenReturn(professores);

        List<User> resultado = service.listProfessors();

        assertSame(professores, resultado);
    }

    @Test
    @DisplayName("Professores são mapeados para a API")
    void listarProfessoresParaApi_mapeiaCadaProfessor() {
        // Ramo: listProfessorsForApi usa o mapper
        User p = criarUsuario(1L, "p", UserRole.PROFESSOR);
        UserSummaryResponse resumo = new UserSummaryResponse(1L, "n", "e", "p");

        when(userRepository.findByRole(UserRole.PROFESSOR)).thenReturn(List.of(p));
        when(offerApiMapper.toUserSummary(p)).thenReturn(resumo);

        List<UserSummaryResponse> resultado = service.listProfessorsForApi();

        assertEquals(1, resultado.size());
        assertSame(resumo, resultado.get(0));
    }

    // ---------- detalhes do aluno ----------

    @Test
    @DisplayName("Detalhes do aluno que pertence à oferta")
    void alunoDaOferta_retornaDetalhesDoAluno() {
        // Ramo: inscrição pertence à oferta informada
        ofertaExiste();
        OfferStudent os = new OfferStudent();
        os.setOffer(offer);
        OfferStudentDetailsResponse esperado = new OfferStudentDetailsResponse(null, null, List.of());

        when(offerStudentRepository.findById(9L)).thenReturn(Optional.of(os));
        when(offerApiMapper.toOfferStudentDetails(os)).thenReturn(esperado);

        OfferStudentDetailsResponse resultado = service.getStudentDetailsForApi(1L, 9L);

        assertSame(esperado, resultado);
    }

    @Test
    @DisplayName("Aluno de outra oferta é rejeitado")
    void alunoDeOutraOferta_lancaIllegalArgumentException() {
        // Ramo: inscrição de outra oferta -> exceção
        ofertaExiste();
        Offer outra = new Offer();
        outra.setId(2L);
        OfferStudent os = new OfferStudent();
        os.setOffer(outra);

        when(offerStudentRepository.findById(9L)).thenReturn(Optional.of(os));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getStudentDetailsForApi(1L, 9L));

        assertEquals("O aluno informado não pertence a esta oferta.", ex.getMessage());
    }

    // ---------- S.04 pré-visualização do encerramento ----------

    @Test
    @DisplayName("Pode encerrar quando aguardando encerramento do secretário")
    void aguardandoEncerramentoDoSecretario_previewPermiteEncerrar() {
        // Ramo: status AGUARDANDO_ENCERRAMENTO_SECRETARIO -> canClose = true
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        ofertaExiste();
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);

        when(offerApiMapper.toOfferSummary(offer)).thenReturn(resumo);

        SecretaryCloseOfferPreviewResponse resultado = service.getCloseOfferPreviewForApi(1L);

        assertTrue(resultado.canClose());
        assertSame(resumo, resultado.offer());
        assertFalse(resultado.instructions().isBlank());
    }

    @Test
    @DisplayName("Não pode encerrar oferta em andamento")
    void ofertaEmAndamento_previewNaoPermiteEncerrar() {
        // Ramo: status diferente de AGUARDANDO_ENCERRAMENTO_SECRETARIO -> canClose = false
        ofertaExiste();

        SecretaryCloseOfferPreviewResponse resultado = service.getCloseOfferPreviewForApi(1L);

        assertFalse(resultado.canClose());
    }

    // ---------- S.01 criar oferta ----------

    @Test
    @DisplayName("Cria oferta em andamento com professor e criador")
    void dadosValidos_criaOfertaEmAndamento() {
        // Ramo: caminho feliz de createOffer
        professorECriadorExistem();
        OfferForm form = criarFormOferta("Minha oferta", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 1));

        Offer criada = service.createOffer(form, "sec");

        assertEquals("Minha oferta", criada.getName());
        assertEquals("2025/2", criada.getSemester());
        assertEquals(OfferStatus.EM_ANDAMENTO, criada.getStatus());
        assertEquals("prof", criada.getResponsibleProfessor().getUsername());
        assertEquals("sec", criada.getCreatedBy().getUsername());
        assertEquals(LocalDate.of(2025, 8, 1), criada.getStartDate());
    }

    @Test
    @DisplayName("Remove espaços do nome da oferta")
    void nomeComEspacos_criaOfertaComNomeSemEspacos() {
        // Ramo: nome preenchido -> trim
        professorECriadorExistem();

        Offer criada = service.createOffer(criarFormOferta("  Nome  ", null, null), "sec");

        assertEquals("Nome", criada.getName());
    }

    @Test
    @DisplayName("Nome nulo gera nome padrão pelo semestre")
    void nomeNulo_criaOfertaComNomePadrao() {
        // Ramo: nome == null -> "Oferta PESCD <semestre>"
        professorECriadorExistem();

        Offer criada = service.createOffer(criarFormOferta(null, null, null), "sec");

        assertEquals("Oferta PESCD 2025/2", criada.getName());
    }

    @Test
    @DisplayName("Nome vazio gera nome padrão pelo semestre")
    void nomeVazio_criaOfertaComNomePadrao() {
        // Ramo: nome vazio -> nome padrão
        professorECriadorExistem();

        Offer criada = service.createOffer(criarFormOferta("", null, null), "sec");

        assertEquals("Oferta PESCD 2025/2", criada.getName());
    }

    @Test
    @DisplayName("Nome só com espaços gera nome padrão pelo semestre")
    void nomeEmBranco_criaOfertaComNomePadrao() {
        // Ramo: nome em branco -> nome padrão
        professorECriadorExistem();

        Offer criada = service.createOffer(criarFormOferta("   ", null, null), "sec");

        assertEquals("Oferta PESCD 2025/2", criada.getName());
    }

    @Test
    @DisplayName("Professor inexistente é rejeitado")
    void professorInexistente_lancaIllegalArgumentException() {
        // Ramo: professor responsável não encontrado -> exceção
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createOffer(criarFormOferta("x", null, null), "sec"));

        assertEquals("Professor responsável inválido.", ex.getMessage());

        verify(offerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Responsável sem perfil de professor é rejeitado")
    void responsavelSemPerfilProfessor_lancaIllegalArgumentException() {
        // Ramo: usuário encontrado mas com perfil diferente de PROFESSOR -> exceção
        when(userRepository.findById(5L)).thenReturn(Optional.of(criarUsuario(5L, "adm", UserRole.ADMIN)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createOffer(criarFormOferta("x", null, null), "sec"));

        assertEquals("Professor responsável inválido.", ex.getMessage());
    }

    @Test
    @DisplayName("Criador não encontrado é rejeitado")
    void criadorInexistente_lancaIllegalArgumentException() {
        // Ramo: usuário criador não encontrado -> exceção
        when(userRepository.findById(5L)).thenReturn(Optional.of(criarUsuario(5L, "prof", UserRole.PROFESSOR)));
        when(userRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createOffer(criarFormOferta("x", null, null), "fantasma"));

        assertEquals("Usuário criador não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Criação via API devolve o resumo")
    void criarOfertaParaApi_retornaResumoDoMapper() {
        // Ramo: createOfferForApi com datas válidas delega ao mapper
        professorECriadorExistem();
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);

        when(offerApiMapper.toOfferSummary(any(Offer.class))).thenReturn(resumo);

        OfferSummaryResponse resultado = service.createOfferForApi(
                criarFormOferta("x", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2)), "sec");

        assertSame(resumo, resultado);
    }

    @Test
    @DisplayName("Data de fim anterior ao início é rejeitada na API")
    void dataFimAnteriorAoInicio_lancaIllegalArgumentException() {
        // Ramo: fim antes do início -> exceção
        OfferForm form = criarFormOferta("x", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createOfferForApi(form, "sec"));

        assertEquals("A data de fim deve ser depois da data de início.", ex.getMessage());

        verify(offerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Data de fim igual ao início é rejeitada na API")
    void dataFimIgualAoInicio_lancaIllegalArgumentException() {
        // Ramo: fim igual ao início -> exceção
        LocalDate data = LocalDate.of(2025, 6, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.createOfferForApi(criarFormOferta("x", data, data), "sec"));
    }

    @Test
    @DisplayName("Sem data de início não valida as datas na API")
    void dataInicioNula_naoValidaDatas() {
        // Ramo: startDate == null -> validação de datas ignorada
        professorECriadorExistem();

        when(offerApiMapper.toOfferSummary(any(Offer.class))).thenReturn(mock(OfferSummaryResponse.class));

        service.createOfferForApi(criarFormOferta("x", null, LocalDate.of(2025, 1, 1)), "sec");

        verify(offerRepository, times(1)).save(any(Offer.class));
    }

    @Test
    @DisplayName("Sem data de fim não valida as datas na API")
    void dataFimNula_naoValidaDatas() {
        // Ramo: endDate == null -> validação de datas ignorada
        professorECriadorExistem();

        when(offerApiMapper.toOfferSummary(any(Offer.class))).thenReturn(mock(OfferSummaryResponse.class));

        service.createOfferForApi(criarFormOferta("x", LocalDate.of(2025, 1, 1), null), "sec");

        verify(offerRepository, times(1)).save(any(Offer.class));
    }

    @Test
    @DisplayName("createOffer aceita datas invertidas no fluxo MVC")
    void dataFimAnteriorAoInicioNoFluxoMvc_criaOfertaMesmoAssim() {
        // BUG: a regra RN-2 de S.01 (fim depois do início) só é validada em createOfferForApi; createOffer aceita datas invertidas
        professorECriadorExistem();
        OfferForm form = criarFormOferta("x", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 5, 1));

        Offer criada = service.createOffer(form, "sec");

        assertTrue(criada.getEndDate().isBefore(criada.getStartDate()));
    }

    // ---------- S.02 adicionar aluno ----------

    @Test
    @DisplayName("Aluno novo é cadastrado com e-mail como usuário e RA como senha")
    void alunoNovo_cadastraUsuarioEInscreve() {
        // Ramo: e-mail não existe -> cria usuário ALUNO e inscreve
        ofertaExiste();

        when(userRepository.findByEmail("novo@ufscar.br")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("novo@ufscar.br")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offerStudentRepository.existsByOfferAndStudent(any(), any())).thenReturn(false);

        service.addStudent(1L, criarFormAluno());

        verify(userRepository, times(1)).save(argThat(u ->
                "novo@ufscar.br".equals(u.getUsername())
                        && "ENC".equals(u.getPassword())
                        && u.getRole() == UserRole.ALUNO
                        && Boolean.TRUE.equals(u.getEnabled())));
        verify(offerStudentRepository, times(1)).save(argThat(os ->
                os.getStatus() == StudentOfferStatus.NAO_ENVIADO
                        && os.getOffer() == offer
                        && "novo@ufscar.br".equals(os.getStudent().getUsername())));
    }

    @Test
    @DisplayName("Aluno já existente no banco só é inscrito")
    void alunoJaCadastrado_apenasInscreve() {
        // Ramo: e-mail já existe com perfil ALUNO -> não cria usuário
        ofertaExiste();
        User existente = criarUsuario(3L, "novo@ufscar.br", UserRole.ALUNO);

        when(userRepository.findByEmail("novo@ufscar.br")).thenReturn(Optional.of(existente));
        when(offerStudentRepository.existsByOfferAndStudent(offer, existente)).thenReturn(false);

        service.addStudent(1L, criarFormAluno());

        verify(userRepository, never()).save(any());
        verify(offerStudentRepository, times(1)).save(any(OfferStudent.class));
    }

    @Test
    @DisplayName("E-mail de usuário que não é aluno é rejeitado")
    void emailDeUsuarioQueNaoEAluno_lancaIllegalArgumentException() {
        // Ramo: usuário existente com perfil diferente de ALUNO -> exceção
        ofertaExiste();

        when(userRepository.findByEmail("novo@ufscar.br"))
                .thenReturn(Optional.of(criarUsuario(3L, "novo", UserRole.PROFESSOR)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStudent(1L, criarFormAluno()));

        assertEquals("O e-mail informado pertence a um usuário que não é aluno.", ex.getMessage());
    }

    @Test
    @DisplayName("Username já existente com outro e-mail é rejeitado")
    void usernameJaExiste_lancaIllegalArgumentException() {
        // Ramo: e-mail inédito mas username já em uso -> exceção
        ofertaExiste();

        when(userRepository.findByEmail("novo@ufscar.br")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("novo@ufscar.br")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStudent(1L, criarFormAluno()));

        assertEquals("Já existe um usuário com este e-mail.", ex.getMessage());
    }

    @Test
    @DisplayName("Aluno já inscrito na oferta é rejeitado")
    void alunoJaInscrito_lancaIllegalArgumentException() {
        // Ramo: inscrição já existe -> exceção
        ofertaExiste();
        User existente = criarUsuario(3L, "novo@ufscar.br", UserRole.ALUNO);

        when(userRepository.findByEmail("novo@ufscar.br")).thenReturn(Optional.of(existente));
        when(offerStudentRepository.existsByOfferAndStudent(offer, existente)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStudent(1L, criarFormAluno()));

        assertEquals("Este aluno já está inscrito nesta oferta.", ex.getMessage());

        verify(offerStudentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Oferta concluída não aceita novo aluno")
    void ofertaConcluida_adicionarAlunoLancaIllegalArgumentException() {
        // Ramo: oferta CONCLUIDA -> exceção
        offer.setStatus(OfferStatus.CONCLUIDA);
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStudent(1L, criarFormAluno()));

        assertEquals("A oferta está concluída e não pode mais ser alterada.", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Adicionar aluno via API devolve detalhes da oferta")
    void adicionarAlunoParaApi_retornaDetalhesDoMapper() {
        // Ramo: addStudentForApi delega ao mapper
        ofertaExiste();
        User existente = criarUsuario(3L, "novo@ufscar.br", UserRole.ALUNO);
        OfferDetailsResponse detalhes = new OfferDetailsResponse(null, List.of());

        when(userRepository.findByEmail("novo@ufscar.br")).thenReturn(Optional.of(existente));
        when(offerApiMapper.toOfferDetails(any(), any())).thenReturn(detalhes);

        OfferDetailsResponse resultado = service.addStudentForApi(1L, criarFormAluno());

        assertSame(detalhes, resultado);
    }

    // ---------- S.02 RN-3 importação de CSV ----------

    @Test
    @DisplayName("CSV com alunos novos cadastra e inscreve todos")
    void csvComDoisAlunosNovos_cadastraEInscreveTodos() {
        // Ramo: e-mails inéditos -> cria usuários e conta inscrições
        ofertaExiste();

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenAnswer(inv -> "ENC-" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offerStudentRepository.existsByOfferAndStudent(any(), any())).thenReturn(false);

        int inscritos = service.importStudentsFromCsv(1L,
                criarCsv("RA,NOME_COMPLETO,EMAIL\n111,Ana Lima,ana@ufscar.br\n222,Bia Souza,bia@ufscar.br\n"));

        assertEquals(2, inscritos);

        verify(userRepository, times(1)).save(argThat(u ->
                "ana@ufscar.br".equals(u.getUsername()) && "ENC-111".equals(u.getPassword())));
        verify(userRepository, times(1)).save(argThat(u ->
                "bia@ufscar.br".equals(u.getUsername()) && "ENC-222".equals(u.getPassword())));
    }

    @Test
    @DisplayName("CSV com e-mail já cadastrado só inscreve")
    void csvComEmailJaCadastrado_apenasInscreve() {
        // Ramo: usuário já existe -> não cria usuário
        ofertaExiste();
        User existente = criarUsuario(3L, "ana@ufscar.br", UserRole.ALUNO);

        when(userRepository.findByEmail("ana@ufscar.br")).thenReturn(Optional.of(existente));
        when(offerStudentRepository.existsByOfferAndStudent(offer, existente)).thenReturn(false);

        int inscritos = service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n111,Ana Lima,ana@ufscar.br\n"));

        assertEquals(1, inscritos);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aluno já inscrito não é contado na importação")
    void csvComAlunoJaInscrito_naoContaInscricao() {
        // Ramo: inscrição já existe -> não salva nem conta
        ofertaExiste();
        User existente = criarUsuario(3L, "ana@ufscar.br", UserRole.ALUNO);

        when(userRepository.findByEmail("ana@ufscar.br")).thenReturn(Optional.of(existente));
        when(offerStudentRepository.existsByOfferAndStudent(offer, existente)).thenReturn(true);

        int inscritos = service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n111,Ana Lima,ana@ufscar.br\n"));

        assertEquals(0, inscritos);

        verify(offerStudentRepository, never()).save(any());
    }

    @Test
    @DisplayName("CSV ignora linhas em branco")
    void csvComLinhasEmBranco_ignoraLinhas() {
        // Ramo: linha em branco -> continue
        ofertaExiste();

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        int inscritos = service.importStudentsFromCsv(1L,
                criarCsv("RA,NOME,EMAIL\n\n   \n111,Ana,ana@ufscar.br\n"));

        assertEquals(1, inscritos);
    }

    @Test
    @DisplayName("CSV só com cabeçalho inscreve zero alunos")
    void csvApenasComCabecalho_retornaZero() {
        // Ramo: nenhuma linha de dados
        ofertaExiste();

        int inscritos = service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n"));

        assertEquals(0, inscritos);
    }

    @Test
    @DisplayName("CSV remove espaços dos campos")
    void csvComEspacosNosCampos_removeEspacos() {
        // Ramo: campos com espaços -> trim
        ofertaExiste();

        when(userRepository.findByEmail("ana@ufscar.br")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("111")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n 111 , Ana Lima , ana@ufscar.br \n"));

        verify(userRepository, times(1)).save(argThat(u ->
                "Ana Lima".equals(u.getFullName()) && "ana@ufscar.br".equals(u.getEmail())));
    }

    @Test
    @DisplayName("Arquivo nulo é rejeitado")
    void arquivoNulo_lancaIllegalArgumentException() {
        // Ramo: file == null -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, null));

        assertEquals("Selecione um arquivo CSV.", ex.getMessage());
    }

    @Test
    @DisplayName("Arquivo vazio é rejeitado")
    void arquivoVazio_lancaIllegalArgumentException() {
        // Ramo: file.isEmpty() -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("")));

        assertEquals("Selecione um arquivo CSV.", ex.getMessage());
    }

    @Test
    @DisplayName("Linha com menos de três colunas é rejeitada")
    void linhaComMenosDeTresColunas_lancaIllegalArgumentException() {
        // Ramo: colunas < 3 -> exceção com número da linha
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n111,Ana\n")));

        assertEquals("Linha 2 inválida: esperado RA,NOME_COMPLETO,EMAIL.", ex.getMessage());
    }

    @Test
    @DisplayName("Linha com RA vazio é rejeitada")
    void linhaComRaVazio_lancaIllegalArgumentException() {
        // Ramo: RA vazio -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n,Ana,ana@ufscar.br\n")));

        assertEquals("Linha 2 possui campos vazios.", ex.getMessage());
    }

    @Test
    @DisplayName("Linha com nome vazio é rejeitada")
    void linhaComNomeVazio_lancaIllegalArgumentException() {
        // Ramo: nome vazio -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n111,,ana@ufscar.br\n")));

        assertEquals("Linha 2 possui campos vazios.", ex.getMessage());
    }

    @Test
    @DisplayName("Linha com e-mail vazio é rejeitada")
    void linhaComEmailVazio_lancaIllegalArgumentException() {
        // Ramo: e-mail vazio -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n111,Ana,\n")));

        assertEquals("Linha 2 possui campos vazios.", ex.getMessage());
    }

    @Test
    @DisplayName("Mensagem de erro traz o número correto da linha")
    void linhaInvalidaNaTerceiraLinha_mensagemInformaLinha3() {
        // Ramo: erro depois de uma linha válida -> número da linha acompanha o arquivo
        ofertaExiste();

        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Throwable ex = assertThrows(Throwable.class, () -> service.importStudentsFromCsv(1L,
                criarCsv("RA,NOME,EMAIL\n111,Ana,ana@ufscar.br\n222,Bia\n")));

        assertEquals("Linha 3 inválida: esperado RA,NOME_COMPLETO,EMAIL.", ex.getMessage());
    }

    @Test
    @DisplayName("IOException vira IllegalArgumentException")
    void falhaDeLeitura_lancaIllegalArgumentExceptionComCausa() throws IOException {
        // Ramo: IOException ao ler o arquivo -> exceção traduzida
        ofertaExiste();
        MultipartFile arquivo = mock(MultipartFile.class);

        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getInputStream()).thenThrow(new IOException("falha"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, arquivo));

        assertEquals("Não foi possível ler o arquivo CSV.", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    @DisplayName("Arquivo sem nenhuma linha legível é rejeitado")
    void arquivoSemLinhas_lancaIllegalArgumentException() throws IOException {
        // Ramo: readLine() devolve null na primeira leitura -> exceção
        ofertaExiste();
        MultipartFile arquivo = mock(MultipartFile.class);

        when(arquivo.isEmpty()).thenReturn(false);
        when(arquivo.getInputStream()).thenReturn(InputStream.nullInputStream());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, arquivo));

        assertEquals("O arquivo CSV está vazio.", ex.getMessage());
    }

    @Test
    @DisplayName("Oferta concluída não aceita importação")
    void ofertaConcluida_importarLancaIllegalArgumentException() {
        // Ramo: oferta CONCLUIDA -> exceção
        offer.setStatus(OfferStatus.CONCLUIDA);
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n")));

        assertEquals("A oferta está concluída e não pode mais ser alterada.", ex.getMessage());
    }

    @Test
    @DisplayName("Importação matricula usuário que não é aluno")
    void csvComProfessor_matriculaSemValidarPerfil() {
        // BUG: ao contrário de addStudent, a importação não verifica se o usuário achado pelo e-mail tem perfil ALUNO
        ofertaExiste();
        User professor = criarUsuario(3L, "prof@ufscar.br", UserRole.PROFESSOR);

        when(userRepository.findByEmail("prof@ufscar.br")).thenReturn(Optional.of(professor));
        when(offerStudentRepository.existsByOfferAndStudent(offer, professor)).thenReturn(false);

        int inscritos = service.importStudentsFromCsv(1L, criarCsv("RA,NOME,EMAIL\n1,Prof,prof@ufscar.br\n"));

        assertEquals(1, inscritos);
    }

    @Test
    @DisplayName("Importação via API devolve quantidade e detalhes")
    void importarCsvParaApi_retornaQuantidadeEDetalhes() {
        // Ramo: importStudentsFromCsvForApi monta a resposta com o mapper
        ofertaExiste();
        OfferDetailsResponse detalhes = new OfferDetailsResponse(null, List.of());

        when(offerApiMapper.toOfferDetails(any(), any())).thenReturn(detalhes);

        ImportStudentsResponse resultado = service.importStudentsFromCsvForApi(1L, criarCsv("RA,NOME,EMAIL\n"));

        assertEquals(0, resultado.enrolled());
        assertSame(detalhes, resultado.offer());
    }

    // ---------- S.04 encerrar oferta ----------

    @Test
    @DisplayName("Conclui oferta registrando horário e secretário")
    void aguardandoEncerramento_concluiOfertaRegistrandoSecretario() {
        // Ramo: status AGUARDANDO_ENCERRAMENTO_SECRETARIO e username informado
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        ofertaExiste();
        User secretario = criarUsuario(6L, "sec", UserRole.SECRETARIO);

        when(userRepository.findByUsername("sec")).thenReturn(Optional.of(secretario));
        when(offerRepository.save(offer)).thenReturn(offer);

        Offer encerrada = service.closeOffer(1L, "sec");

        assertEquals(OfferStatus.CONCLUIDA, encerrada.getStatus());
        assertNotNull(encerrada.getClosedAt());
        assertSame(secretario, encerrada.getClosedBy());
    }

    @Test
    @DisplayName("Encerramento exige status aguardando secretário")
    void ofertaEmAndamento_encerrarLancaIllegalArgumentException() {
        // Ramo: status diferente de AGUARDANDO_ENCERRAMENTO_SECRETARIO -> exceção
        ofertaExiste();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(1L, "sec"));

        assertEquals("A oferta ainda não está aguardando encerramento do secretário.", ex.getMessage());

        verify(offerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Oferta já concluída não pode ser encerrada de novo")
    void ofertaJaConcluida_encerrarLancaIllegalArgumentException() {
        // Ramo: status CONCLUIDA -> exceção
        offer.setStatus(OfferStatus.CONCLUIDA);
        ofertaExiste();

        assertThrows(IllegalArgumentException.class, () -> service.closeOffer(1L, "sec"));
    }

    @Test
    @DisplayName("Secretário não encontrado é rejeitado")
    void secretarioInexistente_encerrarLancaIllegalArgumentException() {
        // Ramo: username informado mas usuário não encontrado -> exceção
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        ofertaExiste();

        when(userRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(1L, "fantasma"));

        assertEquals("Secretário autenticado não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("closeOffer sem username não registra quem encerrou")
    void encerrarSemUsername_naoRegistraUsuarioQueEncerrou() {
        // BUG (S.04 RN-2): closeOffer(Long) delega com username nulo e closedBy permanece nulo
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        ofertaExiste();

        when(offerRepository.save(offer)).thenReturn(offer);

        service.closeOffer(1L);

        assertEquals(OfferStatus.CONCLUIDA, offer.getStatus());
        assertNotNull(offer.getClosedAt());
        assertNull(offer.getClosedBy());

        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    @DisplayName("Encerramento via API devolve o resumo da oferta")
    void encerrarOfertaParaApi_retornaResumoDoMapper() {
        // Ramo: closeOfferForApi delega ao mapper
        offer.setStatus(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        ofertaExiste();
        OfferSummaryResponse resumo = mock(OfferSummaryResponse.class);

        when(userRepository.findByUsername("sec")).thenReturn(Optional.of(criarUsuario(6L, "sec", UserRole.SECRETARIO)));
        when(offerRepository.save(offer)).thenReturn(offer);
        when(offerApiMapper.toOfferSummary(offer)).thenReturn(resumo);

        OfferSummaryResponse resultado = service.closeOfferForApi(1L, "sec");

        assertSame(resumo, resultado);
    }
}

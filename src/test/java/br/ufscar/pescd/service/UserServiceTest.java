package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.PublicUserResponse;
import br.ufscar.pescd.dto.UserForm;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.UserRole;
import br.ufscar.pescd.exception.DuplicateResourceException;
import br.ufscar.pescd.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class UserServiceTest {

    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    private User criarUsuario(Long id, String username, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setFullName("Nome " + username);
        u.setEmail(username + "@ufscar.br");
        u.setUsername(username);
        u.setPassword("hash");
        u.setRole(role);
        u.setEnabled(true);
        return u;
    }

    private UserForm criarForm(Long id, String senha, UserRole role) {
        UserForm f = new UserForm();
        f.setId(id);
        f.setFullName("Maria Silva");
        f.setEmail("maria@ufscar.br");
        f.setUsername("maria");
        f.setPassword(senha);
        f.setRole(role);
        return f;
    }

    private BindingResult criarBindingResult(UserForm f) {
        return new BeanPropertyBindingResult(f, "userForm");
    }

    // ---------------- consultas ----------------

    @Test
    @DisplayName("Lista todos os usuários ordenados por nome")
    void listaTodos_retornaListaDoRepositorio() {
        // Ramo: delega a findAllByOrderByFullNameAsc
        List<User> usuarios = List.of(criarUsuario(1L, "a", UserRole.ADMIN));
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(usuarios);

        List<User> resultado = userService.findAll();

        assertSame(usuarios, resultado);
    }

    @Test
    @DisplayName("Converte usuários para resposta pública sem expor senha")
    void listaPublica_convertePerfilEHabilitado() {
        // Ramo: toPublicResponse com enabled = true
        User u = criarUsuario(1L, "a", UserRole.SECRETARIO);
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(u));

        List<PublicUserResponse> resultado = userService.findAllPublic();

        assertEquals(List.of(new PublicUserResponse(1L, "Nome a", "a@ufscar.br", "a", "SECRETARIO", true)),
                resultado);
    }

    @Test
    @DisplayName("Busca usuário por id")
    void idExistente_retornaUsuario() {
        // Ramo: usuário encontrado
        User u = criarUsuario(5L, "x", UserRole.PROFESSOR);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        User resultado = userService.findById(5L);

        assertSame(u, resultado);
    }

    @Test
    @DisplayName("Usuário inexistente por id")
    void idInexistente_lancaEntityNotFoundException() {
        // Ramo: usuário não encontrado -> exceção
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> userService.findById(9L));

        assertEquals("Usuário não encontrado.", ex.getMessage());
    }

    @Test
    @DisplayName("Retorna resposta pública por id")
    void idExistente_retornaRespostaPublica() {
        // Ramo: findPublicById -> findById + toPublicResponse
        when(userRepository.findById(2L)).thenReturn(Optional.of(criarUsuario(2L, "b", UserRole.ADMIN)));

        PublicUserResponse resultado = userService.findPublicById(2L);

        assertEquals(2L, resultado.id());
        assertEquals("ADMIN", resultado.role());
    }

    @Test
    @DisplayName("Enabled nulo ou falso vira não habilitado")
    void enabledNuloOuFalso_retornaNaoHabilitado() {
        // Ramo: Boolean.TRUE.equals(enabled) falso para null e para false
        User nulo = criarUsuario(1L, "n", UserRole.ADMIN);
        nulo.setEnabled(null);
        User falso = criarUsuario(2L, "f", UserRole.ADMIN);
        falso.setEnabled(false);
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(nulo, falso));

        List<PublicUserResponse> resultado = userService.findAllPublic();

        assertFalse(resultado.get(0).enabled());
        assertFalse(resultado.get(1).enabled());
    }

    @Test
    @DisplayName("Converte usuário para formulário sem senha")
    void usuarioParaForm_naoCopiaSenha() {
        // Ramo: toForm copia campos, exceto senha
        User u = criarUsuario(3L, "c", UserRole.PROFESSOR);

        UserForm form = userService.toForm(u);

        assertEquals(3L, form.getId());
        assertEquals("Nome c", form.getFullName());
        assertEquals("c@ufscar.br", form.getEmail());
        assertEquals("c", form.getUsername());
        assertEquals(UserRole.PROFESSOR, form.getRole());
        assertNull(form.getPassword());
    }

    // ---------------- validateBusinessRules (MVC) ----------------

    @Test
    @DisplayName("Cadastro válido é aceito")
    void cadastroValido_retornaTrue() {
        // Ramo: nenhuma regra violada
        UserForm f = criarForm(null, "123456", UserRole.PROFESSOR);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertTrue(resultado);
        assertFalse(br.hasErrors());
    }

    @Test
    @DisplayName("E-mail duplicado na criação")
    void emailDuplicadoNaCriacao_retornaFalse() {
        // Ramo: criação -> existsByEmail verdadeiro
        UserForm f = criarForm(null, "123456", UserRole.PROFESSOR);
        BindingResult br = criarBindingResult(f);
        when(userRepository.existsByEmail("maria@ufscar.br")).thenReturn(true);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("Já existe um usuário com este e-mail.", br.getFieldError("email").getDefaultMessage());
    }

    @Test
    @DisplayName("Username duplicado na criação")
    void usernameDuplicadoNaCriacao_retornaFalse() {
        // Ramo: criação -> existsByUsername verdadeiro
        UserForm f = criarForm(null, "123456", UserRole.PROFESSOR);
        BindingResult br = criarBindingResult(f);
        when(userRepository.existsByUsername("maria")).thenReturn(true);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertNotNull(br.getFieldError("username"));
    }

    @Test
    @DisplayName("E-mail duplicado na edição ignora o próprio usuário")
    void emailDuplicadoNaEdicao_usaExistsByEmailAndIdNot() {
        // Ramo: edição com id -> existsByEmailAndIdNot verdadeiro
        UserForm f = criarForm(10L, null, UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);
        when(userRepository.existsByEmailAndIdNot("maria@ufscar.br", 10L)).thenReturn(true);

        boolean resultado = userService.validateBusinessRules(f, br, true);

        assertFalse(resultado);
        assertNotNull(br.getFieldError("email"));
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("Username duplicado na edição ignora o próprio usuário")
    void usernameDuplicadoNaEdicao_usaExistsByUsernameAndIdNot() {
        // Ramo: edição com id -> existsByUsernameAndIdNot verdadeiro
        UserForm f = criarForm(10L, null, UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);
        when(userRepository.existsByUsernameAndIdNot("maria", 10L)).thenReturn(true);

        boolean resultado = userService.validateBusinessRules(f, br, true);

        assertFalse(resultado);
        assertNotNull(br.getFieldError("username"));
    }

    @Test
    @DisplayName("Edição sem nova senha é aceita")
    void edicaoComSenhaVazia_retornaTrue() {
        // Ramo: editing = true e senha em branco -> sem erro de senha
        UserForm f = criarForm(10L, "", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, true);

        assertTrue(resultado);
    }

    @Test
    @DisplayName("Edição sem id é tratada como criação")
    void edicaoSemId_verificaEmailComoCriacao() {
        // Ramo: editing = true mas id nulo -> currentId nulo -> existsByEmail
        UserForm f = criarForm(null, "123456", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        userService.validateBusinessRules(f, br, true);

        verify(userRepository, times(1)).existsByEmail("maria@ufscar.br");
    }

    @Test
    @DisplayName("Senha nula na criação")
    void senhaNulaNaCriacao_retornaFalse() {
        // Ramo: !editing e senha == null -> senha obrigatória
        UserForm f = criarForm(null, null, UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("A senha é obrigatória.", br.getFieldError("password").getDefaultMessage());
    }

    @Test
    @DisplayName("Senha vazia na criação")
    void senhaVaziaNaCriacao_retornaFalse() {
        // Ramo: !editing e senha "" -> senha obrigatória
        UserForm f = criarForm(null, "", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("A senha é obrigatória.", br.getFieldError("password").getDefaultMessage());
    }

    @Test
    @DisplayName("Senha só com espaços na criação")
    void senhaEmBrancoNaCriacao_retornaFalse() {
        // Ramo: !editing e senha "   " -> senha obrigatória
        UserForm f = criarForm(null, "   ", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("A senha é obrigatória.", br.getFieldError("password").getDefaultMessage());
    }

    @Test
    @DisplayName("Senha com menos de 6 caracteres")
    void senhaComCincoCaracteres_retornaFalse() {
        // Ramo: senha preenchida com tamanho < 6
        UserForm f = criarForm(null, "12345", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("size", br.getFieldError("password").getCode());
    }

    @Test
    @DisplayName("Senha com exatamente 6 caracteres")
    void senhaComSeisCaracteres_retornaTrue() {
        // Ramo: limite do tamanho mínimo (6) -> aceito
        UserForm f = criarForm(null, "123456", UserRole.ADMIN);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertTrue(resultado);
    }

    @Test
    @DisplayName("Perfil aluno é rejeitado")
    void perfilAluno_retornaFalse() {
        // Ramo: role == ALUNO -> erro no campo role
        UserForm f = criarForm(null, "123456", UserRole.ALUNO);
        BindingResult br = criarBindingResult(f);

        boolean resultado = userService.validateBusinessRules(f, br, false);

        assertFalse(resultado);
        assertEquals("invalid", br.getFieldError("role").getCode());
    }

    // ---------------- create / update (MVC) ----------------

    @Test
    @DisplayName("Cria usuário habilitado com senha codificada")
    void criaUsuario_salvaHabilitadoComSenhaCodificada() {
        // Ramo: create -> applyForm(creating = true) + save
        when(passwordEncoder.encode("123456")).thenReturn("ENC");

        userService.create(criarForm(null, "123456", UserRole.SECRETARIO));

        verify(userRepository, times(1)).save(argThat(u ->
                "ENC".equals(u.getPassword())
                        && Boolean.TRUE.equals(u.getEnabled())
                        && u.getRole() == UserRole.SECRETARIO
                        && "maria@ufscar.br".equals(u.getEmail())
                        && "maria".equals(u.getUsername())
                        && "Maria Silva".equals(u.getFullName())));
    }

    @Test
    @DisplayName("Atualiza mantendo a senha quando não informada")
    void atualizaComSenhaNula_mantemSenhaAtual() {
        // Ramo: update com password == null -> não codifica
        User existente = criarUsuario(1L, "old", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existente));

        userService.update(criarForm(1L, null, UserRole.PROFESSOR));

        assertEquals("hash", existente.getPassword());
        assertEquals(UserRole.PROFESSOR, existente.getRole());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, times(1)).save(existente);
    }

    @Test
    @DisplayName("Atualiza mantendo a senha quando vier em branco")
    void atualizaComSenhaEmBranco_mantemSenhaAtual() {
        // Ramo: update com password "   " -> isBlank -> não codifica
        User existente = criarUsuario(1L, "old", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existente));

        userService.update(criarForm(1L, "   ", UserRole.ADMIN));

        assertEquals("hash", existente.getPassword());
    }

    @Test
    @DisplayName("Atualiza trocando a senha quando informada")
    void atualizaComNovaSenha_codificaSenha() {
        // Ramo: update com password preenchida -> encode
        User existente = criarUsuario(1L, "old", UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(passwordEncoder.encode("novasenha")).thenReturn("ENC2");

        userService.update(criarForm(1L, "novasenha", UserRole.ADMIN));

        assertEquals("ENC2", existente.getPassword());
    }

    @Test
    @DisplayName("Atualizar usuário inexistente")
    void atualizaUsuarioInexistente_lancaEntityNotFoundException() {
        // Ramo: findById vazio -> exceção
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> userService.update(criarForm(1L, null, UserRole.ADMIN)));
    }

    // ---------------- createFromApi ----------------

    @Test
    @DisplayName("Cria usuário pela API e retorna resposta pública")
    void criaPelaApi_retornaRespostaPublica() {
        // Ramo: validação ok -> save -> toPublicResponse
        User salvo = criarUsuario(77L, "maria", UserRole.PROFESSOR);
        when(passwordEncoder.encode("123456")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenReturn(salvo);

        PublicUserResponse resultado = userService.createFromApi(criarForm(null, "123456", UserRole.PROFESSOR));

        assertEquals(77L, resultado.id());
        assertEquals("PROFESSOR", resultado.role());
        assertTrue(resultado.enabled());
    }

    @Test
    @DisplayName("E-mail duplicado pela API")
    void criaPelaApiComEmailExistente_lancaDuplicateResourceException() {
        // Ramo: existsByEmail verdadeiro -> exceção, nada salvo
        when(userRepository.existsByEmail("maria@ufscar.br")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> userService.createFromApi(criarForm(null, "123456", UserRole.ADMIN)));

        assertEquals("Já existe um usuário com este e-mail.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Username duplicado pela API")
    void criaPelaApiComUsernameExistente_lancaDuplicateResourceException() {
        // Ramo: existsByUsername verdadeiro -> exceção
        when(userRepository.existsByUsername("maria")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> userService.createFromApi(criarForm(null, "123456", UserRole.ADMIN)));

        assertEquals("Já existe um usuário com este nome de usuário.", ex.getMessage());
    }

    @Test
    @DisplayName("Senha nula na criação pela API")
    void criaPelaApiComSenhaNula_lancaIllegalArgumentException() {
        // Ramo: !editing e senha nula -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createFromApi(criarForm(null, null, UserRole.ADMIN)));

        assertEquals("A senha é obrigatória.", ex.getMessage());
    }

    @Test
    @DisplayName("Senha vazia na criação pela API")
    void criaPelaApiComSenhaVazia_lancaIllegalArgumentException() {
        // Ramo: !editing e senha vazia -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createFromApi(criarForm(null, "", UserRole.ADMIN)));

        assertEquals("A senha é obrigatória.", ex.getMessage());
    }

    @Test
    @DisplayName("Senha curta na criação pela API")
    void criaPelaApiComSenhaCurta_lancaIllegalArgumentException() {
        // Ramo: senha com menos de 6 caracteres -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createFromApi(criarForm(null, "123", UserRole.ADMIN)));

        assertEquals("A senha deve ter pelo menos 6 caracteres.", ex.getMessage());
    }

    @Test
    @DisplayName("Perfil aluno na criação pela API")
    void criaPelaApiComPerfilAluno_lancaIllegalArgumentException() {
        // Ramo: role == ALUNO -> exceção
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createFromApi(criarForm(null, "123456", UserRole.ALUNO)));

        assertEquals("O perfil deve ser Administrador, Secretário ou Professor.", ex.getMessage());
    }

    // ---------------- updateFromApi ----------------

    @Test
    @DisplayName("Atualiza pela API sem trocar a senha")
    void atualizaPelaApiSemSenha_mantemSenhaERetornaResposta() {
        // Ramo: validação ok, senha nula -> mantém hash atual
        User existente = criarUsuario(4L, "u", UserRole.ADMIN);
        when(userRepository.findById(4L)).thenReturn(Optional.of(existente));
        when(userRepository.save(existente)).thenReturn(existente);

        PublicUserResponse resultado = userService.updateFromApi(criarForm(4L, null, UserRole.SECRETARIO));

        assertEquals("SECRETARIO", resultado.role());
        assertEquals("hash", existente.getPassword());
    }

    @Test
    @DisplayName("E-mail de outro usuário na edição pela API")
    void atualizaPelaApiComEmailDeOutro_lancaDuplicateResourceException() {
        // Ramo: existsByEmailAndIdNot verdadeiro -> exceção
        when(userRepository.existsByEmailAndIdNot("maria@ufscar.br", 4L)).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> userService.updateFromApi(criarForm(4L, null, UserRole.ADMIN)));

        assertEquals("Já existe um usuário com este e-mail.", ex.getMessage());
    }

    @Test
    @DisplayName("Username de outro usuário na edição pela API")
    void atualizaPelaApiComUsernameDeOutro_lancaDuplicateResourceException() {
        // Ramo: existsByUsernameAndIdNot verdadeiro -> exceção
        when(userRepository.existsByUsernameAndIdNot("maria", 4L)).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> userService.updateFromApi(criarForm(4L, null, UserRole.ADMIN)));

        assertEquals("Já existe um usuário com este nome de usuário.", ex.getMessage());
    }

    @Test
    @DisplayName("Senha curta na edição pela API")
    void atualizaPelaApiComSenhaCurta_lancaIllegalArgumentException() {
        // Ramo: senha preenchida com menos de 6 caracteres -> exceção
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateFromApi(criarForm(4L, "123", UserRole.ADMIN)));
    }

    @Test
    @DisplayName("Perfil aluno na edição pela API")
    void atualizaPelaApiComPerfilAluno_lancaIllegalArgumentException() {
        // Ramo: role == ALUNO -> exceção
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateFromApi(criarForm(4L, null, UserRole.ALUNO)));
    }

    // ---------------- delete ----------------

    @Test
    @DisplayName("Remove usuário diferente do logado")
    void removeUsuarioDiferenteDoLogado_deletaUsuario() {
        // Ramo: username != logado -> delete
        User u = criarUsuario(8L, "outro", UserRole.PROFESSOR);
        when(userRepository.findById(8L)).thenReturn(Optional.of(u));

        userService.delete(8L, "admin");

        verify(userRepository, times(1)).delete(u);
    }

    @Test
    @DisplayName("Impede remover o próprio usuário")
    void removeProprioUsuario_lancaIllegalArgumentException() {
        // Ramo: username == logado -> exceção, nada removido
        User u = criarUsuario(8L, "admin", UserRole.ADMIN);
        when(userRepository.findById(8L)).thenReturn(Optional.of(u));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.delete(8L, "admin"));

        assertEquals("Você não pode remover seu próprio usuário.", ex.getMessage());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Remover usuário inexistente")
    void removeUsuarioInexistente_lancaEntityNotFoundException() {
        // Ramo: findById vazio -> exceção
        when(userRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.delete(8L, "admin"));
    }
}

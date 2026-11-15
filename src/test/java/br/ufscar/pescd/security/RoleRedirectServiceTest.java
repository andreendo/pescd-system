package br.ufscar.pescd.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleRedirectServiceTest {

    RoleRedirectService service = new RoleRedirectService();

    // PCE: um papel conhecido por vez (ADMIN, SECRETARIO, PROFESSOR, ALUNO)
    @Test
    @DisplayName("Cada papel conhecido vai para o seu caminho")
    void papelConhecido_redirecionaParaCaminhoDoPapel() {

        List<GrantedAuthority> admin = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        List<GrantedAuthority> secretario = List.of(new SimpleGrantedAuthority("ROLE_SECRETARIO"));
        List<GrantedAuthority> professor = List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR"));
        List<GrantedAuthority> aluno = List.of(new SimpleGrantedAuthority("ROLE_ALUNO"));

        assertEquals("/admin/users", service.resolveRedirectPath(admin));
        assertEquals("/secretary/offers", service.resolveRedirectPath(secretario));
        assertEquals("/dashboard/professor", service.resolveRedirectPath(professor));
        assertEquals("/aluno/ofertas", service.resolveRedirectPath(aluno));
    }

    // Caminho: papel desconhecido -> dashboard padrao
    @Test
    @DisplayName("Papel desconhecido vai para o dashboard padrao")
    void papelDesconhecido_redirecionaParaDashboard() {

        List<GrantedAuthority> outro = List.of(new SimpleGrantedAuthority("ROLE_OUTRO"));

        assertEquals("/dashboard", service.resolveRedirectPath(outro));
    }

    // Caminho: lista vazia -> dashboard padrao
    @Test
    @DisplayName("Sem papeis vai para o dashboard padrao")
    void semPapeis_redirecionaParaDashboard() {

        assertEquals("/dashboard", service.resolveRedirectPath(List.of()));
    }

    // Caminho: ADMIN junto com outro papel -> ADMIN tem prioridade
    @Test
    @DisplayName("ADMIN tem prioridade sobre ALUNO")
    void adminEAluno_adminTemPrioridade() {

        List<GrantedAuthority> papeis = List.of(
                new SimpleGrantedAuthority("ROLE_ALUNO"), new SimpleGrantedAuthority("ROLE_ADMIN"));

        assertEquals("/admin/users", service.resolveRedirectPath(papeis));
    }

    // Caminho: SECRETARIO junto com PROFESSOR e ALUNO -> SECRETARIO tem prioridade
    @Test
    @DisplayName("SECRETARIO tem prioridade sobre PROFESSOR e ALUNO")
    void secretarioProfessorEAluno_secretarioTemPrioridade() {

        List<GrantedAuthority> papeis = List.of(
                new SimpleGrantedAuthority("ROLE_ALUNO"),
                new SimpleGrantedAuthority("ROLE_PROFESSOR"),
                new SimpleGrantedAuthority("ROLE_SECRETARIO"));

        assertEquals("/secretary/offers", service.resolveRedirectPath(papeis));
    }

    // Caminho: PROFESSOR junto com ALUNO -> PROFESSOR tem prioridade
    @Test
    @DisplayName("PROFESSOR tem prioridade sobre ALUNO")
    void professorEAluno_professorTemPrioridade() {

        List<GrantedAuthority> papeis = List.of(
                new SimpleGrantedAuthority("ROLE_ALUNO"), new SimpleGrantedAuthority("ROLE_PROFESSOR"));

        assertEquals("/dashboard/professor", service.resolveRedirectPath(papeis));
    }
}

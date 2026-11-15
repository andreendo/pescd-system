package br.ufscar.pescd.dto;

import br.ufscar.pescd.enums.GradeOption;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Testes das restricoes de Bean Validation dos formularios (so o Validator do Jakarta, sem Spring).
// Frequencia: valida em [0, 100]; limites testados: -1, 0, 1, 99, 100, 101.
class ValidacaoDtoTest {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void criaValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fechaFactory() {
        factory.close();
    }

    ReportForm relatorio(Integer frequencia) {
        ReportForm f = new ReportForm();
        f.setFrequency(frequencia);
        return f;
    }

    DocumentationForm documentacao(Integer horas) {
        DocumentationForm f = new DocumentationForm();
        f.setInstitutionName("UFSCar");
        f.setDisciplineName("Banco de Dados");
        f.setDisciplineCourse("Computação");
        f.setWorkloadHours(horas);
        return f;
    }

    ApproveReportFormDTO aprovacaoRelatorio(Integer frequencia, GradeOption nota) {
        ApproveReportFormDTO f = new ApproveReportFormDTO();
        f.setParecer("Parecer");
        f.setFrequencia(frequencia);
        f.setNotaSugestao(nota);
        return f;
    }

    ConcludeReportFormDTO conclusao(Integer frequencia) {
        ConcludeReportFormDTO f = new ConcludeReportFormDTO();
        f.setParecer("Parecer");
        f.setFrequencia(frequencia);
        f.setNota(GradeOption.A);
        return f;
    }

    AnalyzeDocumentationFormDTO analise(Integer frequencia) {
        AnalyzeDocumentationFormDTO f = new AnalyzeDocumentationFormDTO();
        f.setParecer("Parecer");
        f.setFrequencia(frequencia);
        f.setNota(GradeOption.A);
        return f;
    }

    // AVL: frequencia do relatorio do aluno dentro de [0, 100] -> sem violacoes
    @Test
    @DisplayName("Frequencia do relatorio entre 0 e 100 e aceita")
    void frequenciaRelatorioDentroDoIntervalo_semViolacoes() {

        assertTrue(validator.validate(relatorio(0)).isEmpty());     // limite inferior
        assertTrue(validator.validate(relatorio(1)).isEmpty());     // limite inferior + 1
        assertTrue(validator.validate(relatorio(50)).isEmpty());    // valor nominal
        assertTrue(validator.validate(relatorio(99)).isEmpty());    // limite superior - 1
        assertTrue(validator.validate(relatorio(100)).isEmpty());   // limite superior
    }

    // AVL: frequencia do relatorio logo fora do intervalo -> uma violacao
    @Test
    @DisplayName("Frequencia do relatorio fora de 0 a 100 e rejeitada")
    void frequenciaRelatorioForaDoIntervalo_umaViolacao() {

        assertEquals(1, validator.validate(relatorio(-1)).size());  // limite inferior - 1
        assertEquals(1, validator.validate(relatorio(101)).size()); // limite superior + 1
    }

    // PCE: frequencia ausente (nula) e uma classe invalida
    @Test
    @DisplayName("Frequencia ausente do relatorio e rejeitada")
    void frequenciaRelatorioAusente_umaViolacao() {

        assertEquals(1, validator.validate(relatorio(null)).size());
    }

    // AVL: frequencia confirmada pelo supervisor (-1, 0, 100, 101)
    @Test
    @DisplayName("Frequencia do supervisor respeita o intervalo 0 a 100")
    void frequenciaSupervisor_limites() {

        assertEquals(1, validator.validate(aprovacaoRelatorio(-1, GradeOption.B)).size());
        assertEquals(0, validator.validate(aprovacaoRelatorio(0, GradeOption.B)).size());
        assertEquals(0, validator.validate(aprovacaoRelatorio(100, GradeOption.B)).size());
        assertEquals(1, validator.validate(aprovacaoRelatorio(101, GradeOption.B)).size());
    }

    // PCE: nota sugerida nula e uma classe invalida
    @Test
    @DisplayName("Nota sugerida do supervisor e obrigatoria")
    void notaSugeridaNula_umaViolacao() {

        assertEquals(1, validator.validate(aprovacaoRelatorio(90, null)).size());
    }

    // AVL: frequencia final do responsavel ao concluir o relatorio (-1, 0, 100, 101)
    @Test
    @DisplayName("Frequencia final ao concluir relatorio respeita 0 a 100")
    void frequenciaConclusaoRelatorio_limites() {

        assertEquals(1, validator.validate(conclusao(-1)).size());
        assertEquals(0, validator.validate(conclusao(0)).size());
        assertEquals(0, validator.validate(conclusao(100)).size());
        assertEquals(1, validator.validate(conclusao(101)).size());
    }

    // AVL: frequencia do responsavel ao analisar a documentacao (-1, 0, 100, 101)
    @Test
    @DisplayName("Frequencia ao analisar documentacao respeita 0 a 100")
    void frequenciaAnaliseDocumentacao_limites() {

        assertEquals(1, validator.validate(analise(-1)).size());
        assertEquals(0, validator.validate(analise(0)).size());
        assertEquals(0, validator.validate(analise(100)).size());
        assertEquals(1, validator.validate(analise(101)).size());
    }

    // AVL: carga horaria deve ser maior que zero (-1, 0 invalidos; 1, 60 validos)
    @Test
    @DisplayName("Carga horaria precisa ser maior que zero")
    void cargaHoraria_limites() {

        assertEquals(1, validator.validate(documentacao(-1)).size());
        assertEquals(1, validator.validate(documentacao(0)).size());   // limite
        assertEquals(0, validator.validate(documentacao(1)).size());   // limite + 1
        assertEquals(0, validator.validate(documentacao(60)).size());
    }

    // PCE: carga horaria ausente (nula) e uma classe invalida
    @Test
    @DisplayName("Carga horaria ausente e rejeitada")
    void cargaHorariaAusente_umaViolacao() {

        assertEquals(1, validator.validate(documentacao(null)).size());
    }

    // PCE: parecer nulo, vazio e so com espacos sao classes invalidas
    @Test
    @DisplayName("Parecer vazio na aprovacao do plano e rejeitado")
    void parecerInvalidoAprovacaoPlano_umaViolacao() {

        ApprovePlanFormDTO nulo = new ApprovePlanFormDTO();
        nulo.setParecer(null);
        ApprovePlanFormDTO vazio = new ApprovePlanFormDTO();
        vazio.setParecer("");
        ApprovePlanFormDTO espacos = new ApprovePlanFormDTO();
        espacos.setParecer("   ");

        assertEquals(1, validator.validate(nulo).size());
        assertEquals(1, validator.validate(vazio).size());
        assertEquals(1, validator.validate(espacos).size());
    }

    // PCE: parecer preenchido e a classe valida
    @Test
    @DisplayName("Parecer preenchido na aprovacao do plano e aceito")
    void parecerPreenchidoAprovacaoPlano_semViolacoes() {

        ApprovePlanFormDTO form = new ApprovePlanFormDTO();
        form.setParecer("Plano adequado.");

        assertTrue(validator.validate(form).isEmpty());
    }

    // PCE: licoes aprendidas nulas, vazias e so com espacos sao classes invalidas
    @Test
    @DisplayName("Licoes aprendidas vazias sao rejeitadas")
    void licoesAprendidasInvalidas_umaViolacao() {

        CloseOfferFormDTO nulo = new CloseOfferFormDTO();
        nulo.setLessonsLearned(null);
        CloseOfferFormDTO vazio = new CloseOfferFormDTO();
        vazio.setLessonsLearned("");
        CloseOfferFormDTO espacos = new CloseOfferFormDTO();
        espacos.setLessonsLearned("  ");

        assertEquals(1, validator.validate(nulo).size());
        assertEquals(1, validator.validate(vazio).size());
        assertEquals(1, validator.validate(espacos).size());
    }

    // PCE: e-mails sem arroba, sem usuario e sem dominio sao classes invalidas
    @Test
    @DisplayName("E-mail do aluno com formato invalido e rejeitado")
    void emailAlunoInvalido_umaViolacao() {

        StudentForm semArroba = aluno("sem-arroba");
        StudentForm semUsuario = aluno("@dominio.com");
        StudentForm semDominio = aluno("a@");

        assertEquals(1, validator.validate(semArroba).size());
        assertEquals(1, validator.validate(semUsuario).size());
        assertEquals(1, validator.validate(semDominio).size());
    }

    // PCE: dados completos e e-mail valido -> sem violacoes
    @Test
    @DisplayName("Aluno com dados completos e aceito")
    void alunoValido_semViolacoes() {

        StudentForm form = aluno("aluno@ufscar.br");

        assertTrue(validator.validate(form).isEmpty());
    }

    // PCE (Mud4): parecer da devolucao -> nulo, vazio e so espacos invalidos; preenchido valido
    @Test
    @DisplayName("Parecer de devolucao em branco e rejeitado")
    void devolucaoSemParecer_umaViolacaoPorParticao() {

        ReturnDocumentationFormDTO nulo = devolucao(null);
        ReturnDocumentationFormDTO vazio = devolucao("");
        ReturnDocumentationFormDTO espacos = devolucao("   ");

        assertEquals(1, validator.validate(nulo).size());
        assertEquals(1, validator.validate(vazio).size());
        assertEquals(1, validator.validate(espacos).size());
    }

    @Test
    @DisplayName("Parecer de devolucao preenchido e aceito")
    void devolucaoComParecer_semViolacoes() {

        ReturnDocumentationFormDTO form = devolucao("Anexe o comprovante");

        assertTrue(validator.validate(form).isEmpty());
    }

    ReturnDocumentationFormDTO devolucao(String parecer) {
        ReturnDocumentationFormDTO f = new ReturnDocumentationFormDTO();
        f.setParecer(parecer);
        return f;
    }

    // PCE (bug #Q): o encerramento so vale com confirm=true; null e false violam o contrato
    // que o front-end passou a respeitar ao enviar { confirm: true }.
    @Test
    @DisplayName("Encerramento sem confirmacao verdadeira e rejeitado")
    void encerramentoSemConfirmacao_violaContrato() {

        assertFalse(validator.validate(new SecretaryCloseOfferRequest(null)).isEmpty());
        assertFalse(validator.validate(new SecretaryCloseOfferRequest(false)).isEmpty());
        assertTrue(validator.validate(new SecretaryCloseOfferRequest(true)).isEmpty());
    }

    StudentForm aluno(String email) {
        StudentForm f = new StudentForm();
        f.setFullName("Aluno");
        f.setEmail(email);
        f.setRa("123456");
        return f;
    }
}

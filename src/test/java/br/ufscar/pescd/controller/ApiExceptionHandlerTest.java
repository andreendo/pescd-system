package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.ApiErrorResponse;
import br.ufscar.pescd.exception.BusinessRuleException;
import br.ufscar.pescd.exception.DuplicateResourceException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiExceptionHandlerTest {

    ApiExceptionHandler handler = new ApiExceptionHandler();
    HttpServletRequest request = mock(HttpServletRequest.class);

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/teste");
    }

    // confere status, motivo, mensagem, path e timestamp do ApiErrorResponse
    void verificaCorpo(ResponseEntity<ApiErrorResponse> response, HttpStatus status, String mensagem) {
        assertEquals(status, response.getStatusCode());
        ApiErrorResponse corpo = response.getBody();
        assertNotNull(corpo);
        assertEquals(status.value(), corpo.status());
        assertEquals(status.getReasonPhrase(), corpo.error());
        assertEquals(mensagem, corpo.message());
        assertEquals("/api/teste", corpo.path());
        assertNotNull(corpo.timestamp());
    }

    MethodArgumentNotValidException excecaoComErros(FieldError... erros) {
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(List.of(erros));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);
        return ex;
    }

    // Caminho: MethodArgumentNotValidException com dois campos invalidos -> 400 e mapa campo -> mensagem
    @Test
    @DisplayName("Validacao com erros de campo responde 400 com o mapa de erros")
    void handleValidation_comErrosDeCampo_retorna400ComMapa() {

        MethodArgumentNotValidException ex = excecaoComErros(
                new FieldError("form", "email", "E-mail inválido."),
                new FieldError("form", "nome", "Nome obrigatório."));

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        verificaCorpo(response, HttpStatus.BAD_REQUEST, "Validation failed.");
        Map<String, String> erros = response.getBody().validationErrors();
        assertEquals(2, erros.size());
        assertEquals("E-mail inválido.", erros.get("email"));
        assertEquals("Nome obrigatório.", erros.get("nome"));
    }

    // Caminho: MethodArgumentNotValidException sem nenhum erro de campo (laco nao executa) -> mapa vazio
    @Test
    @DisplayName("Validacao sem erros de campo responde 400 com mapa vazio")
    void handleValidation_semErrosDeCampo_retorna400ComMapaVazio() {

        MethodArgumentNotValidException ex = excecaoComErros();

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        verificaCorpo(response, HttpStatus.BAD_REQUEST, "Validation failed.");
        assertTrue(response.getBody().validationErrors().isEmpty());
    }

    // Caminho: DuplicateResourceException -> 409 com a mensagem da excecao e sem mapa de erros
    @Test
    @DisplayName("Recurso duplicado responde 409")
    void handleDuplicate_retorna409() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleDuplicate(new DuplicateResourceException("E-mail já cadastrado."), request);

        verificaCorpo(response, HttpStatus.CONFLICT, "E-mail já cadastrado.");
        assertNull(response.getBody().validationErrors());
    }

    // Caminho: BusinessRuleException -> 400
    @Test
    @DisplayName("Regra de negocio violada responde 400")
    void handleBusinessRule_businessRuleException_retorna400() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessRule(new BusinessRuleException("Regra violada."), request);

        verificaCorpo(response, HttpStatus.BAD_REQUEST, "Regra violada.");
    }

    // Caminho: IllegalArgumentException -> 400
    @Test
    @DisplayName("Argumento ilegal responde 400")
    void handleBusinessRule_illegalArgumentException_retorna400() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessRule(new IllegalArgumentException("Argumento inválido."), request);

        verificaCorpo(response, HttpStatus.BAD_REQUEST, "Argumento inválido.");
    }

    // Caminho: ConstraintViolationException -> 400
    @Test
    @DisplayName("Violacao de constraint responde 400")
    void handleBusinessRule_constraintViolationException_retorna400() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBusinessRule(new ConstraintViolationException("violação", Set.of()), request);

        verificaCorpo(response, HttpStatus.BAD_REQUEST, "violação");
    }

    // Caminho: EntityNotFoundException -> 404
    @Test
    @DisplayName("Entidade nao encontrada responde 404")
    void handleNotFound_retorna404() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNotFound(new EntityNotFoundException("Oferta não encontrada."), request);

        verificaCorpo(response, HttpStatus.NOT_FOUND, "Oferta não encontrada.");
    }

    // Caminho: BadCredentialsException -> 401 com mensagem fixa (nao vaza o detalhe interno)
    @Test
    @DisplayName("Credenciais invalidas respondem 401 com mensagem padrao")
    void handleBadCredentials_retorna401ComMensagemPadrao() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("detalhe interno"), request);

        verificaCorpo(response, HttpStatus.UNAUTHORIZED, "Nome de usuário ou senha inválidos.");
    }

    // Caminho: AccessDeniedException -> 403 com a mensagem da excecao
    @Test
    @DisplayName("Acesso negado responde 403")
    void handleAccessDenied_retorna403() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("Acesso negado."), request);

        verificaCorpo(response, HttpStatus.FORBIDDEN, "Acesso negado.");
    }
}

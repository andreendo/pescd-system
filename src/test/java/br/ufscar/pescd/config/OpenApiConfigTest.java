package br.ufscar.pescd.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    OpenApiConfig config = new OpenApiConfig();

    // Caminho: bean restApi -> grupo "pescd-rest-api" que cobre apenas /api/**
    @Test
    @DisplayName("restApi cria o grupo que casa com /api/**")
    void restApi_criaGrupoParaCaminhosApi() {

        GroupedOpenApi grupo = config.restApi();

        assertEquals("pescd-rest-api", grupo.getGroup());
        assertEquals(List.of("/api/**"), grupo.getPathsToMatch());
    }

    // Caminho: anotacoes da classe -> titulo da API e esquema de seguranca por cookie de sessao
    @Test
    @DisplayName("Classe declara a API e o esquema de sessao")
    void anotacoes_declaramApiEEsquemaDeSessao() {

        OpenAPIDefinition definicao = OpenApiConfig.class.getAnnotation(OpenAPIDefinition.class);
        SecurityScheme esquema = OpenApiConfig.class.getAnnotation(SecurityScheme.class);

        assertEquals("PESCD REST API", definicao.info().title());
        assertEquals("sessionAuth", definicao.security()[0].name());
        assertEquals("sessionAuth", esquema.name());
        assertEquals("JSESSIONID", esquema.paramName());
    }
}

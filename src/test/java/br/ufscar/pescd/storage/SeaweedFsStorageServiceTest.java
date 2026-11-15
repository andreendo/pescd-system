package br.ufscar.pescd.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// NAO usa mock do RestClient: o service cria o proprio RestClient com RestClient.create() (campo final,
// sem injecao), as respostas sao records privados (AssignResponse/LookupResponse) e o corpo multipart so
// pode ser conferido de verdade no fio. Por isso o master e o volume server do SeaweedFS sao simulados por
// um unico HttpServer local do JDK (sem Docker, sem rede externa), que e o que mantem toda a cobertura de
// ramos (fid/url ausentes, locations vazio, erros HTTP e delete best-effort).
class SeaweedFsStorageServiceTest {

    static {
        // Em alguns Windows o JDK falha em Selector.open() usando o tmpdir padrao para sockets AF_UNIX;
        // apontar para target/uds resolve.
        if (System.getProperty("jdk.net.unixdomain.tmpdir") == null) {
            try {
                java.nio.file.Path dir = java.nio.file.Path.of("target", "uds").toAbsolutePath();
                java.nio.file.Files.createDirectories(dir);
                System.setProperty("jdk.net.unixdomain.tmpdir", dir.toString());
            } catch (IOException ignored) {
                // mantem o padrao do JDK
            }
        }
    }

    HttpServer server;
    int port;
    String hostPort;

    List<String> requests = Collections.synchronizedList(new ArrayList<>());
    volatile String lastBody;
    volatile String lastContentType;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        hostPort = "127.0.0.1:" + port;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // inicia o servidor e cria o SUT apontando para ele
    SeaweedFsStorageService service() {
        server.start();
        return new SeaweedFsStorageService("http://127.0.0.1:" + port);
    }

    void responde(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    void respondeBytes(HttpExchange ex, byte[] bytes) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
        ex.close();
    }

    void registra(HttpExchange ex) {
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI());
    }

    // master: /dir/assign devolve o fid 3,01637037d6 apontando para este mesmo servidor
    void assignOk() {
        server.createContext("/dir/assign", ex -> {
            registra(ex);
            responde(ex, 200, "{\"fid\":\"3,01637037d6\",\"url\":\"" + hostPort + "\",\"publicUrl\":\"x\"}");
        });
    }

    // volume server: aceita o POST do arquivo e guarda corpo e content-type recebidos
    void volumeAceitaUpload() {
        server.createContext("/3,01637037d6", ex -> {
            registra(ex);
            lastContentType = ex.getRequestHeaders().getFirst("Content-Type");
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            responde(ex, 201, "{\"size\":5}");
        });
    }

    // master: /dir/lookup devolve o volume (este servidor) e uma segunda localizacao
    void lookupOk(String volumeId) {
        server.createContext("/dir/lookup", ex -> {
            registra(ex);
            responde(ex, 200, "{\"volumeId\":\"" + volumeId + "\",\"locations\":[{\"url\":\"" + hostPort
                    + "\",\"publicUrl\":\"p\"},{\"url\":\"outro:1\"}]}");
        });
    }

    // ---------- construtor ----------

    // Caminho: URL do master termina com "/" -> barra removida, senao o GET iria para "//dir/assign"
    @Test
    @DisplayName("Construtor remove a barra final da URL do master")
    void construtor_urlComBarraFinal_removeBarra() {

        assignOk();
        server.createContext("/3,01637037d6", ex -> {
            registra(ex);
            responde(ex, 201, "{\"size\":3}");
        });
        server.start();
        SeaweedFsStorageService sut = new SeaweedFsStorageService("http://127.0.0.1:" + port + "/");

        sut.store(new byte[]{1}, "a.pdf", "application/pdf");

        assertTrue(requests.contains("GET /dir/assign"));
    }

    // ---------- store ----------

    // Caminho: assign ok + volume aceita -> devolve o fid e envia multipart com nome e conteudo do arquivo
    @Test
    @DisplayName("store reserva o fid, envia multipart e devolve o fid")
    void store_fluxoCompleto_retornaFidEEnviaMultipart() {

        assignOk();
        volumeAceitaUpload();
        SeaweedFsStorageService sut = service();

        String fid = sut.store("conteudo".getBytes(StandardCharsets.UTF_8), "relatorio.pdf", "application/pdf");

        assertEquals("3,01637037d6", fid);
        assertEquals(List.of("GET /dir/assign", "POST /3,01637037d6"), requests);
        assertTrue(lastContentType.startsWith("multipart/form-data"));
        assertTrue(lastBody.contains("name=\"file\""));
        assertTrue(lastBody.contains("filename=\"relatorio.pdf\""));
        assertTrue(lastBody.contains("conteudo"));
    }

    // PCE: nome do arquivo nulo, vazio e so com espacos -> usa o fid como nome
    @Test
    @DisplayName("store sem nome de arquivo usa o fid como nome")
    void store_nomeNuloVazioOuEmBranco_usaFidComoNome() {

        assignOk();
        volumeAceitaUpload();
        SeaweedFsStorageService sut = service();

        sut.store(new byte[]{1, 2, 3}, null, "application/pdf");
        assertTrue(lastBody.contains("filename=\"3,01637037d6\""));

        sut.store(new byte[]{1, 2, 3}, "", "application/pdf");
        assertTrue(lastBody.contains("filename=\"3,01637037d6\""));

        sut.store(new byte[]{1, 2, 3}, "   ", "application/pdf");
        assertTrue(lastBody.contains("filename=\"3,01637037d6\""));
    }

    // Caminho: assign responde 200 sem corpo (assign == null) -> IllegalStateException
    @Test
    @DisplayName("store com assign sem corpo lanca IllegalStateException")
    void store_assignSemCorpo_lancaIllegalStateException() {

        server.createContext("/dir/assign", ex -> responde(ex, 200, null));
        SeaweedFsStorageService sut = service();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> sut.store(new byte[]{1}, "a", "x"));

        assertEquals("SeaweedFS não retornou um fid válido em /dir/assign.", ex.getMessage());
    }

    // Caminho: assign sem o campo fid -> IllegalStateException
    @Test
    @DisplayName("store com assign sem fid lanca IllegalStateException")
    void store_assignSemFid_lancaIllegalStateException() {

        server.createContext("/dir/assign", ex -> responde(ex, 200, "{\"url\":\"" + hostPort + "\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(IllegalStateException.class, () -> sut.store(new byte[]{1}, "a", "x"));
    }

    // Caminho: assign sem o campo url do volume -> IllegalStateException
    @Test
    @DisplayName("store com assign sem url do volume lanca IllegalStateException")
    void store_assignSemUrl_lancaIllegalStateException() {

        server.createContext("/dir/assign", ex -> responde(ex, 200, "{\"fid\":\"3,01\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(IllegalStateException.class, () -> sut.store(new byte[]{1}, "a", "x"));
    }

    // Caminho: master responde 500 -> o erro HTTP propaga
    @Test
    @DisplayName("store propaga erro HTTP do master")
    void store_masterComErro500_propagaRestClientResponseException() {

        server.createContext("/dir/assign", ex -> responde(ex, 500, "{\"error\":\"boom\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(RestClientResponseException.class, () -> sut.store(new byte[]{1}, "a", "x"));
    }

    // Caminho: volume server responde 500 no upload -> o erro HTTP propaga
    @Test
    @DisplayName("store propaga erro HTTP do volume server")
    void store_volumeComErro500_propagaRestClientResponseException() {

        assignOk();
        server.createContext("/3,01637037d6", ex -> responde(ex, 500, "{\"error\":\"disk\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(RestClientResponseException.class, () -> sut.store(new byte[]{1}, "a", "x"));
    }

    // ---------- read ----------

    // Caminho: fid "3,..." -> lookup do volume 3 e GET do arquivo no volume
    @Test
    @DisplayName("read localiza o volume e le o conteudo")
    void read_fidComVirgula_localizaVolumeELeConteudo() {

        lookupOk("3");
        byte[] dados = {10, 20, 30, 40};
        server.createContext("/3,01637037d6", ex -> {
            registra(ex);
            respondeBytes(ex, dados);
        });
        SeaweedFsStorageService sut = service();

        byte[] resultado = sut.read("3,01637037d6");

        assertArrayEquals(dados, resultado);
        assertEquals(List.of("GET /dir/lookup?volumeId=3", "GET /3,01637037d6"), requests);
    }

    // Caminho: fid sem virgula -> o fid inteiro e usado como volumeId
    @Test
    @DisplayName("read de fid sem virgula usa o fid como volumeId")
    void read_fidSemVirgula_usaFidComoVolumeId() {

        lookupOk("7");
        server.createContext("/7", ex -> respondeBytes(ex, new byte[]{1}));
        SeaweedFsStorageService sut = service();

        byte[] resultado = sut.read("7");

        assertArrayEquals(new byte[]{1}, resultado);
        assertTrue(requests.contains("GET /dir/lookup?volumeId=7"));
    }

    // PCE: fid nulo, vazio e so com espacos -> IllegalArgumentException (nenhuma chamada HTTP)
    @Test
    @DisplayName("read com fid ausente lanca IllegalArgumentException")
    void read_fidNuloVazioOuEmBranco_lancaIllegalArgumentException() {

        SeaweedFsStorageService sut = new SeaweedFsStorageService("http://127.0.0.1:1");

        IllegalArgumentException nulo = assertThrows(IllegalArgumentException.class, () -> sut.read(null));
        IllegalArgumentException vazio = assertThrows(IllegalArgumentException.class, () -> sut.read(""));
        IllegalArgumentException branco = assertThrows(IllegalArgumentException.class, () -> sut.read("  "));

        assertEquals("Identificador de arquivo (fid) ausente.", nulo.getMessage());
        assertEquals("Identificador de arquivo (fid) ausente.", vazio.getMessage());
        assertEquals("Identificador de arquivo (fid) ausente.", branco.getMessage());
    }

    // Caminho: lookup responde 200 sem corpo (lookup == null) -> IllegalStateException
    @Test
    @DisplayName("read com lookup sem corpo lanca IllegalStateException")
    void read_lookupSemCorpo_lancaIllegalStateException() {

        server.createContext("/dir/lookup", ex -> responde(ex, 200, null));
        SeaweedFsStorageService sut = service();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.read("3,01"));

        assertEquals("SeaweedFS não localizou o volume do arquivo 3,01.", ex.getMessage());
    }

    // Caminho: lookup sem o campo locations (null) -> IllegalStateException
    @Test
    @DisplayName("read com lookup sem locations lanca IllegalStateException")
    void read_lookupSemLocations_lancaIllegalStateException() {

        server.createContext("/dir/lookup", ex -> responde(ex, 200, "{\"volumeId\":\"3\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(IllegalStateException.class, () -> sut.read("3,01"));
    }

    // Caminho: lookup com locations vazio -> IllegalStateException
    @Test
    @DisplayName("read com locations vazio lanca IllegalStateException")
    void read_locationsVazio_lancaIllegalStateException() {

        server.createContext("/dir/lookup", ex -> responde(ex, 200, "{\"volumeId\":\"3\",\"locations\":[]}"));
        SeaweedFsStorageService sut = service();

        assertThrows(IllegalStateException.class, () -> sut.read("3,01"));
    }

    // Caminho: arquivo inexistente no volume (404) -> o erro HTTP propaga
    @Test
    @DisplayName("read de arquivo inexistente propaga erro HTTP")
    void read_arquivoNaoExisteNoVolume_propagaRestClientResponseException() {

        lookupOk("3");
        server.createContext("/3,01", ex -> responde(ex, 404, "{\"error\":\"not found\"}"));
        SeaweedFsStorageService sut = service();

        assertThrows(RestClientResponseException.class, () -> sut.read("3,01"));
    }

    // ---------- delete (best-effort: nunca lanca excecao) ----------

    // PCE: fid nulo, vazio e so com espacos -> retorna sem fazer nada
    @Test
    @DisplayName("delete com fid ausente nao faz nada")
    void delete_fidNuloVazioOuEmBranco_naoFazNada() {

        SeaweedFsStorageService sut = new SeaweedFsStorageService("http://127.0.0.1:1");

        assertDoesNotThrow(() -> sut.delete(null));
        assertDoesNotThrow(() -> sut.delete(""));
        assertDoesNotThrow(() -> sut.delete("   "));
    }

    // Caminho: lookup ok -> DELETE enviado ao volume server
    @Test
    @DisplayName("delete localiza o volume e envia DELETE")
    void delete_fidValido_enviaDeleteAoVolume() {

        server.createContext("/dir/lookup", ex -> {
            registra(ex);
            responde(ex, 200, "{\"volumeId\":\"3\",\"locations\":[{\"url\":\"" + hostPort + "\"}]}");
        });
        server.createContext("/3,01", ex -> {
            registra(ex);
            responde(ex, 202, "{\"size\":1}");
        });
        SeaweedFsStorageService sut = service();

        sut.delete("3,01");

        assertEquals(List.of("GET /dir/lookup?volumeId=3", "DELETE /3,01"), requests);
    }

    // Caminho: lookup responde 500 -> falha engolida pelo catch
    @Test
    @DisplayName("delete ignora falha no lookup")
    void delete_lookupComErro500_ignoraFalha() {

        server.createContext("/dir/lookup", ex -> responde(ex, 500, "{}"));
        SeaweedFsStorageService sut = service();

        assertDoesNotThrow(() -> sut.delete("3,01"));
    }

    // Caminho: lookup sem localizacoes (IllegalStateException interna) -> engolida pelo catch
    @Test
    @DisplayName("delete ignora volume nao localizado")
    void delete_volumeNaoLocalizado_ignoraFalha() {

        server.createContext("/dir/lookup", ex -> responde(ex, 200, "{\"locations\":[]}"));
        SeaweedFsStorageService sut = service();

        assertDoesNotThrow(() -> sut.delete("3,01"));
    }

    // Caminho: DELETE no volume server responde 500 -> falha engolida pelo catch
    @Test
    @DisplayName("delete ignora falha no volume server")
    void delete_volumeComErro500_ignoraFalha() {

        server.createContext("/dir/lookup", ex ->
                responde(ex, 200, "{\"locations\":[{\"url\":\"" + hostPort + "\"}]}"));
        server.createContext("/3,01", ex -> responde(ex, 500, "{}"));
        SeaweedFsStorageService sut = service();

        assertDoesNotThrow(() -> sut.delete("3,01"));
    }

    // Caminho: master fora do ar (conexao recusada) -> falha engolida pelo catch
    @Test
    @DisplayName("delete ignora servidor indisponivel")
    void delete_servidorIndisponivel_ignoraFalha() {

        SeaweedFsStorageService sut = new SeaweedFsStorageService("http://127.0.0.1:1");

        assertDoesNotThrow(() -> sut.delete("3,01"));
    }
}

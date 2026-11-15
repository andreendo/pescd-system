package br.ufscar.pescd.controller;

import br.ufscar.pescd.dto.OfferView;
import br.ufscar.pescd.service.OfferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferApiControllerTest {

    OfferService offerService = mock(OfferService.class);
    OfferApiController controller = new OfferApiController(offerService);

    // Caminho: existem ofertas publicas -> devolve a mesma lista do service
    @Test
    @DisplayName("Lista as ofertas publicas")
    void listPublicOffers_comOfertas_retornaListaDoService() {

        List<OfferView> ofertas = List.of(mock(OfferView.class), mock(OfferView.class));
        when(offerService.findPublicOffers()).thenReturn(ofertas);

        List<OfferView> resultado = controller.listPublicOffers();

        assertSame(ofertas, resultado);
    }

    // Caminho: nenhuma oferta publica -> lista vazia
    @Test
    @DisplayName("Sem ofertas publicas retorna lista vazia")
    void listPublicOffers_semOfertas_retornaListaVazia() {

        when(offerService.findPublicOffers()).thenReturn(List.of());

        List<OfferView> resultado = controller.listPublicOffers();

        assertTrue(resultado.isEmpty());
    }
}

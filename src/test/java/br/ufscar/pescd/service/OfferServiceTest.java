package br.ufscar.pescd.service;

import br.ufscar.pescd.dto.OfferView;
import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.repository.OfferRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferServiceTest {

    OfferRepository offerRepository = mock(OfferRepository.class);
    OfferStudentRepository offerStudentRepository = mock(OfferStudentRepository.class);

    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, offerStudentRepository);
    }

    private Offer criarOferta(String nome, String semestre, User professor) {
        Offer o = new Offer();
        o.setName(nome);
        o.setSemester(semestre);
        o.setStartDate(LocalDate.of(2025, 2, 1));
        o.setEndDate(LocalDate.of(2025, 6, 30));
        o.setResponsibleProfessor(professor);
        return o;
    }

    @Test
    @DisplayName("Oferta com professor vira view com todos os dados")
    void ofertaComProfessor_retornaViewCompleta() {
        // Ramo: professor responsável presente -> usa o nome completo
        User prof = new User();
        prof.setFullName("Profa. Ana");
        Offer oferta = criarOferta("Oferta A", "2025/1", prof);

        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(List.of(oferta));
        when(offerStudentRepository.countByOffer(oferta)).thenReturn(7L);

        List<OfferView> resultado = service.findPublicOffers();

        assertEquals(1, resultado.size());
        assertEquals("Oferta A", resultado.get(0).name());
        assertEquals("2025/1", resultado.get(0).semester());
        assertEquals(LocalDate.of(2025, 2, 1), resultado.get(0).startDate());
        assertEquals(LocalDate.of(2025, 6, 30), resultado.get(0).endDate());
        assertEquals("Profa. Ana", resultado.get(0).responsibleProfessor());
        assertEquals(7L, resultado.get(0).enrolledStudents());

        verify(offerStudentRepository, times(1)).countByOffer(oferta);
    }

    @Test
    @DisplayName("Oferta sem professor mostra 'Não definido'")
    void ofertaSemProfessor_retornaNaoDefinido() {
        // Ramo: professor responsável nulo -> "Não definido"
        Offer oferta = criarOferta("Sem professor", "2024/2", null);

        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(List.of(oferta));
        when(offerStudentRepository.countByOffer(oferta)).thenReturn(0L);

        List<OfferView> resultado = service.findPublicOffers();

        assertEquals("Não definido", resultado.get(0).responsibleProfessor());
        assertEquals(0, resultado.get(0).enrolledStudents());
    }

    @Test
    @DisplayName("Mantém a ordem de semestre devolvida pelo repositório")
    void duasOfertas_mantemOrdemDoRepositorio() {
        // Ramo: várias ofertas -> ordem preservada
        Offer o1 = criarOferta("B", "2025/2", null);
        Offer o2 = criarOferta("A", "2025/1", null);

        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(List.of(o1, o2));
        when(offerStudentRepository.countByOffer(o1)).thenReturn(1L);
        when(offerStudentRepository.countByOffer(o2)).thenReturn(2L);

        List<OfferView> resultado = service.findPublicOffers();

        assertEquals(2, resultado.size());
        assertEquals("2025/2", resultado.get(0).semester());
        assertEquals("2025/1", resultado.get(1).semester());
    }

    @Test
    @DisplayName("Sem ofertas devolve lista vazia")
    void semOfertas_retornaListaVazia() {
        // Ramo: repositório vazio -> lista vazia e nenhuma contagem
        when(offerRepository.findAllByOrderBySemesterDesc()).thenReturn(List.of());

        List<OfferView> resultado = service.findPublicOffers();

        assertTrue(resultado.isEmpty());

        verifyNoInteractions(offerStudentRepository);
    }
}

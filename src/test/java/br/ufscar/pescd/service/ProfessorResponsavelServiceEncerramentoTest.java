package br.ufscar.pescd.service;

import br.ufscar.pescd.entity.Offer;
import br.ufscar.pescd.entity.OfferStudent;
import br.ufscar.pescd.entity.User;
import br.ufscar.pescd.enums.OfferStatus;
import br.ufscar.pescd.enums.StudentOfferStatus;
import br.ufscar.pescd.repository.OfferRepository;
import br.ufscar.pescd.repository.OfferStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessorResponsavelServiceEncerramentoTest {

    private static final Long OFFER_ID = 3L;

    @Mock
    private OfferRepository offerRepository;

    @Mock
    private OfferStudentRepository offerStudentRepository;

    @InjectMocks
    private ProfessorResponsavelService service;

    private User responsavel;
    private Offer offer;

    @BeforeEach
    void setUp() {
        responsavel = new User();
        responsavel.setId(5L);
        responsavel.setUsername("ccomin");

        offer = new Offer();
        offer.setId(OFFER_ID);
        offer.setResponsibleProfessor(responsavel);

        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        lenient().when(offerStudentRepository.findByOffer(offer))
                .thenReturn(List.of(enrollment(StudentOfferStatus.CONCLUIDO_RESPONSAVEL)));
    }

    @Test
    void encerrarOfertaEmAndamentoComTodosOsAlunosConcluidosAguardaOSecretario() {
        offer.setStatus(OfferStatus.EM_ANDAMENTO);

        service.closeOffer(OFFER_ID, responsavel, "Lições aprendidas.");

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.AGUARDANDO_ENCERRAMENTO_SECRETARIO);
        assertThat(offer.getLessonsLearned()).isEqualTo("Lições aprendidas.");
        assertThat(offer.getClosureRequestedAt()).isNotNull();
        assertThat(offer.getClosureRequestedBy()).isSameAs(responsavel);
        verify(offerRepository).save(offer);
    }

    @ParameterizedTest
    @EnumSource(value = OfferStatus.class, names = {"AGUARDANDO_ENCERRAMENTO_SECRETARIO", "CONCLUIDA"})
    void encerrarOfertaJaEncerradaLancaExcecaoENaoAlteraAOferta(OfferStatus status) {
        LocalDateTime pedidoOriginal = LocalDateTime.of(2026, 11, 20, 10, 0);
        offer.setStatus(status);
        offer.setLessonsLearned("Lições do encerramento original.");
        offer.setClosureRequestedAt(pedidoOriginal);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(OFFER_ID, responsavel, "Texto sobrescrito."));

        assertThat(ex.getMessage()).isEqualTo("O encerramento desta oferta já foi solicitado.");
        assertThat(offer.getStatus()).isEqualTo(status);
        assertThat(offer.getLessonsLearned()).isEqualTo("Lições do encerramento original.");
        assertThat(offer.getClosureRequestedAt()).isEqualTo(pedidoOriginal);
        verify(offerRepository, never()).save(any());
    }

    @Test
    void encerrarOfertaComAlunoNaoConcluidoLancaExcecao() {
        offer.setStatus(OfferStatus.EM_ANDAMENTO);
        when(offerStudentRepository.findByOffer(offer)).thenReturn(List.of(
                enrollment(StudentOfferStatus.CONCLUIDO_RESPONSAVEL),
                enrollment(StudentOfferStatus.RELATORIO_ENVIADO)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.closeOffer(OFFER_ID, responsavel, "Lições."));

        assertThat(ex.getMessage()).isEqualTo("Todos os alunos devem estar concluídos antes do encerramento.");
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.EM_ANDAMENTO);
        verify(offerRepository, never()).save(any());
    }

    private OfferStudent enrollment(StudentOfferStatus status) {
        OfferStudent os = new OfferStudent();
        os.setOffer(offer);
        os.setStatus(status);
        return os;
    }
}

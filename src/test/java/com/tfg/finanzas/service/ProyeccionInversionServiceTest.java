package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.ProyeccionInversion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionInversionServiceTest {

    @Mock
    InversionService inversion;
    @InjectMocks
    ProyeccionInversionService servicio;

    @Test
    void componeElRetornoAnualYSumaLasAportacionesSinComponerlas() {
        when(inversion.valorTotalCartera(1L)).thenReturn(new BigDecimal("1000"));

        ProyeccionInversion p = servicio.proyectar(1L, 2, new BigDecimal("0.10"), new BigDecimal("100"));

        // 1000 * 1.1^2 + 100 * 2 = 1210 + 200
        assertThat(p.valorProyectado()).isEqualByComparingTo("1410.00");
        assertThat(p.valorActual()).isEqualByComparingTo("1000.00");
        assertThat(p.anios()).isEqualTo(2);
    }

    @Test
    void conCeroAniosElValorNoCambia() {
        when(inversion.valorTotalCartera(1L)).thenReturn(new BigDecimal("500"));
        assertThat(servicio.proyectar(1L, 0, new BigDecimal("0.07"), new BigDecimal("1000")).valorProyectado())
                .isEqualByComparingTo("500.00");
    }

    @Test
    void conRetornoNegativoElValorBaja() {
        when(inversion.valorTotalCartera(1L)).thenReturn(new BigDecimal("1000"));
        assertThat(servicio.proyectar(1L, 1, new BigDecimal("-0.20"), BigDecimal.ZERO).valorProyectado())
                .isEqualByComparingTo("800.00");
    }

    @Test
    void redondeaADosDecimales() {
        when(inversion.valorTotalCartera(1L)).thenReturn(new BigDecimal("333.333"));
        assertThat(servicio.proyectar(1L, 1, new BigDecimal("0.05"), BigDecimal.ZERO).valorProyectado())
                .isEqualByComparingTo("350.00");
    }
}

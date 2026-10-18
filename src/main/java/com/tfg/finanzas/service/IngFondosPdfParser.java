package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.InversionDtos.PosicionImportada;
import com.tfg.finanzas.model.TipoActivo;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de la seccion "Fondos de Inversion" del "Extracto Integrado" de ING
 * (el mismo documento del que {@link IngExtractoIntegradoPdfParser} extrae los
 * movimientos de las cuentas corrientes/ahorro — este parser lee la otra
 * seccion del mismo PDF). Cada fondo trae dos filas de valoracion (inicio y
 * fin del periodo); se usa la ULTIMA fila (mas reciente) como posicion actual.
 * Los fondos no tienen ISIN visible en el extracto, asi que se identifican por
 * el codigo de producto de ING (p. ej. "4006853921.1").
 */
@Slf4j
@Component
public class IngFondosPdfParser implements PosicionesFileParser {

    private static final Pattern INICIO_FONDO = Pattern.compile(
            "^Fondo\\s+(.+?)\\s+-\\s+([\\d.]+)\\s+Saldo final\\s+[\\d.,]+\\s*€$");
    private static final Pattern FILA_DATOS = Pattern.compile(
            "^\\d{2}/\\d{2}\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+[\\d.,]+€\\s*€$");

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }
        String texto = extraerTexto(contenido);
        return texto != null && texto.contains("Extracto Integrado") && texto.contains("Fondos de Inversión");
    }

    @Override
    public List<PosicionImportada> parsear(byte[] contenido) {
        String texto = extraerTexto(contenido);
        if (texto == null) {
            throw new ImportacionException("No se ha podido leer el extracto integrado de ING");
        }

        List<PosicionImportada> posiciones = new ArrayList<>();
        boolean dentroDeFondos = false;
        String nombreFondoActual = null;
        String codigoFondoActual = null;
        BigDecimal ultimaParticipaciones = null;
        BigDecimal ultimoValorLiquidativo = null;

        for (String lineaBruta : texto.lines().toList()) {
            String linea = lineaBruta.trim();
            if (linea.isEmpty()) {
                continue;
            }

            if (!dentroDeFondos) {
                if (linea.equals("• Fondos de Inversión") || linea.startsWith("Fondos de Inversión")) {
                    dentroDeFondos = true;
                }
                continue;
            }

            if (linea.startsWith("Más detalle en el apartado") || linea.equals("Información legal")) {
                break;
            }

            Matcher mInicio = INICIO_FONDO.matcher(linea);
            if (mInicio.matches()) {
                flush(posiciones, nombreFondoActual, codigoFondoActual, ultimaParticipaciones, ultimoValorLiquidativo);
                nombreFondoActual = mInicio.group(1).trim();
                codigoFondoActual = mInicio.group(2).trim();
                ultimaParticipaciones = null;
                ultimoValorLiquidativo = null;
                continue;
            }

            Matcher mFila = FILA_DATOS.matcher(linea);
            if (mFila.matches() && codigoFondoActual != null) {
                try {
                    ultimaParticipaciones = parsearImporte(mFila.group(1));
                    ultimoValorLiquidativo = parsearImporte(mFila.group(2));
                } catch (RuntimeException e) {
                    log.warn("Fila de fondo no interpretable en extracto integrado de ING: {} ({})", linea, e.getMessage());
                }
            }
        }
        flush(posiciones, nombreFondoActual, codigoFondoActual, ultimaParticipaciones, ultimoValorLiquidativo);

        return posiciones;
    }

    private void flush(List<PosicionImportada> posiciones, String nombre, String codigo,
                        BigDecimal participaciones, BigDecimal valorLiquidativo) {
        if (codigo == null || participaciones == null || valorLiquidativo == null) {
            return;
        }
        posiciones.add(new PosicionImportada("ING-" + codigo, "Fondo " + nombre, TipoActivo.FONDO,
                participaciones, valorLiquidativo));
    }

    private BigDecimal parsearImporte(String texto) {
        String limpio = texto.replaceAll("[^0-9,.-]", "").replace(".", "").replace(",", ".");
        return new BigDecimal(limpio);
    }

    private String extraerTexto(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            return new PDFTextStripper().getText(documento);
        } catch (IOException e) {
            return null;
        }
    }
}

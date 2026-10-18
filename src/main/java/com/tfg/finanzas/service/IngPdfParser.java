package com.tfg.finanzas.service;

import com.tfg.finanzas.dto.ImportacionDtos.MovimientoImportado;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de los PDFs de "Movimientos de la Cuenta" que exporta ING.
 * <p>
 * A diferencia de Trade Republic, aqui el importe viene en una sola columna con
 * signo, en formato "1,665.00" (coma de millares, punto decimal), y el texto se
 * extrae ya en orden de lectura normal, asi que basta con trabajar linea a linea.
 */
@Slf4j
@Component
public class IngPdfParser implements FileParser {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern FECHA_LINEA = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}\\b");
    private static final Pattern IMPORTE = Pattern.compile("-?\\d{1,3}(?:,\\d{3})*\\.\\d{2}");

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }
        String texto = extraerTexto(contenido);
        return texto != null && texto.contains("Movimientos de la Cuenta") && texto.contains("F. VALOR");
    }

    @Override
    public List<MovimientoImportado> parsear(byte[] contenido) {
        String texto = extraerTexto(contenido);
        if (texto == null) {
            throw new ImportacionException("No se ha podido leer el PDF de ING");
        }
        List<MovimientoImportado> movimientos = new ArrayList<>();
        for (String registro : agruparPorFecha(texto.lines().toList())) {
            try {
                movimientos.add(parsearRegistro(registro));
            } catch (RuntimeException e) {
                log.warn("Se ha descartado un movimiento de ING que no se pudo interpretar: {} ({})", registro, e.getMessage());
            }
        }
        return movimientos;
    }

    private List<String> agruparPorFecha(List<String> lineas) {
        List<String> registros = new ArrayList<>();
        StringBuilder actual = null;
        for (String linea : lineas) {
            String limpia = linea.trim();
            if (esLineaIgnorable(limpia)) {
                continue;
            }
            if (FECHA_LINEA.matcher(limpia).find()) {
                if (actual != null) {
                    registros.add(actual.toString());
                }
                actual = new StringBuilder(limpia);
            } else if (actual != null) {
                actual.append(' ').append(limpia);
            }
        }
        if (actual != null) {
            registros.add(actual.toString());
        }
        return registros;
    }

    private boolean esLineaIgnorable(String linea) {
        return linea.isEmpty()
                || linea.equals("Movimientos de la Cuenta")
                || linea.startsWith("Número de cuenta:")
                || linea.startsWith("Titular:")
                || linea.startsWith("Fecha exportación:")
                || linea.startsWith("F. VALOR");
    }

    private MovimientoImportado parsearRegistro(String registro) {
        LocalDate fecha = LocalDate.parse(registro.substring(0, 10), FORMATO_FECHA);
        String resto = registro.substring(10).trim();

        List<int[]> posiciones = new ArrayList<>();
        List<String> valores = new ArrayList<>();
        Matcher matcher = IMPORTE.matcher(resto);
        while (matcher.find()) {
            posiciones.add(new int[]{matcher.start(), matcher.end()});
            valores.add(matcher.group());
        }
        if (valores.size() < 2) {
            throw new ImportacionException("No se han podido extraer importe y saldo de: " + registro);
        }

        int indiceImporte = valores.size() - 2;
        String importeTexto = valores.get(indiceImporte);
        String descripcion = resto.substring(0, posiciones.get(indiceImporte)[0]).trim();
        return new MovimientoImportado(fecha, parsearImporte(importeTexto), descripcion);
    }

    private BigDecimal parsearImporte(String texto) {
        return new BigDecimal(texto.replace(",", ""));
    }

    private String extraerTexto(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            return new PDFTextStripper().getText(documento);
        } catch (IOException e) {
            return null;
        }
    }
}

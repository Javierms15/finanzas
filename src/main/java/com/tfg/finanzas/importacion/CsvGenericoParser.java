package com.tfg.finanzas.importacion;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser CSV de base, sin banco concreto todavia: detecta el separador y busca
 * columnas de fecha/descripcion/importe por nombre de cabecera habitual. Sirve
 * de punto de partida hasta tener un export real de un banco que use CSV, momento
 * en el que se debera sustituir (o completar) por un parser especifico como los
 * de PDF.
 */
@Slf4j
@Component
public class CsvGenericoParser implements FileParser {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private static final List<String> COLUMNAS_FECHA = List.of("fecha", "date", "fecha valor", "f. valor", "fecha operacion");
    private static final List<String> COLUMNAS_DESCRIPCION = List.of("concepto", "descripcion", "description", "detalle");
    private static final List<String> COLUMNAS_IMPORTE = List.of("importe", "amount", "importe (eur)", "importe(eur)");

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        return nombreArchivo != null && nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public List<MovimientoImportado> parsear(byte[] contenido) {
        char separador = detectarSeparador(contenido);
        try (CSVReader reader = construirReader(contenido, separador)) {
            String[] cabecera = reader.readNext();
            if (cabecera == null) {
                return List.of();
            }
            int indiceFecha = indiceColumna(cabecera, COLUMNAS_FECHA);
            int indiceDescripcion = indiceColumna(cabecera, COLUMNAS_DESCRIPCION);
            int indiceImporte = indiceColumna(cabecera, COLUMNAS_IMPORTE);
            if (indiceFecha < 0 || indiceDescripcion < 0 || indiceImporte < 0) {
                throw new ImportacionException(
                        "No se han reconocido las columnas de fecha/descripcion/importe en el CSV. Cabecera: "
                                + String.join(",", cabecera));
            }

            List<MovimientoImportado> movimientos = new ArrayList<>();
            String[] fila;
            while ((fila = reader.readNext()) != null) {
                try {
                    LocalDate fecha = parsearFecha(fila[indiceFecha].trim());
                    String descripcion = fila[indiceDescripcion].trim();
                    BigDecimal importe = parsearImporte(fila[indiceImporte].trim());
                    movimientos.add(new MovimientoImportado(fecha, importe, descripcion));
                } catch (RuntimeException e) {
                    log.warn("Se ha descartado una fila de CSV que no se pudo interpretar: {} ({})",
                            String.join(",", fila), e.getMessage());
                }
            }
            return movimientos;
        } catch (IOException | CsvValidationException e) {
            throw new ImportacionException("No se ha podido leer el CSV", e);
        }
    }

    private CSVReader construirReader(byte[] contenido, char separador) {
        CSVParser parser = new CSVParserBuilder().withSeparator(separador).build();
        return new CSVReaderBuilder(new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8))
                .withCSVParser(parser)
                .build();
    }

    private char detectarSeparador(byte[] contenido) {
        String primeraLinea = new String(contenido, StandardCharsets.UTF_8).lines().findFirst().orElse("");
        long comas = primeraLinea.chars().filter(c -> c == ',').count();
        long puntoYComa = primeraLinea.chars().filter(c -> c == ';').count();
        return puntoYComa > comas ? ';' : ',';
    }

    private int indiceColumna(String[] cabecera, List<String> nombresPosibles) {
        for (int i = 0; i < cabecera.length; i++) {
            String normalizado = cabecera[i].trim().toLowerCase(Locale.ROOT);
            if (nombresPosibles.contains(normalizado)) {
                return i;
            }
        }
        return -1;
    }

    private LocalDate parsearFecha(String texto) {
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignored) {
                // se prueba con el siguiente formato
            }
        }
        throw new ImportacionException("Fecha no reconocida: " + texto);
    }

    private BigDecimal parsearImporte(String texto) {
        String limpio = texto.replaceAll("[^0-9,.-]", "");
        int ultimaComa = limpio.lastIndexOf(',');
        int ultimoPunto = limpio.lastIndexOf('.');
        if (ultimaComa > ultimoPunto) {
            limpio = limpio.replace(".", "").replace(",", ".");
        } else if (ultimoPunto > ultimaComa) {
            limpio = limpio.replace(",", "");
        }
        return new BigDecimal(limpio);
    }
}

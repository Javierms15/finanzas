package com.tfg.finanzas.importacion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parser de los extractos de cuenta en PDF de Trade Republic.
 * <p>
 * El texto de la tabla se extrae con la posicion X de cada fragmento (en vez de
 * confiar en el orden de lectura de PDFBox) porque ENTRADA DE DINERO y SALIDA DE
 * DINERO son columnas separadas: es la unica forma fiable de saber si un importe
 * es un ingreso o un gasto, ya que el tipo de movimiento (p. ej. "Operar") no
 * determina el signo por si solo.
 */
@Slf4j
@Component
public class TradeRepublicPdfParser implements FileParser {

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("abr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("ago", 8),
            Map.entry("sept", 9), Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dic", 12)
    );

    private static final Set<String> ETIQUETAS_CABECERA = Set.of(
            "TIPO", "DESCRIPCIÓN", "ENTRADA DE", "SALIDA DE", "DINERO", "BALANCE"
    );

    private static final double COL_FECHA_MIN = 65, COL_FECHA_MAX = 90;
    private static final double COL_TIPO_MIN = 95, COL_TIPO_MAX = 150;
    private static final double COL_DESCRIPCION_MIN = 150, COL_DESCRIPCION_MAX = 400;
    private static final double COL_ENTRADA_MIN = 400, COL_ENTRADA_MAX = 428;
    private static final double COL_SALIDA_MIN = 428, COL_SALIDA_MAX = 462;
    private static final double COL_BALANCE_MIN = 462, COL_BALANCE_MAX = 520;

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }
        String primeraPagina = extraerTextoPagina(contenido, 1);
        return primeraPagina != null && primeraPagina.contains("TRADE REPUBLIC");
    }

    @Override
    public List<MovimientoImportado> parsear(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            List<MovimientoImportado> movimientos = new ArrayList<>();
            int numPaginas = documento.getNumberOfPages();
            for (int pagina = 1; pagina <= numPaginas; pagina++) {
                movimientos.addAll(procesarChunks(extraerChunks(documento, pagina)));
            }
            return movimientos;
        } catch (IOException e) {
            throw new ImportacionException("No se ha podido leer el PDF de Trade Republic", e);
        }
    }

    private String extraerTextoPagina(byte[] contenido, int pagina) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pagina);
            stripper.setEndPage(pagina);
            return stripper.getText(documento);
        } catch (IOException e) {
            return null;
        }
    }

    private List<Chunk> extraerChunks(PDDocument documento, int pagina) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String string, List<TextPosition> textPositions) {
                if (!string.isBlank() && !textPositions.isEmpty()) {
                    chunks.add(new Chunk(string.trim(), textPositions.get(0).getX()));
                }
            }
        };
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        stripper.getText(documento);
        return chunks;
    }

    private List<MovimientoImportado> procesarChunks(List<Chunk> chunks) {
        List<MovimientoImportado> movimientos = new ArrayList<>();
        Estado estado = new Estado();

        for (Chunk chunk : chunks) {
            String texto = chunk.texto();

            if (texto.equals("FECHA") && dentro(chunk.x(), COL_FECHA_MIN, COL_FECHA_MAX)) {
                estado.enTabla = true;
                continue;
            }
            if (texto.startsWith("RESUMEN DEL BALANCE") || texto.startsWith("NOTAS SOBRE")
                    || texto.startsWith("CONFIRMACIÓN DE LA VALIDEZ")) {
                estado.flushSiHayFecha(movimientos);
                estado.reset();
                estado.enTabla = false;
                continue;
            }
            if (!estado.enTabla || ETIQUETAS_CABECERA.contains(texto)) {
                continue;
            }

            if (dentro(chunk.x(), COL_FECHA_MIN, COL_FECHA_MAX) && esDiaMes(texto)) {
                estado.flushSiHayFecha(movimientos);
                estado.reset();
                estado.diaMes = texto;
            } else if (dentro(chunk.x(), COL_FECHA_MIN, COL_FECHA_MAX) && texto.matches("\\d{4}") && estado.diaMes != null) {
                try {
                    estado.fecha = parsearFecha(estado.diaMes, texto);
                } catch (RuntimeException e) {
                    log.warn("Fecha no interpretable en extracto Trade Republic: {} {} ({})", estado.diaMes, texto, e.getMessage());
                    estado.reset();
                }
            } else if (dentro(chunk.x(), COL_ENTRADA_MIN, COL_ENTRADA_MAX)) {
                estado.entrada = parsearImporteSeguro(texto);
            } else if (dentro(chunk.x(), COL_SALIDA_MIN, COL_SALIDA_MAX)) {
                estado.salida = parsearImporteSeguro(texto);
            } else if (dentro(chunk.x(), COL_BALANCE_MIN, COL_BALANCE_MAX)) {
                // balance de la cuenta tras el movimiento: no se persiste
            } else if (dentro(chunk.x(), COL_TIPO_MIN, COL_TIPO_MAX)) {
                estado.tipo.append(estado.tipo.isEmpty() ? "" : " ").append(texto);
            } else if (dentro(chunk.x(), COL_DESCRIPCION_MIN, COL_DESCRIPCION_MAX)) {
                estado.descripcion.append(estado.descripcion.isEmpty() ? "" : " ").append(texto);
            }
        }

        estado.flushSiHayFecha(movimientos);
        return movimientos;
    }

    private boolean dentro(double x, double min, double max) {
        return x >= min && x < max;
    }

    private boolean esDiaMes(String texto) {
        return texto.matches("(?i)\\d{1,2}\\s+[a-záéíóúñ]{3,6}\\.?");
    }

    private LocalDate parsearFecha(String diaMes, String anio) {
        String[] partes = diaMes.split("\\s+");
        int dia = Integer.parseInt(partes[0]);
        String mesTexto = partes[1].toLowerCase(Locale.ROOT).replace(".", "");
        Integer mes = MESES.get(mesTexto);
        if (mes == null) {
            throw new ImportacionException("Mes no reconocido en extracto Trade Republic: " + mesTexto);
        }
        return LocalDate.of(Integer.parseInt(anio), mes, dia);
    }

    private BigDecimal parsearImporteSeguro(String texto) {
        try {
            // PDFBox extrae un espacio de no separacion (NBSP) entre el numero y el
            // simbolo €; se eliminan todos los caracteres que no sean digitos o separadores.
            String limpio = texto.replaceAll("[^0-9,.-]", "").replace(".", "").replace(",", ".");
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            log.warn("Importe no interpretable en extracto Trade Republic: '{}'", texto);
            return null;
        }
    }

    private record Chunk(String texto, float x) {
    }

    private class Estado {
        boolean enTabla = false;
        String diaMes;
        LocalDate fecha;
        StringBuilder tipo = new StringBuilder();
        StringBuilder descripcion = new StringBuilder();
        BigDecimal entrada;
        BigDecimal salida;

        void reset() {
            diaMes = null;
            fecha = null;
            tipo = new StringBuilder();
            descripcion = new StringBuilder();
            entrada = null;
            salida = null;
        }

        void flushSiHayFecha(List<MovimientoImportado> movimientos) {
            if (fecha == null) {
                return;
            }
            try {
                BigDecimal importe;
                if (entrada != null && salida != null) {
                    importe = entrada.subtract(salida);
                } else if (entrada != null) {
                    importe = entrada;
                } else if (salida != null) {
                    importe = salida.negate();
                } else {
                    importe = BigDecimal.ZERO;
                }
                String descripcionCompleta = tipo.isEmpty()
                        ? descripcion.toString().trim()
                        : (tipo + ": " + descripcion).trim();
                movimientos.add(new MovimientoImportado(fecha, importe, descripcionCompleta));
            } catch (RuntimeException e) {
                log.warn("Se ha descartado un movimiento de Trade Republic que no se pudo interpretar: {}", e.getMessage());
            }
        }
    }
}

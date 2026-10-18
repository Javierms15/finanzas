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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser del "Extracto Integrado" mensual de ING: a diferencia del PDF de
 * "Movimientos de la Cuenta" (una sola cuenta), este reune en un unico PDF
 * varias cuentas (Nomina, Naranja...) y los fondos de inversion, cada uno con
 * su propia tabla. Solo se extraen los movimientos de la seccion "Cuentas":
 * los fondos de inversion no son movimientos de efectivo y se ignoran aqui,
 * igual que las operaciones "Operar" de Trade Republic no generan todavia una
 * Posicion automaticamente al importar.
 * <p>
 * Como todas las cuentas del extracto se importan dentro de la misma
 * CuentaBancaria elegida por el usuario (la importacion no soporta repartir un
 * fichero entre varias cuentas), cada descripcion se prefija con el nombre de
 * la cuenta de origen ("[NARANJA] ...") para no perder esa informacion.
 * <p>
 * Las fechas del extracto vienen sin año (dd/MM); se combinan con el mes y
 * año del encabezado ("Septiembre 2026").
 */
@Slf4j
@Component
public class IngExtractoIntegradoPdfParser implements FileParser {

    private static final Pattern CABECERA_MES = Pattern.compile(
            "^([A-Za-zÁÉÍÓÚÑáéíóúñ]+)\\s+(\\d{4})\\s+Saldo Final");
    private static final Pattern INICIO_CUENTA = Pattern.compile("^Cuenta\\s+(.+?)\\s+-\\s+IBAN:");
    private static final Pattern FILA_MOVIMIENTO = Pattern.compile(
            "^(\\d{2}/\\d{2})\\s+\\d{2}/\\d{2}\\s+(.+?)\\s+(-?[\\d.,]+)\\s*€\\s+[\\d.,]+\\s*€$");

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("octubre", 10), Map.entry("noviembre", 11), Map.entry("diciembre", 12)
    );

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }
        String texto = extraerTexto(contenido);
        return texto != null && texto.contains("Extracto Integrado");
    }

    @Override
    public List<MovimientoImportado> parsear(byte[] contenido) {
        String texto = extraerTexto(contenido);
        if (texto == null) {
            throw new ImportacionException("No se ha podido leer el extracto integrado de ING");
        }

        Integer mes = null;
        Integer anio = null;
        String cuentaActual = null;
        List<MovimientoImportado> movimientos = new ArrayList<>();

        for (String lineaBruta : texto.lines().toList()) {
            String linea = lineaBruta.trim();
            if (linea.isEmpty()) {
                continue;
            }

            if (mes == null) {
                Matcher mCabecera = CABECERA_MES.matcher(linea);
                if (mCabecera.find()) {
                    mes = MESES.get(mCabecera.group(1).toLowerCase(Locale.ROOT));
                    anio = Integer.parseInt(mCabecera.group(2));
                    if (mes == null) {
                        log.warn("Mes no reconocido en extracto integrado de ING: {}", mCabecera.group(1));
                    }
                    continue;
                }
            }

            if (linea.startsWith("Fondos de Inversión") || linea.equals("• Fondos de Inversión")) {
                break;
            }

            Matcher mCuenta = INICIO_CUENTA.matcher(linea);
            if (mCuenta.find()) {
                cuentaActual = mCuenta.group(1).trim();
                continue;
            }

            if (cuentaActual == null || mes == null) {
                continue;
            }

            Matcher mFila = FILA_MOVIMIENTO.matcher(linea);
            if (mFila.matches()) {
                try {
                    LocalDate fecha = LocalDate.of(anio, mes, Integer.parseInt(mFila.group(1).split("/")[0]));
                    BigDecimal importe = parsearImporte(mFila.group(3));
                    String descripcion = "[" + cuentaActual + "] " + mFila.group(2).trim();
                    movimientos.add(new MovimientoImportado(fecha, importe, descripcion));
                } catch (RuntimeException e) {
                    log.warn("Se ha descartado una fila del extracto integrado de ING: {} ({})", linea, e.getMessage());
                }
            }
        }

        return movimientos;
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

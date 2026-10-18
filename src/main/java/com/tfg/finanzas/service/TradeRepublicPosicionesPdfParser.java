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
 * Parser del "Extracto de la cuenta de valores" de Trade Republic: una foto de
 * todas las posiciones actuales (cantidad y precio del dia), no un historial
 * de operaciones. Cada posicion ocupa un bloque de lineas de longitud variable
 * (el nombre del valor puede ocupar 1 o 2 lineas, y solo algunas posiciones
 * tienen la linea "Cuenta de valores en Alemania"), asi que se parsea como una
 * maquina de estados por marcadores (inicio "N tít.", "ISIN:", y el primer
 * numero suelto tras el ISIN, que es el precio) en vez de por offsets fijos de
 * linea.
 */
@Slf4j
@Component
public class TradeRepublicPosicionesPdfParser implements PosicionesFileParser {

    private static final Pattern INICIO_POSICION = Pattern.compile("^([\\d.,]+)\\s+tít\\.\\s*(.*)$");
    private static final Pattern LINEA_ISIN = Pattern.compile("^ISIN:\\s*(\\S+)$");
    private static final Pattern LINEA_PRECIO = Pattern.compile("^-?\\d{1,3}(?:\\.\\d{3})*,\\d+$");
    private static final Pattern FIN_LISTA = Pattern.compile("^NÚMERO DE POSICIONES:");

    @Override
    public boolean soporta(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return false;
        }
        String texto = extraerTexto(contenido);
        return texto != null && texto.contains("EXTRACTO DE LA CUENTA DE VALORES");
    }

    @Override
    public List<PosicionImportada> parsear(byte[] contenido) {
        String texto = extraerTexto(contenido);
        if (texto == null) {
            throw new ImportacionException("No se ha podido leer el extracto de la cuenta de valores");
        }

        List<PosicionImportada> posiciones = new ArrayList<>();
        BigDecimal cantidadActual = null;
        StringBuilder nombreActual = new StringBuilder();
        String isinActual = null;
        boolean dentroDePosicion = false;
        boolean esperandoPrecio = false;

        for (String lineaBruta : texto.lines().toList()) {
            String linea = lineaBruta.trim();
            if (linea.isEmpty()) {
                continue;
            }

            if (FIN_LISTA.matcher(linea).find()) {
                break;
            }

            Matcher mInicio = INICIO_POSICION.matcher(linea);
            if (mInicio.matches()) {
                if (esperandoPrecio) {
                    log.warn("Posicion sin precio localizado, se descarta: ISIN={}", isinActual);
                }
                cantidadActual = parsearImporte(mInicio.group(1));
                nombreActual = new StringBuilder(mInicio.group(2).trim());
                isinActual = null;
                dentroDePosicion = true;
                esperandoPrecio = false;
                continue;
            }

            if (dentroDePosicion && isinActual == null) {
                Matcher mIsin = LINEA_ISIN.matcher(linea);
                if (mIsin.matches()) {
                    isinActual = mIsin.group(1);
                    esperandoPrecio = true;
                } else {
                    if (!nombreActual.isEmpty()) {
                        nombreActual.append(' ');
                    }
                    nombreActual.append(linea);
                }
                continue;
            }

            // Lineas intermedias ("Cuenta de valores en Alemania", "País de custodia: ...")
            // se ignoran hasta encontrar la linea de precio; tras capturarlo, el resto
            // (fecha, cotizacion en EUR) tambien se ignora hasta la siguiente posicion.
            if (esperandoPrecio && LINEA_PRECIO.matcher(linea).matches()) {
                try {
                    BigDecimal precio = parsearImporte(linea);
                    String nombre = nombreActual.toString().trim();
                    posiciones.add(new PosicionImportada(isinActual, nombre, inferirTipo(nombre), cantidadActual, precio));
                } catch (RuntimeException e) {
                    log.warn("Se ha descartado una posicion del extracto de valores: {} ({})", isinActual, e.getMessage());
                }
                dentroDePosicion = false;
                esperandoPrecio = false;
            }
        }

        return posiciones;
    }

    private TipoActivo inferirTipo(String nombre) {
        String n = nombre.toUpperCase(Locale.ROOT);
        if (n.contains("ETF") || n.contains("ETC") || n.contains("PHYSICAL METALS")) {
            return TipoActivo.ETF;
        }
        return TipoActivo.ACCION;
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

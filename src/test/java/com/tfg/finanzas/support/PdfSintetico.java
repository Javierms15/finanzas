package com.tfg.finanzas.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera PDFs de pruebas con texto colocado donde se indique. Asi los parsers se prueban con documentos
 * sinteticos que imitan la estructura de los extractos reales, sin guardar ningun dato bancario de nadie.
 */
public final class PdfSintetico {

    /** Fragmento de texto en la posicion (x, y) de la pagina (origen abajo a la izquierda). */
    public record Texto(float x, float y, String texto) {
    }

    private PdfSintetico() {
    }

    /** Un PDF cuyas lineas van una debajo de otra, alineadas a la izquierda; cada lista es una pagina. */
    @SafeVarargs
    public static byte[] deLineas(List<String>... paginas) {
        List<List<Texto>> resultado = new ArrayList<>();
        for (List<String> lineas : paginas) {
            List<Texto> textos = new ArrayList<>();
            float y = 800;
            for (String linea : lineas) {
                textos.add(new Texto(50, y, linea));
                y -= 14;
            }
            resultado.add(textos);
        }
        return deTextos(resultado);
    }

    public static byte[] deLineas(String... lineas) {
        return deLineas(List.of(lineas));
    }

    /** Un PDF con los fragmentos colocados en coordenadas exactas; cada lista interna es una pagina. */
    public static byte[] deTextos(List<List<Texto>> paginas) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<Texto> textos : paginas) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                    for (Texto t : textos) {
                        contenido.beginText();
                        contenido.setFont(fuente, 8);
                        contenido.newLineAtOffset(t.x(), t.y());
                        contenido.showText(t.texto());
                        contenido.endText();
                    }
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

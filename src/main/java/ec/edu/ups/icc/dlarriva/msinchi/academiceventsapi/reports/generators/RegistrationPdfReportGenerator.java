package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.utils.BusinessTimeZoneConverter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * OpenPDF 2.0.5 (com.lowagie.text — build.gradle fija esta versión en vez
 * de la 3.x porque esa compila a bytecode Java 21, incompatible con el
 * toolchain Java 17 del proyecto) genera el PDF por completo en memoria
 * (ByteArrayOutputStream): sin archivos temporales en disco, según
 * docs/instrucciones.pdf sección 15 ("El contenedor de Spring Boot no deberá
 * almacenar archivos permanentes"). Fechas siempre en America/Guayaquil vía
 * BusinessTimeZoneConverter — se almacenan en UTC, pero un reporte es para
 * leer un humano (docs/instrucciones.pdf sección 14).
 */
public final class RegistrationPdfReportGenerator {

    private static final Font TITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 16);
    private static final Font SUBTITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 10);
    private static final Font HEADER_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 9);
    private static final Font CELL_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 9);

    private RegistrationPdfReportGenerator() {
    }

    public static byte[] generate(EventEntity event, List<RegistrationEntity> registrations) {
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Listado de inscritos", TITLE_FONT));
            document.add(new Paragraph(event.getTitle(), SUBTITLE_FONT));
            document.add(new Paragraph(
                    "Generado: " + BusinessTimeZoneConverter.formatForDisplay(OffsetDateTime.now())
                            + " (America/Guayaquil)", SUBTITLE_FONT));
            document.add(new Paragraph("Total: " + registrations.size(), SUBTITLE_FONT));
            document.add(new Paragraph(" "));
            document.add(buildTable(registrations));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el reporte PDF", e);
        }
    }

    private static PdfPTable buildTable(List<RegistrationEntity> registrations) {
        PdfPTable table = new PdfPTable(new float[] {0.6f, 2.5f, 3f, 1.3f, 2f});
        table.setWidthPercentage(100);

        addHeaderCell(table, "#");
        addHeaderCell(table, "Participante");
        addHeaderCell(table, "Correo");
        addHeaderCell(table, "Estado");
        addHeaderCell(table, "Fecha de inscripción");

        int index = 1;
        for (RegistrationEntity registration : registrations) {
            addCell(table, String.valueOf(index++));
            addCell(table, registration.getParticipant().getFirstName() + " " + registration.getParticipant().getLastName());
            addCell(table, registration.getParticipant().getEmail());
            addCell(table, registration.getStatus().name());
            addCell(table, BusinessTimeZoneConverter.formatForDisplay(registration.getRegisteredAt()));
        }
        return table;
    }

    private static void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text) {
        // OpenPDF/Phrase no tolera texto null (registeredAt podría serlo si
        // la fila todavía no pasó por la BD, ej. en pruebas unitarias).
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, CELL_FONT));
        cell.setPadding(4);
        table.addCell(cell);
    }
}

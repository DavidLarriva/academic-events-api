package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.utils.BusinessTimeZoneConverter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;

/**
 * Comprobante individual (docs/instrucciones.pdf sección 13) — documento propio, no
 * la tabla de RegistrationPdfReportGenerator: es un registro personal de una
 * sola inscripción, no un listado. Comparte la misma librería/convenciones
 * (OpenPDF en memoria, fechas en America/Guayaquil), pero con su propio
 * layout porque el contenido es distinto (una ficha, no una tabla de N filas).
 */
public final class RegistrationCertificatePdfGenerator {

    private static final Font TITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 18);
    private static final Font SUBTITLE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 11);
    private static final Font LABEL_FONT =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 10);
    private static final Font VALUE_FONT =
            FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 10);
    private static final Font CODE_FONT =
            FontFactory.getFont(FontFactory.COURIER_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED, 12);

    private RegistrationCertificatePdfGenerator() {
    }

    public static byte[] generate(RegistrationEntity registration) {
        EventEntity event = registration.getEvent();
        UserEntity participant = registration.getParticipant();

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(centered("Comprobante de Inscripción", TITLE_FONT));
            document.add(centered("Este documento certifica que la inscripción está CONFIRMADA", SUBTITLE_FONT));
            document.add(new Paragraph(" "));

            document.add(labelValueTable(event, registration, participant));

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            document.add(centered("Código de verificación", LABEL_FONT));
            document.add(centered(registration.getRegistrationCode().toString(), CODE_FONT));

            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Generado: " + BusinessTimeZoneConverter.formatForDisplay(OffsetDateTime.now())
                            + " (America/Guayaquil)", SUBTITLE_FONT));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el comprobante PDF", e);
        }
    }

    private static Paragraph centered(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private static PdfPTable labelValueTable(EventEntity event, RegistrationEntity registration,
                                              UserEntity participant) {
        PdfPTable table = new PdfPTable(new float[] {1.3f, 3f});
        table.setWidthPercentage(90);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        addRow(table, "Evento", event.getTitle());
        addRow(table, "Participante", participant.getFirstName() + " " + participant.getLastName());
        addRow(table, "Correo", participant.getEmail());
        addRow(table, "Fecha del evento",
                BusinessTimeZoneConverter.formatForDisplay(event.getStartAt()) + " - "
                        + BusinessTimeZoneConverter.formatForDisplay(event.getEndAt()));
        addRow(table, "Modalidad", event.getModality().name());
        addRow(table, "Lugar / enlace", locationOrLink(event));
        addRow(table, "Confirmado el", BusinessTimeZoneConverter.formatForDisplay(registration.getConfirmedAt()));

        return table;
    }

    private static String locationOrLink(EventEntity event) {
        if (event.getModality() == EventModality.HYBRID) {
            return event.getLocation() + " / " + event.getVirtualUrl();
        }
        return event.getModality() == EventModality.VIRTUAL ? event.getVirtualUrl() : event.getLocation();
    }

    private static void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new com.lowagie.text.Phrase(label, LABEL_FONT));
        labelCell.setPadding(5);
        labelCell.setBorder(0);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new com.lowagie.text.Phrase(value == null ? "" : value, VALUE_FONT));
        valueCell.setPadding(5);
        valueCell.setBorder(0);
        table.addCell(valueCell);
    }
}

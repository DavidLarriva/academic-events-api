package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.utils.BusinessTimeZoneConverter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Apache POI (ya en build.gradle desde el prompt 1) genera el .xlsx por
 * completo en memoria: sin archivos temporales en disco (docs/instrucciones.pdf
 * sección 15). Fechas siempre en America/Guayaquil vía BusinessTimeZoneConverter.
 */
public final class RegistrationExcelReportGenerator {

    private static final String[] HEADERS = {"#", "Participante", "Correo", "Estado", "Fecha de inscripción"};

    private RegistrationExcelReportGenerator() {
    }

    public static byte[] generate(EventEntity event, List<RegistrationEntity> registrations) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Inscritos");

            CellStyle titleStyle = boldStyle(workbook, 14);
            CellStyle headerStyle = boldStyle(workbook, 11);

            int rowIndex = 0;
            createCell(sheet.createRow(rowIndex++), 0, "Listado de inscritos - " + event.getTitle(), titleStyle);
            createCell(sheet.createRow(rowIndex++), 0,
                    "Generado: " + BusinessTimeZoneConverter.formatForDisplay(OffsetDateTime.now())
                            + " (America/Guayaquil)", null);
            createCell(sheet.createRow(rowIndex++), 0, "Total: " + registrations.size(), null);
            rowIndex++; // fila en blanco

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < HEADERS.length; i++) {
                createCell(headerRow, i, HEADERS[i], headerStyle);
            }

            int index = 1;
            for (RegistrationEntity registration : registrations) {
                Row row = sheet.createRow(rowIndex++);
                createCell(row, 0, String.valueOf(index++), null);
                createCell(row, 1,
                        registration.getParticipant().getFirstName() + " " + registration.getParticipant().getLastName(),
                        null);
                createCell(row, 2, registration.getParticipant().getEmail(), null);
                createCell(row, 3, registration.getStatus().name(), null);
                createCell(row, 4, BusinessTimeZoneConverter.formatForDisplay(registration.getRegisteredAt()), null);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte Excel", e);
        }
    }

    private static CellStyle boldStyle(XSSFWorkbook workbook, int fontHeightPoints) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) fontHeightPoints);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}

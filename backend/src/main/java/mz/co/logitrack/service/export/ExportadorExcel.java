package mz.co.logitrack.service.export;

import mz.co.logitrack.dto.relatorio.RelatorioFiltro;
import mz.co.logitrack.dto.relatorio.RelatorioLinha;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.relatorio.RelatorioResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/** Exportação do relatório operacional em Excel (.xlsx) — W5. */
@Component
public class ExportadorExcel implements ExportadorRelatorio {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String formato() {
        return "xlsx";
    }

    @Override
    public String contentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String nomeFicheiro(RelatorioFiltro filtro) {
        return "logitrack-relatorio-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx";
    }

    @Override
    public byte[] gerar(RelatorioResponse relatorio) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Relatório Operacional");
            sheet.setDefaultRowHeightInPoints(18);

            CellStyle titulo = estilo(workbook, IndexedColors.DARK_BLUE.getIndex(),
                    IndexedColors.WHITE.getIndex(), true, 14);
            CellStyle subtitulo = estilo(workbook, IndexedColors.WHITE.getIndex(),
                    IndexedColors.GREY_50_PERCENT.getIndex(), false, 9);
            CellStyle destaque = estilo(workbook, IndexedColors.WHITE.getIndex(),
                    IndexedColors.ORANGE.getIndex(), true, 9);
            CellStyle cabecalho = estilo(workbook, IndexedColors.DARK_BLUE.getIndex(),
                    IndexedColors.WHITE.getIndex(), true, 9);
            CellStyle corpo = estilo(workbook, IndexedColors.WHITE.getIndex(),
                    IndexedColors.BLACK.getIndex(), false, 9);
            CellStyle corpoAlternado = estilo(workbook, IndexedColors.GREY_25_PERCENT.getIndex(),
                    IndexedColors.BLACK.getIndex(), false, 9);
            CellStyle numero = estilo(workbook, IndexedColors.WHITE.getIndex(),
                    IndexedColors.BLACK.getIndex(), false, 9);
            numero.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
            CellStyle numeroAlternado = estilo(workbook, IndexedColors.GREY_25_PERCENT.getIndex(),
                    IndexedColors.BLACK.getIndex(), false, 9);
            numeroAlternado.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
            CellStyle atraso = estilo(workbook, IndexedColors.WHITE.getIndex(),
                    IndexedColors.DARK_RED.getIndex(), true, 9);
            CellStyle atrasoAlternado = estilo(workbook, IndexedColors.GREY_25_PERCENT.getIndex(),
                    IndexedColors.DARK_RED.getIndex(), true, 9);

            Row linhaTitulo = sheet.createRow(0);
            linhaTitulo.setHeightInPoints(28);
            Cell celulaTitulo = linhaTitulo.createCell(0);
            celulaTitulo.setCellValue("LOGITRACK — Relatório Operacional");
            celulaTitulo.setCellStyle(titulo);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

            Row linhaSubtitulo = sheet.createRow(1);
            Cell celulaSub = linhaSubtitulo.createCell(0);
            celulaSub.setCellValue("Sistema de Gestão e Rastreabilidade de Carregamentos");
            celulaSub.setCellStyle(subtitulo);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            RelatorioFiltro f = relatorio.filtro();
            Row linhaFiltro = sheet.createRow(2);
            Cell celulaFiltro = linhaFiltro.createCell(0);
            celulaFiltro.setCellValue(String.format(
                    "Período: %s a %s%s  |  Emitido em: %s",
                    f.de() != null ? f.de().format(DATA) : "início",
                    f.ate() != null ? f.ate().format(DATA) : "hoje",
                    f.estado() != null ? "  |  Estado: " + f.estado().getEtiqueta() : "",
                    java.time.LocalDateTime.now().format(DATA_HORA)));
            celulaFiltro.setCellStyle(subtitulo);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 12));

            RelatorioResponse.ResumoRelatorio r = relatorio.resumo();
            String fases = "Espera: " + horas(r.esperaMediaHoras())
                    + "  |  Carga: " + horas(r.carregamentoMedioHoras())
                    + "  |  Transporte: " + horas(r.transporteMedioHoras());
            Row linhaResumo = sheet.createRow(3);
            Cell celulaResumo = linhaResumo.createCell(0);
            celulaResumo.setCellValue(String.format(
                    "Total: %d  |  Toneladas: %.1f  |  Concluídos: %d  |  Atrasados: %d  |  "
                            + "Ciclo: %s  |  %s",
                    r.total(), r.toneladas(), r.concluidos(), r.atrasados(),
                    horas(r.tempoMedioHoras()), fases));
            celulaResumo.setCellStyle(destaque);
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 12));

            String[] cabecalhos = {"Guia", "Fábrica", "Veículo", "Motorista", "Tipo de carga",
                    "Toneladas", "Estado", "Início", "Fim", "Atraso (min)",
                    "Espera (min)", "Carga (min)", "Transporte (min)"};
            Row linhaCabecalho = sheet.createRow(5);
            for (int i = 0; i < cabecalhos.length; i++) {
                Cell celula = linhaCabecalho.createCell(i);
                celula.setCellValue(cabecalhos[i]);
                celula.setCellStyle(cabecalho);
            }

            int numeroLinha = 6;
            boolean alternado = false;
            for (RelatorioLinha linha : relatorio.linhas()) {
                Row row = sheet.createRow(numeroLinha++);
                CellStyle estiloCorpo = alternado ? corpoAlternado : corpo;
                CellStyle estiloNumero = alternado ? numeroAlternado : numero;
                CellStyle estiloAtraso = alternado ? atrasoAlternado : atraso;

                criarCelula(row, 0, linha.guiaNumero(), estiloCorpo);
                criarCelula(row, 1, linha.fabrica(), estiloCorpo);
                criarCelula(row, 2, linha.veiculo(), estiloCorpo);
                criarCelula(row, 3, linha.motorista(), estiloCorpo);
                criarCelula(row, 4, linha.tipoCarga(), estiloCorpo);

                Cell toneladas = row.createCell(5);
                toneladas.setCellValue(linha.toneladas());
                toneladas.setCellStyle(estiloNumero);

                criarCelula(row, 6, linha.estado(), estiloCorpo);
                criarCelula(row, 7, linha.dataHoraInicio() != null
                        ? linha.dataHoraInicio().format(DATA_HORA) : "—", estiloCorpo);
                criarCelula(row, 8, linha.dataHoraFim() != null
                        ? linha.dataHoraFim().format(DATA_HORA) : "—", estiloCorpo);

                Cell atrasoCell = row.createCell(9);
                if (linha.atrasoMinutos() > 0) {
                    atrasoCell.setCellValue(linha.atrasoMinutos());
                    atrasoCell.setCellStyle(estiloAtraso);
                } else {
                    criarCelula(row, 9, "—", estiloCorpo);
                }

                criarCelula(row, 10, minutosFase(linha.fases(), 0), estiloNumero);
                criarCelula(row, 11, minutosFase(linha.fases(), 1), estiloNumero);
                criarCelula(row, 12, minutosFase(linha.fases(), 2), estiloNumero);
                alternado = !alternado;
            }

            int[] larguras = {18, 24, 16, 24, 18, 12, 14, 18, 18, 14, 14, 13, 17};
            for (int i = 0; i < larguras.length; i++) {
                sheet.setColumnWidth(i, larguras[i] * 256);
            }
            sheet.createFreezePane(0, 6);
            if (numeroLinha > 6) {
                sheet.setAutoFilter(new CellRangeAddress(5, numeroLinha - 1, 0, 12));
            }

            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar o Excel do relatório", ex);
        }
    }

    private void criarCelula(Row row, int coluna, String valor, CellStyle estilo) {
        Cell celula = row.createCell(coluna);
        celula.setCellValue(valor != null ? valor : "—");
        celula.setCellStyle(estilo);
    }

    private String horas(Double valor) {
        return valor == null ? "sem dados" : String.format("%.1f h", valor);
    }

    private String minutosFase(FasesCarregamento fases, int fase) {
        if (fases == null) {
            return "—";
        }
        Long minutos = switch (fase) {
            case 0 -> fases.esperaMinutos();
            case 1 -> fases.carregamentoMinutos();
            default -> fases.transporteMinutos();
        };
        return minutos != null ? String.valueOf(minutos) : "—";
    }

    private CellStyle estilo(XSSFWorkbook workbook, short fundo, short fonte,
                             boolean negrito, int tamanho) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFillForegroundColor(fundo);
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(HorizontalAlignment.LEFT);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(true);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

        Font font = workbook.createFont();
        font.setColor(fonte);
        font.setBold(negrito);
        font.setFontHeightInPoints((short) tamanho);
        font.setFontName("Calibri");
        estilo.setFont(font);
        return estilo;
    }
}

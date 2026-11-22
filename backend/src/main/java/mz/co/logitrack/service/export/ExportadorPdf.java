package mz.co.logitrack.service.export;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import mz.co.logitrack.dto.relatorio.RelatorioFiltro;
import mz.co.logitrack.dto.relatorio.RelatorioLinha;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.relatorio.RelatorioResponse;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** Exportação do relatório operacional em PDF (W5). */
@Component
public class ExportadorPdf implements ExportadorRelatorio {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color AZUL = new Color(11, 31, 58);
    private static final Color LARANJA = new Color(242, 107, 46);
    private static final Color CINZA = new Color(244, 246, 249);
    private static final Color VERMELHO = new Color(190, 40, 40);
    private static final Color VERDE = new Color(28, 128, 88);

    @Override
    public String formato() {
        return "pdf";
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String nomeFicheiro(RelatorioFiltro filtro) {
        return "logitrack-relatorio-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".pdf";
    }

    @Override
    public byte[] gerar(RelatorioResponse relatorio) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 24, 24, 30, 30);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            writer.setPageEvent(new Rodape());
            documento.open();

            Font titulo = new Font(Font.HELVETICA, 18, Font.BOLD, AZUL);
            Font subtitulo = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(90, 100, 115));
            Font seccao = new Font(Font.HELVETICA, 10, Font.BOLD, AZUL);
            Font normal = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

            Paragraph marca = new Paragraph("LOGITRACK", titulo);
            marca.setSpacingAfter(2);
            documento.add(marca);
            documento.add(new Paragraph(
                    "Sistema de Gestão e Rastreabilidade de Carregamentos", subtitulo));
            Paragraph tituloRel = new Paragraph("RELATÓRIO OPERACIONAL", seccao);
            tituloRel.setSpacingBefore(12);
            tituloRel.setSpacingAfter(4);
            documento.add(tituloRel);

            RelatorioFiltro f = relatorio.filtro();
            StringBuilder filtros = new StringBuilder("Período: ");
            filtros.append(f.de() != null ? f.de().format(DATA) : "início")
                    .append(" a ")
                    .append(f.ate() != null ? f.ate().format(DATA) : "hoje");
            if (f.estado() != null) {
                filtros.append("  |  Estado: ").append(f.estado().getEtiqueta());
            }
            filtros.append("  |  Emitido em: ")
                    .append(java.time.LocalDateTime.now().format(DATA_HORA));
            documento.add(new Paragraph(filtros.toString(), normal));

            RelatorioResponse.ResumoRelatorio r = relatorio.resumo();
            String fases = r.registosComFases() > 0
                    ? String.format("Espera: %.1f h  |  Carga: %.1f h  |  Transporte: %.1f h",
                    r.esperaMediaHoras(), r.carregamentoMedioHoras(),
                    r.transporteMedioHoras())
                    : "Fases sem dados";
            Paragraph resumo = new Paragraph(String.format(
                    "Total: %d carregamentos  |  %.1f toneladas  |  Concluídos: %d  |  "
                            + "Atrasados: %d  |  Ciclo: %.1f h  |  %s",
                    r.total(), r.toneladas(), r.concluidos(), r.atrasados(),
                    r.tempoMedioHoras(), fases),
                    new Font(Font.HELVETICA, 9, Font.BOLD, LARANJA));
            resumo.setSpacingBefore(6);
            resumo.setSpacingAfter(10);
            documento.add(resumo);

            PdfPTable tabela = new PdfPTable(new float[]{1.05f, 1.35f, 0.9f, 1.25f, 0.9f,
                    0.62f, 0.9f, 1.0f, 0.95f, 0.58f, 0.7f, 0.7f, 0.75f});
            tabela.setWidthPercentage(100);

            String[] cabecalhos = {"Guia", "Fábrica", "Veículo", "Motorista", "Carga",
                    "Ton.", "Estado", "Início", "Fim", "Atraso", "Espera min",
                    "Carga min", "Transporte min"};
            Font fonteCabecalho = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);
            for (String cabecalho : cabecalhos) {
                PdfPCell celula = new PdfPCell(new Phrase(cabecalho, fonteCabecalho));
                celula.setBackgroundColor(AZUL);
                celula.setPadding(5);
                celula.setHorizontalAlignment(Element.ALIGN_LEFT);
                tabela.addCell(celula);
            }

            Font fonteLinha = new Font(Font.HELVETICA, 7.2f, Font.NORMAL, Color.DARK_GRAY);
            boolean alternado = false;
            for (RelatorioLinha linha : relatorio.linhas()) {
                Color fundo = alternado ? CINZA : Color.WHITE;
                alternado = !alternado;
                adicionar(tabela, linha.guiaNumero(), fonteLinha, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, nulo(linha.fabrica()), fonteLinha, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, nulo(linha.veiculo()), fonteLinha, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, nulo(linha.motorista()), fonteLinha, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, nulo(linha.tipoCarga()), fonteLinha, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, String.format("%.1f", linha.toneladas()), fonteLinha,
                        fundo, Element.ALIGN_RIGHT);
                Font fonteEstado = new Font(Font.HELVETICA, 7.2f, Font.BOLD,
                        corEstado(linha.estado()));
                adicionar(tabela, linha.estado(), fonteEstado, fundo, Element.ALIGN_LEFT);
                adicionar(tabela, linha.dataHoraInicio() != null
                        ? linha.dataHoraInicio().format(DATA_HORA) : "—", fonteLinha,
                        fundo, Element.ALIGN_LEFT);
                adicionar(tabela, linha.dataHoraFim() != null
                        ? linha.dataHoraFim().format(DATA_HORA) : "—", fonteLinha,
                        fundo, Element.ALIGN_LEFT);
                adicionar(tabela, linha.atrasoMinutos() > 0
                        ? "+" + linha.atrasoMinutos() + " min" : "—", fonteLinha,
                        fundo, Element.ALIGN_RIGHT);
                adicionar(tabela, minutosFase(linha.fases(), 0), fonteLinha,
                        fundo, Element.ALIGN_RIGHT);
                adicionar(tabela, minutosFase(linha.fases(), 1), fonteLinha,
                        fundo, Element.ALIGN_RIGHT);
                adicionar(tabela, minutosFase(linha.fases(), 2), fonteLinha,
                        fundo, Element.ALIGN_RIGHT);
            }

            if (relatorio.linhas().isEmpty()) {
                PdfPCell vazio = new PdfPCell(new Phrase(
                        "Nenhum carregamento corresponde aos filtros selecionados.",
                        normal));
                vazio.setColspan(13);
                vazio.setPadding(12);
                vazio.setHorizontalAlignment(Element.ALIGN_CENTER);
                tabela.addCell(vazio);
            }

            documento.add(tabela);
            documento.close();
            return saida.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Falha ao gerar o PDF do relatório", ex);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
    }

    private void adicionar(PdfPTable tabela, String texto, Font fonte, Color fundo, int alinhamento) {
        PdfPCell celula = new PdfPCell(new Phrase(texto != null ? texto : "—", fonte));
        celula.setBackgroundColor(fundo);
        celula.setPadding(4);
        celula.setHorizontalAlignment(alinhamento);
        tabela.addCell(celula);
    }

    private String nulo(String valor) {
        return valor != null ? valor : "—";
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
        return minutos != null ? minutos + " min" : "—";
    }

    private Color corEstado(String estado) {
        if (estado == null) {
            return Color.DARK_GRAY;
        }
        return switch (estado) {
            case "Concluído" -> VERDE;
            case "Atrasado", "Rejeitado" -> VERMELHO;
            default -> AZUL;
        };
    }

    private static class Rodape extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Font fonte = new Font(Font.HELVETICA, 7, Font.NORMAL, new Color(130, 140, 150));
                Phrase rodape = new Phrase(
                        "Logitrack · Documento gerado automaticamente · Página "
                                + writer.getPageNumber(), fonte);
                ColumnText.showTextAligned(writer.getDirectContent(),
                        Element.ALIGN_CENTER, rodape,
                        (document.getPageSize().getLeft() + document.getPageSize().getRight()) / 2,
                        document.getPageSize().getBottom() + 14, 0);
            } catch (Exception ignored) {
                // O rodapé é meramente informativo.
            }
        }
    }
}

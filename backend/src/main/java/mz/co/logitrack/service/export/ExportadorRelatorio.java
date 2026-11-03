package mz.co.logitrack.service.export;

import mz.co.logitrack.dto.relatorio.RelatorioFiltro;
import mz.co.logitrack.dto.relatorio.RelatorioResponse;

/** Strategy de exportação (PDF / Excel) definida na Fase II. */
public interface ExportadorRelatorio {

    String formato();

    String contentType();

    String nomeFicheiro(RelatorioFiltro filtro);

    byte[] gerar(RelatorioResponse relatorio);
}

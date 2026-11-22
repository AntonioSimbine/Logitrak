package mz.co.logitrack.service;

import jakarta.annotation.PostConstruct;
import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.DocumentoGuia;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.DocumentoGuiaRepository;
import mz.co.logitrack.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * Armazenamento e leitura das fotografias das guias. Os ficheiros ficam num
 * diretório configurável (ou volume S3 futuramente); a base guarda o caminho e
 * o hash SHA-256 para deteção de adulteração (RN08/Fase II).
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);
    private static final DateTimeFormatter PASTA_DATA = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final String FOTOGRAFIA_INVALIDA =
            "A fotografia não serve. Tire outra com boa luz e o documento inteiro dentro da moldura.";

    private final DocumentoGuiaRepository documentoRepository;

    @Value("${logitrack.upload-dir:uploads}")
    private String uploadDir;

    private Path raiz;

    public DocumentoService(DocumentoGuiaRepository documentoRepository) {
        this.documentoRepository = documentoRepository;
    }

    @PostConstruct
    void inicializar() {
        this.raiz = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
            log.info("Diretório de documentos: {}", raiz);
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível criar o diretório de uploads", ex);
        }
    }

    /**
     * Valida e guarda a fotografia enviada pela app móvel. A imagem tem de ser
     * um JPEG/PNG legível e com resolução mínima aceitável.
     */
    @Transactional
    public DocumentoGuia salvarFoto(Carregamento carregamento, byte[] bytes,
                                    String mimeRecebido, LocalDateTime captura) {
        if (bytes == null || bytes.length == 0) {
            throw ApiException.unprocessable("Falta a fotografia da guia.");
        }

        String formato = detetarFormato(bytes);
        if (!"jpeg".equals(formato) && !"png".equals(formato)) {
            log.warn("Formato de imagem não suportado: {}", formato);
            throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
        }

        validarDimensoes(bytes);

        String extensao = "jpeg".equals(formato) ? "jpg" : "png";
        String mime = "jpeg".equals(formato) ? "image/jpeg" : "image/png";
        String caminhoRelativo = "guias/" + LocalDateTime.now().format(PASTA_DATA) + "/"
                + UUID.randomUUID() + "." + extensao;

        Path destino = resolver(caminhoRelativo);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gravar a fotografia da guia", ex);
        }

        DocumentoGuia documento = new DocumentoGuia();
        documento.setCarregamento(carregamento);
        documento.setCaminhoFotografia(caminhoRelativo);
        documento.setDataHoraCaptura(captura != null ? captura : LocalDateTime.now());
        documento.setTamanhoBytes((long) bytes.length);
        documento.setHashSha256(calcularHash(bytes));
        documento.setMimeType(mime);

        try {
            DocumentoGuia guardado = documentoRepository.save(documento);
            carregamento.setDocumento(guardado);
            return guardado;
        } catch (RuntimeException ex) {
            apagarSilenciosamente(destino);
            throw ex;
        }
    }

    /** Usado pelo seeder para criar guias de demonstração em SVG. */
    @Transactional
    public DocumentoGuia salvarSvgSeed(Carregamento carregamento, String svg, String nomeFicheiro) {
        String caminhoRelativo = "guias/seed/" + nomeFicheiro + ".svg";
        Path destino = resolver(caminhoRelativo);
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gravar a guia de demonstração", ex);
        }

        DocumentoGuia documento = new DocumentoGuia();
        documento.setCarregamento(carregamento);
        documento.setCaminhoFotografia(caminhoRelativo);
        documento.setDataHoraCaptura(carregamento.getDataHoraInicio());
        documento.setTamanhoBytes((long) bytes.length);
        documento.setHashSha256(calcularHash(bytes));
        documento.setMimeType("image/svg+xml");
        DocumentoGuia guardado = documentoRepository.save(documento);
        carregamento.setDocumento(guardado);
        return guardado;
    }

    /** Substitui a fotografia de um carregamento (reenvio/correção). */
    @Transactional
    public DocumentoGuia substituir(Carregamento carregamento, byte[] bytes, String mime) {
        DocumentoGuia existente = documentoRepository.findByCarregamentoId(carregamento.getId())
                .orElse(null);
        if (existente == null) {
            return salvarFoto(carregamento, bytes, mime, LocalDateTime.now());
        }
        String caminhoAntigo = existente.getCaminhoFotografia();
        String formato = detetarFormato(bytes);
        if (!"jpeg".equals(formato) && !"png".equals(formato)) {
            log.warn("Formato de imagem não suportado na substituição: {}", formato);
            throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
        }
        validarDimensoes(bytes);
        String extensao = "jpeg".equals(formato) ? "jpg" : "png";
        String caminhoNovo = "guias/" + LocalDateTime.now().format(PASTA_DATA) + "/"
                + UUID.randomUUID() + "." + extensao;
        Path destino = resolver(caminhoNovo);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gravar a nova fotografia", ex);
        }
        existente.setCaminhoFotografia(caminhoNovo);
        existente.setTamanhoBytes((long) bytes.length);
        existente.setHashSha256(calcularHash(bytes));
        existente.setMimeType("jpeg".equals(formato) ? "image/jpeg" : "image/png");
        existente.setDataHoraCaptura(LocalDateTime.now());
        DocumentoGuia guardado = documentoRepository.save(existente);
        apagarSilenciosamente(resolver(caminhoAntigo));
        return guardado;
    }

    @Transactional(readOnly = true)
    public DocumentoGuia obter(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Não foi possível encontrar a fotografia da guia. Confirme os dados e tente novamente."));
    }

    @Transactional(readOnly = true)
    public DocumentoGuia obterPorCarregamento(Long carregamentoId) {
        return documentoRepository.findByCarregamentoId(carregamentoId)
                .orElseThrow(() -> ApiException.notFound("Falta a fotografia da guia."));
    }

    @Transactional(readOnly = true)
    public DocumentoGuia obterComAcesso(Long id, AuthenticatedUser solicitante) {
        DocumentoGuia documento = obter(id);
        verificarPropriedade(documento, solicitante);
        return documento;
    }

    @Transactional(readOnly = true)
    public DocumentoGuia obterPorCarregamentoComAcesso(Long carregamentoId,
                                                       AuthenticatedUser solicitante) {
        DocumentoGuia documento = obterPorCarregamento(carregamentoId);
        verificarPropriedade(documento, solicitante);
        return documento;
    }

    private void verificarPropriedade(DocumentoGuia documento, AuthenticatedUser solicitante) {
        if (solicitante == null || solicitante.getPerfil() != Role.MOTORISTA) {
            return;
        }
        Carregamento carregamento = documento.getCarregamento();
        if (carregamento == null || carregamento.getMotorista() == null
                || !carregamento.getMotorista().getId().equals(solicitante.getId())) {
            throw ApiException.forbidden(
                    "Não tem permissão para esta operação. Fale com a central.");
        }
    }

    /**
     * Devolve o ficheiro e registra o acesso (utilizador, documento e momento)
     * conforme RN08.
     */
    @Transactional(readOnly = true)
    public FicheiroGuia carregarFicheiro(Long id, AuthenticatedUser solicitante) {
        DocumentoGuia documento = obterComAcesso(id, solicitante);
        Path caminho = resolver(documento.getCaminhoFotografia());
        if (!Files.exists(caminho)) {
            log.warn("Fotografia da guia {} não encontrada em {}", id, caminho);
            throw ApiException.notFound(
                    "A fotografia da guia não está disponível. Tente novamente dentro de instantes.");
        }
        String guia = documento.getCarregamento() != null
                ? documento.getCarregamento().getGuiaNumero() : "documento-" + id;

        log.info("Acesso a guia digitalizada | utilizador={} | documento={} | guia={} | dataHora={}",
                solicitante != null ? solicitante.getEmail() : "sistema",
                id, guia, LocalDateTime.now());

        Resource recurso = new FileSystemResource(caminho);
        String extensao = caminho.getFileName().toString()
                .substring(caminho.getFileName().toString().lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        String nome = "guia-" + guia.replaceAll("[^A-Za-z0-9._-]", "_") + "." + extensao;
        String mime = documento.getMimeType() != null
                ? documento.getMimeType() : mimePorExtensao(extensao);
        return new FicheiroGuia(documento, recurso, nome, mime);
    }

    public record FicheiroGuia(DocumentoGuia documento, Resource recurso,
                               String nomeFicheiro, String mimeType) {
    }

    private Path resolver(String caminhoRelativo) {
        Path caminho = raiz.resolve(caminhoRelativo).normalize();
        if (!caminho.startsWith(raiz)) {
            log.warn("Caminho de documento fora da raiz: {}", caminho);
            throw ApiException.badRequest(
                    "Não foi possível abrir a fotografia da guia. Tente novamente.");
        }
        return caminho;
    }

    private void validarDimensoes(byte[] bytes) {
        try {
            BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(bytes));
            if (imagem == null) {
                log.warn("Não foi possível interpretar a fotografia da guia");
                throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
            }
            int largura = imagem.getWidth();
            int altura = imagem.getHeight();
            if (Math.max(largura, altura) < 640 || Math.min(largura, altura) < 360) {
                log.warn("Fotografia da guia com resolução {}x{} abaixo do mínimo", largura, altura);
                throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
            }
        } catch (IOException ex) {
            log.warn("Não foi possível validar a imagem da guia", ex);
            throw ApiException.unprocessable(FOTOGRAFIA_INVALIDA);
        }
    }

    private String detetarFormato(byte[] bytes) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (entrada == null) {
                return "desconhecido";
            }
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                return "desconhecido";
            }
            ImageReader leitor = leitores.next();
            try {
                return leitor.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                leitor.dispose();
            }
        } catch (IOException ex) {
            return "desconhecido";
        }
    }

    private String calcularHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }

    private String mimePorExtensao(String extensao) {
        return switch (extensao) {
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            default -> "image/jpeg";
        };
    }

    private void apagarSilenciosamente(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ex) {
            log.warn("Não foi possível apagar {}", caminho, ex);
        }
    }
}

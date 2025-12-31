package com.ai.rag.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocumentParser {

    /**
     * PDF -> plain text (PDFBox)
     */
    public String extractPdf(MultipartFile file) {
        requireNotEmpty(file);

        try (InputStream in = file.getInputStream();
             PDDocument doc = Loader.loadPDF(in.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return normalize(text);

        } catch (IOException e) {
            throw new IllegalArgumentException("PDF 파싱 실패: " + safeName(file), e);
        }
    }

    /**
     * DOCX -> plain text (Apache POI)
     */
    public String extractDocx(MultipartFile file) {
        requireNotEmpty(file);

        try (InputStream in = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {

            String text = extractor.getText();
            return normalize(text);

        } catch (IOException e) {
            throw new IllegalArgumentException("DOCX 파싱 실패: " + safeName(file), e);
        }
    }

    /**
     * HWPX -> plain text
     * - HWPX는 ZIP 컨테이너 + 내부 XML 구조
     * - MVP: ZIP 안의 .xml 파일들을 순회하면서 텍스트 노드들만 수집
     */
    public String extractHwpx(MultipartFile file) {
        requireNotEmpty(file);

        // 안전장치: 너무 큰 파일은 일단 제한 (원하면 조정)
        long maxBytes = 30L * 1024 * 1024; // 30MB
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("HWPX 파일이 너무 큽니다(>30MB): " + safeName(file));
        }

        StringBuilder sb = new StringBuilder(32_000);

        try (InputStream in = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                // HWPX에서 핵심 본문은 보통 Contents/section*.xml 쪽이지만,
                // MVP는 xml 전체를 훑되, binary/미디어는 제외
                if (!name.endsWith(".xml")) {
                    continue;
                }
                // 불필요/노이즈 가능성이 큰 경로는 제외(원하면 조정)
                if (name.startsWith("BinData/") || name.startsWith("Preview/")) {
                    continue;
                }

                // ZipInputStream은 entry별로 "현재 스트림"에서 읽어야 함.
                // DOM 파서가 스트림을 끝까지 읽기 때문에 entry 단위로 처리.
                String xmlText = readAllToString(zis);
                if (xmlText.isBlank()) {
                    continue;
                }

                // XML 파싱해서 텍스트 노드만 수집
                String extracted = extractTextFromXml(xmlText);
                if (!extracted.isBlank()) {
                    sb.append(extracted).append('\n');
                }

                zis.closeEntry();
            }

            String text = sb.toString();
            text = normalize(text);

            if (text.isBlank()) {
                throw new IllegalArgumentException("HWPX에서 추출된 텍스트가 없습니다: " + safeName(file));
            }
            return text;

        } catch (IOException e) {
            throw new IllegalArgumentException("HWPX ZIP 처리 실패: " + safeName(file), e);
        }
    }

    // -------------------------
    // Helpers
    // -------------------------

    private void requireNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }
    }

    private String safeName(MultipartFile file) {
        try {
            return Objects.toString(file.getOriginalFilename(), "(unknown)");
        } catch (Exception ignore) {
            return "(unknown)";
        }
    }

    /**
     * Zip entry 스트림을 통째로 문자열로 읽음.
     * - HWPX XML은 UTF-8인 경우가 대부분.
     * - 혹시 인코딩 이슈가 있으면 여기서 확장.
     */
    private String readAllToString(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            bos.write(buf, 0, read);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * XML 문자열에서 텍스트 노드만 모두 수집
     * - MVP: 태그 이름/스키마에 의존하지 않고 전부 긁음
     */
    private String extractTextFromXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // 보안 옵션(XXE 방지)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setExpandEntityReferences(false);

            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            StringBuilder sb = new StringBuilder();
            collectTextNodes(doc, sb);

            return sb.toString();
        } catch (Exception e) {
            // XML 파싱이 실패하더라도 전체를 죽이지 않도록, 빈 값 리턴
            // (HWPX 내부 xml이 일부 깨진 경우를 고려)
            return "";
        }
    }

    private void collectTextNodes(Node node, StringBuilder out) {
        if (node == null) {
            return;
        }

        short type = node.getNodeType();
        if (type == Node.TEXT_NODE) {
            String v = node.getNodeValue();
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    out.append(t).append(' ');
                }
            }
        }

        NodeList children = node.getChildNodes();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.getLength(); i++) {
            collectTextNodes(children.item(i), out);
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        // 줄바꿈/공백 정리 (너무 공격적으로 줄이면 문맥 손상될 수 있어 적당히만)
        return text
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}

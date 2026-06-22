package com.ronkadosh.bubbleup.bootstrap.seed;

import com.ronkadosh.bubbleup.common.file.FileAccessPolicy;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.common.file.FileUploadRequest;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides the demo's sample files. To avoid committing binary blobs to the repo (no
 * {@code resources/demo/files/*.pdf} to maintain), the bytes are <b>generated in code</b>
 * once at boot — a tiny valid one-page PDF per document, a plaintext note, a 1x1 PNG —
 * and held as in-memory templates.
 *
 * <p><b>Group files get a fresh upload per row.</b> {@code group_files.file_id} is UNIQUE
 * ({@code uk_group_files_storage}), so the same stored blob cannot back two {@code GroupFile}
 * rows — sharing one {@code fileId} across bubbles violates the constraint. {@link #file}
 * therefore uploads a fresh copy of the (tiny, pre-generated) bytes on every call, minting
 * a distinct {@code fileId} each time. The per-file cost is negligible.
 *
 * <p>Avatars, by contrast, have no unique constraint on {@code users.avatar_file_id}, so a
 * single uploaded blob is shared across personas. {@link #avatar} probes the classpath so
 * real photos can be dropped into {@code resources/demo/avatars/...} later with no code
 * change; when none are bundled (the normal case) it returns null and the seeder leaves
 * {@code avatarFileId} null, so the app draws a generated initials-avatar.
 *
 * <p>Only registers when {@code app.demo.enabled}.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoAssetRegistry {

    private final FileStorageService fileStorageService;

    private record Template(String contentType, byte[] bytes) {}

    /** sample-file name -> in-memory template (bytes generated once at boot). */
    private final Map<String, Template> templates = new HashMap<>();
    /** avatar key ("people/<slug>") -> shared StoredFile (sharing is legal for avatars). */
    private final Map<String, StoredFile> avatars = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void buildTemplates() {
        templates.put("syllabus.pdf", new Template("application/pdf", pdf("Course Syllabus")));
        templates.put("welcome.txt", new Template("text/plain", WELCOME.getBytes(StandardCharsets.UTF_8)));
        // Hebrew variant of the plaintext welcome note (PDFs stay Latin — see pdf()).
        templates.put("welcome.he.txt", new Template("text/plain", WELCOME_HE.getBytes(StandardCharsets.UTF_8)));
        templates.put("lecture-notes-1.pdf", new Template("application/pdf", pdf("Lecture Notes  Week 1")));
        templates.put("lecture-notes-2.pdf", new Template("application/pdf", pdf("Lecture Notes  Week 2")));
        templates.put("past-exam-2025.pdf", new Template("application/pdf", pdf("2025 Midterm")));
        templates.put("cheat-sheet.pdf", new Template("application/pdf", pdf("Cheat Sheet")));
        templates.put("diagram.png", new Template("image/png", PNG_1X1));
        log.info("DemoAssetRegistry: prepared {} demo file templates", templates.size());
    }

    /**
     * A <b>fresh</b> upload of the named sample file (new {@code fileId} each call), or null
     * if the name is unknown. Fresh-per-call is required because {@code group_files.file_id}
     * is unique — two GroupFile rows cannot share one stored blob.
     */
    public StoredFile file(String fileName) {
        Template tpl = templates.get(fileName);
        if (tpl == null) return null;
        try {
            return fileStorageService.upload(
                    new FileUploadRequest(fileName, tpl.contentType(), tpl.bytes(), FileAccessPolicy.GROUP));
        } catch (RuntimeException e) {
            log.warn("DemoAssetRegistry: failed to upload {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * Optional persona/expert avatar from {@code resources/demo/avatars/<dir>/<slug>.jpg},
     * uploaded once and shared across worlds. Null when none is bundled (the normal case),
     * which makes the seeder leave {@code avatarFileId} null and the app draw a generated avatar.
     */
    public StoredFile avatar(String dir, String slug) {
        String key = dir + "/" + slug;
        if (avatars.containsKey(key)) return avatars.get(key);
        StoredFile stored = loadClasspath("demo/avatars/" + dir + "/" + slug + ".jpg", "image/jpeg");
        avatars.put(key, stored); // cache the miss (null) too, so we don't re-probe per session
        return stored;
    }

    private StoredFile loadClasspath(String classpath, String contentType) {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (!resource.exists()) return null;
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String name = classpath.substring(classpath.lastIndexOf('/') + 1);
            return fileStorageService.upload(new FileUploadRequest(name, contentType, bytes, FileAccessPolicy.GROUP));
        } catch (IOException e) {
            log.warn("DemoAssetRegistry: failed to load {}: {}", classpath, e.getMessage());
            return null;
        }
    }

    private static final String WELCOME =
            "Welcome to the Bubble!\n\n"
            + "This is your group's shared shelf. Drop notes, past exams, and resources here\n"
            + "so everyone can study together. Files are organized into folders on the left.\n";

    private static final String WELCOME_HE =
            "ברוכים הבאים לקבוצה!\n\n"
            + "זהו המדף המשותף של הקבוצה שלכם. העלו לכאן סיכומים, מבחנים קודמים ומשאבים\n"
            + "כדי שכולם יוכלו ללמוד יחד. הקבצים מאורגנים בתיקיות בצד.\n";

    /** Smallest valid PNG (a 1x1 transparent pixel), so "Diagram.png" has real bytes to preview. */
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    /**
     * Builds a minimal, valid single-page PDF showing {@code title}. Byte offsets for the
     * xref table are computed from the assembled content (all Latin-1, so char count == byte
     * count), so pdf.js renders it cleanly in the file preview.
     */
    private static byte[] pdf(String title) {
        String safe = title.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String content = "BT /F1 28 Tf 72 700 Td (" + safe + ") Tj ET";
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        };
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = sb.length();
            sb.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objects.length + 1).append("\n");
        sb.append("0000000000 65535 f \n");
        for (int i = 1; i <= objects.length; i++) {
            sb.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        sb.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xref).append("\n%%EOF");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}

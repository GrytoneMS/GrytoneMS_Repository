package com.grytonems.backend.global.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 업로드 파일을 서버 디렉터리(app.upload.dir)에 저장합니다.
 * - profiles/: 프로필 이미지 (PROF-01). /files/profiles/** 로 누구나 볼 수 있음
 * - documents/: 학교 인증 서류 (AUTH-07). 공개하지 않고 관리자 API로만 내려줌
 *
 * jpg·png만, 5MB 이하. 확장자가 아니라 파일 앞부분(매직 넘버)으로 형식을 확인합니다.
 */
@Slf4j
@Component
public class FileStorage {

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final Path root;
    private final String publicUrl;

    public FileStorage(@Value("${app.upload.dir:uploads}") String dir,
            @Value("${app.upload.public-url:http://localhost:8080/files}") String publicUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** 이미지를 저장하고 root 기준 상대 경로(예: profiles/7-uuid.png)를 돌려줍니다 */
    public String saveImage(String folder, String prefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "파일을 선택해 주세요");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "5MB 이하 파일만 올릴 수 있어요");
        }
        String ext = detectImageExtension(file);
        String relative = folder + "/" + prefix + "-" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("파일 저장 실패 {}", target, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return relative;
    }

    /** 공개 폴더(profiles) 파일의 주소 */
    public String publicUrlOf(String relative) {
        return publicUrl + "/" + relative;
    }

    /** 공개 주소에서 상대 경로를 꺼냅니다. 우리 주소가 아니면 null */
    public String relativeOfPublicUrl(String url) {
        if (url == null || !url.startsWith(publicUrl + "/")) {
            return null;
        }
        return url.substring(publicUrl.length() + 1);
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "파일을 찾을 수 없어요");
        }
    }

    /** 없으면 조용히 넘어갑니다 */
    public void delete(String relative) {
        if (relative == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException e) {
            log.warn("파일 삭제 실패 {}", relative, e);
        }
    }

    public Path root() {
        return root;
    }

    private Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        return path;
    }

    private static String detectImageExtension(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        throw new BusinessException(ErrorCode.INVALID_FILE, "jpg·png 이미지만 올릴 수 있어요");
    }
}

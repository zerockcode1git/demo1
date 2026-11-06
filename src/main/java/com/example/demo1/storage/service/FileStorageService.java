package com.example.demo1.storage.service;

import com.example.demo1.storage.config.SupabaseStorageProperties;
import com.example.demo1.storage.dto.FileUploadResponse;
import com.example.demo1.storage.exception.FileStorageException;
import com.example.demo1.storage.exception.InvalidFileException;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriBuilder;

/**
 * Supabase Storage REST API로 파일을 올리고 지운다.
 * 파일 이름은 원본 이름 대신 UUID로 바꿔 저장한다. 경로 형식: yyyy/MM/dd/{uuid}.{확장자}
 */
@Service
public class FileStorageService {

    private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final RestClient client;
    private final SupabaseStorageProperties properties;

    public FileStorageService(@Qualifier("supabaseStorageClient") RestClient client,
                              SupabaseStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public FileUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("업로드할 파일을 선택해 주세요.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new InvalidFileException("파일 크기는 " + readable(properties.maxFileSize()) + " 이하여야 합니다.");
        }

        String path = LocalDate.now().format(DATE_FOLDER) + "/" + UUID.randomUUID() + extensionOf(file);
        MediaType contentType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.getContentType());

        try {
            client.post()
                    .uri(builder -> objectUri(builder, path))
                    .contentType(contentType)
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            throw new FileStorageException("파일 업로드에 실패했습니다.", e);
        }

        return new FileUploadResponse(path, publicUrl(path));
    }

    public void delete(String path) {
        try {
            client.delete()
                    .uri(builder -> objectUri(builder, path))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new FileStorageException("파일 삭제에 실패했습니다. path=" + path, e);
        }
    }

    /**
     * 공개 URL로 파일을 지운다. 이 버킷의 URL이 아니면(외부 URL, 예전 데이터 등) 아무것도 하지 않는다.
     */
    public void deleteByUrl(String url) {
        String prefix = publicUrl("");
        if (url != null && url.startsWith(prefix)) {
            delete(url.substring(prefix.length()));
        }
    }

    private URI objectUri(UriBuilder builder, String path) {
        // pathSegment가 각 구간을 인코딩하므로 경로를 '/' 단위로 나눠 넣는다.
        return builder.pathSegment("object", properties.bucket())
                .pathSegment(path.split("/"))
                .build();
    }

    /**
     * 1MB 단위로 나누어떨어지면 MB, 아니면 KB로 표시한다. 예: 10MB, 500KB
     */
    private String readable(DataSize size) {
        long bytes = size.toBytes();
        return bytes % DataSize.ofMegabytes(1).toBytes() == 0
                ? size.toMegabytes() + "MB"
                : size.toKilobytes() + "KB";
    }

    private String publicUrl(String path) {
        return properties.url() + "/storage/v1/object/public/" + properties.bucket() + "/" + path;
    }

    /**
     * 원본 파일명의 확장자를 영문 소문자·숫자만 남겨 돌려준다. 없으면 빈 문자열.
     */
    private String extensionOf(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null) {
            return "";
        }
        String cleaned = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() || cleaned.length() > 10 ? "" : "." + cleaned;
    }
}

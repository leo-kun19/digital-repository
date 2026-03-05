package com.example.thesisrepo.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StorageService {

  private enum StorageProvider {
    LOCAL,
    AZURE
  }

  @Value("${file.storage.provider:local}")
  private String provider;

  @Value("${file.storage-root:./storage}")
  private String root;

  @Value("${file.max-size-bytes:104857600}")
  private long maxSizeBytes;

  @Value("${azure.storage.connection-string:}")
  private String azureConnectionString;

  @Value("${azure.storage.container:thesisrepo}")
  private String azureContainer;

  @Value("${azure.storage.prefix:documents}")
  private String azurePrefix;

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
    "application/pdf"
  );

  private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf");

  private StorageProvider storageProvider = StorageProvider.LOCAL;
  private BlobContainerClient blobContainerClient;
  private String normalizedAzurePrefix = "";

  @PostConstruct
  void initializeStorage() {
    storageProvider = resolveProvider(provider);
    if (storageProvider == StorageProvider.LOCAL) {
      Path base = Paths.get(root).toAbsolutePath().normalize();
      log.info("Storage provider configured: local (root={})", base);
      return;
    }

    if (!hasText(azureConnectionString)) {
      throw new IllegalStateException(
        "FILE_STORAGE_PROVIDER=azure requires AZURE_STORAGE_CONNECTION_STRING to be configured"
      );
    }

    BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
      .connectionString(azureConnectionString)
      .buildClient();
    blobContainerClient = blobServiceClient.getBlobContainerClient(azureContainer);
    blobContainerClient.createIfNotExists();
    normalizedAzurePrefix = normalizePrefix(azurePrefix);

    log.info(
      "Storage provider configured: azure (container={}, prefix={})",
      azureContainer,
      normalizedAzurePrefix.isBlank() ? "<none>" : normalizedAzurePrefix
    );
  }

  public String saveDocument(MultipartFile file) throws IOException {
    String ext = extension(file.getOriginalFilename());
    String nowBucket = LocalDate.now().toString().substring(0, 7);
    String objectKey = nowBucket + "/" + UUID.randomUUID() + ext;
    return saveWithKey(file, objectKey);
  }

  public String saveWithKey(MultipartFile file, String objectKey) throws IOException {
    validatePdf(file);
    String normalizedObjectKey = normalizeObjectKey(objectKey);
    return storageProvider == StorageProvider.AZURE
      ? saveToAzure(file, normalizedObjectKey)
      : saveToLocal(file, normalizedObjectKey);
  }

  public boolean exists(String storedKey) {
    if (!hasText(storedKey)) {
      return false;
    }

    if (storageProvider == StorageProvider.AZURE) {
      return blobContainerClient.getBlobClient(toBlobName(storedKey)).exists();
    }

    try {
      Path path = resolveStoredFile(storedKey);
      return Files.exists(path) && Files.isRegularFile(path);
    } catch (RuntimeException ex) {
      return false;
    }
  }

  public Resource openAsResource(String storedKey) throws IOException {
    if (!hasText(storedKey)) {
      throw new IOException("Stored key is empty");
    }

    if (storageProvider == StorageProvider.AZURE) {
      BlobClient blobClient = blobContainerClient.getBlobClient(toBlobName(storedKey));
      if (!blobClient.exists()) {
        throw new IOException("File not found");
      }
      return new InputStreamResource(blobClient.openInputStream());
    }

    Path file = resolveStoredFile(storedKey);
    return new InputStreamResource(Files.newInputStream(file));
  }

  public Path resolveStoredFile(String storedPath) {
    if (storedPath == null || storedPath.isBlank()) {
      throw new IllegalArgumentException("Stored path is empty");
    }

    if (storedPath.startsWith("file:")) {
      return Paths.get(java.net.URI.create(storedPath));
    }

    Path path = Paths.get(storedPath);
    if (path.isAbsolute()) {
      return path.normalize();
    }

    Path base = Paths.get(root).toAbsolutePath().normalize();
    Path resolved = base.resolve(path).normalize();
    if (!resolved.startsWith(base)) {
      throw new IllegalArgumentException("Invalid stored path");
    }
    return resolved;
  }

  private String saveToLocal(MultipartFile file, String objectKey) throws IOException {
    Path base = Paths.get(root).toAbsolutePath().normalize();
    Files.createDirectories(base);

    Path relativeObjectKey = Paths.get(objectKey).normalize();
    if (relativeObjectKey.isAbsolute() || relativeObjectKey.startsWith("..")) {
      throw new IOException("Invalid storage key");
    }

    Path dest = base.resolve(relativeObjectKey).normalize();
    if (!dest.startsWith(base)) {
      throw new IOException("Invalid storage destination");
    }

    if (dest.getParent() != null) {
      Files.createDirectories(dest.getParent());
    }

    file.transferTo(dest.toFile());
    return base.relativize(dest).toString().replace('\\', '/');
  }

  private String saveToAzure(MultipartFile file, String objectKey) throws IOException {
    String blobName = buildBlobName(objectKey);
    BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
    try (var inputStream = file.getInputStream()) {
      blobClient.upload(inputStream, file.getSize(), true);
    }
    blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType("application/pdf"));
    return blobName;
  }

  private void validatePdf(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IOException("File is required");
    }
    if (file.getSize() > maxSizeBytes) {
      throw new IOException("File exceeds maximum size of " + maxSizeBytes + " bytes");
    }

    String ext = extension(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(ext)) {
      throw new IOException("Only PDF files are accepted.");
    }

    String contentType = file.getContentType();
    if (contentType != null
      && !contentType.isBlank()
      && !"application/octet-stream".equalsIgnoreCase(contentType)
      && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new IOException("Only PDF files are accepted.");
    }

    if (!hasPdfMagicBytes(file)) {
      throw new IOException("Only PDF files are accepted.");
    }
  }

  private StorageProvider resolveProvider(String rawProvider) {
    String normalized = rawProvider == null ? "local" : rawProvider.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "local" -> StorageProvider.LOCAL;
      case "azure" -> StorageProvider.AZURE;
      default -> throw new IllegalStateException("Unsupported file storage provider: " + rawProvider);
    };
  }

  private String normalizeObjectKey(String objectKey) throws IOException {
    if (!hasText(objectKey)) {
      throw new IOException("Invalid storage key");
    }
    String normalized = objectKey.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    Path normalizedPath = Paths.get(normalized).normalize();
    if (normalizedPath.isAbsolute() || normalizedPath.startsWith("..")) {
      throw new IOException("Invalid storage key");
    }
    String candidate = normalizedPath.toString().replace('\\', '/');
    if (!hasText(candidate) || ".".equals(candidate)) {
      throw new IOException("Invalid storage key");
    }
    return candidate;
  }

  private String normalizePrefix(String prefix) {
    if (!hasText(prefix)) {
      return "";
    }

    String normalized = prefix.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (!hasText(normalized)) {
      return "";
    }

    Path normalizedPath = Paths.get(normalized).normalize();
    if (normalizedPath.isAbsolute() || normalizedPath.startsWith("..")) {
      throw new IllegalStateException("Invalid AZURE_STORAGE_PREFIX value");
    }
    String candidate = normalizedPath.toString().replace('\\', '/');
    return ".".equals(candidate) ? "" : candidate;
  }

  private String toBlobName(String storedKey) {
    String normalized = storedKey.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalizedAzurePrefix.isBlank()) {
      return normalized;
    }
    if (normalized.equals(normalizedAzurePrefix) || normalized.startsWith(normalizedAzurePrefix + "/")) {
      return normalized;
    }
    return buildBlobName(normalized);
  }

  private String buildBlobName(String objectKey) {
    return normalizedAzurePrefix.isBlank() ? objectKey : normalizedAzurePrefix + "/" + objectKey;
  }

  private String extension(String originalFilename) {
    if (originalFilename == null || !originalFilename.contains(".")) {
      return ".bin";
    }
    String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    if (".pdf".equals(ext)) {
      return ext;
    }
    return ".bin";
  }

  private static boolean hasPdfMagicBytes(MultipartFile file) {
    byte[] expected = "%PDF-".getBytes();
    byte[] header = new byte[expected.length];
    try (BufferedInputStream inputStream = new BufferedInputStream(file.getInputStream())) {
      int read = inputStream.read(header);
      if (read < expected.length) {
        return false;
      }
      return Arrays.equals(header, expected);
    } catch (IOException ex) {
      return false;
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}

package org.eclipse.pass.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;

import edu.wisc.library.ocfl.api.exception.NotFoundException;
import io.findify.s3mock.S3Mock;
import org.eclipse.pass.file.service.storage.FileStorageService;
import org.eclipse.pass.file.service.storage.StorageConfiguration;
import org.eclipse.pass.file.service.storage.StorageFile;
import org.eclipse.pass.file.service.storage.StorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

class FileStorageServiceS3Test {
    StorageConfiguration storageConfiguration;
    private FileStorageService fileStorageService;
    private final StorageProperties properties = new StorageProperties();
    private final String fileSystemType = "S3";
    private final String rootDir = System.getProperty("java.io.tmpdir") + "/pass-s3-test";
    private final int idLength = 25;
    private final String idCharSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final String s3Endpoint = "http://localhost:8001";
    private final String s3Bucket = "bucket-test-name";
    private final String s3Region = "us-east-1";
    private final String s3Prefix = "s3-repo-prefix";
    private S3Mock s3MockApi;

    @BeforeEach
    void setUp() {
        s3MockApi = new S3Mock.Builder().withPort(8001).withInMemoryBackend().build();
        s3MockApi.start();
        properties.setStorageType(fileSystemType);
        properties.setRootDir(rootDir);
        properties.setS3Endpoint(s3Endpoint);
        properties.setS3BucketName(s3Bucket);
        properties.setS3Region(s3Region);
        properties.setS3RepoPrefix(s3Prefix);
        storageConfiguration =  new StorageConfiguration(properties);
        try {
            fileStorageService = new FileStorageService(storageConfiguration);
        } catch (IOException e) {
            assertEquals("Exception during setup", e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        s3MockApi.stop();
        FileSystemUtils.deleteRecursively(Paths.get(rootDir).toFile());
    }

    @Test
    void storeFileToS3ThatExists() {
        try {
            StorageFile storageFile = fileStorageService.storeFile(new MockMultipartFile("test", "test.txt",
                    MediaType.TEXT_PLAIN_VALUE, "Test S3 Pass-core".getBytes()));
            assertFalse(fileStorageService.getResourceFileRelativePath(storageFile.getId()).isEmpty());
        } catch (Exception e) {
            assertEquals("An exception was thrown in storeFileThatExists.", e.getMessage());
        }
    }

    @Test
    void storeFileToS3ThatNotExistsShouldThrowException() {
        Exception exception = assertThrows(IOException.class,
                () -> {
                    fileStorageService.storeFile(new MockMultipartFile("test", "test.txt",
                            MediaType.TEXT_PLAIN_VALUE, "".getBytes()));
                }
        );
        String expectedExceptionText = "File Service: The file system was unable to store the uploaded file";
        String actualExceptionText = exception.getMessage();
        assertTrue(actualExceptionText.contains(expectedExceptionText));
    }

    @Test
    void getFileFromS3ShouldReturnFile() {
        try {
            StorageFile storageFile = fileStorageService.storeFile(new MockMultipartFile("test", "test.txt",
                    MediaType.TEXT_PLAIN_VALUE, "Test S3 Pass-core".getBytes()));
            ByteArrayResource file = fileStorageService.getFile(storageFile.getId());
            assertTrue(file.contentLength() > 0);
        } catch (IOException e) {
            assertEquals("Exception during getFileShouldReturnFile", e.getMessage());
        }
    }

    @Test
    void getFileShouldThrowException() {
        try {
            StorageFile storageFile = fileStorageService.storeFile(new MockMultipartFile("test", "test.txt",
                    MediaType.TEXT_PLAIN_VALUE, "Test S3 Pass-core".getBytes()));

            Exception exception = assertThrows(IOException.class,
                    () -> {
                        ByteArrayResource file = fileStorageService.getFile("12345");
                    }
            );
            String expectedExceptionText = "File Service: The file could not be loaded";
            String actualExceptionText = exception.getMessage();
            assertTrue(actualExceptionText.contains(expectedExceptionText));
        } catch (IOException e) {
            assertEquals("Exception during getFileShouldThrowException", e.getMessage());
        }
    }

    @Test
    void deleteShouldThrowExceptionFileNotExist() {
        try {
            StorageFile storageFile = fileStorageService.storeFile(new MockMultipartFile("test", "test.txt",
                    MediaType.TEXT_PLAIN_VALUE, "Test Pass-core".getBytes()));
            fileStorageService.deleteFile(storageFile.getId());
            Exception exception = assertThrows(NotFoundException.class,
                    () -> {
                        fileStorageService.getResourceFileRelativePath(storageFile.getId());
                    });
            String exceptionText = exception.getMessage();
            assertTrue(exceptionText.matches("(.)+(was not found){1}(.)+"));
        } catch (IOException e) {
            assertEquals("Exception during deleteShouldThrowExceptionFileNotExist", e.getMessage());
        }
    }
}
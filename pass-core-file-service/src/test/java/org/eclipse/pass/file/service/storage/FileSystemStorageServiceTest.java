package org.eclipse.pass.file.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.wisc.library.ocfl.api.exception.NotFoundException;
import liquibase.repackaged.org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

class FileSystemStorageServiceTest {
    @Autowired
    StorageConfiguration storageConfiguration;
    @Autowired
    StorageServiceFactory storageFactory;
    private StorageProperties properties = new StorageProperties();
    private FileSystemStorageService storageService;
    private String fileSystemType = "FILE_SYSTEM";
    private String rootDirUnix = "/pass-core-ocfl-root";
    private String workDirUnix = "/pass-core-ocfl-work";
    private String tempDirUnix = "/pass-core-temp";
    private String rootDirWin = "c:\\pass-core-ocfl-root";
    private String workDirWin = "c:\\pass-core-ocfl-work";
    private String tempDirWin = "c:\\pass-core-temp";

    @BeforeEach
    void setUp() {
        setFileSystemProperties();
        storageService = new FileSystemStorageService(properties);
        storageService.init();
    }

    @AfterEach
    void tearDown() {
        try {
            assertTrue(FileSystemUtils.deleteRecursively(Paths.get(properties.getStorageRootDir())));
        } catch (IOException e) {
            assertEquals("An exception was thrown during cleanup.", e.getMessage());
        }
    }

    @Test
    public void storeFileThatExists() {
        StorageFile storageFile = storageService.store(new MockMultipartFile("test", "test.txt",
                MediaType.TEXT_PLAIN_VALUE, "Test Pass-core".getBytes()));
        assertTrue(!storageService.getResourceFileName(storageFile.getId()).isEmpty());

    }

    @Test
    void storeFileNotExistsShouldThrowException() {
        Exception exception = assertThrows(RuntimeException.class,
                () -> {
                    storageService.store(new MockMultipartFile("test", "test.txt",
                        MediaType.TEXT_PLAIN_VALUE, "".getBytes()));
                }
        );
        String expectedExceptionText = "File Service: The file system was unable to store the uploaded file";
        String actualExceptionText = exception.getMessage();
        assertTrue(actualExceptionText.contains(expectedExceptionText));
    }

    @Test
    void deleteShouldThrowExceptionFileNotExist() {
        StorageFile storageFile = storageService.store(new MockMultipartFile("test", "test.txt",
                MediaType.TEXT_PLAIN_VALUE, "Test Pass-core".getBytes()));
        storageService.delete(storageFile.getId());
        Exception exception = assertThrows(NotFoundException.class,
                () -> {
                    storageService.getResourceFileName(storageFile.getId());
                }
        );
        String exceptionText = exception.getMessage();
        assertTrue(exceptionText.matches("(.)+(was not found){1}(.)+"));
    }

    @Test
    void generateIdShouldBeValidId() {
        String id = storageService.generateId();
        assertTrue(id.matches("(\\w)+"));
        assertEquals(id.length(), 25);
    }

    private String getOsType() {
        Pattern winPattern = Pattern.compile("windows", Pattern.CASE_INSENSITIVE);
        Pattern linuxPattern = Pattern.compile("linux", Pattern.CASE_INSENSITIVE);
        Pattern unixPattern = Pattern.compile("unix", Pattern.CASE_INSENSITIVE);
        Matcher winMatch = winPattern.matcher(SystemUtils.OS_NAME);
        Matcher linuxMatch = linuxPattern.matcher(SystemUtils.OS_NAME);
        Matcher unixMatch = unixPattern.matcher(SystemUtils.OS_NAME);
        if (winMatch.find()) {
            return "WINDOWS";
        }
        if (linuxMatch.find()) {
            return "LINUX";
        }
        if (unixMatch.find()) {
            return "UNIX";
        }
        return "UNKNOWN";
    }

    private void setFileSystemProperties() {
        String osType = getOsType();
        properties.setStorageType(fileSystemType);
        switch (osType) {
            case "WINDOWS":
                properties.setOcflRootDir(rootDirWin);
                properties.setOcflWorkDir(workDirWin);
                properties.setTempDir(tempDirWin);
                break;
            case "LINUX":
            case "UNIX":
                properties.setOcflRootDir(rootDirUnix);
                properties.setOcflWorkDir(workDirUnix);
                properties.setTempDir(tempDirUnix);
                break;
            default:
                throw new RuntimeException("Unable to set system properties for test");
        }
    }
}
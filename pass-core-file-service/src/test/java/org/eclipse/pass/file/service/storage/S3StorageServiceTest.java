package org.eclipse.pass.file.service.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class S3StorageServiceTest {
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
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void storeFileThatExists() {
        /*StorageFile storageFile = storageService.store(new MockMultipartFile("test", "test.txt",
                MediaType.TEXT_PLAIN_VALUE, "Test Pass-core".getBytes()));
        assertTrue(!storageService.getResourceFileName(storageFile.getId()).isEmpty());*/

    }
}
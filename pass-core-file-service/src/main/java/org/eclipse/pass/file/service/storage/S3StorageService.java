/*
 *
 * Copyright 2019 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.file.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Optional;

import edu.wisc.library.ocfl.api.OcflRepository;
import edu.wisc.library.ocfl.api.model.ObjectVersionId;
import edu.wisc.library.ocfl.api.model.VersionInfo;
import edu.wisc.library.ocfl.aws.OcflS3Client;
import edu.wisc.library.ocfl.core.OcflRepositoryBuilder;
import edu.wisc.library.ocfl.core.extension.storage.layout.config.HashedNTupleLayoutConfig;
import edu.wisc.library.ocfl.core.path.constraint.ContentPathConstraints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

//TODO implement
public class S3StorageService implements StorageService {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemStorageService.class);
    private final StorageProperties storageProperties;
    private final Path workLoc;
    private final Path tempLoc;
    private final int idLength = 25;
    private final String idCharSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final String storageType = "S3";
    private OcflRepository ocflRepository;
    private S3Client cloudS3Client;

    //TODO move to env and get from storage properties
    private String bucketName = "";
    private String repoPrefix = "";
    private Region region = Region.US_EAST_1;

    public S3StorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.workLoc = Paths.get(storageProperties.getStorageWorkDir());
        this.tempLoc = Paths.get(storageProperties.getStorageTempDir());
    }

    @Override
    public void init() {
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        S3Client cloudS3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        ocflRepository = new OcflRepositoryBuilder()
                .defaultLayoutConfig(new HashedNTupleLayoutConfig())
                .contentPathConstraints(ContentPathConstraints.cloud())
                .storage(storage -> storage
                        .cloud(OcflS3Client.builder()
                                .s3Client(cloudS3Client)
                                .bucket(bucketName)
                                .repoPrefix(repoPrefix)
                                .build()))
                .workDir(workLoc)
                .build();
    }

    @Override
    public StorageFile store(MultipartFile mFile) {
        //TODO implement

        try {
            if (mFile.isEmpty() || mFile == null) {
                throw new FileSystemException("File Service: Files is empty or missing.");
            }
            String origFileName = mFile.getOriginalFilename();
            String fileId = generateId(); //Not sure if needed. S3 might create names from IDs
            String mimeType = URLConnection.guessContentTypeFromName(origFileName);
            Path tempFileDir = this.tempLoc.resolve(Paths.get(origFileName))
                    .normalize()
                    .toAbsolutePath();
            //copy uploaded file to temp dir before ocfl persistence
            try (InputStream inputstream = mFile.getInputStream()) {
                Files.copy(inputstream, tempFileDir, StandardCopyOption.REPLACE_EXISTING);
            }

            //persist file using ocfl
            ocflRepository.putObject(ObjectVersionId.head(fileId), tempFileDir,
                    new VersionInfo().setMessage("Pass-Core File Service: Initial commit"));
            LOG.info("File Service: File with ID " + fileId + " was stored in the repo");
            //create pass-core File object to return
            StorageFile storageFile = new StorageFile(
                    fileId,
                    origFileName,
                    mimeType,
                    storageType,
                    mFile.getSize(),
                    getExtensionFromFile(origFileName).get()
            );

            //clean up temp directory
            if (!FileSystemUtils.deleteRecursively(tempFileDir)) {
                LOG.info("File Service: No files to cleanup on file upload");
            }

        } catch (IOException e) {
            LOG.error(e.toString());
            throw new RuntimeException("File Service: Unable to persist file to S3", e);
        }
        return null;
    }

    @Override
    public ByteArrayResource loadAsResource(String fileId) {
        //TODO implement
        return null;
    }

    @Override
    public Boolean resourceExists(String fileId, String rootDir) {
        //TODO implement
        return null;
    }

    @Override
    public void delete(String fileId) {
        //TODO implement
    }

    @Override
    public String generateId() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder(idLength);
        for (int i = 0; i < idLength; i++) {
            sb.append(idCharSet.charAt(secureRandom.nextInt(idCharSet.length())));
        }
        return sb.toString();
    }

    @Override
    public String getResourceFileName(String fileId) {
        //TODO implement
        return null;
    }

    @Override
    public Optional<String> getExtensionFromFile(String fn) {
        return Optional.ofNullable(fn)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fn.lastIndexOf(".") + 1));
    }
}

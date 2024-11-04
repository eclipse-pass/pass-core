/*
 *
 * Copyright 2023 Johns Hopkins University
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.ocfl.api.OcflRepository;
import io.ocfl.aws.OcflS3Client;
import io.ocfl.core.OcflRepositoryBuilder;
import io.ocfl.core.extension.storage.layout.config.HashedNTupleLayoutConfig;
import io.ocfl.core.path.constraint.ContentPathConstraints;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * The StorageConfiguration is responsible for handling the StorageProperties. The FileStorageService does not get the
 * storage configuration directly but through the StorageConfiguration.
 *
 * @author Tim Sanders
 * @see StorageProperties
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(StorageConfiguration.class);

    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    @ConditionalOnProperty(name = "pass.file-service.storage-type", havingValue = "S3")
    public S3TransferManager s3TransferManager() {
        S3AsyncClient s3AsyncClient = S3AsyncClient.crtBuilder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .region(Region.of(awsRegion))
            .build();

        return S3TransferManager.builder()
            .s3Client(s3AsyncClient)
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "pass.file-service.storage-type", havingValue = "S3")
    public S3AsyncClient s3Client(StorageProperties storageProperties) throws IOException {
        String bucketName = storageProperties.getBucketName().
            orElseThrow(() -> new IOException("File Service: S3 bucket name is not set"));

        S3AsyncClientBuilder builder = S3AsyncClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .region(Region.of(awsRegion));

        String endpoint = storageProperties.getS3Endpoint().orElse(null);
        S3AsyncClient s3Client = StringUtils.isNotBlank(endpoint)
            ? builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true).build()
            : builder.build();

        if (s3Client.listBuckets().join().buckets().stream().noneMatch(b -> b.name().equals(bucketName))) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
        return s3Client;
    }

    @Bean
    @ConditionalOnProperty(name = "pass.file-service.storage-type", havingValue = "S3")
    public OcflRepository ocflS3Repository(S3AsyncClient s3Client, S3TransferManager s3TransferManager,
                                           StorageProperties storageProperties,
                                           @Qualifier("rootPath") Path rootLoc) throws IOException {
        String bucketName = storageProperties.getBucketName().
            orElseThrow(() -> new IOException("File Service: S3 bucket name is not set"));
        String repoPrefix = storageProperties.getS3RepoPrefix().orElse(null);

        OcflS3Client.Builder builder = OcflS3Client.builder()
            .s3Client(s3Client)
            .transferManager(s3TransferManager)
            .bucket(bucketName);
        OcflS3Client ocflS3Client = StringUtils.isNotBlank(repoPrefix)
            ? builder.repoPrefix(repoPrefix).build()
            : builder.build();

        Path workLoc = ocflWorkingDir(storageProperties, rootLoc);
        OcflRepository ocflRepository = new OcflRepositoryBuilder()
            .defaultLayoutConfig(new HashedNTupleLayoutConfig())
            .contentPathConstraints(ContentPathConstraints.cloud())
            .storage(storage -> storage.cloud(ocflS3Client))
            .workDir(workLoc)
            .build();
        LOG.info("File Service: S3 OCFL is configured and OCFL repository is built");
        return ocflRepository;
    }

    @Bean
    @ConditionalOnProperty(name = "pass.file-service.storage-type", havingValue = "FILE_SYSTEM")
    public OcflRepository ocflFileRepository(StorageProperties storageProperties,
                                             @Qualifier("rootPath") Path rootLoc) throws IOException {
        Path ocflLoc = Paths.get(rootLoc.toString(), storageProperties.getStorageOcflDir());
        if (!Files.exists(ocflLoc)) {
            Files.createDirectory(ocflLoc);
        }
        if (!Files.isReadable(ocflLoc) || !Files.isWritable(ocflLoc)) {
            throw new IOException("File Service: No permission to read/write OCFL directory.");
        }
        Path workLoc = ocflWorkingDir(storageProperties, rootLoc);
        OcflRepository ocflRepository = new OcflRepositoryBuilder()
            .defaultLayoutConfig(new HashedNTupleLayoutConfig())
            .storage(storage -> storage.fileSystem(ocflLoc))
            .workDir(workLoc)
            .build();
        LOG.info("File Service: File Service OCFL is configured and OCFL repository is built");
        return ocflRepository;
    }

    @Bean
    @Qualifier("rootPath")
    public Path rootPath(StorageProperties storageProperties) throws IOException {
        Path rootLoc;
        if (StringUtils.isBlank(storageProperties.getStorageRootDir())) {
            //when a storage root is not specified, then it should be: system_temp/create_temp_dir
            rootLoc = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")),null);
            //set the rootLoc in the storageProperties
            storageProperties.setRootDir(rootLoc.toString().
                substring(rootLoc.toString().lastIndexOf(File.separator) + 1));
        } else {
            rootLoc = Paths.get(storageProperties.getStorageRootDir());
        }
        LOG.info("File Service: " + rootLoc + " Storage Root Directory");
        return rootLoc;
    }

    private Path ocflWorkingDir(StorageProperties storageProperties, Path rootLoc) throws IOException {
        Path workLoc = Paths.get(rootLoc.toString(), storageProperties.getStorageWorkDir());
        try {
            if (!Files.exists(rootLoc)) {
                Files.createDirectory(rootLoc);
            }
            if (!Files.exists(workLoc)) {
                Files.createDirectory(workLoc);
            }
            if (!Files.isReadable(workLoc) || !Files.isWritable(workLoc)) {
                throw new IOException("File Service: No permission to read/write work directory.");
            }
            if (!Files.isReadable(rootLoc) || !Files.isWritable(rootLoc)) {
                throw new IOException("File Service: No permission to read/write File Service root directory.");
            }
        } catch (IOException e) {
            throw new IOException("File Service: Unable to setup File Storage directories: " + e);
        }
        return workLoc;
    }

}

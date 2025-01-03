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
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import io.ocfl.api.OcflRepository;
import io.ocfl.api.exception.NotFoundException;
import io.ocfl.api.model.FileDetails;
import io.ocfl.api.model.ObjectVersionId;
import io.ocfl.api.model.User;
import io.ocfl.api.model.VersionDetails;
import io.ocfl.api.model.VersionInfo;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * The FileStorageService is responsible for the implementation of the persistence of files to their respective
 * storage or repository. The types of storage are defined in the
 * {@link org.eclipse.pass.file.service.storage.StorageServiceType StorageServiceType} enum.
 * The FileStorageService depends on a properly configured repository. The environment variables are externalized
 * in the .ENV file. The FileStorageService is lazily loaded to ensure that the configuration is properly loaded and
 * to minimize the startup time. The FileStorageService currently supports File System and S3 storage.
 * A configuration of File System requires that the environment variables are properly set in the env file and
 * the respective directories have read/write access. For a S3 configuration to work the client needs the
 * following permissions: s3:PutObject, s3:GetObject,s3:DeleteObject, s3:ListBucket, s3:AbortMultipartUpload.
 * The directory structure for the File System is as follows:
 *  - rootDir: This is the root directory for the File System. This is set in the
 *      .env file (PASS_CORE_FILE_SERVICE_ROOT_DIR). If it is not set then the default is the system temp directory.
 *  - ocflDir: This is the directory where files are stored in the OCFL repository. This is a child of the rootDir.
 *  - workDir: This is a temporary working directory that is required by the OcflRepositoryBuilder. This is a child of
 *       the rootDir. Both the ocflDir and workDir are required to be on the same mount.
 *  - tempDir: This is a temporary directory that is used to move files to/from the OCFL repository and staging them
 *      for download. This is a child of the rootDir.
 * Note, the S3 OCFL implementation does not cache locally and therefore performs much slower compared to the file
 * system implementation, most notably on large files.
 *
 * @author Tim Sanders
 * @see StorageServiceType
 */
@Lazy
@Service
public class FileStorageService {
    private static final Logger LOG = LoggerFactory.getLogger(FileStorageService.class);

    private final OcflRepository ocflRepository;
    private final Path tempLoc;
    private final StorageServiceType storageType;

    /**
     *  FileStorageService Class constructor.
     *
     * @param ocflRepository ocfl object that is a layer to handle the io of the files
     * @param storageProperties properties indicating where and what type of storage is used for persistence.
     * @param rootLoc path of the root location used to set up temp working directory for the File Service
     */
    public FileStorageService(OcflRepository ocflRepository,
                              StorageProperties storageProperties,
                              Path rootLoc) {
        this.ocflRepository = ocflRepository;
        this.tempLoc = Paths.get(rootLoc.toString(), storageProperties.getStorageTempDir());
        this.storageType = storageProperties.getStorageType();
    }

    /**
     * Persists a file to the repository/storage indicated in the StorageProperties.
     *
     * @param mFile A MultiPart file that is to be persisted into storage or repository.
     * @param userName The username of the user that is uploading the file.
     * @return StorageFile representation of the file that was persisted. It contains meta information about the file
     * for example the name, file size and mime type.
     * @throws IOException If a file is empty or missing, paths are incorrect, or the appropriate permissions
     * are not configured on the repository an IOException will be thrown.
     *
     * @see StorageFile
     */
    public StorageFile storeFile(MultipartFile mFile, String userName) throws IOException {
        StorageFile storageFile;
        //NOTE: the work directory on the ocfl-java client should be located on the same mount as the OCFL storage root.
        try {
            //remove any unsafe characters from the original file name and the hyphen, since it is used as a delimiter
            String origFileNameExt = Jsoup.clean(Objects.requireNonNull(mFile.getOriginalFilename()), Safelist.basic());
            String fileExt = FilenameUtils.getExtension(origFileNameExt);
            String fileUuid = UUID.randomUUID().toString();
            String fileId = fileUuid + "/" + origFileNameExt;
            String mimeType = URLConnection.guessContentTypeFromName(origFileNameExt);
            //changing the stored file name to UUID to prevent any issues with long file names
            //e.g. 260 char limit on the path in Windows. Original filename is preserved in the fileId.
            String ocflRepoFileName = StringUtils.isNotEmpty(fileExt) ? fileUuid + "." + fileExt : fileUuid;
            Path pathTempLoc = Paths.get(tempLoc.toString());

            //Create OCFL user to identify the owner of the file
            User fileUser = new User();
            fileUser.setName(userName);

            if (!Files.exists(pathTempLoc)) {
                Files.createDirectory(pathTempLoc);
            }
            Path tempPathAndFileName = Paths.get(tempLoc.toString(), ocflRepoFileName);
            mFile.transferTo(tempPathAndFileName);
            ocflRepository.putObject(ObjectVersionId.head(fileId), tempPathAndFileName,
                new VersionInfo().setMessage("Pass-Core File Service: Initial commit").setUser(fileUser));
            String fileRepoRelPath = ocflRepository.describeVersion(ObjectVersionId.head(fileId))
                .getFileMap().entrySet().iterator().next().getValue().getStorageRelativePath();
            LOG.info("File Service: File with ID " + fileId + " was stored in the system repo at location: " +
                "location:" + fileRepoRelPath);

            storageFile = new StorageFile(
                    fileId,
                    fileUuid,
                    origFileNameExt,
                    mimeType,
                    storageType.label,
                    mFile.getSize(),
                    fileExt
            );

            Files.delete(tempPathAndFileName);
        } catch (IOException e) {
            LOG.error("Error storing file", e);
            throw new IOException("File Service: The file system was unable to store the uploaded file", e);
        }
        return storageFile;
    }

    /**
     * Gets the file (bytes) of the supplied fileId.
     *
     * @param fileId The fileId of the file to be returned.
     * @return Returns a file as a ByteArrayResource
     * @throws IOException If a file does not exist or the appropriate read/write permissions are not correct an
     * IOException will be thrown.
     */
    public ByteArrayResource getFile(String fileId) throws IOException {
        ByteArrayResource loadedResource;
        Path tempLoadDir = Paths.get(this.tempLoc.toString(),"output", fileId.split("/")[0],
                Instant.now().toString().replace(":","-").replace(".","-"));
        Path tempLoadParentDir = Paths.get(this.tempLoc.toString(),"output", fileId.split("/")[0]);
        try {
            //need the parent directory for the OCFL getObject to work
            if (!Files.exists(tempLoadParentDir)) {
                Files.createDirectories(tempLoadParentDir);
            }
            // the output path for getObject must not exist, hence temp dir is created on the fly
            ocflRepository.getObject(ObjectVersionId.head(fileId), tempLoadDir);
            String loggingFieldId = fileId.replaceAll("[\n\r]", " ");
            LOG.debug("File Service: File with ID {} was loaded from the repo", loggingFieldId);
            Path fileNamePath = Objects.requireNonNull(tempLoadDir.toFile().listFiles())[0].toPath();
            loadedResource = new ByteArrayResource(Files.readAllBytes(fileNamePath));

        } catch (NotFoundException e) {
            throw new IOException("File Service: The file could not be loaded, file ID: " + fileId + " " + e);
        }

        if (loadedResource.exists() && loadedResource.isReadable()) {
            //clean up temp directory
            if (!FileSystemUtils.deleteRecursively(Paths.get(this.tempLoc.toString()))) {
                LOG.debug("File Service: No files to cleanup on file get");
            }
            return loadedResource;
        } else {
            throw new IOException("File Service: Unable to return the file. Verify read/write " +
                    "permissions of the temp directory.");
        }
    }

    /**
     * Deletes a file in storage or repository that is defined in the configuration
     * @param fileId The fileId of the file to be deleted
     */
    public void deleteFile(String fileId) {
        ocflRepository.purgeObject(fileId);
    }

    /**
     * Gets the relative path in the OCFL repository from the fileID supplied. It will return the most recent version
     * file path. When using S3, this will provide the path of the file in the S3 bucket.
     * @param fileId The fileId of the file path to be returned.
     * @return The relative path of the file.
     * @throws IOException If unable to get the relative path for a given fileId
     */
    public String getResourceFileRelativePath(String fileId) throws IOException {
        VersionDetails versionDetails = ocflRepository.describeVersion(ObjectVersionId.head(fileId));
        Collection<FileDetails> allVersionFiles = versionDetails.getFiles();
        return allVersionFiles.stream().findFirst()
                .orElseThrow(() -> new IOException("The relative path could not be found for file ID: " + fileId))
                .getStorageRelativePath();
    }

    /**
     * Gets the content type of the file from the fileID supplied. It will return the most recent version
     * of the file. When using S3, this will provide the content type of the file in the S3 bucket.
     *
     * @param fileId The fileId of the content type of the file to be returned.
     * @return The content type of the file.
     */
    public String getFileContentType(String fileId) {
        try {
            VersionDetails versionDetails = ocflRepository.describeVersion(ObjectVersionId.head(fileId));
            FileDetails fileDetails = versionDetails.getFiles().stream().findFirst()
                    .orElseThrow(() -> new IOException("The content type could not be found for file ID: " + fileId));
            Path fileDetailPath = Paths.get(fileDetails.getPath());
            File file = fileDetailPath.toFile();

            String type = Files.probeContentType(file.toPath());
            if (type == null) {
                type = MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
            }

            return type;
        } catch (IOException e) {
            LOG.error("File Service: Unable to determine the content type of the file with ID: " + fileId, e);
            return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    /**
     * Checks the users permissions to delete a file. The user must be the same user that uploaded the file.
     * @param fileId The fileId of the file to be deleted
     * @param userId The userId of the user requesting to delete the file
     * @return Returns true if the user has permissions to delete the file, false if not.
     */
    public boolean checkUserDeletePermissions(String fileId, String userId) {
        return userId.equals(getFileOwner(fileId));
    }

    /**
     * Get the owner of the file from the fileID supplied. It will look at the most recent version of the file to
     * obtain the owner.
     *
     * @param fileId The fileId of the file.
     * @return The owner of the file.
     */
    public String getFileOwner(String fileId) {
        VersionInfo versionInfo = ocflRepository.describeVersion(ObjectVersionId.head(fileId)).getVersionInfo();
        return versionInfo.getUser().getName();
    }
}


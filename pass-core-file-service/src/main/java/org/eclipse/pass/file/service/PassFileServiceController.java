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
package org.eclipse.pass.file.service;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.file.service.storage.FileStorageService;
import org.eclipse.pass.file.service.storage.StorageFile;
import org.eclipse.pass.object.security.WebSecurityRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PassFileServiceController is the controller class responsible for the File Service endpoints, which allows pass-core
 * internal and external services to upload, retrieve and delete files. Configuration of the File Service is done
 * through .env file and is loaded into the StorageProperties.
 *
 * @author Tim Sanders
 */
@RestController
public class PassFileServiceController {
    private static final Logger LOG = LoggerFactory.getLogger(PassFileServiceController.class);

    private final FileStorageService fileStorageService;

    /**
     *   Class constructor.
     *   @param fileStorageService the FileStorageService
     */
    public PassFileServiceController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Handles a file upload and will call the FileStorageService to determine the repository where the file is to be
     * deposited.
     *
     * @param file A multipart file that is uploaded from the client.
     * @param principal The user that is uploading the file.
     * @return return a File object that has been uploaded.
     */
    @PostMapping("/file")
    public ResponseEntity<?> fileUpload(@RequestParam("file") MultipartFile file, Principal principal) {
        StorageFile returnStorageFile;
        try {
            if (file.getBytes().length == 0 || file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
        } catch (IOException e) {
            LOG.error("File Service: Error processing file upload: " + e);
            return ResponseEntity.badRequest().build();
        }

        try {
            returnStorageFile = fileStorageService.storeFile(file, principal.getName());
        } catch (IOException e) {
            LOG.error("File Service: Error storing file upload: " + e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.created(URI.create(returnStorageFile.getUuid())).body(returnStorageFile);
    }

    /**
     * Gets a file by the fileId and returns a single file. Implicitly supports HTTP HEAD.
     *
     * @param uuid of the file to return (required), is one part of the fileId
     * @param origFileName of the file to return (required), is one part of the fileId
     * @return Bitstream The file requested by the fileId
     */
    @GetMapping("/file/{uuid:.+}/{origFileName:.+}")
    @ResponseBody
    public ResponseEntity<?> getFileById(@PathVariable("uuid") String uuid,
                                         @PathVariable("origFileName") String origFileName) {
        String fileId = uuid  + "/" + origFileName;
        if (StringUtils.isEmpty(uuid) || StringUtils.isEmpty(origFileName)) {
            LOG.error("File ID not provided to get a file.");
            return ResponseEntity.badRequest().body("File ID not provided to get a file.");
        }
        ByteArrayResource fileResource;
        String contentType = "";

        try {
            contentType = fileStorageService.getFileContentType(fileId);
            fileResource = fileStorageService.getFile(fileId);
        } catch (Exception e) {
            LOG.error("File Service: File not found: " + e);
            return ResponseEntity.notFound().build();
        }

        String headerAttachment = "attachment; filename=\"" + origFileName + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, headerAttachment)
                .contentLength(fileResource.contentLength())
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileResource);
    }

    /**
     * Deletes a file by the provided file ID
     *
     * @param uuid ID of the file to delete (required), is one part of the fileId
     * @param origFileName ID of the file to delete (required), is one part of the fileId
     * @param principal the user making the request
     * @param request the request
     * @return File
     */
    @DeleteMapping("/file/{uuid:.+}/{origFileName:.+}")
    public ResponseEntity<?> deleteFileById(@PathVariable("uuid") String uuid,
                                            @PathVariable("origFileName") String origFileName,
                                            Principal principal, HttpServletRequest request) {
        String principalName = principal.getName();
        String fileId = uuid  + "/" + origFileName;

        //Get the file, check that it exists, and then check if current user has permissions to delete
        try {
            fileStorageService.getFile(fileId);
        } catch (Exception e) {
            LOG.error("File Service: File not found: " + e);
            return ResponseEntity.notFound().build();
        }

        return canUserDeleteFile(principalName, fileId, request)
            ? deleteFile(fileId)
            : ResponseEntity.status(HttpStatus.FORBIDDEN).body("User does not have permission to delete this file.");
    }

    private boolean canUserDeleteFile(String principalName, String fileId, HttpServletRequest request) {
        try {
            boolean hasDeletePermission = fileStorageService.checkUserDeletePermissions(fileId, principalName);
            return hasDeletePermission || request.isUserInRole(WebSecurityRole.BACKEND.getValue());
        } catch (Exception e) {
            LOG.error("File Service: Unable to determine user permissions to delete file: " + e);
            return false;
        }
    }

    private ResponseEntity<?> deleteFile(String fileId) {
        try {
            fileStorageService.deleteFile(fileId);
            return ResponseEntity.ok().body("Deleted");
        } catch (Exception e) {
            LOG.error("File Service: Unable to delete file: " + e);
            return ResponseEntity.badRequest().build();
        }
    }
}
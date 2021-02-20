package com.program.analysis.app;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.springframework.web.multipart.MultipartFile;

public class ZipFileStorageManager {
    private String DEFAULT_ZIP_PATH;
    private String DEFAULT_UPZIP_PATH;
    
    public ZipFileStorageManager() {
    }

    public ZipFileStorageManager initDefaultFolders() {
        File unzipDirectory = new File("src/main/zip");
        DEFAULT_ZIP_PATH = unzipDirectory.getAbsolutePath();

        File zipDirectory = new File("src/main/unzip");
        DEFAULT_UPZIP_PATH = zipDirectory.getAbsolutePath();

        // Create and clean the new folders
        createSourceFolder();
        createDestinationFolder();
        cleanSourceFolder();
        cleanDestinationFolder();

        return this;
    }

    public void setPaths(String zipPath, String unzipPath, boolean deleteBeforeSwitch, boolean createAfterSwitch) throws IOException {
        // Delete before switch folders
        if (deleteBeforeSwitch) {
            deleteSourceFolder();
            deleteDestinationFolder();
        }

        File unzipDirectory = new File(zipPath);
        DEFAULT_ZIP_PATH = unzipDirectory.getAbsolutePath();

        File zipDirectory = new File(unzipPath);
        DEFAULT_UPZIP_PATH = zipDirectory.getAbsolutePath();

        // Create the new folders
        if (createAfterSwitch) {
            createSourceFolder();
            createDestinationFolder();
        }
    }

    public void cleanSourceFolder() {
        if (DEFAULT_ZIP_PATH != null) {
            File zipDirectory = new File(DEFAULT_ZIP_PATH);
            if (zipDirectory.exists()) {
                try {
                    FileUtils.cleanDirectory(zipDirectory);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void cleanDestinationFolder() {
        if (DEFAULT_UPZIP_PATH != null) {
            File unzipDirectory = new File(DEFAULT_UPZIP_PATH);
            if (unzipDirectory.exists()) {
                try {
                    FileUtils.cleanDirectory(unzipDirectory);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Delete the zip folder if it exists
    public void deleteSourceFolder() throws IOException {
        if (DEFAULT_ZIP_PATH != null) {
            File zipDirectory = new File(DEFAULT_ZIP_PATH);
            if (zipDirectory.exists()) {
                FileUtils.deleteDirectory(zipDirectory);
            }
        }
    }

    // Delete the unzip folder if it exists
    public void deleteDestinationFolder() throws IOException {
        if (DEFAULT_UPZIP_PATH != null) {
            File unzipDirectory = new File(DEFAULT_UPZIP_PATH);
            if (unzipDirectory.exists()) {
                FileUtils.deleteDirectory(unzipDirectory);
            }
        }
    }

    // Create the zip folder if it does not exist
    public void createSourceFolder() {
        if (DEFAULT_ZIP_PATH != null) {
            File zipDirectory = new File(DEFAULT_ZIP_PATH);
            if (!zipDirectory.exists()) {
                zipDirectory.mkdirs();
            }
        }
    }

    // Create the unzip folder if it does not exist
    public void createDestinationFolder() {
        if (DEFAULT_UPZIP_PATH != null) {
            File unzipDirectory = new File(DEFAULT_UPZIP_PATH);
            if (!unzipDirectory.exists()) {
                unzipDirectory.mkdirs();
            }
        }
    }

    public void saveFile(MultipartFile multipartFile, boolean cleanBeforeSave) throws IOException {
        if (cleanBeforeSave) {
            cleanSourceFolder();
        }
        File file = new File(DEFAULT_ZIP_PATH + "/" + multipartFile.getOriginalFilename());
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(multipartFile.getBytes());
        }
    }

    /*
        This method unzip a .zip file and returns a list of files. The files from .zip will be extracted and
        store at default location: "backend/src/main/java/unzip".
     */
    public File[] unzipFile(String fileName, boolean cleanBeforeSave) throws IOException {
        if (cleanBeforeSave) {
            cleanDestinationFolder();
        }
        try (ZipFile file = new ZipFile(DEFAULT_ZIP_PATH + "/" + fileName)) {
            Enumeration<? extends ZipEntry> zipEntries = file.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                File newFile = new File(DEFAULT_UPZIP_PATH, zipEntry.getName());
                newFile.getParentFile().mkdirs();
                if (!zipEntry.isDirectory()) {
                    try (FileOutputStream outputStream = new FileOutputStream(newFile)) {
                        BufferedInputStream inputStream = new BufferedInputStream(file.getInputStream(zipEntry));
                        while (inputStream.available() > 0) {
                            outputStream.write(inputStream.read());
                        }
                        inputStream.close();
                    }
                }
            }
            File newfile = new File(DEFAULT_UPZIP_PATH);
            return newfile.listFiles();
        } catch (IOException e) {
            throw e;
        }
    }
}

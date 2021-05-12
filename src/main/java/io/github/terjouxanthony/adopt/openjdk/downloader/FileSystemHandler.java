package io.github.terjouxanthony.adopt.openjdk.downloader;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileSystemHandler {

    public void move(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    /**
     * !Warning! enclose the return Stream in a try-with-resources to close the associated operating system resources.
     *
     * @param folder folder to list.
     * @return a Stream of files/folders in this folder.
     */
    public Stream<Path> listFolderAsStream(Path folder) throws IOException {
        return Files.list(folder);
    }

    public OutputStream outputStream(Path path) throws IOException {
        return Files.newOutputStream(path);
    }

    public InputStream inputStream(Path path) throws IOException {
        return Files.newInputStream(path);
    }

    public boolean fileOrFolderExists(Path path) {
        return Files.exists(path);
    }

    public void mkdir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    public List<Path> listFolder(Path folder) throws IOException {
        try (Stream<Path> fileStream = Files.list(folder)) {
            return fileStream.collect(Collectors.toList());
        }
    }

    public void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void deleteRecursively(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!file.toFile().setWritable(true)) {
                        throw new IOException("Impossible to set writable file " + file);
                    }
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}

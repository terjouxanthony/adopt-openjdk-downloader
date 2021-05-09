package com.atrjx.adopt.openjdk.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

@Slf4j
public class Utils {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static Response httpGet(String path,
                                   Map<String, String> queryParams,
                                   Headers headers) throws IOException, HttpStatusException {
        final OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.MINUTES)
                .build();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(path).newBuilder();
        queryParams.forEach(urlBuilder::addQueryParameter);
        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .get()
                .url(url)
                .headers(headers)
                .build();

        Call call = httpClient.newCall(request);

        final long start = System.nanoTime();
        Response response = call.execute();
        final long end = System.nanoTime();
        log.debug("Request {} took {} ms", url, Duration.ofNanos(end - start).toMillis());

        if (response.isSuccessful()) {
            return response;
        }

        final String bodyStr = response.body().string();
        throw new HttpStatusException(response.code(), response.request().url().toString(), bodyStr);
    }

    @Getter
    public static class HttpStatusException extends Exception {
        private final int statusCode;
        private final String request;
        private final String body;

        public <T> HttpStatusException(int statusCode, String request, String body) {
            super(String.format("Error %d for Http request %s : %s", statusCode, request, body));
            this.statusCode = statusCode;
            this.request = request;
            this.body = body;
        }
    }

    public static long transferTo(InputStream in, OutputStream out, int bufferSize, LongConsumer nbBytesReadConsumer) throws IOException {
        long transferred = 0;
        byte[] buffer = new byte[bufferSize];
        int read;
        while ((read = in.read(buffer, 0, bufferSize)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
            nbBytesReadConsumer.accept(read);
        }
        return transferred;
    }

    public static void decompressTarGzipFile(Path source, Path target) throws IOException {

        if (Files.notExists(source)) {
            throw new IOException("File doesn't exists!");
        }

        try (InputStream fi = Files.newInputStream(source);
             BufferedInputStream bi = new BufferedInputStream(fi);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

            ArchiveEntry entry;
            while ((entry = ti.getNextEntry()) != null) {
                // create a new path, zip slip validate
                Path newPath = zipSlipProtect(entry, target);
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    // check parent folder again
                    Path parent = newPath.getParent();
                    if (parent != null) {
                        if (Files.notExists(parent)) {
                            Files.createDirectories(parent);
                        }
                    }
                    // copy TarArchiveInputStream to Path newPath
                    Files.copy(ti, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path zipSlipProtect(ArchiveEntry entry, Path targetDir)
            throws IOException {

        Path targetDirResolved = targetDir.resolve(entry.getName());
        // make sure normalized file still has targetDir as its prefix,
        // else throws exception
        Path normalizePath = targetDirResolved.normalize();
        if (!normalizePath.startsWith(targetDir)) {
            throw new IOException("Bad entry: " + entry.getName());
        }
        return normalizePath;
    }

    public static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void deleteRecursively(Path path) {
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

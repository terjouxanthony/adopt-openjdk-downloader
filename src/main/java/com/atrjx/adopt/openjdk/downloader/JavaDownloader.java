package com.atrjx.adopt.openjdk.downloader;

import com.atrjx.adopt.openjdk.downloader.Model.ImageType;
import com.atrjx.adopt.openjdk.downloader.Model.Nullable;
import com.atrjx.adopt.openjdk.downloader.Model.ReleaseInfo;
import com.atrjx.adopt.openjdk.downloader.Utils.HttpStatusException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import okhttp3.Headers;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atrjx.adopt.openjdk.downloader.AdoptOpenJdkApi.getJavaReleaseInfo;
import static com.atrjx.adopt.openjdk.downloader.AdoptOpenJdkApi.getLatestJavaRelaseInfo;
import static com.atrjx.adopt.openjdk.downloader.Utils.deleteFile;
import static com.atrjx.adopt.openjdk.downloader.Utils.deleteRecursively;
import static com.atrjx.adopt.openjdk.downloader.Utils.httpGet;
import static com.atrjx.adopt.openjdk.downloader.Utils.transferTo;
import static java.util.Objects.requireNonNull;

@Slf4j
public class JavaDownloader {
    private static final Pattern JDK_RELEASE_NAME_REGEX = Pattern.compile("^\\D+(\\d+)");
    private static final String SEPARATOR_IN_FILENAMES = "--";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssz");

    @Data
    public static class JavaInstallDescription {
        private final Path installPath;
        private final Path jdkHomePath;
    }

    public static JavaInstallDescription installJava(String arch,
                                                     String os,
                                                     @Nullable Integer javaVersion,
                                                     @Nullable String fullJavaReleaseName,
                                                     boolean downloadLatest,
                                                     boolean cleanExistingSameMajorVersion,
                                                     ImageType imageType
    ) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {

        final Path installPath = installJava(arch, os, javaVersion, fullJavaReleaseName, downloadLatest, imageType);
        if (cleanExistingSameMajorVersion) {
            log.info("Flag 'cleanExistingSameMajorVersion' enabled, cleaning {} folders other than {} ...", imageType, installPath);
            listFolder(installPath.getParent())
                    .stream()
                    .filter(path -> !path.equals(installPath))
                    .forEach(path -> {
                        log.info("Deleting other {} {} ...", imageType, path);
                        deleteRecursively(path);
                    });
            log.info("{} folders Cleaning done", imageType);
        }

        return new JavaInstallDescription(installPath, findJavaHomeFolder(installPath, os).get());
    }

    private static Path installJava(String arch,
                                    String os,
                                    @Nullable Integer javaVersion,
                                    @Nullable String fullJavaReleaseName,
                                    boolean downloadLatest, ImageType imageType
    ) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {

        if (javaVersion == null && fullJavaReleaseName == null) {
            throw new IllegalArgumentException("Either java version (eg. 16) or full java release name (eg. 16.0.1+9) must be provided");
        }

        if (fullJavaReleaseName != null) {
            final Matcher matcher = JDK_RELEASE_NAME_REGEX.matcher(fullJavaReleaseName);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Invalid fullJavaReleaseName, examples: jdk-16.0.1+9, jdk8u292-b10");
            }
            javaVersion = Integer.parseInt(matcher.group(1));
        }

        final String homeFolderPath = System.getProperty("user.home");
        final Path installRootFolder = Paths.get(homeFolderPath).resolve(".m2").resolve("java").resolve(imageType.getValue());
        Files.createDirectories(installRootFolder);
        final Path installParentFolder = installRootFolder.resolve(String.valueOf(javaVersion)).resolve(osArchString(os, arch));

        Optional<Path> installPathOpt = tryFindJavaLocally(arch, os, javaVersion, fullJavaReleaseName, downloadLatest, installParentFolder, imageType);
        if (installPathOpt.isPresent()) {
            return installPathOpt.get();
        }

        final ReleaseInfo releaseInfo = (fullJavaReleaseName != null) ?
                getJavaReleaseInfo(fullJavaReleaseName, arch, os, imageType) :
                getLatestJavaRelaseInfo(javaVersion, arch, os, imageType);

        log.info("Java release is {}", releaseInfo);
        final Path installFolder = installParentFolder.resolve(createInstallName(os, arch, releaseInfo));

        if (downloadLatest && isValidJavaInstall(installFolder, os)) {
            log.info("Latest {} is already installed for java {} os {} arch {} : {}", imageType, javaVersion, os, arch, installFolder);
            return installFolder;
        }

        final Path downloadsFolder = installRootFolder.resolve("downloads");
        Files.createDirectories(downloadsFolder);
        final Path archivePath = downloadsFolder.resolve(releaseInfo.getPackageName());
        final Path tmpExtractFolder = installFolder.getParent().resolve(installFolder.getFileName().toString() + "_temporary");

        try {
            log.info("Downloading {} {} os {} arch {} ...", imageType, releaseInfo.getReleaseName(), os, arch);
            downloadJava(releaseInfo, archivePath);

            checkSha256Hash(releaseInfo, archivePath);
            log.info("Checksum is valid for {} {} os {} arch {}", imageType, releaseInfo.getReleaseName(), os, arch);

            log.info("Extracting compressed archive for {} {} os {} arch {}", imageType, releaseInfo.getReleaseName(), os, arch);
            extractArchive(archivePath, tmpExtractFolder, imageType);

            putToFinalDestination(releaseInfo, installFolder, tmpExtractFolder, imageType, os);

            log.info("Installation done for {} {} os {} arch {}", imageType, releaseInfo.getReleaseName(), os, arch);
        } finally {
            deleteFile(archivePath);
        }

        return installFolder;
    }

    private static String createInstallName(String os, String arch, ReleaseInfo releaseInfo) {
        return releaseInfo.getReleaseName()
                + SEPARATOR_IN_FILENAMES
                + releaseInfo.getTimestamp().replace(":", "-")
                + SEPARATOR_IN_FILENAMES
                + osArchString(os, arch);
    }

    private static String osArchString(String os, String arch) {
        return os + "_" + arch;
    }

    private static Optional<Path> tryFindJavaLocally(String arch,
                                                     String os,
                                                     Integer javaVersion,
                                                     @Nullable String fullJavaReleaseName,
                                                     boolean downloadLatest,
                                                     Path parentFolder,
                                                     ImageType imageType
    ) throws IOException {

        if (fullJavaReleaseName == null && downloadLatest) {
            return Optional.empty();
        }

        if (!Files.exists(parentFolder)) {
            return Optional.empty();
        }

        final String osArchString = osArchString(os, arch);

        try (Stream<Path> fileStream = Files.list(parentFolder)) {
            final Optional<Path> latestFolder = fileStream
                    .filter(path -> {
                        final String fileName = path.getFileName().toString();
                        if (fullJavaReleaseName != null) {
                            return fileName.startsWith(fullJavaReleaseName) && fileName.endsWith(SEPARATOR_IN_FILENAMES + osArchString);
                        } else {
                            return fileName.endsWith(SEPARATOR_IN_FILENAMES + osArchString);
                        }
                    })
                    .max(Comparator.comparing(p -> {
                        final String fileName = p.getFileName().toString();
                        final String date = fileName.substring(
                                fileName.indexOf(SEPARATOR_IN_FILENAMES) + SEPARATOR_IN_FILENAMES.length(),
                                fileName.lastIndexOf(SEPARATOR_IN_FILENAMES));
                        return ZonedDateTime.parse(date, TIMESTAMP_FORMATTER);
                    }));
            if (latestFolder.isPresent() && isValidJavaInstall(latestFolder.get(), os)) {
                log.info("Found existing {} for java {} os {} arch {} : {}", imageType, javaVersion, os, arch, latestFolder.get());
                return latestFolder;
            } else {
                return Optional.empty();
            }
        }
    }

    private static void putToFinalDestination(ReleaseInfo releaseInfo, Path installFolder, Path tmpExtractFolder, ImageType imageType, String os) throws IOException {
        try {
            if (Files.exists(installFolder)) {
                deleteRecursively(installFolder);
            }

            Files.createDirectories(installFolder);

            try (Stream<Path> files = Files.list(tmpExtractFolder)) {
                files.forEach(file -> {
                    try {
                        Files.move(file, installFolder.resolve(file.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (Exception ex) {
            deleteRecursively(installFolder);
            throw ex;
        } finally {
            deleteRecursively(tmpExtractFolder);
        }
    }

    private static List<Path> listFolder(Path folder) throws IOException {
        try (Stream<Path> fileStream = Files.list(folder)) {
            return fileStream.collect(Collectors.toList());
        }
    }

    private static void extractArchive(Path archivePath, Path destinationFolder, ImageType imageType) throws IOException {
        final String fileName = archivePath.getFileName().toString();
        if (fileName.endsWith(".zip")) {
            log.info("Extracting .zip archive {} ...", archivePath);
            new ZipFile(toAbsolutePath(archivePath)).extractAll(toAbsolutePath(destinationFolder));
        } else if (fileName.endsWith(".tar.gz")) {
            log.info("Extracting .tar.gz archive {} ...", archivePath);
            Utils.decompressTarGzipFile(archivePath, destinationFolder);
        } else {
            throw new IllegalStateException("Invalid " + imageType + " archive " + archivePath + " , extension must be either .zip or .tar.gz");
        }
    }

    private static void checkSha256Hash(ReleaseInfo releaseInfo, Path archivePath) throws IOException {
        try (InputStream in = Files.newInputStream(archivePath)) {
            final String sha256Hex = DigestUtils.sha256Hex(in);
            if (!sha256Hex.equals(releaseInfo.getChecksum())) {
                throw new RuntimeException(String.format(
                        "Invalid checksum when downloading file %s : %s != %s",
                        releaseInfo.getPackageName(), releaseInfo.getChecksum(), sha256Hex));
            }
        }
    }

    private static void downloadJava(ReleaseInfo releaseInfo, Path archivePath) throws IOException, HttpStatusException {
        final long start = System.nanoTime();

        final InputStream packageInputStream = requireNonNull(httpGet(
                releaseInfo.getPackageLink(),
                Collections.emptyMap(),
                Headers.of()).body()).byteStream();

        ProgressBarPrinter progressBar = new ProgressBarPrinter(
                releaseInfo.getSize(),
                "Downloading " + releaseInfo.getPackageName());

        try (OutputStream outputStream = Files.newOutputStream(archivePath)) {
            transferTo(packageInputStream, outputStream, 8192, progressBar::update);
        }

        final long end = System.nanoTime();

        log.info("Successfully downloaded {} in {} ms to {}",
                releaseInfo.getPackageName(),
                Duration.ofMillis(end - start),
                archivePath);
    }

    private static String toAbsolutePath(Path path) {
        return path.toAbsolutePath().toString();
    }

    private static Optional<Path> findJavaHomeFolder(Path javaInstallFolder, String os) throws IOException {
        if (!Files.exists(javaInstallFolder)) {
            return Optional.empty();
        }

        final List<Path> installFiles = listFolder(javaInstallFolder);
        final Optional<Path> firstFolder = installFiles.stream().filter(Files::isDirectory).findFirst();
        if (os.equalsIgnoreCase("mac")) {
            return firstFolder.map(path -> path.resolve("Contents/Home"));
        } else {
            return firstFolder;
        }
    }

    private static boolean isValidJavaInstall(Path javaInstallFolder, String os) throws IOException {
        return findJavaHomeFolder(javaInstallFolder, os)
                .map(path -> {
                    final Path binFolder = path.resolve("bin");
                    try {
                        return Files.exists(binFolder) &&
                                listFolder(binFolder).stream().anyMatch(f -> f.getFileName().toString().contains("java"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).orElse(false);
    }
}

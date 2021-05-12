package io.github.terjouxanthony.adopt.openjdk.downloader;

import io.github.terjouxanthony.adopt.openjdk.downloader.HttpRequester.HttpStatusException;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ImageType;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.InstallJavaParams;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.JavaInstallDescription;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ReleaseInfo;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ReleaseNamesRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.terjouxanthony.adopt.openjdk.downloader.Utils.transferTo;
import static java.util.Objects.requireNonNull;

@Slf4j
@AllArgsConstructor
public class JavaDownloader {
    private static final Pattern JDK_RELEASE_NAME_REGEX = Pattern.compile("^\\D+(\\d+)");
    private static final String SEPARATOR_IN_FILENAMES = "--";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssz");

    private final ArchiveUnpacker archiveUnpacker;
    private final FileSystemHandler fileSystemHandler;
    private final HttpRequester httpRequester;
    private final AdoptOpenJdkApi adoptOpenJdkApi;

    public JavaDownloader() {
        this.archiveUnpacker = new ArchiveUnpacker();
        this.fileSystemHandler = new FileSystemHandler();
        this.httpRequester = new HttpRequester();
        this.adoptOpenJdkApi = new AdoptOpenJdkApi(this.httpRequester);
    }

    public List<String> listAllReleases() throws HttpStatusException, IOException {
        return adoptOpenJdkApi.listAllReleases(ReleaseNamesRequest.builder()
                .releaseType(Model.ReleaseType.GENERAL_AVAILABILITY)
                .vendor(Model.Vendor.ADOPT_OPENJDK)
                .build());
    }

    public JavaInstallDescription installJava(InstallJavaParams params) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {

        final Path installPath = installJavaWithoutCleaning(params);
        if (params.isCleanExistingSameMajorVersion()) {
            log.info("Flag 'cleanExistingSameMajorVersion' enabled, cleaning {} folders other than {} ...", params.getImageType(), installPath);
            fileSystemHandler.listFolder(installPath.getParent())
                    .stream()
                    .filter(path -> !path.equals(installPath))
                    .forEach(path -> {
                        log.info("Deleting other {} {} ...", params.getImageType(), path);
                        fileSystemHandler.deleteRecursively(path);
                    });
            log.info("{} folders Cleaning done", params.getImageType());
        }

        return new JavaInstallDescription(installPath, findJavaHomeFolder(installPath, params.getOs()).get());
    }

    private Path installJavaWithoutCleaning(InstallJavaParams params) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {

        if (params.getJavaVersion() == null && params.getFullJavaReleaseName() == null) {
            throw new IllegalArgumentException("Either java version (eg. 16) or full java release name (eg. 16.0.1+9) must be provided");
        }

        if (params.getFullJavaReleaseName() != null) {
            final Matcher matcher = JDK_RELEASE_NAME_REGEX.matcher(params.getFullJavaReleaseName());
            if (!matcher.find()) {
                throw new IllegalArgumentException("Invalid fullJavaReleaseName, examples: jdk-16.0.1+9, jdk8u292-b10");
            }
            params.setJavaVersion(Integer.parseInt(matcher.group(1)));
        }

        final Path installRootFolder = params.getJavaDownloaderDir().resolve(params.getImageType().getValue());
        fileSystemHandler.mkdir(installRootFolder);
        final Path installParentFolder = installRootFolder.resolve(String.valueOf(params.getJavaVersion()))
                .resolve(osArchString(params.getOs(), params.getArch()));

        Optional<Path> installPathOpt = tryFindJavaLocally(params, installParentFolder);
        if (installPathOpt.isPresent()) {
            return installPathOpt.get();
        }

        final ReleaseInfo releaseInfo = (params.getFullJavaReleaseName() != null) ?
                adoptOpenJdkApi.getJavaReleaseInfo(params.getFullJavaReleaseName(), params.getArch(), params.getOs(), params.getImageType()) :
                adoptOpenJdkApi.getLatestJavaRelaseInfo(params.getJavaVersion(), params.getArch(), params.getOs(), params.getImageType());

        log.info("Java release is {}", releaseInfo);
        final Path installFolder = installParentFolder.resolve(createInstallName(params.getOs(), params.getArch(), releaseInfo));

        if (params.isDownloadLatest() && isValidJavaInstall(installFolder, params.getOs())) {
            log.info("Latest {} is already installed for java {} os {} arch {} : {}",
                    params.getImageType(), params.getJavaVersion(), params.getOs(), params.getArch(), installFolder);
            return installFolder;
        }

        final Path downloadsFolder = installRootFolder.resolve("downloads");
        fileSystemHandler.mkdir(downloadsFolder);
        final Path archivePath = downloadsFolder.resolve(releaseInfo.getPackageName());
        final Path tmpExtractFolder = installFolder.getParent().resolve(installFolder.getFileName().toString() + "_temporary");

        try {
            log.info("Downloading {} {} os {} arch {} ...", params.getImageType(), releaseInfo.getReleaseName(), params.getOs(), params.getArch());
            downloadJava(releaseInfo, archivePath);

            checkSha256Hash(releaseInfo, archivePath);
            log.info("Checksum is valid for {} {} os {} arch {}", params.getImageType(), releaseInfo.getReleaseName(), params.getOs(), params.getArch());

            log.info("Extracting compressed archive for {} {} os {} arch {}", params.getImageType(), releaseInfo.getReleaseName(), params.getOs(), params.getArch());
            extractArchive(archivePath, tmpExtractFolder, params.getImageType());

            putToFinalDestination(installFolder, tmpExtractFolder);

            log.info("Installation done for {} {} os {} arch {}", params.getImageType(), releaseInfo.getReleaseName(), params.getOs(), params.getArch());
        } finally {
            fileSystemHandler.deleteFile(archivePath);
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

    private Optional<Path> tryFindJavaLocally(InstallJavaParams params, Path parentFolder) throws IOException {

        if (params.getFullJavaReleaseName() == null && params.isDownloadLatest()) {
            return Optional.empty();
        }

        if (!fileSystemHandler.fileOrFolderExists(parentFolder)) {
            return Optional.empty();
        }

        final String osArchString = osArchString(params.getOs(), params.getArch());

        try (Stream<Path> fileStream = fileSystemHandler.listFolderAsStream(parentFolder)) {
            final Optional<Path> latestFolder = fileStream
                    .filter(path -> {
                        final String fileName = path.getFileName().toString();
                        if (params.getFullJavaReleaseName() != null) {
                            return fileName.startsWith(params.getFullJavaReleaseName()) && fileName.endsWith(SEPARATOR_IN_FILENAMES + osArchString);
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
            if (latestFolder.isPresent() && isValidJavaInstall(latestFolder.get(), params.getOs())) {
                log.info("Found existing {} for java {} os {} arch {} : {}",
                        params.getImageType(), params.getJavaVersion(), params.getOs(), params.getArch(), latestFolder.get());
                return latestFolder;
            } else {
                return Optional.empty();
            }
        }
    }

    private void putToFinalDestination(Path installFolder, Path tmpExtractFolder) throws IOException {
        try {
            if (fileSystemHandler.fileOrFolderExists(installFolder)) {
                fileSystemHandler.deleteRecursively(installFolder);
            }

            fileSystemHandler.mkdir(installFolder);

            try (Stream<Path> files = fileSystemHandler.listFolderAsStream(tmpExtractFolder)) {
                files.forEach(file -> {
                    try {
                        fileSystemHandler.move(file, installFolder.resolve(file.getFileName()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (Exception ex) {
            fileSystemHandler.deleteRecursively(installFolder);
            throw ex;
        } finally {
            fileSystemHandler.deleteRecursively(tmpExtractFolder);
        }
    }

    private void extractArchive(Path archivePath, Path destinationFolder, ImageType imageType) throws IOException {
        final String fileName = archivePath.getFileName().toString();
        if (fileName.endsWith(".zip")) {
            log.info("Extracting .zip archive {} ...", archivePath);
            archiveUnpacker.unZip(archivePath, destinationFolder);
        } else if (fileName.endsWith(".tar.gz")) {
            log.info("Extracting .tar.gz archive {} ...", archivePath);
            archiveUnpacker.unTarGz(archivePath, destinationFolder);
        } else {
            throw new IllegalStateException("Invalid " + imageType + " archive " + archivePath + " , extension must be either .zip or .tar.gz");
        }
    }

    private void checkSha256Hash(ReleaseInfo releaseInfo, Path archivePath) throws IOException {
        try (InputStream in = fileSystemHandler.inputStream(archivePath)) {
            final String sha256Hex = DigestUtils.sha256Hex(in);
            if (!sha256Hex.equals(releaseInfo.getChecksum())) {
                throw new RuntimeException(String.format(
                        "Invalid checksum when downloading file %s : %s != %s",
                        releaseInfo.getPackageName(), releaseInfo.getChecksum(), sha256Hex));
            }
        }
    }

    private void downloadJava(ReleaseInfo releaseInfo, Path archivePath) throws IOException, HttpStatusException {
        final long start = System.nanoTime();

        final InputStream packageInputStream = requireNonNull(httpRequester.httpGet(
                releaseInfo.getPackageLink(),
                Collections.emptyMap(),
                Headers.of()).body()).byteStream();

        ProgressBarPrinter progressBar = new ProgressBarPrinter(
                releaseInfo.getSize(),
                "Downloading " + releaseInfo.getPackageName());

        try (OutputStream outputStream = fileSystemHandler.outputStream(archivePath)) {
            transferTo(packageInputStream, outputStream, 8192, progressBar::update);
        }

        final long end = System.nanoTime();

        log.info("Successfully downloaded {} in {} ms to {}",
                releaseInfo.getPackageName(),
                Duration.ofNanos(end - start).toMillis(),
                archivePath);
    }

    private Optional<Path> findJavaHomeFolder(Path javaInstallFolder, String os) throws IOException {
        if (!fileSystemHandler.fileOrFolderExists(javaInstallFolder)) {
            return Optional.empty();
        }

        final List<Path> installFiles = fileSystemHandler.listFolder(javaInstallFolder);
        final Optional<Path> firstFolder = installFiles.stream().filter(Files::isDirectory).findFirst();
        if (os.equalsIgnoreCase("mac")) {
            return firstFolder.map(path -> path.resolve("Contents/Home"));
        } else {
            return firstFolder;
        }
    }

    private boolean isValidJavaInstall(Path javaInstallFolder, String os) throws IOException {
        return findJavaHomeFolder(javaInstallFolder, os)
                .map(path -> {
                    final Path binFolder = path.resolve("bin");
                    try {
                        return fileSystemHandler.fileOrFolderExists(binFolder) &&
                                fileSystemHandler.listFolder(binFolder).stream().anyMatch(f -> f.getFileName().toString().contains("java"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).orElse(false);
    }
}

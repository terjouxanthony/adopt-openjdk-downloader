import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.terjouxanthony.adopt.openjdk.downloader.AdoptOpenJdkApi;
import io.github.terjouxanthony.adopt.openjdk.downloader.ArchiveUnpacker;
import io.github.terjouxanthony.adopt.openjdk.downloader.FileSystemHandler;
import io.github.terjouxanthony.adopt.openjdk.downloader.HttpRequester;
import io.github.terjouxanthony.adopt.openjdk.downloader.JavaDownloader;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.InstallJavaParams;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.JavaInstallDescription;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * High level test testing logic and filesystem operations, Http requests creation, archive unpacking.
 * The only thing which is mocked is Http network connections.
 * Unit tests can also be added to test things separately and to improve testing feedback loop.
 */
public class JavaDownloaderHighLevelTest {

    private ArchiveUnpacker archiveUnpacker;
    private FileSystemHandler fileSystemHandler;
    private HttpRequester httpRequester;
    private AdoptOpenJdkApi adoptOpenJdkApi;

    private JavaDownloader javaDownloader;
    private static Path rootFolder;
    private Path testFolder;

    @BeforeEach
    public void before() {
        this.httpRequester = mock(HttpRequester.class);
        this.archiveUnpacker = new ArchiveUnpacker();
        this.fileSystemHandler = new FileSystemHandler();
        this.adoptOpenJdkApi = new AdoptOpenJdkApi(httpRequester);

        this.javaDownloader = new JavaDownloader(
                archiveUnpacker, fileSystemHandler, httpRequester, adoptOpenJdkApi
        );

        testFolder = rootFolder.resolve(UUID.randomUUID().toString());
    }

    @AfterEach
    public void after() throws IOException {
        FileUtils.deleteDirectory(testFolder.toFile());
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        rootFolder = Files.createTempDirectory("java-downloader-tests");
    }

    @AfterAll
    public static void afterAll() throws IOException {
        FileUtils.deleteDirectory(rootFolder.toFile());
    }

    @Test
    public void should_download_jre_if_not_installed() throws Exception {
        //given
        final Path jreDir = makeJreDir(testFolder, 16);
        final Path zippedJreDir = addZipExtension(jreDir);
        ArchiverUtils.makeZip(jreDir, zippedJreDir);
        final String checksum = DigestUtils.sha256Hex(Files.readAllBytes(zippedJreDir));

        when(httpRequester.httpGet(eq("https://api.adoptopenjdk.net/v3/assets/feature_releases/16/ga"), any(), any()))
                .thenReturn(mockHttpResponse(updateCheckSum(readFile("feature_releases.json"), checksum)));

        when(httpRequester.httpGet(
                eq("https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jre_x64_windows_hotspot_16.0.1_9.zip"), any(), any()))
                .thenReturn(mockHttpResponse(zippedJreDir));

        //when
        final JavaInstallDescription installation = javaDownloader.installJava(InstallJavaParams.builder()
                .arch("x64")
                .os("windows")
                .javaVersion(16)
                .downloadLatest(false)
                .cleanExistingSameMajorVersion(false)
                .imageType(Model.ImageType.JRE)
                .javaDownloaderDir(testFolder)
                .build());

        //then
        final Path expectedJreFolder = testFolder.resolve("jre/16/windows_x64/jdk-16.0.1+9--2021-04-23T09-10-06Z--windows_x64");

        assertThat(installation).isEqualTo(new JavaInstallDescription(expectedJreFolder, expectedJreFolder.resolve("jdk-16-jre")));

        verify(httpRequester).httpGet("https://api.adoptopenjdk.net/v3/assets/feature_releases/16/ga", map(
                "project", "jdk",
                "sort_method", "DATE",
                "sort_order", "DESC",
                "page", "0",
                "page_size", "1",
                "jvm_impl", "hotspot",
                "image_type", "jre",
                "vendor", "adoptopenjdk",
                "architecture", "x64",
                "os", "windows"), Headers.of("accept", "application/json"));

        verify(httpRequester).httpGet(
                "https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jre_x64_windows_hotspot_16.0.1_9.zip",
                Collections.emptyMap(),
                Headers.of()
        );

        assertThat(Files.exists(expectedJreFolder)).isTrue();

        FileUtils.deleteDirectory(jreDir.toFile());
        FileUtils.deleteQuietly(zippedJreDir.toFile());
        final Collection<File> allFiles = FileUtils.listFiles(testFolder.toFile(), null, true);
        assertThat(allFiles).containsExactlyInAnyOrder(
                expectedJreFolder.resolve("jdk-16-jre/lib/classlist").toFile(),
                expectedJreFolder.resolve("jdk-16-jre/bin/java").toFile(),
                expectedJreFolder.resolve("jdk-16-jre/bin/keytool").toFile()
        );
    }

    @Test
    public void should_reused_installed_jre() throws Exception {
        //given
        final Path jreDir = makeJreDir(testFolder, 16);
        final Path zippedJreDir = addZipExtension(jreDir);
        ArchiverUtils.makeZip(jreDir, zippedJreDir);
        final String checksum = DigestUtils.sha256Hex(Files.readAllBytes(zippedJreDir));

        when(httpRequester.httpGet(eq("https://api.adoptopenjdk.net/v3/assets/feature_releases/16/ga"), any(), any()))
                .thenReturn(mockHttpResponse(updateCheckSum(readFile("feature_releases.json"), checksum)));

        when(httpRequester.httpGet(
                eq("https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jre_x64_windows_hotspot_16.0.1_9.zip"), any(), any()))
                .thenReturn(mockHttpResponse(zippedJreDir));

        //when
        final JavaInstallDescription installation = javaDownloader.installJava(InstallJavaParams.builder()
                .arch("x64")
                .os("windows")
                .javaVersion(16)
                .downloadLatest(false)
                .cleanExistingSameMajorVersion(false)
                .imageType(Model.ImageType.JRE)
                .javaDownloaderDir(testFolder)
                .build());

        final JavaInstallDescription installation2 = javaDownloader.installJava(InstallJavaParams.builder()
                .arch("x64")
                .os("windows")
                .javaVersion(16)
                .downloadLatest(false)
                .cleanExistingSameMajorVersion(false)
                .imageType(Model.ImageType.JRE)
                .javaDownloaderDir(testFolder)
                .build());

        //then
        final Path expectedJreFolder = testFolder.resolve("jre/16/windows_x64/jdk-16.0.1+9--2021-04-23T09-10-06Z--windows_x64");
        assertThat(installation).isEqualTo(new JavaInstallDescription(expectedJreFolder, expectedJreFolder.resolve("jdk-16-jre")));
        assertThat(installation2).isEqualTo(installation);

        verify(httpRequester, times(1)).httpGet(eq("https://api.adoptopenjdk.net/v3/assets/feature_releases/16/ga"), any(), any());
        verify(httpRequester, times(1)).httpGet(eq("https://github.com/AdoptOpenJDK/openjdk16-binaries/releases/download/jdk-16.0.1%2B9/OpenJDK16U-jre_x64_windows_hotspot_16.0.1_9.zip"), any(), any());
        assertThat(Files.exists(expectedJreFolder)).isTrue();
    }

    private Path addZipExtension(Path jreDir) {
        return jreDir.getParent().resolve(jreDir.getFileName().toString() + ".zip");
    }

    private String updateCheckSum(String json, String newChecksum) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode jsonNode = mapper.readTree(json);
        ((ObjectNode) jsonNode.get(0).path("binaries").get(0).path("package"))
                .put("checksum", newChecksum);
        return mapper.writeValueAsString(jsonNode);
    }

    private Response mockHttpResponse(String bodyContent) throws IOException {
        return new Response.Builder()
                .code(200)
                .request(new Request.Builder().url("http://fake.com").build())
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .body(ResponseBody.create(bodyContent, MediaType.get("application/json")))
                .build();
    }

    private Response mockHttpResponse(Path bodyContent) throws IOException {
        final byte[] fileContent = Files.readAllBytes(bodyContent);
        return new Response.Builder()
                .code(200)
                .request(new Request.Builder().url("http://fake.com").build())
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .body(ResponseBody.create(fileContent, MediaType.get("application/octet-stream")))
                .build();
    }

    private static Map<String, String> map(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Number of keys/values must be even");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    public String readFile(String file) throws IOException {
        final InputStream inputStream = JavaDownloaderHighLevelTest.class.getClassLoader().getResourceAsStream(file);
        return IOUtils.toString(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
    }

    public Path makeJreDir(Path path, int javaVersion) throws IOException {
        final Path jreDir = path.resolve("jdk-" + javaVersion + "-jre");
        Files.createDirectories(jreDir);
        Files.createDirectories(jreDir.resolve("lib"));
        Files.createDirectories(jreDir.resolve("bin"));
        Files.createFile(jreDir.resolve("bin/java"));
        Files.createFile(jreDir.resolve("bin/keytool"));
        Files.createFile(jreDir.resolve("lib/classlist"));
        return jreDir;
    }

}

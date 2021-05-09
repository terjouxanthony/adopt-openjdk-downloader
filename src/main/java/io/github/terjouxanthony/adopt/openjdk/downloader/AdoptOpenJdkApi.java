package io.github.terjouxanthony.adopt.openjdk.downloader;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.terjouxanthony.adopt.openjdk.downloader.HttpRequester.HttpStatusException;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ImageType;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ListReleasesRequest;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ReleaseInfo;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ReleaseInfoRequest;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ReleaseNamesRequest;
import lombok.AllArgsConstructor;
import okhttp3.Headers;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.github.terjouxanthony.adopt.openjdk.downloader.Utils.MAPPER;

@AllArgsConstructor
public class AdoptOpenJdkApi {
    private final HttpRequester httpRequester;

    public ReleaseInfo getLatestJavaRelaseInfo(int javaVersion, String arch, String os, ImageType imageType) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {
        final String releases = listFeatureReleases(new ListReleasesRequest(
                javaVersion,
                Model.ReleaseType.GENERAL_AVAILABILITY,
                arch,
                imageType,
                Model.JvmImpl.HOTSPOT,
                os,
                Model.Vendor.ADOPT_OPENJDK
        ), 0, 1);

        return parseReleaseInfo(MAPPER.readTree(releases).get(0));
    }

    public ReleaseInfo getJavaReleaseInfo(String javaReleaseName, String arch, String os, ImageType jre) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {
        final String releaseInfo = getReleaseInformation(new ReleaseInfoRequest(
                javaReleaseName,
                arch,
                jre,
                Model.JvmImpl.HOTSPOT,
                os,
                Model.Vendor.ADOPT_OPENJDK
        ));

        return parseReleaseInfo(MAPPER.readTree(releaseInfo));
    }

    public List<String> listAllReleases(ReleaseNamesRequest req) throws IOException, HttpStatusException {
        int page = 0;
        final int pageSize = 20;

        List<String> releases = new ArrayList<>();

        boolean hasMore = true;
        while (hasMore) {
            try {
                String response = listReleaseNames(req, page, pageSize);
                JsonNode releasesNode = MAPPER.readTree(response).path("releases");
                List<String> releaseList = StreamSupport.stream(releasesNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList());
                hasMore = releaseList.size() == pageSize;
                releases.addAll(releaseList);
                page += 1;
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 404) {
                    hasMore = false;
                } else {
                    throw e;
                }
            }
        }

        return releases;
    }

    private static ReleaseInfo parseReleaseInfo(JsonNode release) {
        final JsonNode packageInfo = release.path("binaries").get(0).path("package");
        final String checksum = packageInfo.path("checksum").textValue();
        final String packageName = packageInfo.path("name").textValue();
        final long size = packageInfo.path("size").longValue();
        final String link = packageInfo.path("link").textValue();
        final String releaseName = release.path("release_name").textValue();
        final String timestamp = release.path("timestamp").textValue();
        return new ReleaseInfo(checksum, packageName, link, releaseName, size, timestamp);
    }

    public String listReleaseNames(ReleaseNamesRequest req, int page, int pageSize) throws IOException, HttpStatusException {

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("sort_method", "DEFAULT");
        queryParams.put("sort_order", "DESC");
        queryParams.put("page", String.valueOf(page));
        queryParams.put("page_size", String.valueOf(pageSize));
        queryParams.put("release_type", req.getReleaseType().getValue());
        queryParams.put("vendor", req.getVendor().getValue());

        if (req.getVersion() != null) {
            queryParams.put("version", req.getVersion());
        }

        final ResponseBody body = httpRequester.httpGet(
                "https://api.adoptopenjdk.net/v3/info/release_names",
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }

    public String getReleaseInformation(ReleaseInfoRequest req) throws IOException, HttpStatusException {

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("project", "jdk");
        queryParams.put("jvm_impl", req.getJvmImpl().getValue());
        queryParams.put("image_type", req.getImageType().getValue());
        queryParams.put("architecture", req.getArchitecture());
        queryParams.put("os", req.getOs());

        final ResponseBody body = httpRequester.httpGet(
                String.format("https://api.adoptopenjdk.net/v3/assets/release_name/%s/%s",
                        req.getVendor().getValue(), req.getReleaseName()),
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }

    public String listFeatureReleases(ListReleasesRequest req, int page, int pageSize) throws IOException, HttpStatusException {

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("project", "jdk");
        queryParams.put("sort_method", "DATE");
        queryParams.put("sort_order", "DESC");
        queryParams.put("page", String.valueOf(page));
        queryParams.put("page_size", String.valueOf(pageSize));
        queryParams.put("jvm_impl", req.getJvmImpl().getValue());
        queryParams.put("image_type", req.getImageType().getValue());
        queryParams.put("vendor", req.getVendor().getValue());
        queryParams.put("architecture", req.getArchitecture());
        queryParams.put("os", req.getOs());

        final ResponseBody body = httpRequester.httpGet(
                String.format("https://api.adoptopenjdk.net/v3/assets/feature_releases/%s/%s",
                        req.getFeatureVersion(), req.getReleaseType().getValue()),
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }
}

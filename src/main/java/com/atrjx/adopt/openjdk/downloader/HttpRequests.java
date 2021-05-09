package com.atrjx.adopt.openjdk.downloader;

import okhttp3.Headers;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.atrjx.adopt.openjdk.downloader.Utils.httpGet;

public class HttpRequests {
    public static String listReleaseNames(Model.ReleaseNamesRequest req, int page, int pageSize)
            throws IOException, Utils.HttpStatusException {

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

        final ResponseBody body = httpGet(
                "https://api.adoptopenjdk.net/v3/info/release_names",
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }

    public static String getReleaseInformation(Model.ReleaseInfoRequest req) throws IOException, Utils.HttpStatusException {

        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("project", "jdk");
        queryParams.put("jvm_impl", req.getJvmImpl().getValue());
        queryParams.put("image_type", req.getImageType().getValue());
        queryParams.put("architecture", req.getArchitecture());
        queryParams.put("os", req.getOs());

        final ResponseBody body = httpGet(
                String.format("https://api.adoptopenjdk.net/v3/assets/release_name/%s/%s",
                        req.getVendor().getValue(), req.getReleaseName()),
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }

    public static String listFeatureReleases(Model.ListReleasesRequest req, int page, int pageSize)
            throws IOException, Utils.HttpStatusException {

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

        final ResponseBody body = httpGet(
                String.format("https://api.adoptopenjdk.net/v3/assets/feature_releases/%s/%s",
                        req.getFeatureVersion(), req.getReleaseType().getValue()),
                queryParams,
                Headers.of("accept", "application/json")).body();

        return Objects.requireNonNull(body).string();
    }

}

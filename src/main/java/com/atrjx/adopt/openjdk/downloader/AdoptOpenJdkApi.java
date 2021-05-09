package com.atrjx.adopt.openjdk.downloader;

import com.atrjx.adopt.openjdk.downloader.Model.ListReleasesRequest;
import com.atrjx.adopt.openjdk.downloader.Model.ReleaseInfo;
import com.atrjx.adopt.openjdk.downloader.Model.ReleaseInfoRequest;
import com.atrjx.adopt.openjdk.downloader.Model.ReleaseNamesRequest;
import com.atrjx.adopt.openjdk.downloader.Utils.HttpStatusException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.atrjx.adopt.openjdk.downloader.HttpRequests.getReleaseInformation;
import static com.atrjx.adopt.openjdk.downloader.HttpRequests.listFeatureReleases;
import static com.atrjx.adopt.openjdk.downloader.HttpRequests.listReleaseNames;
import static com.atrjx.adopt.openjdk.downloader.Utils.MAPPER;

public class AdoptOpenJdkApi {

    public static ReleaseInfo getLatestJavaRelaseInfo(int javaVersion, String arch, String os, Model.ImageType imageType) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {
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

    public static ReleaseInfo getJavaReleaseInfo(String javaReleaseName, String arch, String os, Model.ImageType jre) throws IOException, InterruptedException, URISyntaxException, HttpStatusException {
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

    public static List<String> listAllReleases(ReleaseNamesRequest req) throws IOException, URISyntaxException, InterruptedException, HttpStatusException {
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
}

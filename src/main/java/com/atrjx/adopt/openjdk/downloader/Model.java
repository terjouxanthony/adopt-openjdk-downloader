package com.atrjx.adopt.openjdk.downloader;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public class Model {
    public static List<String> osList = Arrays.asList(
            "linux", "windows", "mac", "solaris", "aix", "alpine-linux"
    );

    public static List<String> architectureList = Arrays.asList(
            "x64", "x32", "ppc64", "ppc64le", "s390x", "aarch64", "arm", "sparcv9", "riscv64"
    );

    @AllArgsConstructor
    @Getter
    public enum ReleaseType {
        GENERAL_AVAILABILITY("ga"),
        EARLY_ACCESS("ea");

        @JsonValue
        private final String value;
    }

    @AllArgsConstructor
    @Getter
    public enum JvmImpl {
        HOTSPOT("hotspot"),
        OPENJ9("openj9");

        @JsonValue
        private final String value;
    }

    @AllArgsConstructor
    @Getter
    public enum ImageType {
        JDK("jdk"),
        JRE("jre");

        @JsonValue
        private final String value;

        @Override
        public String toString() {
            return value;
        }
    }

    @AllArgsConstructor
    @Getter
    public enum Vendor {
        ADOPT_OPENJDK("adoptopenjdk"),
        OPENJDK("openjdk");

        @JsonValue
        private final String value;
    }

    @Data
    public static class ListReleasesRequest {
        private final int featureVersion;
        private final ReleaseType releaseType;
        private final String architecture;
        private final ImageType imageType;
        private final JvmImpl jvmImpl;
        private final String os;
        private final Vendor vendor;
    }

    @Data
    public static class ReleaseInfoRequest {
        private final String releaseName;
        private final String architecture;
        private final ImageType imageType;
        private final JvmImpl jvmImpl;
        private final String os;
        private final Vendor vendor;
    }

    @Data
    public static class ReleaseNamesRequest {
        private final ReleaseType releaseType;
        private final Vendor vendor;
        @Nullable
        private final String version; /* Java version range (maven style) of versions to include. e.g: 11.0.4.1+11.1 or [1.0,2.0) or (,1.0] */
    }

    @Data
    public static class ReleaseInfo {
        private final String checksum;
        private final String packageName;
        private final String packageLink;
        private final String releaseName;
        private final long size;
        private final String timestamp; // ex. 2021-03-16T17:21:16Z
    }

    @interface Nullable {
    }
}

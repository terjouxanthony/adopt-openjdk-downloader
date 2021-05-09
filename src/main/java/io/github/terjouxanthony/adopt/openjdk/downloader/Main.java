package io.github.terjouxanthony.adopt.openjdk.downloader;

import io.github.terjouxanthony.adopt.openjdk.downloader.HttpRequester.HttpStatusException;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.InstallJavaParams;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.JavaInstallDescription;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class Main {

    public static void main(String[] args) {
        final int javaVersion = 16;

        final List<String> osList = Arrays.asList("linux", "windows", "mac");
        final List<String> archList = Arrays.asList("x64");
        final List<Model.ImageType> imageTypes = Arrays.asList(Model.ImageType.JRE, Model.ImageType.JDK);

        final JavaDownloader javaDownloader = new JavaDownloader();

        osList.forEach(os ->
                archList.forEach(arch ->
                        imageTypes.forEach(imageType -> {
                            try {
                                final JavaInstallDescription installation = javaDownloader.installJava(
                                        InstallJavaParams.builder()
                                                .arch(arch)
                                                .os(os)
                                                .javaVersion(javaVersion)
                                                .downloadLatest(false)
                                                .cleanExistingSameMajorVersion(true)
                                                .imageType(imageType)
                                                .build());

                                System.out.println(installation);
                            } catch (HttpStatusException | IOException | URISyntaxException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        })));
    }

}

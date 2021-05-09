package com.atrjx.adopt.openjdk.downloader;

import com.atrjx.adopt.openjdk.downloader.JavaDownloader.JavaInstallDescription;
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

        osList.forEach(os ->
                archList.forEach(arch ->
                        imageTypes.forEach(imageType -> {
                            try {
                                final JavaInstallDescription javaInstall = JavaDownloader.installJava(
                                        arch,
                                        os,
                                        javaVersion,
                                        null,//"jdk-16+36"
                                        false,
                                        true,
                                        imageType);

                                System.out.println(javaInstall);
                            } catch (Utils.HttpStatusException | IOException | URISyntaxException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        })));
    }

}

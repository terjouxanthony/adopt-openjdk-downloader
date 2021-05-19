package io.github.terjouxanthony.jdk.downloader.maven.plugin;

import io.github.terjouxanthony.adopt.openjdk.downloader.HttpRequester;
import io.github.terjouxanthony.adopt.openjdk.downloader.JavaDownloader;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.ImageType;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.InstallJavaParams.InstallJavaParamsBuilder;
import io.github.terjouxanthony.adopt.openjdk.downloader.Model.JavaInstallDescription;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Duration;

@Mojo(name = "download-java", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class JdkDownloaderMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    // Mandatory params:

    @Parameter(property = "architecture", required = true)
    String architecture;

    @Parameter(property = "os", required = true)
    String os;

    @Parameter(property = "imageType", required = true)
    String imageType; // jdk or jre

    // Optional params:

    @Parameter(property = "downloadLatest", defaultValue = "false", required = true)
    Boolean downloadLatest;

    @Parameter(property = "cleanExistingSameMajorVersion", defaultValue = "true", required = true)
    Boolean cleanExistingSameMajorVersion;

    @Parameter(property = "rootDir")
    String rootDir;  // root folder to store JRE's and JDK's, defaults to $HOME/.m2/java

    @Parameter(property = "javaVersion")
    Integer javaVersion; // optional

    @Parameter(property = "fullJavaReleaseName")
    String fullJavaReleaseName; // optional

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final long start = System.nanoTime();

            JavaDownloader javaDownloader = new JavaDownloader();

            InstallJavaParamsBuilder builder = Model.InstallJavaParams.builder()
                    .arch(architecture)
                    .os(os)
                    .imageType(getImageType())
                    .downloadLatest(downloadLatest)
                    .cleanExistingSameMajorVersion(cleanExistingSameMajorVersion);

            if (rootDir != null) {
                builder = builder.javaDownloaderDir(Paths.get(rootDir));
            }

            if (fullJavaReleaseName != null) {
                builder = builder.fullJavaReleaseName(fullJavaReleaseName);
            } else {
                builder = builder.javaVersion(getJavaVersion());
            }

            final JavaInstallDescription installation = javaDownloader.installJava(builder.build());

            project.getProperties().setProperty("jdk-downloader-maven-plugin.jdk-install-path",
                    installation.getInstallPath().toAbsolutePath().toString()); // Folder containing the downloaded JDK/JRE

            project.getProperties().setProperty("jdk-downloader-maven-plugin.jdk-home",
                    installation.getJdkHomePath().toAbsolutePath().toString());  // JAVA_HOME path for this JDK/JRE

            final long end = System.nanoTime();
            final long elapsedMillis = Duration.ofNanos(end - start).toMillis();
            getLog().info(getImageType() + " HOME is " + installation.getJdkHomePath() + " , took " + elapsedMillis + " ms");

        } catch (IOException | InterruptedException | URISyntaxException | HttpRequester.HttpStatusException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private int getJavaVersion() throws MojoExecutionException {
        if (javaVersion != null) {
            return javaVersion;
        }
        final Object targetVersion = project.getProperties().get("maven.compiler.target");
        if (targetVersion != null) {
            return Integer.parseInt((String) targetVersion);
        }
        throw new MojoExecutionException("No java version, you must either set the parameter 'javaVersion' or set the property 'maven.compiler.target'");

    }

    private ImageType getImageType() throws MojoExecutionException {
        if ("jdk".equalsIgnoreCase(imageType)) {
            return ImageType.JDK;
        }
        if ("jre".equalsIgnoreCase(imageType)) {
            return ImageType.JRE;
        }
        throw new MojoExecutionException("Invalid 'imageType' parameter, must be either jdk or jre");
    }

}

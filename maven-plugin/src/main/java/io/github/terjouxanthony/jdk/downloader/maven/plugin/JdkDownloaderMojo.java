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

/**
 * Downloads and installs a general availability hotspot JDK/JRE from AdoptOpenJDK vendor.
 * <p>
 * If the JDK/JRE is already present, no download is performed, except if the flag 'downloadLatest' is true and if there is a newer minor/bugfix version for the supplied java version.
 * The JDK/JREs are installed by default under the folder $HOME/.m2/java. This can be configured by the rootDir parameter.
 * <p>
 * After execution, the plugin sets the following project properties:
 * <ul>
 *     <li><b>jdk-downloader-maven-plugin.jdk-install-path</b>: Absolute path to the folder containing the JDK/JRE</li>
 *     <li><b>jdk-downloader-maven-plugin.jdk-home</b>: Absolute path to the JDK/JRE</li>
 * </ul>
 * The plugin executes in the PREPARE_PACKAGE lifecycle phase, so other plugins can use the above properties if they are bound to the PACKAGE phase.
 * Example of properties usage: ${jdk-downloader-maven-plugin.jdk-home}.
 * <p>
 * Example of the plugin's usage:
 * <pre>
 * {@code
 * <plugin>
 *     <groupId>io.github.terjouxanthony</groupId>
 *     <artifactId>jdk-downloader-maven-plugin</artifactId>
 *     <version>0.0.1</version>
 *     <executions>
 *         <execution>
 *             <goals>
 *                 <goal>download-java</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 *     <configuration>
 *         <imageType>jre</imageType>
 *         <os>windows</os>
 *         <architecture>x64</architecture>
 *     </configuration>
 * </plugin>
 * }
 * </pre>
 */
@Mojo(name = "download-java", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class JdkDownloaderMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    // Mandatory params:

    /**
     * Architecture, example: x64, x32, ppc64, arm
     */
    @Parameter(property = "architecture", required = true)
    String architecture;

    /**
     * Operating system, example: linux, windows, mac, solaris
     */
    @Parameter(property = "os", required = true)
    String os;

    /**
     * jre or jdk
     */
    @Parameter(property = "imageType", required = true)
    String imageType;

    // Optional params:

    /**
     * Optional flag. If true, always request AdoptOpenJdk API to get the latest version with bugfixes.
     * If there is a minor/bugfix version, it is downloaded alongside the previous ones. To clean them, use the flag cleanExistingSameMajorVersion.
     */
    @Parameter(property = "downloadLatest", defaultValue = "false")
    Boolean downloadLatest;

    /**
     * Optional flag. If true, delete the previously downloaded artifacts for the supplied java version.
     */
    @Parameter(property = "cleanExistingSameMajorVersion", defaultValue = "true")
    Boolean cleanExistingSameMajorVersion;

    /**
     * Optional value. Root folder to store JRE's and JDK's, defaults to $HOME/.m2/java
     */
    @Parameter(property = "rootDir")
    String rootDir;  // root folder to store JRE's and JDK's, defaults to $HOME/.m2/java

    /**
     * Optional value. Example: 11, 12, 13, 17 ...
     * By default use the version set in project property 'maven.compiler.target'
     */
    @Parameter(property = "javaVersion")
    Integer javaVersion; // optional

    /**
     * Optional value. You can choose an exact version, it takes precedence over the parameter 'javaVersion'.
     * Example: jdk-16.0.1+9
     */
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

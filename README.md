# AdoptOpenJDK downloader

Java library to ease downloading and installation of AdoptOpenJdk java JDK's and JRE's.



It can be useful in a gradle build or a maven plugin, in order to automate the packaging of a java application for multiple platforms.



##### Dependency:

```
<dependency>
  <groupId>io.github.terjouxanthony</groupId>
  <artifactId>adopt.openjdk.downloader</artifactId>
  <version>0.0.1</version>
</dependency>
```



##### Usage:

```java
    public static void main(String[] args) throws HttpStatusException, IOException, URISyntaxException, InterruptedException {

        final JavaDownloader javaDownloader = new JavaDownloader();

        // Downloads and installs a GA hotspot JDK/JRE from AdoptOpenJDK vendor.
        // If the JDK/JRE is already present, no download is performed, except if the flag 'downloadLatest' is true and if there are minor/bug fixes for the java version supplied.
        final JavaInstallDescription installation = javaDownloader.installJava(
                InstallJavaParams.builder()
                        .arch("x64") // "x64", "x32", "ppc64", "arm" ...
                        .os("windows") // "linux", "windows", "mac", "solaris" ...
                        .javaVersion(16) // 11, 12, 13 ...
                        //.fullJavaReleaseName("jdk-16.0.1+9") // you can choose an exact version, it takes precedence over the parameter 'javaVersion'
                        .downloadLatest(false) // set to true to always fetch the latest version with bugfixes
                        .cleanExistingSameMajorVersion(true) // delete the previously downloaded versions for java 16 here
                        .imageType(Model.ImageType.JRE) // can also be JDK
                        .javaDownloaderDir(Paths.get("G:\\projects\\java\\test")) // root folder to store JRE's and JDK's, defaults to $HOME/.m2/java
                        .build());

        System.out.println(installation.getInstallPath()); // Folder containing the downloaded JDK/JRE
        System.out.println(installation.getJdkHomePath()); // JAVA_HOME path for this JDK/JRE
    }
```



##### Build:

```
mvn clean compile test
```

##### Run example:

```
mvn -q clean compile exec:java
```



##### Related links:

https://adoptopenjdk.net/


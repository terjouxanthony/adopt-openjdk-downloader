

Usage:

```xml
<plugin>
    <groupId>io.github.terjouxanthony</groupId>
    <artifactId>jdk-downloader-maven-plugin</artifactId>
    <version>0.0.1</version>
    <executions>
        <execution>
            <goals>
                <goal>download-java</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <imageType>jre</imageType>
        <os>windows</os>
        <architecture>x64</architecture>
    </configuration>
</plugin>
```


package io.github.terjouxanthony.adopt.openjdk.downloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongConsumer;

@Slf4j
public class Utils {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static long transferTo(InputStream in, OutputStream out, int bufferSize, LongConsumer nbBytesReadConsumer) throws IOException {
        long transferred = 0;
        byte[] buffer = new byte[bufferSize];
        int read;
        while ((read = in.read(buffer, 0, bufferSize)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
            nbBytesReadConsumer.accept(read);
        }
        return transferred;
    }

}

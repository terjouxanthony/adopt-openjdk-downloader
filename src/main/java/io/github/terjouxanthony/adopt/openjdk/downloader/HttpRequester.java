package io.github.terjouxanthony.adopt.openjdk.downloader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpRequester {

    private OkHttpClient httpClient;

    public HttpRequester() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.MINUTES)
                .build();
    }

    public Response httpGet(String path,
                            Map<String, String> queryParams,
                            Headers headers) throws IOException, HttpStatusException {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(path).newBuilder();
        queryParams.forEach(urlBuilder::addQueryParameter);
        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .get()
                .url(url)
                .headers(headers)
                .build();

        Call call = httpClient.newCall(request);

        final long start = System.nanoTime();
        Response response = call.execute();
        final long end = System.nanoTime();
        log.debug("Request {} took {} ms", url, Duration.ofNanos(end - start).toMillis());

        if (response.isSuccessful()) {
            return response;
        }

        final String bodyStr = response.body().string();
        throw new HttpStatusException(response.code(), response.request().url().toString(), bodyStr);
    }

    @Getter
    public static class HttpStatusException extends Exception {
        private final int statusCode;
        private final String request;
        private final String body;

        public <T> HttpStatusException(int statusCode, String request, String body) {
            super(String.format("Error %d for Http request %s : %s", statusCode, request, body));
            this.statusCode = statusCode;
            this.request = request;
            this.body = body;
        }
    }
}

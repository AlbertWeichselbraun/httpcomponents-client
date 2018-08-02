package org.apache.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.ByteArrayBuffer;


public class TestClient implements ResponseHandler<String> {

    protected static final PoolingHttpClientConnectionManager CONNECTION_MANAGER =
            new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .build());

    private static RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000)
            .setConnectionRequestTimeout(10000).setCookieSpec(CookieSpecs.STANDARD).build();
    private final HttpClientBuilder httpClientBuilder;

    private final static int maxContent = 1500000;

    public TestClient() {
        httpClientBuilder = getHttpClientBuilder("http test client");
    }

    public static void main(final String[] args) throws IOException {
        final TestClient t = new TestClient();
        String output;
        int cnt = 1;
        for (String url: new String[]{"http://www.vienna.at", "http://www.vienna.at/schwarzkappler-warnung-fuer-wien-informationen-zu-den-kontrollen/4115696", "http://www.vienna.at/bestwerte-im-ersten-halbjahr-wien-ohne-overtourism-problem/5874248"}) {
            output = t.fetch(url);
            Files.write(Paths.get(cnt+".html"), Arrays.asList(output));
            System.out.println("Fetched output for URL '" + url + "' with " + output.length() + " characters.");
            cnt ++;
        }		
    }

    private String fetch(String url) {
        HttpGet httpget = new HttpGet(url);
        httpget.setConfig(requestConfig);

        try  {
            CloseableHttpClient httpClient = httpClientBuilder.build();
            return httpClient.execute(httpget, this);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    private static HttpClientBuilder getHttpClientBuilder(String userAgent) {
        return HttpClients.custom().setUserAgent(userAgent).setConnectionManager(CONNECTION_MANAGER)
                .setConnectionManagerShared(true).disableRedirectHandling();
        // .disableAutomaticRetries();
    }

    public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
        boolean trimmed = false;

        byte[] bytes;

        bytes = toByteArray(response.getEntity(), maxContent, trimmed);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final byte[] toByteArray(final HttpEntity entity, int maxContent,
            boolean trimmed) throws IOException {

        if (entity == null)
            return new byte[] {};

        try  {
            final InputStream instream = entity.getContent();
            if (instream == null) {
                return new byte[] {};
            }
            int reportedLength = (int) entity.getContentLength();
            // set default size for buffer: 100 KB
            int bufferInitSize = 102400;
            if (reportedLength != -1) {
                bufferInitSize = reportedLength;
            }
            // avoid init of too large a buffer when we will trim anyway
            if (maxContent != -1 && bufferInitSize > maxContent) {
                bufferInitSize = maxContent;
            }

            final ByteArrayBuffer buffer = new ByteArrayBuffer(bufferInitSize);
            final byte[] tmp = new byte[4096];
            int lengthRead;
            while ((lengthRead = instream.read(tmp)) != -1) {
                // check whether we need to trim
                if (maxContent != -1 && buffer.length() + lengthRead > maxContent) {
                    buffer.append(tmp, 0, maxContent - buffer.length());
                    System.err.println("Warning - trimmed content to " + maxContent + " bytes.");
                    break;
                }
                buffer.append(tmp, 0, lengthRead);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            System.err.println(e);
            return new byte[] {};
        }
    }

}

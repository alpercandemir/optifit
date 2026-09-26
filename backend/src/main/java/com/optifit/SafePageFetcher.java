package com.optifit;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
class SafePageFetcher {

    private final List<String> hosts;

    SafePageFetcher(AppProperties properties) {
        hosts = properties.allowedMerchants();
    }

    boolean allowed(URI uri) {
        return ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && hosts.contains(uri.getHost().toLowerCase(Locale.ROOT)) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443));
    }

    Document fetch(String url) throws Exception {
        checkInterrupted();
        var uri = URI.create(url);
        for (int i = 0; i < 4; i++) {
            checkInterrupted();
            if (!allowed(uri)) {
                throw new IllegalArgumentException("Disallowed source");
            }
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                checkInterrupted();
                if (!publicAddress(address)) {
                    throw new IllegalArgumentException("Private address");
                }
            }
            var response = Jsoup.connect(uri.toString()).userAgent("OptiFit/0.1 (product verification)")
                    .followRedirects(false).timeout(5000).maxBodySize(2_000_000).ignoreHttpErrors(true).execute();
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                var location = response.header("Location");
                if (location == null) {
                    throw new IllegalArgumentException("No redirect location");
                }
                uri = uri.resolve(location);
                continue;
            }
            if (response.statusCode() != 200 || response.contentType() == null
                    || !response.contentType().contains("text/html")) {
                throw new IllegalArgumentException("Unverified source");
            }
            var document = response.parse();
            checkInterrupted();
            return document;
        }
        throw new IllegalArgumentException("Too many redirects");
    }

    static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Product verification was cancelled");
        }
    }

    static boolean publicAddress(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        var b = a.getAddress();
        if (b.length == 4) {
            int x = b[0] & 255, y = b[1] & 255;
            return (x != 0 && x < 224 && !(x == 100 && y >= 64 && y <= 127) && !(x == 192 && y == 0)
                    && !(x == 198 && (y == 18 || y == 19)));
        }
        // only global unicast IPv6; excludes ULA and mapped private addresses
        return (b[0] & 0xe0) == 0x20;
    }
}

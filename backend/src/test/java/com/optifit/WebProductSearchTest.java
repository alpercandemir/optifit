package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.optifit.config.AppProperties;
import com.optifit.exception.ApiException;
import com.optifit.model.Category;
import com.optifit.model.FaceProfile;
import com.optifit.model.ModelSuggestion;
import com.optifit.model.Preferences;
import com.optifit.model.Product;
import com.optifit.model.Shape;
import com.optifit.search.ProductPageParser;
import com.optifit.search.SafePageFetcher;
import com.optifit.search.WebProductSearch;

import tools.jackson.databind.json.JsonMapper;

class WebProductSearchTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = mock(HttpClient.class);
    private final AppProperties properties = settings("live", List.of("shop.example"));
    private final SafePageFetcher fetcher = spy(new SafePageFetcher(properties));
    private final Preferences preferences = new Preferences(Category.SUNGLASSES, null, "ANY", "ANY");
    private final FaceProfile face = new FaceProfile(true, 1, "Private face description", List.of(Shape.ROUND));

    private AppProperties settings(String mode, List<String> merchants) {
        return new AppProperties(mode, "private-gemini-key", "model", "private-tavily-key", merchants, 5, 20, 100, 3600,
                60);
    }

    private WebProductSearch search(AppProperties config) {
        return new WebProductSearch(config, fetcher, new ProductPageParser(json), json, http);
    }

    @SuppressWarnings("unchecked")
    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private String requestBody(HttpRequest request) {
        var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                subscriber.onNext(List.of(buffer));
            }

            @Override
            public void onError(Throwable error) {
                subscriber.onError(error);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
        return subscriber.getBody().toCompletableFuture().join();
    }

    @Test
    void discoversAndVerifiesProductsUsingOneBasicSearch() throws Exception {
        String url = "https://shop.example/product";
        respond(200, """
                {"results":[
                  {"url":"https://shop.example/product","content":"Untrusted price: 1 TRY"},
                  {"url":"https://shop.example/product"},
                  {"url":"https://evil.example/product"},
                  {"url":"http://shop.example/product"},
                  {"url":"not a URI"}
                ]}
                """);
        var page = Jsoup.parse("""
                <h1>Ray-Ban RB4171 Erika</h1>
                <script type="application/ld+json">
                {"@type":"Product","url":"/product","name":"Ray-Ban RB4171 Sunglasses",
                 "brand":{"name":"Ray-Ban"},"offers":{"price":"2500",
                 "priceCurrency":"TRY","availability":"https://schema.org/InStock"}}
                </script>
                """, url);
        doReturn(page).when(fetcher).fetch(url);

        var products = search(properties).search(preferences, face);
        assertThat(products).hasSize(1);
        assertThat(products.getFirst().price()).isEqualByComparingTo("2500");
        verify(fetcher).fetch(url);
        verify(fetcher, never()).fetch("https://evil.example/product");
        verify(fetcher, never()).fetch("http://shop.example/product");

        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(), any());
        var request = capture.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://api.tavily.com/search");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer private-tavily-key");
        String body = requestBody(request);
        var payload = json.readTree(body);
        assertThat(payload.path("search_depth").asString()).isEqualTo("basic");
        assertThat(payload.path("auto_parameters").asBoolean()).isFalse();
        assertThat(payload.path("include_domains").get(0).asString()).isEqualTo("shop.example");
        assertThat(payload.path("include_domains_mode").asString()).isEqualTo("restrict");
        assertThat(payload.path("max_results").asInt()).isEqualTo(12);
        assertThat(payload.path("query").asString()).isEqualTo("sunglasses round");
        assertThat(payload.path("country").asString()).isEqualTo("turkey");
        assertThat(body).doesNotContain("private-gemini-key", "private-tavily-key", "Private face description");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyModelSearchFallsBackOnceAndVerifiesRealProducts() throws Exception {
        var suggested = new FaceProfile(true, 1, "Private face description", List.of(Shape.ROUND),
                List.of(new ModelSuggestion("Ray-Ban", "RB9999", Category.SUNGLASSES)));
        HttpResponse<String> empty = mock(HttpResponse.class);
        HttpResponse<String> found = mock(HttpResponse.class);
        when(empty.statusCode()).thenReturn(200);
        when(empty.body()).thenReturn("{\"results\":[]}");
        when(found.statusCode()).thenReturn(200);
        when(found.body()).thenReturn("{\"results\":[{\"url\":\"https://shop.example/product\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(empty, found);
        doReturn(Jsoup.parse("""
                <h1>Ray-Ban RB4171</h1><script type="application/ld+json">
                {"@type":"Product","url":"/product","name":"Ray-Ban RB4171 Sunglasses","brand":"Ray-Ban",
                 "offers":{"price":"2500","priceCurrency":"TRY","availability":"https://schema.org/InStock"}}
                </script>
                """, "https://shop.example/product")).when(fetcher).fetch("https://shop.example/product");
        var products = search(properties).search(preferences, suggested);
        assertThat(products).hasSize(1);
        assertThat(products.getFirst().modelCode()).isEqualTo("RB4171");
        assertThat(products.getFirst().checkedAt()).isNotNull();
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(capture.capture(), any());
        var first = json.readTree(requestBody(capture.getAllValues().get(0)));
        var fallback = json.readTree(requestBody(capture.getAllValues().get(1)));
        assertThat(first.path("query").asString()).contains("RB9999");
        assertThat(fallback.path("query").asString()).isEqualTo("sunglasses round");
        assertThat(fallback.path("search_depth").asString()).isEqualTo("basic");
        assertThat(fallback.path("include_domains").get(0).asString()).isEqualTo("shop.example");
        assertThat(fallback.path("auto_parameters").asBoolean()).isFalse();
        verify(fetcher).fetch("https://shop.example/product");
    }

    @Test
    void emptyFallbackIsBoundedAndDoesNotSubstituteDemoProducts() throws Exception {
        respond(200, "{\"results\":[]}");
        var suggested = new FaceProfile(true, 1, "", List.of(Shape.ROUND),
                List.of(new ModelSuggestion("Ray-Ban", "RB9999", Category.SUNGLASSES)));
        assertThat(search(properties).search(preferences, suggested)).isEmpty();
        verify(http, times(2)).send(any(), any());
        verify(fetcher, never()).fetch(anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 432, 433, 500})
    void providerErrorsFailWithoutRetryOrLeakingResponse(int status) throws Exception {
        respond(status, "private provider diagnostics");
        assertThatThrownBy(() -> search(properties).search(preferences, face)).isInstanceOf(ApiException.class)
                .hasMessage("The product search service is unavailable. Please try again later.");
        verify(http).send(any(), any());
        verify(fetcher, never()).fetch(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"results\":null}", "{\"results\":{}}", "not json"})
    void malformedResponsesAreNotReportedAsNoMatches(String body) throws Exception {
        respond(200, body);
        assertThatThrownBy(() -> search(properties).search(preferences, face)).isInstanceOf(ApiException.class);
        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    void verificationTimeoutKeepsCompletedProductsWithoutWaitingForUnresponsiveFetch() throws Exception {
        String fast = "https://shop.example/fast", slow = "https://shop.example/slow";
        var release = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doReturn(Jsoup.parse("""
                <h1>Ray-Ban RB4171</h1><script type="application/ld+json">
                {"@type":"Product","url":"/fast","name":"Ray-Ban RB4171 Sunglasses","brand":"Ray-Ban"}
                </script>
                """, fast)).when(fetcher).fetch(fast);
        doAnswer(invocation -> {
            try {
                // Simulate a transport that does not promptly stop on interrupt.
                while (true) {
                    try {
                        release.await();
                        return Jsoup.parse("<h1>Category</h1>", slow);
                    } catch (InterruptedException e) {
                        cancelled.countDown();
                    }
                }
            } finally {
                finished.countDown();
            }
        }).when(fetcher).fetch(slow);
        try {
            var products = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> search(properties)
                    .verifyPages(List.of(fast, slow), Category.SUNGLASSES, Duration.ofMillis(250)));
            assertThat(products).hasSize(1);
            assertThat(products.getFirst().modelCode()).isEqualTo("RB4171");
            assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void verificationFailuresAreNotReportedAsNoMatches() throws Exception {
        String url = "https://shop.example/failed";
        respond(200, "{\"results\":[{\"url\":\"" + url + "\"}]}");
        doThrow(new java.io.IOException("private provider diagnostics")).when(fetcher).fetch(url);
        assertThatThrownBy(() -> search(properties).search(preferences, face))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SEARCH_UNAVAILABLE"))
                .hasMessage("Product sources could not be verified. Please try again later.");
    }

    @Test
    void verificationTimeoutWithoutProductsIsNotReportedAsNoMatches() throws Exception {
        String url = "https://shop.example/slow";
        var finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            try {
                new CountDownLatch(1).await();
                return null;
            } finally {
                finished.countDown();
            }
        }).when(fetcher).fetch(url);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThatThrownBy(
                    () -> search(properties).verifyPages(List.of(url), Category.SUNGLASSES, Duration.ofMillis(250)))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.code()).isEqualTo("SEARCH_UNAVAILABLE"));
        });
        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancellingSearchInterruptsFetchAndPreservesCallerInterrupt() throws Exception {
        String url = "https://shop.example/slow";
        respond(200, "{\"results\":[{\"url\":\"" + url + "\"}]}");
        var entered = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        doAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                return null;
            } finally {
                finished.countDown();
            }
        }).when(fetcher).fetch(url);
        var caller = Thread.ofPlatform().daemon().start(() -> {
            try {
                search(properties).search(preferences, face);
            } catch (Throwable e) {
                failure.set(e);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.code()).isEqualTo("CANCELLED"));
            assertThat(interrupted).isTrue();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            caller.interrupt();
            caller.join(2000);
        }
    }

    @Test
    void categoryPageWithoutProductEvidenceIsAValidEmptyResult() throws Exception {
        String url = "https://shop.example/category";
        respond(200, "{\"results\":[{\"url\":\"" + url + "\",\"content\":\"RB4171 2500 TRY\"}]}");
        doReturn(Jsoup.parse("<h1>Sunglasses</h1><a href='/product'>Ray-Ban RB4171</a>", url)).when(fetcher).fetch(url);
        assertThat(search(properties).search(preferences, face)).isEmpty();
    }

    @Test
    void followsListingLinksAndVerifiesDetailsWithoutTrustingListingPrices() throws Exception {
        String category = "https://shop.example/category", detail = "https://shop.example/detail";
        respond(200, "{\"results\":[{\"url\":\"" + category + "\"}]}");
        doReturn(Jsoup.parse("""
                <h1>Sunglasses</h1><script type="application/ld+json">
                {"@type":"ItemList","itemListElement":[
                 {"url":"/detail","price":"1"},{"url":"/detail#duplicate"},
                 {"url":"http://shop.example/unsafe"},{"url":"https://evil.example/product"},
                 {"url":"https://shop.example:8443/product"}]}
                </script>
                """, category)).when(fetcher).fetch(category);
        doReturn(Jsoup.parse("""
                <h1>Ray-Ban RB2140</h1><script type="application/ld+json">
                {"@type":"Product","url":"/detail","name":"Ray-Ban RB2140 Sunglasses","brand":"Ray-Ban",
                 "offers":{"price":"8600","priceCurrency":"TRY"}}
                </script>
                """, detail)).when(fetcher).fetch(detail);
        var products = search(properties).search(preferences, face);
        assertThat(products).hasSize(1);
        assertThat(products.getFirst().price()).isEqualByComparingTo("8600");
        assertThat(products.getFirst().productUrl()).isEqualTo(detail);
        verify(fetcher, times(1)).fetch(detail);
        verify(fetcher, never()).fetch("http://shop.example/unsafe");
        verify(fetcher, never()).fetch("https://evil.example/product");
        verify(fetcher, never()).fetch("https://shop.example:8443/product");
        verify(http, times(1)).send(any(), any());
    }

    @Test
    void followsOnlyOneLevelOfLinks() throws Exception {
        String category = "https://shop.example/category", nested = "https://shop.example/nested";
        doReturn(Jsoup.parse("""
                <script type="application/ld+json">
                {"@type":"ItemList","itemListElement":[{"url":"/nested"}]}
                </script>
                """, category)).when(fetcher).fetch(category);
        doReturn(Jsoup.parse("""
                <script type="application/ld+json">
                {"@type":"ItemList","itemListElement":[{"url":"/third-level"}]}
                </script>
                """, nested)).when(fetcher).fetch(nested);
        assertThat(search(properties).verifyPages(List.of(category), Category.SUNGLASSES, Duration.ofSeconds(2)))
                .isEmpty();
        verify(fetcher).fetch(nested);
        verify(fetcher, never()).fetch("https://shop.example/third-level");
    }

    @Test
    void capsDiscoveredDetailRequestsAcrossListingPages() throws Exception {
        var entries = java.util.stream.IntStream.range(0, 40).mapToObj(i -> "{\"url\":\"/detail-" + i + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        doAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.endsWith("/category")) {
                return Jsoup.parse("<script type='application/ld+json'>{\"@type\":\"ItemList\",\"itemListElement\":["
                        + entries + "]}</script>", url);
            }
            return Jsoup.parse("<h1>Not a verified product</h1>", url);
        }).when(fetcher).fetch(anyString());
        assertThat(search(properties).verifyPages(List.of("https://shop.example/category"), Category.SUNGLASSES,
                Duration.ofSeconds(2))).isEmpty();
        verify(fetcher, times(25)).fetch(anyString());
        verify(fetcher, never()).fetch("https://shop.example/detail-24");
    }

    @Test
    void emptyResultsAreValid() throws Exception {
        respond(200, "{\"results\":[]}");
        assertThat(search(properties).search(preferences, face)).isEmpty();
        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    void demoAndEmptyMerchantListDoNotSpendSearchCredits() {
        assertThat(search(settings("demo", List.of("shop.example"))).search(preferences, face)).hasSize(3);
        assertThat(search(settings("live", List.of())).search(preferences, face)).isEmpty();
        verifyNoInteractions(http, fetcher);
    }
}

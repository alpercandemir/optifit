package com.optifit.search;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.optifit.config.AppProperties;
import com.optifit.exception.ApiException;
import com.optifit.model.Category;
import com.optifit.model.FaceProfile;
import com.optifit.model.Preferences;
import com.optifit.model.Product;
import com.optifit.model.Shape;
import com.optifit.service.ModelSuggestions;
import com.optifit.service.RecommendationRanker;

import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebProductSearch implements ProductSearchProvider {

    private final AppProperties properties;
    private final SafePageFetcher fetcher;
    private final ProductPageParser parser;
    private final ObjectMapper json;
    private final HttpClient http;
    private final ProductLinkExtractor links;

    @Autowired
    public WebProductSearch(AppProperties properties, SafePageFetcher fetcher, ProductPageParser parser,
            ObjectMapper json) {
        this(properties, fetcher, parser, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
    }

    public WebProductSearch(AppProperties properties, SafePageFetcher fetcher, ProductPageParser parser,
            ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.fetcher = fetcher;
        this.parser = parser;
        this.json = json;
        this.http = http;
        this.links = new ProductLinkExtractor(json);
    }

    @Override
    public List<Product> search(Preferences preferences, FaceProfile face) {
        if (properties.demo()) {
            return demoProducts(preferences.category());
        }
        if (properties.allowedMerchants().isEmpty()) {
            return List.of();
        }
        // Both searches share the original 24-second search/verification budget.
        long deadline = System.nanoTime() + Duration.ofSeconds(24).toNanos();
        int modelCount = ModelSuggestions.valid(face.suggestedModels(), preferences.category()).size();
        var products = searchOnce(searchQuery(preferences, face), preferences.category(),
                modelCount == 0 ? "shape" : "models", deadline);
        boolean eligible = products.stream()
                .anyMatch(product -> !"OUT_OF_STOCK".equals(product.availability())
                        && (preferences.budget() == null || (product.price() != null && "TRY".equals(product.currency())
                                && product.price().compareTo(preferences.budget()) <= 0)));
        if (modelCount > 0 && !eligible) {
            log.info("Model search found no eligible products; falling back to category/shape search");
            return searchOnce(shapeQuery(preferences, face), preferences.category(), "shape-fallback", deadline);
        }
        return products;
    }

    private List<Product> searchOnce(String query, Category category, String strategy, long deadline) {
        log.info("Tavily search started: category={}, strategy={}", category, strategy);
        long started = System.nanoTime();
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("query", query);
            payload.put("topic", "general");
            // Keep each search at one credit; never automatically upgrade to advanced.
            payload.put("search_depth", "basic");
            payload.put("auto_parameters", false);
            payload.put("max_results", 12);
            payload.put("include_domains", properties.allowedMerchants());
            payload.put("include_domains_mode", "restrict");
            payload.put("country", "turkey");
            payload.put("include_answer", false);
            payload.put("include_raw_content", false);
            payload.put("include_images", false);
            var request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                    .header("Authorization", "Bearer " + properties.searchKey())
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .timeout(remaining(deadline, Duration.ofSeconds(8)))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Tavily response: status={}, elapsedMs={}", response.statusCode(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (response.statusCode() != 200) {
                throw new ApiException(502, "SEARCH_UNAVAILABLE",
                        "The product search service is unavailable. Please try again later.");
            }
            var results = json.readTree(response.body()).path("results");
            if (!results.isArray()) {
                log.warn("Tavily response has no valid results array");
                throw new ApiException(502, "SEARCH_UNAVAILABLE",
                        "The product search service returned an invalid response. Please try again later.");
            }
            var urls = new LinkedHashSet<String>();
            for (var result : results) {
                String url = result.path("url").asString("");
                try {
                    if (fetcher.allowed(URI.create(url))) {
                        urls.add(url);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid search-result URLs are not eligible product sources.
                }
            }
            log.info("Tavily search: results={}, allowedUrls={}", results.size(), urls.size());
            return verifyPages(urls.stream().limit(12).toList(), category, remaining(deadline, Duration.ofSeconds(16)));
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "CANCELLED", "The request was cancelled.");
        } catch (Exception e) {
            // Status/type only: provider bodies and exception messages may contain secrets.
            log.warn("Product search failed: type={}, elapsedMs={}", e.getClass().getSimpleName(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            throw new ApiException(502, "SEARCH_UNAVAILABLE",
                    "Product sources could not be verified. Please try again later.");
        }
    }

    private static Duration remaining(long deadline, Duration maximum) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new ApiException(502, "SEARCH_UNAVAILABLE",
                    "Product sources could not be verified. Please try again later.");
        }
        return Duration.ofNanos(Math.min(nanos, maximum.toNanos()));
    }

    public static String searchQuery(Preferences preferences, FaceProfile face) {
        var models = ModelSuggestions.valid(face.suggestedModels(), preferences.category());
        if (!models.isEmpty()) {
            String candidates = models.stream().map(model -> "\"" + model.brand() + " " + model.modelCode() + "\"")
                    .collect(Collectors.joining(" OR "));
            return "(" + candidates + ") "
                    + (preferences.category() == Category.SUNGLASSES ? "güneş gözlüğü" : "optik gözlük çerçevesi");
        }
        return shapeQuery(preferences, face);
    }

    private static String shapeQuery(Preferences preferences, FaceProfile face) {
        return (preferences.category() == Category.OPTICAL ? "optical eyeglass frames" : "sunglasses") + " "
                + RecommendationRanker.label(face.preferredShapes().getFirst()).toLowerCase(Locale.ROOT);
    }

    private record PageRequest(String url, boolean discoverProducts) {
    }

    private record PageResult(Optional<Product> product, List<String> links) {
    }

    public List<Product> verifyPages(List<String> urls, Category category, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        var pending = new ArrayDeque<PageRequest>();
        var seen = new LinkedHashSet<String>();
        for (String url : urls.stream().limit(12).toList()) {
            enqueue(pending, seen, url, true);
        }
        var executor = Executors.newFixedThreadPool(4,
                Thread.ofPlatform().daemon().name("product-verification-", 0).factory());
        var completion = new ExecutorCompletionService<PageResult>(executor);
        var active = new LinkedHashSet<Future<PageResult>>();
        var products = new LinkedHashMap<String, Product>();
        int rejected = 0, failed = 0, discovered = 0, timedOut = 0;
        try {
            while (!pending.isEmpty() || !active.isEmpty()) {
                SafePageFetcher.checkInterrupted();
                if (System.nanoTime() >= deadline) {
                    timedOut = active.size();
                    break;
                }
                while (active.size() < 4 && !pending.isEmpty()) {
                    var page = pending.removeFirst();
                    active.add(completion.submit(() -> inspectPage(page, category)));
                }
                // Process completed pages immediately, so discovered product details can
                // start while slower search-result pages are still downloading.
                var future = completion.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (future == null) {
                    timedOut = active.size();
                    break;
                }
                active.remove(future);
                try {
                    var page = future.get();
                    if (page.product().isPresent()) {
                        var product = page.product().get();
                        products.putIfAbsent(product.productUrl(), product);
                    } else {
                        rejected++;
                        // At most 24 extra detail pages, one hop only. Product candidates
                        // take priority over the remaining category/guide pages.
                        var detailPages = new ArrayDeque<PageRequest>();
                        for (String url : page.links()) {
                            if (discovered >= 24) {
                                break;
                            }
                            if (enqueue(detailPages, seen, url, false)) {
                                discovered++;
                            }
                        }
                        while (!detailPages.isEmpty()) {
                            pending.addFirst(detailPages.removeLast());
                        }
                    }
                } catch (ExecutionException e) {
                    failed++;
                    log.debug("Product verification failed: type={}", e.getCause().getClass().getSimpleName());
                }
            }
            SafePageFetcher.checkInterrupted();
            log.info(
                    "Product verification: verified={}, discovered={}, rejected={}, failed={}, timedOut={}, skipped={}",
                    products.size(), discovered, rejected, failed, timedOut, pending.size());
            if (products.isEmpty() && (failed > 0 || timedOut > 0 || !pending.isEmpty())) {
                throw new ApiException(502, "SEARCH_UNAVAILABLE",
                        "Product sources could not be verified. Please try again later.");
            }
            return new ArrayList<>(products.values());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Product verification cancelled by the calling job");
            throw e;
        } finally {
            active.forEach(future -> future.cancel(true));
            // close() would wait indefinitely for an unresponsive transport.
            executor.shutdownNow();
        }
    }

    private boolean enqueue(ArrayDeque<PageRequest> queue, LinkedHashSet<String> seen, String url, boolean discover) {
        try {
            var uri = URI.create(url).normalize();
            String canonical = uri.toString().split("#", 2)[0];
            if (fetcher.allowed(uri) && seen.add(canonical)) {
                queue.addLast(new PageRequest(canonical, discover));
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            // Every discovered URL must pass the same allowlist as search results.
        }
        return false;
    }

    private PageResult inspectPage(PageRequest page, Category category) throws Exception {
        SafePageFetcher.checkInterrupted();
        var document = fetcher.fetch(page.url());
        SafePageFetcher.checkInterrupted();
        var product = parser.parse(document, category);
        var discovered = product.isEmpty() && page.discoverProducts() ? links.extract(document) : List.<String>of();
        SafePageFetcher.checkInterrupted();
        return new PageResult(product, discovered);
    }

    public static List<Product> demoProducts(Category category) {
        // Real discovered source links, no invented stock/price or claims of live
        // verification.
        // Optical demo intentionally returns no match rather than relabeling sunglasses
        // as prescription frames.
        if (category == Category.OPTICAL) {
            return List.of();
        }
        return List.of(demo("demo-wayfarer", "RB2140", "Wayfarer Tortoise", Shape.RECTANGULAR, "BROWN",
                "ray-ban-rb-x1-2140-902-5022150-erkek-gunes-gozlukleri_81552",
                "https://stn-atasun.mncdn.com/Content/media/ProductImg/original/gu021638-rb-x1-2140-902-5022150-639107252383816776.jpg"),
                demo("demo-erika", "RB4171", "Erika Classic", Shape.ROUND, "BLACK",
                        "rayban-rb-4171-6228g-5418-unisex-gunes-gozlukleri_78158",
                        "https://stn-atasun.mncdn.com/Content/media/ProductImg/original/gu020929-rb-4171-6228g-5418145-638288289028848005.png"),
                demo("demo-reverse", "RBR0103S", "Round Reverse", Shape.ROUND, "GOLD",
                        "ray-ban-rb-0rbr0103s-001vr-5321140-unisex-gunes-gozlukleri_83922",
                        "https://stn-atasun.mncdn.com/Content/media/ProductImg/original/gu036708-rb-0rbr0103s-001vr-5321140-638741241686624228.png"));
    }

    private static Product demo(String id, String model, String name, Shape shape, String color, String path,
            String imageUrl) {
        return new Product(id, "Ray-Ban", model, name, Category.SUNGLASSES, shape, color, "CLASSIC",
                Map.of("shape", RecommendationRanker.label(shape)), "Atasun Optik",
                "https://www.atasunoptik.com.tr/" + path, "https://www.atasunoptik.com.tr/", null, "TRY", "UNKNOWN",
                null, imageUrl);
    }
}

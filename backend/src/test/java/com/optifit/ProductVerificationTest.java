package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.optifit.Models.Category;
import com.optifit.Models.FaceProfile;
import com.optifit.Models.Preferences;
import com.optifit.Models.Product;
import com.optifit.Models.Shape;

import tools.jackson.databind.json.JsonMapper;

class ProductVerificationTest {

    AppProperties settings() {
        return new AppProperties("demo", "", "gemini-2.5-flash-lite", "", List.of("www.atasunoptik.com.tr"), 5, 20, 100,
                3600, 60);
    }

    String html(String name, String heading) {
        return ("<h1>" + heading + "</h1><script type='application/ld+json'>"
                + "{\"@type\":\"Product\",\"url\":\"/product\",\"name\":\"" + name
                + "\",\"brand\":{\"name\":\"Ray-Ban\"},\"description\":\"Yuvarlak klasik\",\"offers\":{\"price\":\"2500.00\",\"priceCurrency\":\"TRY\",\"availability\":\"https://schema.org/InStock\"}}</script>");
    }

    @Test
    void parsesEvidenceAndRejectsCategoryAndHeadingMismatches() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        var doc = Jsoup.parse(html("Ray-Ban RB4171 Güneş Gözlüğü", "Ray-Ban RB4171 Erika"),
                "https://www.atasunoptik.com.tr/product");
        var product = parser.parse(doc, Category.SUNGLASSES).orElseThrow();
        assertThat(product.modelCode()).isEqualTo("RB4171");
        assertThat(product.price()).isEqualByComparingTo("2500");
        assertThat(product.shape()).isEqualTo(Shape.ROUND);
        assertThat(parser.parse(doc, Category.OPTICAL)).isEmpty();
        assertThat(parser.parse(Jsoup.parse(html("Ray-Ban RB4171 Güneş Gözlüğü", "Ray-Ban RB2140"), doc.location()),
                Category.SUNGLASSES)).isEmpty();
        assertThat(parser.parse(Jsoup.parse("<h1>Ray-Ban RB4171</h1>", doc.location()), Category.SUNGLASSES)).isEmpty();
    }

    @Test
    void verifiesAtasunProductWhenModelFieldContainsTurkishShape() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        // Relevant fields observed on Atasun's RB3447 product page.
        var html = """
                <h1>Ray-Ban RB3447 Round Metal</h1>
                <script type="application/ld+json">
                {"@type":"Product","url":"/product","name":"RB 3447 001 50*21*145","brand":"Ray-Ban",
                 "model":"Yuvarlak","category":"Sunglasses",
                 "offers":{"price":"8600.00","priceCurrency":"TRY",
                           "availability":"https://schema.org/InStock"}}
                </script>
                """;
        var doc = Jsoup.parse(html, "https://www.atasunoptik.com.tr/product");
        var product = parser.parse(doc, Category.SUNGLASSES).orElseThrow();
        assertThat(product.modelCode()).isEqualTo("RB3447");
        assertThat(product.shape()).isEqualTo(Shape.ROUND);
        assertThat(product.price()).isEqualByComparingTo("8600");
        assertThat(parser.parse(doc, Category.OPTICAL)).isEmpty();
        doc.selectFirst("h1").text("Ray-Ban RB2140");
        assertThat(parser.parse(doc, Category.SUNGLASSES)).isEmpty();
        doc.selectFirst("h1").text("Ray-Ban RB3447 Round Metal");
        doc.selectFirst("script").html(doc.selectFirst("script").data().replace("Yuvarlak", "RB2140"));
        assertThat(parser.parse(doc, Category.SUNGLASSES)).isEmpty();
    }

    @Test
    void acceptsOnlySingleProductDetailEvidenceForTheFetchedUrl() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        String detail = html("Ray-Ban RB4171 Güneş Gözlüğü", "Ray-Ban RB4171 Erika");
        String url = "https://www.atasunoptik.com.tr/product";
        assertThat(parser.parse(Jsoup.parse(detail, url + "?utm_source=search"), Category.SUNGLASSES)).isPresent();
        for (String bad : List.of(detail.replace("/product", "/different-product"),
                detail.replace("/product", "/product?id=other"), detail.replace("\"url\":\"/product\",", ""),
                detail + "<link rel='canonical' href='/category'>",
                detail + "<script type='application/ld+json'>{\"@type\":\"CollectionPage\"}</script>",
                detail + "<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"ItemList\"}]}</script>",
                detail + detail, detail.replace("\"price\":", "\"url\":\"/other\",\"price\":"),
                detail.replace("Ray-Ban RB4171 Erika", "Ray-Ban RB41710 Erika"), detail.replace("RB4171", "C002"))) {
            assertThat(parser.parse(Jsoup.parse(bad, url), Category.SUNGLASSES)).isEmpty();
        }
        assertThat(parser.parse(Jsoup.parse(detail, "https://www.atasunoptik.com.tr/category"), Category.SUNGLASSES))
                .isEmpty();
    }

    @Test
    void vendorLeadingZeroDoesNotTurnColourCodeIntoModelIdentity() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        var doc = Jsoup.parse(html("Inesta 0M018 C002 Güneş Gözlüğü", "Inesta M018 C002"),
                "https://www.atasunoptik.com.tr/product");
        assertThat(parser.parse(doc, Category.SUNGLASSES).orElseThrow().modelCode()).isEqualTo("M018");
    }

    @Test
    void extractsOnlyProductImagesFromTrustedHttpsSourcesAndPreservesVariant() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        String url = "https://www.atasunoptik.com.tr/product";
        String expected = "https://stn-atasun.mncdn.com/Content/media/ProductImg/model.png";
        String base = html("Ray-Ban RB4171 Güneş Gözlüğü", "Ray-Ban RB4171");
        for (String image : List.of("\"" + expected + "\"", "[\"http://bad.example/a\",\"" + expected + "\"]",
                "{\"@type\":\"ImageObject\",\"contentUrl\":\"" + expected + "\"}", "{\"url\":\"" + expected + "\"}")) {
            var product = parser
                    .parse(Jsoup.parse(base.replace("\"brand\":", "\"image\":" + image + ",\"brand\":"), url),
                            Category.SUNGLASSES)
                    .orElseThrow();
            assertThat(product.imageUrl()).isEqualTo(expected);
            var result = new RecommendationRanker().rank(List.of(product),
                    new Preferences(Category.SUNGLASSES, null, "ANY", "ANY"),
                    new FaceProfile(true, 1, "", List.of(Shape.ROUND)), false);
            assertThat(result.getFirst().imageUrl()).isEqualTo(expected);
            assertThat(result.getFirst().offers().getFirst().productUrl()).isEqualTo(url);
        }
        for (String value : List.of("http://www.atasunoptik.com.tr/a", "javascript:alert(1)",
                "https://stn-atasun.mncdn.com.evil.example/a", "https://user@stn-atasun.mncdn.com/a",
                "https://stn-atasun.mncdn.com:8443/a", "https://127.0.0.1/a", "")) {
            var doc = Jsoup.parse(base.replace("\"brand\":", "\"image\":\"" + value + "\",\"brand\":"), url);
            assertThat(parser.parse(doc, Category.SUNGLASSES).orElseThrow().imageUrl()).isNull();
        }
        var relative = Jsoup.parse(base.replace("\"brand\":", "\"image\":\"/images/model.jpg\",\"brand\":"), url);
        relative.head().append("<base href='https://evil.example/'>");
        assertThat(parser.parse(relative, Category.SUNGLASSES).orElseThrow().imageUrl())
                .isEqualTo("https://www.atasunoptik.com.tr/images/model.jpg");
        assertThat(parser.parse(Jsoup.parse(base, url), Category.SUNGLASSES).orElseThrow().imageUrl()).isNull();
    }

    @Test
    void opticalProductCanBeVerifiedWithoutSunglassSubstitution() {
        var parser = new ProductPageParser(JsonMapper.builder().build());
        var doc = Jsoup.parse(html("Ray-Ban RX5228 Optik Çerçeve", "Ray-Ban RX5228"),
                "https://www.atasunoptik.com.tr/product");
        assertThat(parser.parse(doc, Category.OPTICAL)).isPresent();
        assertThat(parser.parse(doc, Category.SUNGLASSES)).isEmpty();
    }

    @Test
    void interruptedFetchStopsBeforeAccessingTheNetwork() {
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> new SafePageFetcher(settings()).fetch("https://www.atasunoptik.com.tr/product"))
                    .isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void urlPolicyRejectsPrivateNetworksCredentialsAndUnapprovedHosts() throws Exception {
        var fetcher = new SafePageFetcher(settings());
        assertThat(fetcher.allowed(URI.create("https://www.atasunoptik.com.tr/product"))).isTrue();
        for (var url : List.of("http://www.atasunoptik.com.tr/product", "https://www.atasunoptik.com.tr.evil.com/a",
                "https://user@www.atasunoptik.com.tr/a", "https://www.atasunoptik.com.tr:8080/a",
                "https://127.0.0.1/a")) {
            assertThat(fetcher.allowed(URI.create(url))).isFalse();
        }
        for (var ip : List.of("127.0.0.1", "10.0.0.1", "169.254.169.254", "192.168.0.1", "100.64.0.1", "::1", "fc00::1",
                "fe80::1")) {
            assertThat(SafePageFetcher.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();
        }
        assertThat(SafePageFetcher.publicAddress(InetAddress.getByName("8.8.8.8"))).isTrue();
    }

    Product p(String id, String model, BigDecimal price, String stock, Category category) {
        return new Product(id, "Ray-Ban", model, "Test", category, Shape.ROUND, "BLACK", "CLASSIC", Map.of(), "Shop",
                "https://shop.example/product", "https://shop.example/", price, "TRY", stock, Instant.now(), null);
    }

    @Test
    void deduplicatesVariantsAndExcludesUnavailableOverBudgetAndWrongCategory() {
        var face = new FaceProfile(true, 1, "", List.of(Shape.ROUND));
        var prefs = new Preferences(Category.SUNGLASSES, new BigDecimal("3000"), "ANY", "ANY");
        var products = List.of(p("1", "RB4171", new BigDecimal("2500"), "IN_STOCK", Category.SUNGLASSES),
                p("2", "RB4171", new BigDecimal("2600"), "IN_STOCK", Category.SUNGLASSES),
                p("3", "RB2140", new BigDecimal("3500"), "IN_STOCK", Category.SUNGLASSES),
                p("4", "RB3016", null, "IN_STOCK", Category.SUNGLASSES),
                p("5", "RB3025", BigDecimal.TEN, "OUT_OF_STOCK", Category.SUNGLASSES),
                p("6", "RX5228", BigDecimal.TEN, "IN_STOCK", Category.OPTICAL));
        var result = new RecommendationRanker().rank(products, prefs, face, false);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().offers()).hasSize(2);
    }

    @Test
    void liveModeCannotStartWithoutBothKeys() {
        assertThatThrownBy(() -> new AppProperties("live", "", "model", "", List.of(), 5, 20, 100, 3600, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quotaCannotBeBypassedByChangingOnlySession() {
        var p = new AppProperties("demo", "", "model", "", List.of(), 1, 2, 3, 3600, 60);
        var limiter = new UsageLimiter(p);
        limiter.reserve("a", "same-ip");
        assertThatThrownBy(() -> limiter.reserve("a", "same-ip")).isInstanceOf(ApiException.class);
        limiter.reserve("b", "same-ip");
        assertThatThrownBy(() -> limiter.reserve("c", "same-ip")).isInstanceOf(ApiException.class);
    }
}

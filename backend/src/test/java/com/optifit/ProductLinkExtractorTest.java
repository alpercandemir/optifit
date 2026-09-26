package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.optifit.search.ProductLinkExtractor;

import tools.jackson.databind.json.JsonMapper;

class ProductLinkExtractorTest {

    private final ProductLinkExtractor extractor = new ProductLinkExtractor(JsonMapper.builder().build());

    @Test
    void extractsObservedAtasunProductCardsWithoutFollowingNavigationOrHtmlBase() {
        var page = Jsoup.parse("""
                <base href="https://evil.example/">
                <a class="item link" href="/kurumsal/nerelerdeyiz_1476">Stores</a>
                <a class="text-left p-name w-100 f-3"
                   href="rayban-rb-x1-2140-901-5022-unisex-gunes-gozlukleri_78185">RB2140 Wayfarer</a>
                <a class="p-name" href="/rayban-rb-x1-2140-901-5022-unisex-gunes-gozlukleri_78185#details">Duplicate</a>
                <a class="p-name" href="https://evil.example/product">Untrusted</a>
                <a class="p-name" href="https://[broken">Broken</a>
                <a href="?p=2">Next page</a>
                """, "https://www.atasunoptik.com.tr/rayban-wayfarer-gunes-gozlukleri");
        assertThat(extractor.extract(page)).containsExactly(
                "https://www.atasunoptik.com.tr/rayban-rb-x1-2140-901-5022-unisex-gunes-gozlukleri_78185");
    }

    @Test
    void extractsStructuredItemListLinksAndProductMicrodata() {
        var page = Jsoup.parse("""
                <script type="application/ld+json">invalid</script>
                <script type="application/ld+json">
                {"@graph":[{"@type":"ItemList","itemListElement":[
                 {"@type":"ListItem","item":{"@type":"Product","url":"/first"}},
                 {"@type":"ListItem","url":"/second"},
                 {"@type":"ListItem","item":"/third"}]}]}
                </script>
                <div itemtype="https://schema.org/Product"><a itemprop="url" href="/fourth">Product</a></div>
                """, "https://shop.example/category");
        assertThat(extractor.extract(page)).containsExactly("https://shop.example/first", "https://shop.example/second",
                "https://shop.example/third", "https://shop.example/fourth");
    }
}

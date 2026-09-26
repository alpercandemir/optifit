package com.optifit.search;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Document;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Discovers detail-page candidates; listing text is never treated as product
 * evidence.
 */
@RequiredArgsConstructor
public final class ProductLinkExtractor {

    private static final int MAX_LINKS = 24;
    private final ObjectMapper json;

    public List<String> extract(Document document) {
        var candidates = new LinkedHashSet<String>();
        for (var script : document.select("script[type=application/ld+json]")) {
            try {
                collectLists(json.readTree(script.data()), candidates);
            } catch (RuntimeException ignored) {
                // Malformed listing metadata is not a reason to trust arbitrary links.
            }
        }
        for (var link : document.select("[itemtype$='/Product'] a[itemprop=url][href]")) {
            candidates.add(link.attr("href"));
        }
        var base = URI.create(document.location());
        if ("www.atasunoptik.com.tr".equals(base.getHost())) {
            // Observed merchant product-card links, excluding navigation and pagination.
            for (var link : document.select("a.p-name[href]")) {
                candidates.add(link.attr("href"));
            }
        }
        var urls = new LinkedHashSet<String>();
        for (String candidate : candidates) {
            try {
                // Resolve against the fetched URL, not an untrusted HTML <base> tag.
                var uri = base.resolve(candidate).normalize();
                if (!candidate.isBlank() && base.getHost() != null && base.getHost().equals(uri.getHost())) {
                    urls.add(uri.toString().split("#", 2)[0]);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid links are not candidates.
            }
            if (urls.size() >= MAX_LINKS) {
                break;
            }
        }
        return List.copyOf(urls);
    }

    private void collectLists(JsonNode node, Set<String> urls) {
        if (node.isArray()) {
            node.forEach(child -> collectLists(child, urls));
        } else if (node.isObject()) {
            if (hasType(node, "ItemList") && node.path("itemListElement").isArray()) {
                for (var entry : node.path("itemListElement")) {
                    var item = entry.has("item") ? entry.path("item") : entry;
                    String url = item.isString() ? item.asString() : item.path("url").asString("");
                    if (url.isBlank()) {
                        url = entry.path("url").asString("");
                    }
                    if (!url.isBlank()) {
                        urls.add(url);
                    }
                }
            }
            if (node.has("@graph")) {
                collectLists(node.path("@graph"), urls);
            }
        }
    }

    private boolean hasType(JsonNode node, String type) {
        var value = node.path("@type");
        if (value.isArray()) {
            for (var item : value) {
                if (type.equals(item.asString(""))) {
                    return true;
                }
            }
        }
        return type.equals(value.asString(""));
    }
}

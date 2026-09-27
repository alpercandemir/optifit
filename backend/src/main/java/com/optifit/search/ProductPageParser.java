package com.optifit.search;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.optifit.model.Category;
import com.optifit.model.Product;
import com.optifit.model.Shape;
import com.optifit.service.RecommendationRanker;
import com.optifit.util.TextNormalizer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductPageParser {

    private static final Pattern MODEL = Pattern.compile("(?i)\\b0?([a-z]{1,5})[ -]?(\\d{3,6}[a-z]?(?:/[a-z])?)\\b");
    private final ObjectMapper json;

    public Optional<Product> parse(Document document, Category requested) {
        var found = new ArrayList<JsonNode>();
        for (var script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = json.readTree(script.data());
                if (isListing(root)) {
                    return Optional.empty();
                }
                collectProducts(root, found);
            } catch (RuntimeException ignored) {
                /* Malformed storefront metadata is not verified evidence. */
            }
        }
        // A listing with embedded products is not a single product detail page.
        if (found.size() != 1) {
            return Optional.empty();
        }
        try {
            return parseNode(found.getFirst(), document, requested);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasType(JsonNode node, String type) {
        var types = node.path("@type");
        if (types.isArray()) {
            for (var value : types) {
                if (type.equals(value.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return type.equals(types.asString(""));
    }

    private static boolean isListing(JsonNode node) {
        if (hasType(node, "CollectionPage") || hasType(node, "ItemList")) {
            return true;
        }
        if (node.isArray()) {
            for (var child : node) {
                if (isListing(child)) {
                    return true;
                }
            }
        }
        return node.has("@graph") && isListing(node.get("@graph"));
    }

    private static void collectProducts(JsonNode node, List<JsonNode> found) {
        if (node.isArray()) {
            for (var child : node) {
                collectProducts(child, found);
            }
        } else if (node.isObject()) {
            if (hasType(node, "Product")) {
                found.add(node);
            }
            if (node.has("@graph")) {
                collectProducts(node.get("@graph"), found);
            }
        }
    }

    private static boolean identifiesCurrentPage(JsonNode node, Document document) {
        URI page = URI.create(document.location());
        String productUrl = node.path("url").asString("");
        JsonNode offers = node.path("offers");
        List<JsonNode> offerNodes = new ArrayList<>();
        if (offers.isArray()) {
            offers.forEach(offerNodes::add);
        } else {
            offerNodes.add(offers);
        }
        boolean evidence = !productUrl.isBlank();
        if (evidence && !samePage(page, productUrl)) {
            return false;
        }
        for (var offer : offerNodes) {
            String offerUrl = offer.path("url").asString("");
            if (!offerUrl.isBlank()) {
                evidence = true;
                if (!samePage(page, offerUrl)) {
                    return false;
                }
            }
        }
        for (var canonical : document.select("link[rel=canonical]")) {
            if (!samePage(page, canonical.attr("href"))) {
                return false;
            }
        }
        return evidence;
    }

    private static boolean samePage(URI page, String reference) {
        if (reference.isBlank()) {
            return false;
        }
        URI target = page.resolve(reference).normalize();
        // Ignore known tracking parameters, but preserve query-based product IDs.
        return "https".equalsIgnoreCase(target.getScheme()) && target.getUserInfo() == null
                && (target.getPort() == -1 || target.getPort() == 443)
                && page.getHost().equalsIgnoreCase(target.getHost())
                && page.normalize().getPath().equals(target.getPath())
                && identityQuery(page).equals(identityQuery(target));
    }

    private static List<String> identityQuery(URI uri) {
        if (uri.getRawQuery() == null) {
            return List.of();
        }
        return java.util.Arrays.stream(uri.getRawQuery().split("&")).filter(parameter -> !parameter.isBlank())
                .filter(parameter -> !parameter.toLowerCase(Locale.ROOT).matches("(?:utm_[^=]*|gclid|fbclid)=.*"))
                .sorted().toList();
    }

    private static String modelCode(String text) {
        var matcher = MODEL.matcher(text);
        while (matcher.find()) {
            // C002/C014 are colour codes, not a verifiable frame model.
            if (!"C".equalsIgnoreCase(matcher.group(1))) {
                return (matcher.group(1) + matcher.group(2)).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private Optional<Product> parseNode(JsonNode node, Document document, Category requested) {
        String name = node.path("name").asString("");
        String brand = node.path("brand").isObject()
                ? node.path("brand").path("name").asString("")
                : node.path("brand").asString("");
        if (name.length() < 5 || name.length() > 250 || brand.isBlank() || brand.length() > 80) {
            return Optional.empty();
        }
        if (!identifiesCurrentPage(node, document)) {
            return Optional.empty();
        }
        String model = modelCode(node.path("model").asString(""));
        if (model.isEmpty()) {
            // Merchants may put the frame shape in model; use the name as fallback.
            model = modelCode(name);
        }
        var heading = document.selectFirst("h1");
        if (model.isEmpty() || heading == null) {
            return Optional.empty();
        }
        String headingModel = modelCode(heading.text());
        if (!headingModel.isEmpty() && !model.equals(headingModel)) {
            return Optional.empty();
        }
        if (headingModel.isEmpty() && !normalize(heading.text()).contains(normalize(brand))) {
            return Optional.empty();
        }
        String categoryText = normalize(name + " " + node.path("category").asString(""));
        Category category = category(categoryText);
        if (category != requested) {
            return Optional.empty();
        }
        JsonNode offer = node.path("offers");
        if (offer.isArray()) {
            offer = offer.isEmpty() ? offer : offer.get(0);
        }
        String currency = offer.path("priceCurrency").asString("");
        BigDecimal price = price(offer, currency);
        String availability = availability(offer.path("availability").asString(""));
        String color = node.path("color").asString("");
        String shapeText = normalize(node.path("description").asString("") + " " + name + " "
                + node.path("model").asString("") + " " + node.path("additionalProperty"));
        Shape shape = shape(shapeText);
        var attributes = new LinkedHashMap<String, String>();
        if (!color.isBlank() && color.length() < 60) {
            attributes.put("color", color);
        }
        var material = node.path("material").asString("");
        if (!material.isBlank() && material.length() < 60) {
            attributes.put("material", material);
        }
        if (shape != Shape.UNKNOWN) {
            attributes.put("shape", RecommendationRanker.label(shape));
        }
        URI url = URI.create(document.location());
        String host = url.getHost();
        String merchant = switch (host) {
            case "www.atasunoptik.com.tr" -> "Atasun Optik";
            case "www.istanbuloptik.com.tr" -> "İstanbul Optik";
            case "www.alkimoptik.com.tr" -> "Alkım Optik";
            default -> host;
        };
        String colorCode = color(normalize(color));
        String style = style(shapeText);
        String productUrl = cleanUrl(url);
        return Optional.of(new Product(UUID.nameUUIDFromBytes(productUrl.getBytes(StandardCharsets.UTF_8)).toString(),
                brand, model, name, category, shape, colorCode, style, attributes, merchant, productUrl,
                "https://" + host + "/", price, currency, availability, Instant.now(),
                imageUrl(node.path("image"), url)));
    }

    private static String imageUrl(JsonNode image, URI page) {
        if (image.isArray()) {
            for (var entry : image) {
                String url = imageUrl(entry, page);
                if (url != null) {
                    return url;
                }
            }
            return null;
        }
        if (image.isObject()) {
            String url = imageUrl(image.path("contentUrl"), page);
            return url != null ? url : imageUrl(image.path("url"), page);
        }
        String value = image.asString("");
        if (value.isBlank() || value.length() > 2048) {
            return null;
        }
        try {
            // Resolve against the verified page, never an untrusted HTML base tag.
            URI url = page.resolve(value).normalize();
            boolean merchantImage = page.getHost().equalsIgnoreCase(url.getHost());
            boolean atasunCdn = "www.atasunoptik.com.tr".equalsIgnoreCase(page.getHost())
                    && "stn-atasun.mncdn.com".equalsIgnoreCase(url.getHost());
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getUserInfo() != null
                    || (url.getPort() != -1 && url.getPort() != 443) || !(merchantImage || atasunCdn)) {
                return null;
            }
            return url.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String normalize(String s) {
        return TextNormalizer.normalize(s);
    }

    private static String cleanUrl(URI uri) {
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return "https://" + uri.getHost() + uri.getRawPath() + query;
    }

    private static BigDecimal price(JsonNode offer, String currency) {
        if (!"TRY".equals(currency)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(offer.path("price").asString(""));
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            // An absent or malformed price cannot satisfy an explicit budget.
            return null;
        }
    }

    private static String availability(String value) {
        if (value.endsWith("OutOfStock") || value.endsWith("SoldOut")) {
            return "OUT_OF_STOCK";
        }
        return value.endsWith("InStock") ? "IN_STOCK" : "UNKNOWN";
    }

    // Localized keywords are external merchant vocabulary, not application
    // messages.
    private static Category category(String text) {
        if (containsAny(text, "optik", "numarali", "eyeglass", "cerceve", "mavi koruma", "mavi isik")) {
            return Category.OPTICAL;
        }
        if (containsAny(text, "gunes", "sunglass")) {
            return Category.SUNGLASSES;
        }
        return null;
    }

    private static Shape shape(String text) {
        if (containsAny(text, "cat eye", "kedi gozu")) {
            return Shape.CAT_EYE;
        }
        if (containsAny(text, "aviator", "damla")) {
            return Shape.AVIATOR;
        }
        if (containsAny(text, "clubmaster", "browline")) {
            return Shape.BROWLINE;
        }
        if (containsAny(text, "yuvarlak", "round", "oval")) {
            return Shape.ROUND;
        }
        if (containsAny(text, "wayfarer", "dikdortgen", "koseli")) {
            return Shape.RECTANGULAR;
        }
        return text.contains("geometr") ? Shape.GEOMETRIC : Shape.UNKNOWN;
    }

    private static String color(String text) {
        if (containsAny(text, "siyah", "black")) {
            return "BLACK";
        }
        if (containsAny(text, "kahverengi", "havana", "brown")) {
            return "BROWN";
        }
        if (containsAny(text, "altin", "gold")) {
            return "GOLD";
        }
        return containsAny(text, "seffaf", "clear") ? "CLEAR" : "UNKNOWN";
    }

    private static String style(String text) {
        if (containsAny(text, "retro", "classic", "klasik")) {
            return "CLASSIC";
        }
        if (containsAny(text, "oversize", "kalin")) {
            return "BOLD";
        }
        return containsAny(text, "modern", "minimal") ? "MODERN" : "UNKNOWN";
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}

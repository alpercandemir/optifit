package com.optifit.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.optifit.config.AppProperties;
import com.optifit.exception.ApiException;
import com.optifit.model.Category;
import com.optifit.model.FaceProfile;
import com.optifit.model.JobView;
import com.optifit.model.Preferences;
import com.optifit.model.Shape;
import com.optifit.search.WebProductSearch;
import com.optifit.service.PhotoProcessor;
import com.optifit.service.RecommendationRanker;
import com.optifit.service.RecommendationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService service;
    private final PhotoProcessor photos;
    private final AppProperties properties;
    private final RecommendationRanker ranker;

    @ModelAttribute
    public void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request, CsrfToken csrf) {
        request.getSession(true);
        return Map.of("csrfToken", csrf.getToken(), "csrfHeader", csrf.getHeaderName(), "mode", properties.mode(),
                "resultTtlSeconds", properties.ttlSeconds());
    }

    @GetMapping("/examples")
    public JobView examples() {
        var prefs = new Preferences(Category.SUNGLASSES, null, "ANY", "ANY");
        var face = new FaceProfile(true, 1, "Demo", List.of(Shape.RECTANGULAR, Shape.ROUND));
        var examples = ranker.rank(WebProductSearch.demoProducts(Category.SUNGLASSES), prefs, face, true);
        return new JobView("example", "COMPLETED", Instant.now().plusSeconds(properties.ttlSeconds()), examples, List
                .of("This is an example collection. No photo analysis was performed; prices and availability have not been verified."),
                true, null, null);
    }

    @PostMapping(value = "/recommendations", consumes = "multipart/form-data")
    public ResponseEntity<JobView> create(@RequestPart("photo") MultipartFile photo, @RequestParam Category category,
            @RequestParam(required = false) BigDecimal budget, @RequestParam(defaultValue = "ANY") String style,
            @RequestParam(defaultValue = "ANY") String color, @RequestParam boolean consent,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, HttpServletRequest request) {
        if (!consent) {
            throw ApiException.badRequest("Acknowledge the photo processing notice before starting an analysis.");
        }
        var prefs = new Preferences(category, budget, style, color);
        return ResponseEntity.accepted()
                .body(service.submit(owner(request), request.getRemoteAddr(), key, photos.process(photo), prefs));
    }

    @GetMapping("/recommendations/{id}")
    public JobView get(@PathVariable String id, HttpServletRequest request) {
        return service.get(id, owner(request));
    }

    @DeleteMapping("/recommendations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        service.delete(id, owner(request));
        return ResponseEntity.noContent().build();
    }

    private String owner(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            throw ApiException.missing();
        }
        return session.getId();
    }
}

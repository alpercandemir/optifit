package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.optifit.config.AppProperties;
import com.optifit.exception.ApiException;
import com.optifit.model.Category;
import com.optifit.model.FaceProfile;
import com.optifit.model.Preferences;
import com.optifit.model.Shape;
import com.optifit.repository.JobRepository;
import com.optifit.search.WebProductSearch;
import com.optifit.security.UsageLimiter;
import com.optifit.service.FaceAnalyzer;
import com.optifit.service.PhotoProcessor;
import com.optifit.service.RecommendationRanker;
import com.optifit.service.RecommendationService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "optifit.mode=demo")
class JobLifecycleTest {

    @Autowired
    JobRepository jobs;

    @Autowired
    ObjectMapper json;

    AppProperties settings(int timeout) {
        return new AppProperties("demo", "", "test", "", List.of(), 100, 100, 1000, 3600, timeout);
    }

    @Test
    void deletionErasesPhotoAndLateProviderCannotRecreateResult() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var done = new AtomicBoolean();
        FaceAnalyzer analyzer = bytes -> {
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                done.set(true);
            }
            return new FaceProfile(true, 1, "", List.of(Shape.ROUND));
        };
        var p = settings(60);
        var service = new RecommendationService(jobs, analyzer,
                (prefs, face) -> WebProductSearch.demoProducts(prefs.category()), new RecommendationRanker(),
                new UsageLimiter(p), p, json, new SimpleMeterRegistry());
        var photo = new PhotoProcessor.Photo(new byte[]{1, 2, 3});
        String owner = UUID.randomUUID().toString();
        try {
            var job = service.submit(owner, "test", UUID.randomUUID().toString(), photo,
                    new Preferences(Category.SUNGLASSES, null, "ANY", "ANY"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            service.delete(job.jobId(), owner);
            release.countDown();
            await().atMost(Duration.ofSeconds(2)).untilTrue(done);
            assertThat(photo.bytes()).containsOnly((byte) 0);
            assertThat(jobs.get(job.jobId(), owner)).isEmpty();
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    @Test
    void modelQualityRejectionDoesNotSearchProductsAndErasesPhoto() {
        var searched = new AtomicBoolean();
        var p = settings(60);
        var service = new RecommendationService(jobs, bytes -> {
            throw new ApiException(422, "PHOTO_NOT_USABLE", "Upload a photo containing exactly one face.");
        }, (prefs, face) -> {
            searched.set(true);
            return List.of();
        }, new RecommendationRanker(), new UsageLimiter(p), p, json, new SimpleMeterRegistry());
        var photo = new PhotoProcessor.Photo(new byte[]{1, 2, 3});
        String owner = UUID.randomUUID().toString();
        try {
            var job = service.submit(owner, "test", UUID.randomUUID().toString(), photo,
                    new Preferences(Category.SUNGLASSES, null, "ANY", "ANY"));
            await().atMost(Duration.ofSeconds(2))
                    .until(() -> service.get(job.jobId(), owner).status().equals("FAILED"));
            assertThat(service.get(job.jobId(), owner).errorCode()).isEqualTo("PHOTO_NOT_USABLE");
            assertThat(searched).isFalse();
            assertThat(photo.bytes()).containsOnly((byte) 0);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void deadlineStopsLateResults() throws Exception {
        var entered = new CountDownLatch(1);
        var p = settings(1);
        var service = new RecommendationService(jobs, bytes -> {
            entered.countDown();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
            return new FaceProfile(true, 1, "", List.of(Shape.ROUND));
        }, (prefs, face) -> List.of(), new RecommendationRanker(), new UsageLimiter(p), p, json,
                new SimpleMeterRegistry());
        var photo = new PhotoProcessor.Photo(new byte[]{1, 2, 3});
        String owner = UUID.randomUUID().toString();
        try {
            var job = service.submit(owner, "test", UUID.randomUUID().toString(), photo,
                    new Preferences(Category.SUNGLASSES, null, "ANY", "ANY"));
            entered.await(2, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                service.expire();
                return service.get(job.jobId(), owner).status().equals("FAILED");
            });
            var result = service.get(job.jobId(), owner);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorCode()).isEqualTo("TIMEOUT");
            assertThat(photo.bytes()).containsOnly((byte) 0);
        } finally {
            service.shutdown();
        }
    }
}

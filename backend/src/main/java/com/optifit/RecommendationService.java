package com.optifit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.optifit.Models.Category;
import com.optifit.Models.FaceProfile;
import com.optifit.Models.JobView;
import com.optifit.Models.Preferences;
import com.optifit.Models.Result;

import io.micrometer.core.instrument.MeterRegistry;

import tools.jackson.databind.ObjectMapper;

@Service
class RecommendationService {
    private static final int WORKER_COUNT = 2;
    private static final int QUEUE_CAPACITY = 4;
    private static final int RECOMMENDATION_COUNT = 3;

    private final JobRepository jobs;
    private final FaceAnalyzer analyzer;
    private final ProductSearchProvider products;
    private final RecommendationRanker ranker;
    private final UsageLimiter limiter;
    private final AppProperties properties;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(WORKER_COUNT, WORKER_COUNT, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), new ThreadPoolExecutor.AbortPolicy());

    private record Work(Future<?> future, PhotoProcessor.Photo photo) {
    }

    private final ConcurrentMap<String, Work> work = new ConcurrentHashMap<>();

    RecommendationService(JobRepository jobs, FaceAnalyzer analyzer, ProductSearchProvider products,
            RecommendationRanker ranker, UsageLimiter limiter, AppProperties properties, ObjectMapper json,
            MeterRegistry metrics) {
        this.jobs = jobs;
        this.analyzer = analyzer;
        this.products = products;
        this.ranker = ranker;
        this.limiter = limiter;
        this.properties = properties;
        this.json = json;
        this.metrics = metrics;
    }

    synchronized JobView submit(String owner, String ip, String key, PhotoProcessor.Photo photo,
            Preferences preferences) {
        boolean transferred = false;
        try {
            if (key == null || !key.matches("[A-Za-z0-9_-]{16,80}")) {
                throw ApiException.badRequest("A valid idempotency key is required.");
            }
            jobs.cleanup();
            String fingerprint = fingerprint(photo.bytes(), preferences);
            var previous = jobs.byKey(key, owner);
            if (previous.isPresent()) {
                if (!previous.get().fingerprint().equals(fingerprint)) {
                    throw new ApiException(409, "IDEMPOTENCY_CONFLICT",
                            "This idempotency key was already used with a different photo or preferences.");
                }
                return view(previous.get());
            }
            if (workers.getActiveCount() >= WORKER_COUNT && workers.getQueue().remainingCapacity() == 0) {
                throw new ApiException(503, "BUSY", "The service is busy. Please try again shortly.");
            }
            limiter.reserve(owner, ip);
            String id = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            jobs.create(id, owner, key, fingerprint, now, now + properties.ttlSeconds() * 1000L);
            var task = new FutureTask<Void>(() -> {
                process(id, photo, preferences);
                return null;
            });
            work.put(id, new Work(task, photo));
            try {
                workers.execute(task);
            } catch (RejectedExecutionException e) {
                work.remove(id);
                jobs.delete(id, owner);
                throw new ApiException(503, "BUSY", "The service is busy. Please try again shortly.");
            }
            transferred = true;
            return get(id, owner);
        } finally {
            if (!transferred) {
                photo.close();
            }
        }
    }

    JobView get(String id, String owner) {
        return view(jobs.get(id, owner).orElseThrow(ApiException::missing));
    }

    synchronized void delete(String id, String owner) {
        jobs.get(id, owner).orElseThrow(ApiException::missing);
        jobs.delete(id, owner);
        cancel(id);
    }

    private void cancel(String id) {
        var task = work.remove(id);
        if (task != null) {
            task.future().cancel(true);
            task.photo().close();
            workers.purge();
        }
    }

    private void process(String id, PhotoProcessor.Photo photo, Preferences preferences) {
        long start = System.nanoTime();
        try {
            if (!jobs.active(id)) {
                return;
            }
            jobs.stage(id, "ANALYZING");
            FaceProfile face;
            try {
                face = analyzer.analyze(photo.bytes(), preferences);
            } finally {
                photo.close();
            }
            if (!jobs.active(id) || Thread.currentThread().isInterrupted()) {
                return;
            }
            jobs.stage(id, "SEARCHING");
            var found = products.search(preferences, face);
            if (!jobs.active(id) || Thread.currentThread().isInterrupted()) {
                return;
            }
            var recommendations = ranker.rank(found, preferences, face, properties.demo());
            var warnings = warnings(preferences.category(), recommendations.size());
            String status = resultStatus(recommendations.size());
            jobs.finish(id, status, json.writeValueAsString(new Result(recommendations, warnings, properties.demo())));
            recordOutcome(status);
        } catch (ApiException e) {
            jobs.fail(id, e.code(), e.getMessage());
            recordOutcome("FAILED");
        } catch (Exception e) {
            jobs.fail(id, "ANALYSIS_UNAVAILABLE",
                    "The analysis service is unavailable. Your photo has been deleted; please try again later.");
            recordOutcome("FAILED");
        } finally {
            photo.close();
            work.remove(id);
            metrics.timer("optifit.analysis.duration").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private List<String> warnings(Category category, int count) {
        var warnings = new ArrayList<String>();
        if (properties.demo()) {
            warnings.add(
                    "Demo mode: your photo was not analyzed. Products are examples; prices and availability have not been verified.");
        }
        if (properties.demo() && category == Category.OPTICAL) {
            warnings.add("The demo catalog has no optical frames. Live mode searches online for this category.");
        }
        if (count < RECOMMENDATION_COUNT) {
            warnings.add(
                    "Fewer than three verified models match your filters. Try a different budget or eyewear category.");
        }
        return warnings;
    }

    private String resultStatus(int count) {
        if (count == RECOMMENDATION_COUNT) {
            return "COMPLETED";
        }
        return count == 0 ? "NO_MATCH" : "PARTIAL";
    }

    private void recordOutcome(String status) {
        metrics.counter("optifit.analyses", "result", status, "mode", properties.mode()).increment();
    }

    private JobView view(JobRepository.Job job) {
        Result result = job.result() == null
                ? new Result(List.of(), List.of(), properties.demo())
                : json.readValue(job.result(), Result.class);
        return new JobView(job.id(), job.status(), Instant.ofEpochMilli(job.expiresAt()), result.recommendations(),
                result.warnings(), result.demo(), job.errorCode(), job.message());
    }

    private String fingerprint(byte[] bytes, Preferences preferences) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            digest.update(json.writeValueAsString(preferences).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot fingerprint request", e);
        }
    }

    @Scheduled(fixedDelay = 1000)
    void expire() {
        for (var job : jobs.timedOut(System.currentTimeMillis() - properties.timeoutSeconds() * 1000L)) {
            jobs.fail(job.id(), "TIMEOUT", "The analysis timed out. Your photo has been deleted; please try again.");
            cancel(job.id());
        }
        jobs.cleanup();
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        work.values().forEach(w -> w.photo().close());
    }
}

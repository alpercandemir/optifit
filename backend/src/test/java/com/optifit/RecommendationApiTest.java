package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.optifit.repository.JobRepository;
import com.optifit.service.RecommendationService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"optifit.mode=demo", "optifit.session-limit=100", "optifit.ip-limit=100",
        "optifit.daily-limit=1000"})
@AutoConfigureMockMvc
class RecommendationApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JobRepository jobs;

    @Autowired
    RecommendationService service;

    MockMultipartFile photo() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 250, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("photo", "face.png", "image/png", out.toByteArray());
    }

    String submit(MockHttpSession session, String key, String category, String budget) throws Exception {
        var request = multipart("/api/v1/recommendations").file(photo()).session(session).with(csrf())
                .param("category", category).param("consent", "true").header("Idempotency-Key", key);
        if (budget != null) {
            request.param("budget", budget);
        }
        var response = mvc.perform(request).andExpect(status().isAccepted()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("jobId").asString();
    }

    String waitFor(String id, MockHttpSession session) {
        await().atMost(Duration.ofSeconds(5)).until(() -> List.of("COMPLETED", "PARTIAL", "NO_MATCH", "FAILED")
                .contains(service.get(id, session.getId()).status()));
        return service.get(id, session.getId()).status();
    }

    @Test
    void examplesIncludeProductPhotosWithoutClaimingLiveVerification() throws Exception {
        var response = mvc.perform(get("/api/v1/examples")).andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true)).andReturn().getResponse();
        var recommendations = json.readTree(response.getContentAsString()).path("recommendations");
        assertThat(recommendations.size()).isEqualTo(3);
        for (var recommendation : recommendations) {
            assertThat(recommendation.path("imageUrl").asString())
                    .startsWith("https://stn-atasun.mncdn.com/Content/media/ProductImg/original/");
            var offer = recommendation.path("offers").get(0);
            assertThat(offer.path("price").isNull()).isTrue();
            assertThat(offer.path("checkedAt").isNull()).isTrue();
            assertThat(offer.path("availability").asString()).isEqualTo("UNKNOWN");
        }
    }

    @Test
    void anonymousSessionSuppliesCsrfWithoutLogin() throws Exception {
        mvc.perform(get("/api/v1/session")).andExpect(status().isOk()).andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.mode").value("demo")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/api/v1/recommendations")).andExpect(status().isForbidden());
    }

    @Test
    void realCsrfTokenRoundTripCreatesAnonymousJob() throws Exception {
        var response = mvc.perform(get("/api/v1/session")).andReturn();
        var session = (MockHttpSession) response.getRequest().getSession(false);
        var token = json.readTree(response.getResponse().getContentAsString());
        mvc.perform(multipart("/api/v1/recommendations").file(photo()).session(session).param("category", "SUNGLASSES")
                .param("consent", "true").header("Idempotency-Key", UUID.randomUUID().toString())
                .header(token.path("csrfHeader").asString(), token.path("csrfToken").asString()))
                .andExpect(status().isAccepted());
    }

    @Test
    void returnsThreeDistinctExamplesAndEnforcesSessionOwnershipAndDeletion() throws Exception {
        var owner = new MockHttpSession();
        var stranger = new MockHttpSession();
        String id = submit(owner, UUID.randomUUID().toString(), "SUNGLASSES", null);
        assertThat(waitFor(id, owner)).isEqualTo("COMPLETED");
        mvc.perform(get("/api/v1/recommendations/" + id).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(3)).andExpect(jsonPath("$.demo").value(true));
        mvc.perform(get("/api/v1/recommendations/" + id).session(stranger)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/recommendations/" + id).session(stranger).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/recommendations/" + id).session(owner).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/recommendations/" + id).session(owner)).andExpect(status().isNotFound());
    }

    @Test
    void repeatSubmissionIsIdempotentButChangedPreferencesConflict() throws Exception {
        var owner = new MockHttpSession();
        String key = UUID.randomUUID().toString();
        String first = submit(owner, key, "SUNGLASSES", null), second = submit(owner, key, "SUNGLASSES", null);
        assertThat(second).isEqualTo(first);
        mvc.perform(multipart("/api/v1/recommendations").file(photo()).session(owner).with(csrf())
                .param("category", "OPTICAL").param("consent", "true").header("Idempotency-Key", key))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownPricesNeverPassABudgetAndOpticalNeverReturnsSunglasses() throws Exception {
        var owner = new MockHttpSession();
        assertThat(waitFor(submit(owner, UUID.randomUUID().toString(), "SUNGLASSES", "5000"), owner))
                .isEqualTo("NO_MATCH");
        assertThat(waitFor(submit(owner, UUID.randomUUID().toString(), "OPTICAL", null), owner)).isEqualTo("NO_MATCH");
    }

    @Test
    void rejectsSpoofedOrBrokenImagesAndMissingConsent() throws Exception {
        mvc.perform(multipart("/api/v1/recommendations")
                .file(new MockMultipartFile("photo", "image.png", "image/png", "not an image".getBytes()))
                .session(new MockHttpSession()).with(csrf()).param("category", "SUNGLASSES").param("consent", "true")
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/recommendations").file(photo()).session(new MockHttpSession()).with(csrf())
                .param("category", "SUNGLASSES").param("consent", "false")
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isBadRequest());
    }

    @Test
    void expiredJobsAreNotReadableEvenBeforeCleanup() throws Exception {
        var owner = new MockHttpSession();
        String id = UUID.randomUUID().toString();
        jobs.create(id, owner.getId(), UUID.randomUUID().toString(), "fingerprint", 0, 1);
        mvc.perform(get("/api/v1/recommendations/" + id).session(owner)).andExpect(status().isNotFound());
    }

    @Test
    void apiErrorsAndExamplesUseEnglishWithStablePresentationCodes() throws Exception {
        mvc.perform(post("/api/v1/recommendations")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED")).andExpect(
                        jsonPath("$.message").value("Your session must be refreshed. Reload the page and try again."));
        mvc.perform(get("/api/v1/recommendations/missing").session(new MockHttpSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This result is no longer available. Start a new analysis."));
        mvc.perform(get("/api/v1/examples")).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].reasonCode").value("DEMO"))
                .andExpect(jsonPath("$.recommendations[0].reason")
                        .value(org.hamcrest.Matchers.startsWith("Example result:")))
                .andExpect(jsonPath("$.recommendations[0].attributes.shape").isNotEmpty())
                .andExpect(jsonPath("$.warnings[0]").value(
                        "This is an example collection. No photo analysis was performed; prices and availability have not been verified."));
    }
}

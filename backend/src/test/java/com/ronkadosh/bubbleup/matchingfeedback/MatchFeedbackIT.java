package com.ronkadosh.bubbleup.matchingfeedback;

import com.ronkadosh.bubbleup.common.events.RecommendationsShownEvent;
import com.ronkadosh.bubbleup.matchingfeedback.application.MatchFeedbackService;
import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;
import com.ronkadosh.bubbleup.matchingfeedback.persistence.MatchFeedbackRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchFeedbackIT extends IntegrationTest {

    @Autowired MatchFeedbackService service;
    @Autowired MatchFeedbackRepository repo;

    @Test
    void explicit_rating_from_group_view_persists() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        AuthedUser viewer = registerEnrolled();

        mvc.perform(post("/api/matching/feedback").with(bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\",\"sentiment\":\"GOOD_FIT\"}"))
                .andExpect(status().isOk());

        var row = repo.findByUserIdAndGroupId(viewer.id(), groupId).orElseThrow();
        assertThat(row.getRating()).isEqualTo(FeedbackRating.GOOD_FIT);
        assertThat(row.getRatedAt()).isNotNull();
    }

    @Test
    void rating_is_once_per_user_per_group() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        AuthedUser viewer = registerEnrolled();

        mvc.perform(get("/api/matching/feedback/" + groupId).with(bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rated").value(false));

        rate(viewer, groupId, "GOOD_FIT");
        rate(viewer, groupId, "BAD_FIT");   // second vote must be ignored — keep the first

        var row = repo.findByUserIdAndGroupId(viewer.id(), groupId).orElseThrow();
        assertThat(row.getRating()).isEqualTo(FeedbackRating.GOOD_FIT);

        mvc.perform(get("/api/matching/feedback/" + groupId).with(bearer(viewer)))
                .andExpect(jsonPath("$.data.rated").value(true))
                .andExpect(jsonPath("$.data.sentiment").value("GOOD_FIT"));
    }

    @Test
    void rating_unknown_group_is_404() throws Exception {
        AuthedUser viewer = registerAndLogin();
        mvc.perform(post("/api/matching/feedback").with(bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + UUID.randomUUID() + "\",\"sentiment\":\"BAD_FIT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_feedback_endpoint_is_gated() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(get("/api/admin/matching-feedback").with(bearer(student)))
                .andExpect(status().isForbidden());

        AuthedUser admin = registerAndLoginAsAdmin();
        mvc.perform(get("/api/admin/matching-feedback").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnelByMode").isArray())
                .andExpect(jsonPath("$.data.matchPercentBuckets").isArray());
    }

    @Test
    void analytics_reflect_funnel_and_buckets() {
        repo.deleteAll();   // isolate from any impressions other tests logged into the shared context
        UUID user = UUID.randomUUID();
        UUID matchedJoined = UUID.randomUUID();
        UUID matchedNotJoined = UUID.randomUUID();
        UUID trending = UUID.randomUUID();

        service.recordImpressions(user, List.of(
                new RecommendationsShownEvent.Shown(matchedJoined, "MATCHED", 92, 0.8),
                new RecommendationsShownEvent.Shown(matchedNotJoined, "MATCHED", 70, 0.5),
                new RecommendationsShownEvent.Shown(trending, "TRENDING", null, 0.1)));
        service.recordJoin(user, matchedJoined);

        var a = service.buildAnalytics();

        var matched = a.funnelByMode().stream()
                .filter(f -> f.mode().equals("MATCHED")).findFirst().orElseThrow();
        assertThat(matched.shown()).isEqualTo(2);
        assertThat(matched.joined()).isEqualTo(1);
        assertThat(matched.joinRate()).isEqualTo(0.5);

        var trendingFunnel = a.funnelByMode().stream()
                .filter(f -> f.mode().equals("TRENDING")).findFirst().orElseThrow();
        assertThat(trendingFunnel.shown()).isEqualTo(1);
        assertThat(trendingFunnel.joined()).isZero();

        var topBucket = a.matchPercentBuckets().stream()
                .filter(b -> b.label().equals("90-100")).findFirst().orElseThrow();
        assertThat(topBucket.shown()).isEqualTo(1);
        assertThat(topBucket.joined()).isEqualTo(1);
        assertThat(topBucket.joinRate()).isEqualTo(1.0);
    }

    private void rate(AuthedUser u, UUID groupId, String sentiment) throws Exception {
        mvc.perform(post("/api/matching/feedback").with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\",\"sentiment\":\"" + sentiment + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String body = "{\"name\":\"FB Bubble\",\"description\":\"x\",\"visibility\":\"PUBLIC\","
                + "\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}";
        String json = mvc.perform(post("/api/groups").with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}

package com.campusoverflow.discovery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.discovery.domain.notification.MentionParser;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex.RelatedCandidate;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.discovery.domain.search.SearchSort;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.page.PageRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 领域层边界与异常分支：参数校验、退化输入、投影文档不可变性。 */
class DiscoveryDomainEdgeCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");

    // ---------- SearchQuery ----------

    @Test
    void keywordLongerThanFiftyCharactersIsRejected() {
        String tooLong = "红".repeat(51);

        assertThatThrownBy(() -> new SearchQuery(tooLong, null, null, null, false, false, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("50");
    }

    @Test
    void blankTagAndDefaultPageAreNormalised() {
        SearchQuery q = new SearchQuery("红黑树", null, "   ", null, false, false, null);

        assertThat(q.tag()).isNull();
        assertThat(q.page()).isEqualTo(new PageRequest(1, 20));
        assertThat(q.sort()).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void explicitSortIsKeptWhenKeywordPresent() {
        SearchQuery q = new SearchQuery("红黑树", null, null, SearchSort.HOT, false, false, null);

        assertThat(q.sort()).isEqualTo(SearchSort.HOT);
    }

    @Test
    void termsAreDeduplicatedAndCappedAtFive() {
        SearchQuery q = new SearchQuery("java Java JAVA spring boot 算法 数据结构 网络", null, null,
                SearchSort.RELEVANCE, false, false, null);

        assertThat(q.terms()).hasSize(5);
        assertThat(q.terms().get(0)).isEqualTo("java");
        assertThat(q.terms().stream().filter("java"::equals)).hasSize(1);
    }

    // ---------- RelatedQuestionPolicy ----------

    @Test
    void jaccardOnDegenerateInputsIsZero() {
        assertThat(RelatedQuestionPolicy.jaccard(0, 3, 3)).isZero();
        assertThat(RelatedQuestionPolicy.jaccard(3, 2, 1)).isZero();
    }

    @Test
    void rankHandlesNullEmptyAndNonPositiveLimit() {
        assertThat(RelatedQuestionPolicy.rank(null, 3, 5)).isEmpty();
        assertThat(RelatedQuestionPolicy.rank(List.of(), 3, 5)).isEmpty();
        assertThat(RelatedQuestionPolicy.rank(List.of(new RelatedCandidate(1, "A", 1, 2, 0, 0, false)), 3, 0))
                .isEmpty();
    }

    @Test
    void candidatesWithoutSharedTagsAreDropped() {
        List<RelatedCandidate> ranked = RelatedQuestionPolicy.rank(List.of(
                new RelatedCandidate(1, "A", 0, 2, 99, 0, true),
                new RelatedCandidate(2, "B", 1, 2, 0, 0, false)), 3, 5);

        assertThat(ranked).extracting(RelatedCandidate::questionId).containsExactly(2L);
    }

    @Test
    void equalSimilarityFallsBackToScoreThenAccepted() {
        List<RelatedCandidate> ranked = RelatedQuestionPolicy.rank(List.of(
                new RelatedCandidate(1, "低分已采纳", 1, 2, 10, 3, true),
                new RelatedCandidate(2, "高分未采纳", 1, 2, 50, 0, false)), 3, 5);

        assertThat(ranked).extracting(RelatedCandidate::questionId).containsExactly(2L, 1L);
    }

    // ---------- MentionParser ----------

    @Test
    void nullOrBlankTextYieldsNoMentions() {
        assertThat(MentionParser.extract(null)).isEmpty();
        assertThat(MentionParser.extract("   ")).isEmpty();
    }

    @Test
    void loneAtSignIsNotAMention() {
        assertThat(MentionParser.extract("@")).isEmpty();
        assertThat(MentionParser.extract("@ @")).isEmpty();
        assertThat(MentionParser.extract("@王 你好")).isEmpty();
    }

    // ---------- Notification ----------

    @Test
    void createRejectsInvalidRecipientAndBlankTitle() {
        assertThatThrownBy(() -> Notification.create(0, NotificationType.MENTIONED, "标题", "/q/1", "e1", NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Notification.create(7, NotificationType.MENTIONED, "  ", "/q/1", "e1", NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createDefaultsTimestampsAndTrimsTitle() {
        Notification n = Notification.create(7, NotificationType.MENTIONED, "  有提醒  ", "/q/1", "e1", null);

        assertThat(n.title()).isEqualTo("有提醒");
        assertThat(n.createdAt()).isNotNull();
        assertThat(n.read()).isFalse();
        assertThat(n.sourceEventId()).isEqualTo("e1");
    }

    // ---------- 投影模型 ----------

    @Test
    void searchDocumentCopiesTagsSoLaterMutationDoesNotLeak() {
        List<String> mutableTags = new ArrayList<>(List.of("java"));
        SearchDocument doc = new SearchDocument(3L, 2L, 7L, "红黑树", "正文", mutableTags, NOW);
        mutableTags.add("算法");

        assertThat(doc.tags()).containsExactly("java");
        assertThat(doc.questionId()).isEqualTo(3L);
        assertThat(doc.courseId()).isEqualTo(2L);
        assertThat(doc.authorId()).isEqualTo(7L);
        assertThat(doc.title()).isEqualTo("红黑树");
        assertThat(doc.bodyText()).isEqualTo("正文");
        assertThat(doc.createdAt()).isEqualTo(NOW);
    }

    @Test
    void searchHitExposesAllProjectionFields() {
        SearchHit hit = new SearchHit(3L, "红黑树怎么旋转", "插入后失衡", List.of("数据结构", "java"), 2L, 7L, 12, 4,
                true, true, NOW);

        assertThat(hit.questionId()).isEqualTo(3L);
        assertThat(hit.title()).isEqualTo("红黑树怎么旋转");
        assertThat(hit.snippet()).isEqualTo("插入后失衡");
        assertThat(hit.tags()).containsExactly("数据结构", "java");
        assertThat(hit.courseId()).isEqualTo(2L);
        assertThat(hit.authorId()).isEqualTo(7L);
        assertThat(hit.score()).isEqualTo(12);
        assertThat(hit.answerCount()).isEqualTo(4);
        assertThat(hit.accepted()).isTrue();
        assertThat(hit.bountyOpen()).isTrue();
        assertThat(hit.createdAt()).isEqualTo(NOW);
    }
}

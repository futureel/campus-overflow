package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.discovery.domain.search.SearchSort;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Discovery 应用层用例：通知中心、事件投影、搜索与相关推荐。 */
class DiscoveryApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");
    private static final Actor PENG = new Actor(7L, "彭天赐", Role.STUDENT);

    private NotificationRepository notifications;
    private IdentityApi identity;
    private QaQueryApi qa;
    private SearchPort search;
    private SearchIndex index;
    private ProcessedEventStore processed;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        identity = mock(IdentityApi.class);
        qa = mock(QaQueryApi.class);
        search = mock(SearchPort.class);
        index = mock(SearchIndex.class);
        processed = mock(ProcessedEventStore.class);
    }

    // ---------- NotificationService ----------

    @Test
    void listMapsEntitiesToViews() {
        Notification n = Notification.reconstitute(1L, 7L, NotificationType.ANSWER_RECEIVED, "你的问题收到新回答",
                "/questions/3", "evt-1", false, NOW);
        when(notifications.findByRecipient(eq(7L), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(n), 1, 1, 20));

        PageResult<NotificationView> page = new NotificationService(notifications).list(PENG, new PageRequest(1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(1L);
            assertThat(v.type()).isEqualTo("ANSWER_RECEIVED");
            assertThat(v.link()).isEqualTo("/questions/3");
            assertThat(v.read()).isFalse();
        });
    }

    @Test
    void markReadRejectsOtherRecipientAndUpdatesOwner() {
        NotificationService service = new NotificationService(notifications);
        Notification n = Notification.create(7L, NotificationType.MENTIONED, "有人在评论中 @ 了你", "/questions/3",
                "evt-2", NOW);
        n.assignId(5L);
        when(notifications.findById(5L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.markRead(new Actor(8L, "路人", Role.STUDENT), 5L))
                .isInstanceOf(com.campusoverflow.shared.domain.ForbiddenException.class);

        service.markRead(PENG, 5L);
        assertThat(n.read()).isTrue();
        verify(notifications).save(n);
    }

    @Test
    void markReadOnMissingNotificationIs404() {
        when(notifications.findById(anyLong())).thenReturn(Optional.empty());
        NotificationService service = new NotificationService(notifications);

        assertThatThrownBy(() -> service.markRead(PENG, 99L))
                .isInstanceOf(com.campusoverflow.shared.domain.NotFoundException.class);
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(notifications.countUnread(7L)).thenReturn(4L);
        assertThat(new NotificationService(notifications).unreadCount(PENG)).isEqualTo(4L);
    }

    // ---------- NotificationProjector ----------

    @Test
    void answerSubmissionNotifiesQuestionAuthorWithAuthorName() {
        when(identity.findUser(9L)).thenReturn(Optional.of(new UserSummary(9L, "王老师", Role.TEACHER, true, "计算机学院")));
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerSubmittedEvent("evt-10", NOW, 100L, 3L, 9L, 7L, "红黑树怎么旋转"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.recipientId()).isEqualTo(7L);
        assertThat(saved.type()).isEqualTo(NotificationType.ANSWER_RECEIVED);
        assertThat(saved.title()).contains("王老师").contains("红黑树怎么旋转");
        assertThat(saved.link()).isEqualTo("/questions/3");
    }

    @Test
    void selfAnsweredQuestionDoesNotNotifyYourself() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerSubmittedEvent("evt-11", NOW, 100L, 3L, 7L, 7L, "自问自答"));

        verify(notifications, never()).save(any());
    }

    @Test
    void duplicatedEventDoesNotCreateSecondNotification() {
        when(notifications.exists(anyString(), anyLong())).thenReturn(true);
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerSubmittedEvent("evt-12", NOW, 100L, 3L, 9L, 7L, "重复投递"));

        verify(notifications, never()).save(any());
    }

    @Test
    void commentMentionNotifiesEachMentionedUser() {
        when(identity.findByDisplayName("王老师"))
                .thenReturn(Optional.of(new UserSummary(9L, "王老师", Role.TEACHER, true, "计算机学院")));
        when(identity.findUser(7L)).thenReturn(Optional.of(new UserSummary(7L, "彭天赐", Role.STUDENT, false, "计算机学院")));
        when(qa.findQuestion(3L)).thenReturn(Optional.of(new QuestionRef(3L, 1L, null, "红黑树怎么旋转", null, false)));
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new CommentPostedEvent("evt-13", NOW, 55L, 3L, 7L, "感谢 @王老师 的讲解"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.recipientId()).isEqualTo(9L);
        assertThat(saved.type()).isEqualTo(NotificationType.MENTIONED);
        assertThat(saved.title()).contains("@ 了你").contains("红黑树怎么旋转");
    }

    @Test
    void mentionOfYourselfIsIgnored() {
        when(identity.findByDisplayName("彭天赐"))
                .thenReturn(Optional.of(new UserSummary(7L, "彭天赐", Role.STUDENT, false, "计算机学院")));
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new CommentPostedEvent("evt-14", NOW, 55L, 3L, 7L, "我 @彭天赐 自己"));

        verify(notifications, never()).save(any());
    }

    // ---------- SearchIndexProjector ----------

    @Test
    void questionPostedInsertsSearchDocumentOnce() {
        when(processed.markProcessed("evt-20", SearchIndexProjector.CONSUMER)).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new QuestionPostedEvent("evt-20", NOW, 3L, 7L, 2L, "红黑树怎么旋转",
                "插入操作后失衡，需要旋转", List.of("数据结构", "java")));

        verify(index).insert(new SearchDocument(3L, 2L, 7L, "红黑树怎么旋转", "插入操作后失衡，需要旋转",
                List.of("数据结构", "java"), NOW));
    }

    @Test
    void duplicateQuestionPostedIsSkipped() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(false);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new QuestionPostedEvent("evt-21", NOW, 3L, 7L, null, "t", "b", List.of("java")));

        verify(index, never()).insert(any());
    }

    @Test
    void voteDeltaUsesNewValueMinusOldValue() {
        when(processed.markProcessed("evt-22", SearchIndexProjector.CONSUMER)).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new VoteCastEvent("evt-22", NOW, "QUESTION", 3L, 7L, 9L, 3L, 1, -1));

        verify(index).adjustScore(3L, -2);
    }

    @Test
    void deletingQuestionMarksIndexDeleted() {
        when(processed.markProcessed("evt-23", SearchIndexProjector.CONSUMER)).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new ContentDeletedEvent("evt-23", NOW, "QUESTION", 3L, 3L));

        verify(index).markDeleted(3L);
        verify(index, never()).adjustAnswerCount(anyLong(), anyInt(), any());
    }

    // ---------- DiscoveryQueryService ----------

    @Test
    void searchFillsAuthorFromIdentityApi() {
        SearchQuery query = new SearchQuery("红黑树", null, null, null, false, false, new PageRequest(1, 20));
        SearchHit hit = new SearchHit(3L, "红黑树怎么旋转", "插入后失衡", List.of("数据结构"), 2L, 7L, 12, 4, true,
                false, NOW);
        when(search.search(query)).thenReturn(new PageResult<>(List.of(hit), 1, 1, 20));
        when(identity.findUsers(any())).thenReturn(
                Map.of(7L, new UserSummary(7L, "彭天赐", Role.STUDENT, true, "计算机学院")));

        PageResult<SearchResultView> page = new DiscoveryQueryService(search, index, identity).search(query);

        assertThat(page.items()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(3L);
            assertThat(v.score()).isEqualTo(12);
            assertThat(v.accepted()).isTrue();
            assertThat(v.author().displayName()).isEqualTo("彭天赐");
            assertThat(v.author().verified()).isTrue();
        });
    }

    @Test
    void relatedRanksByJaccardAndHonoursLimit() {
        when(index.findTags(3L)).thenReturn(List.of("数据结构", "java", "算法"));
        when(index.findCandidatesSharingTags(eq(3L), anyList(), anyInt())).thenReturn(List.of(
                new SearchIndex.RelatedCandidate(11L, "A", 1, 5, 99, 3, true),
                new SearchIndex.RelatedCandidate(12L, "B", 2, 2, 0, 0, false),
                new SearchIndex.RelatedCandidate(13L, "C", 2, 3, 5, 1, true)));

        List<RelatedQuestionView> related = new DiscoveryQueryService(search, index, identity).related(3L, 2);

        assertThat(related).extracting(RelatedQuestionView::id).containsExactly(12L, 13L);
        assertThat(related.getFirst().similarity()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void relatedWithoutTagsReturnsEmpty() {
        when(index.findTags(3L)).thenReturn(List.of());

        assertThat(new DiscoveryQueryService(search, index, identity).related(3L, 5)).isEmpty();
        verify(index, never()).findCandidatesSharingTags(anyLong(), anyList(), anyInt());
    }

    @Test
    void blankSearchFallsBackToNewest() {
        SearchQuery query = new SearchQuery("   ", null, null, SearchSort.RELEVANCE, false, false, null);
        assertThat(query.sort()).isEqualTo(SearchSort.NEWEST);
        assertThat(query.terms()).isEmpty();
    }
}

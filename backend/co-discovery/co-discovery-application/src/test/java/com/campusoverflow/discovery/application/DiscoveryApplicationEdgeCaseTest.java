package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
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

/** 应用层剩余分支：各类事件的静默条件、投影事件矩阵、作者缺失兜底。 */
class DiscoveryApplicationEdgeCaseTest {

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
    void notificationWithoutIdRendersZeroInsteadOfNull() {
        Notification n = Notification.create(7L, NotificationType.BADGE_AWARDED, "徽章", "/u/7", "evt-1", NOW);
        when(notifications.findByRecipient(eq(7L), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(n), 1, 1, 20));

        PageResult<NotificationView> page = new NotificationService(notifications).list(PENG, new PageRequest(1, 20));

        assertThat(page.items()).singleElement().satisfies(v -> assertThat(v.id()).isZero());
    }

    @Test
    void markAllReadReturnsUpdatedRows() {
        when(notifications.markAllRead(7L)).thenReturn(3);

        assertThat(new NotificationService(notifications).markAllRead(PENG)).isEqualTo(3);
    }

    // ---------- NotificationProjector ----------

    @Test
    void acceptedAnswerNotifiesItsAuthor() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerAcceptedEvent("evt-2", NOW, 100L, 3L, 9L, 7L, 7L, "红黑树怎么旋转"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(9L);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.ANSWER_ACCEPTED);
    }

    @Test
    void acceptingYourOwnAnswerIsSilent() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerAcceptedEvent("evt-3", NOW, 100L, 3L, 7L, 7L, 7L, "自问自答"));

        verify(notifications, never()).save(any());
    }

    @Test
    void endorsedAnswerNotifiesAuthorWithEndorserName() {
        when(identity.findUser(9L)).thenReturn(Optional.of(new UserSummary(9L, "王老师", Role.TEACHER, true, "计算机学院")));
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerEndorsedEvent("evt-4", NOW, 100L, 3L, 7L, 9L, "红黑树怎么旋转"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(7L);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.ANSWER_ENDORSED);
        assertThat(captor.getValue().title()).contains("王老师");
    }

    @Test
    void endorsingYourOwnAnswerIsSilent() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new AnswerEndorsedEvent("evt-5", NOW, 100L, 3L, 7L, 7L, "自己认证自己"));

        verify(notifications, never()).save(any());
    }

    @Test
    void commentWithoutMentionTouchesNothing() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new CommentPostedEvent("evt-6", NOW, 55L, 3L, 7L, "这里没有任何提及"));

        verify(notifications, never()).save(any());
        verify(qa, never()).findQuestion(any(Long.class));
    }

    @Test
    void mentionOfUnknownNicknameIsDropped() {
        when(qa.findQuestion(3L)).thenReturn(Optional.of(
                new com.campusoverflow.qa.api.QuestionRef(3L, 1L, null, "红黑树怎么旋转", null, false)));
        when(identity.findByDisplayName("查无此人")).thenReturn(Optional.empty());
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new CommentPostedEvent("evt-7", NOW, 55L, 3L, 7L, "感谢 @查无此人"));

        verify(notifications, never()).save(any());
    }

    @Test
    void bountyAwardAndBadgeAwardProduceNotifications() {
        NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

        projector.on(new BountyAwardedEvent("evt-8", NOW, 40L, 3L, 7L, 9L, 50));
        projector.on(new BadgeAwardedEvent("evt-9", NOW, 9L, "FIRST_ACCEPT", "首次被采纳"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::recipientId).containsExactly(9L, 9L);
        assertThat(captor.getAllValues().get(0).type()).isEqualTo(NotificationType.BOUNTY_AWARDED);
        assertThat(captor.getAllValues().get(0).title()).contains("50");
        assertThat(captor.getAllValues().get(1).type()).isEqualTo(NotificationType.BADGE_AWARDED);
        assertThat(captor.getAllValues().get(1).title()).contains("首次被采纳");
    }

    // ---------- SearchIndexProjector ----------

    @Test
    void questionEditedRewritesSearchText() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new QuestionEditedEvent("evt-10", NOW, 3L, "新标题", "新正文", List.of("java", "算法")));

        verify(index).updateText(3L, "新标题", "新正文", List.of("java", "算法"), NOW);
    }

    @Test
    void answerSubmittedBumpsAnswerCount() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new AnswerSubmittedEvent("evt-11", NOW, 100L, 3L, 9L, 7L, "红黑树"));

        verify(index).adjustAnswerCount(3L, 1, NOW);
    }

    @Test
    void deletingAnswerDecrementsAnswerCount() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new ContentDeletedEvent("evt-12", NOW, "ANSWER", 100L, 3L));

        verify(index).adjustAnswerCount(3L, -1, NOW);
        verify(index, never()).markDeleted(3L);
    }

    @Test
    void acceptingAnswerMarksSearchDocumentAccepted() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new AnswerAcceptedEvent("evt-13", NOW, 100L, 3L, 9L, 7L, 7L, "红黑树"));

        verify(index).markAccepted(3L, NOW);
    }

    @Test
    void bountyOpeningAndClosingToggleTheFlag() {
        when(processed.markProcessed(anyString(), anyString())).thenReturn(true);
        SearchIndexProjector projector = new SearchIndexProjector(index, processed);

        projector.on(new BountyOpenedEvent("evt-14", NOW, 40L, 3L, 7L, 50, NOW.plusSeconds(86400)));
        projector.on(new BountyClosedEvent("evt-15", NOW, 40L, 3L, 7L, 50));
        projector.on(new BountyAwardedEvent("evt-16", NOW, 40L, 3L, 7L, 9L, 50));

        verify(index).setBountyOpen(3L, true);
        verify(index, org.mockito.Mockito.times(2)).setBountyOpen(3L, false);
    }

    // ---------- DiscoveryQueryService ----------

    @Test
    void unknownAuthorFallsBackToPlaceholder() {
        SearchQuery query = new SearchQuery("红黑树", null, null, null, false, false, new PageRequest(1, 20));
        SearchHit hit = new SearchHit(3L, "红黑树怎么旋转", "插入后失衡", List.of("数据结构"), null, 7L, 0, 0, false,
                false, NOW);
        when(search.search(query)).thenReturn(new PageResult<>(List.of(hit), 1, 1, 20));
        when(identity.findUsers(any())).thenReturn(Map.of());

        PageResult<SearchResultView> page = new DiscoveryQueryService(search, index, identity).search(query);

        assertThat(page.items()).singleElement().satisfies(v -> {
            assertThat(v.author().displayName()).isEqualTo("已注销用户");
            assertThat(v.author().verified()).isFalse();
        });
    }
}

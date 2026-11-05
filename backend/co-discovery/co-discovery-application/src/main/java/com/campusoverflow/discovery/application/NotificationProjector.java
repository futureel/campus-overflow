package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.notification.MentionParser;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 通知中心：把上游事件转换为站内通知。幂等键 (sourceEventId, recipientId)。 */
@Component
public class NotificationProjector {

    private final NotificationRepository notifications;
    private final IdentityApi identity;
    private final QaQueryApi qa;

    public NotificationProjector(NotificationRepository notifications, IdentityApi identity, QaQueryApi qa) {
        this.notifications = notifications;
        this.identity = identity;
        this.qa = qa;
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        if (e.answerAuthorId() == e.questionAuthorId()) {
            return;
        }
        notify(e.questionAuthorId(), NotificationType.ANSWER_RECEIVED,
                name(e.answerAuthorId()) + " 回答了你的问题「" + e.questionTitle() + "」",
                "/questions/" + e.questionId(),
                eventKey(e.eventId(), NotificationType.ANSWER_RECEIVED, e.questionAuthorId()), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        if (e.answerAuthorId() == e.acceptedById()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ACCEPTED,
                "你的回答被采纳「" + e.questionTitle() + "」",
                "/questions/" + e.questionId(),
                eventKey(e.eventId(), NotificationType.ANSWER_ACCEPTED, e.answerAuthorId()), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        if (e.endorserId() == e.answerAuthorId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ENDORSED,
                name(e.endorserId()) + " 教师认证了你的回答「" + e.questionTitle() + "」",
                "/questions/" + e.questionId(),
                eventKey(e.eventId(), NotificationType.ANSWER_ENDORSED, e.answerAuthorId()), e.occurredAt());
    }

    /** 评论里的 @昵称 会被解析成通知；跳过 @自己，最多提醒 MentionParser.MAX_MENTIONS 人。 */
    @EventListener
    @Transactional
    public void on(CommentPostedEvent e) {
        Set<String> mentions = MentionParser.extract(e.body());
        if (mentions.isEmpty()) {
            return;
        }
        String questionTitle = qa.findQuestion(e.questionId()).map(QuestionRef::title).orElse("某个问题");
        for (String displayName : mentions) {
            identity.findByDisplayName(displayName)
                    .filter(u -> u.id() != e.authorId())
                    .ifPresent(u -> notify(u.id(), NotificationType.MENTIONED,
                            name(e.authorId()) + " 在「" + questionTitle + "」的评论中 @ 了你",
                            "/questions/" + e.questionId(),
                            eventKey(e.eventId(), NotificationType.MENTIONED, u.id()), e.occurredAt()));
        }
    }

    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        notify(e.winnerId(), NotificationType.BOUNTY_AWARDED,
                "你获得了 " + e.points() + " 点悬赏",
                "/questions/" + e.questionId(),
                eventKey(e.eventId(), NotificationType.BOUNTY_AWARDED, e.winnerId()), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(BadgeAwardedEvent e) {
        notify(e.userId(), NotificationType.BADGE_AWARDED,
                "你获得了徽章「" + e.badgeName() + "」",
                "/users/" + e.userId() + "/reputation",
                eventKey(e.eventId(), NotificationType.BADGE_AWARDED, e.userId()), e.occurredAt());
    }

    /** 幂等写入：同一 (sourceEventId, recipientId) 只落一条通知，事件重复投递不会重复提醒。 */
    private void notify(long recipientId, NotificationType type, String title, String link, String eventId,
                        java.time.Instant at) {
        if (notifications.exists(eventId, recipientId)) {
            return;
        }
        notifications.save(Notification.create(recipientId, type, title, link, eventId, at));
    }

    private String name(long userId) {
        return identity.findUser(userId).map(UserSummary::displayName).orElse("某位同学");
    }

    /** 一个集成事件可能衍生出多条通知，幂等键需带上类型与接收人。 */
    private static String eventKey(String eventId, NotificationType type, long recipientId) {
        return eventId + "#" + type.name() + "#" + recipientId;
    }
}

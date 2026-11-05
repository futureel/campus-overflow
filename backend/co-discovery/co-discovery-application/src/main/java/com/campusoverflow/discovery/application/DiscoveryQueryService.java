package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    /** 搜索：命中结果按页返回，作者信息批量补齐（避免 N+1）。 */
    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        Map<Long, UserSummary> authors = identity.findUsers(
                hits.items().stream().map(SearchHit::authorId).distinct().toList());
        return hits.map(hit -> toView(hit, authors.get(hit.authorId())));
    }

    /** 相关问题：先取自身标签，再按 Jaccard 相似度排序，最多返回 limit 条。 */
    public List<RelatedQuestionView> related(long questionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> ownTags = index.findTags(questionId);
        if (ownTags.isEmpty()) {
            return List.of();
        }
        List<SearchIndex.RelatedCandidate> candidates =
                index.findCandidatesSharingTags(questionId, ownTags, Math.max(limit * 3, limit));
        return RelatedQuestionPolicy.rank(candidates, ownTags.size(), limit).stream()
                .map(c -> new RelatedQuestionView(c.questionId(), c.title(), c.answerCount(), c.accepted(),
                        RelatedQuestionPolicy.jaccard(c.sharedTags(), ownTags.size(), c.tagCount())))
                .toList();
    }

    private static SearchResultView toView(SearchHit hit, UserSummary author) {
        SearchResultView.Author view = author == null
                ? new SearchResultView.Author(hit.authorId(), "已注销用户", "STUDENT", false)
                : new SearchResultView.Author(author.id(), author.displayName(), author.role().name(), author.verified());
        return new SearchResultView(hit.questionId(), hit.title(), hit.snippet(), hit.tags(), hit.courseId(), view,
                hit.score(), hit.answerCount(), hit.accepted(), hit.bountyOpen(), hit.createdAt());
    }
}

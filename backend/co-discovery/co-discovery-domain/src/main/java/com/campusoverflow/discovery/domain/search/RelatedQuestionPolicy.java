package com.campusoverflow.discovery.domain.search;

import java.util.Comparator;
import java.util.List;

/**
 * 相关问题推荐（规则引擎，非 AI）：按标签集合的 Jaccard 相似度排序，
 * J(A,B) = |A∩B| / |A∪B|；相似度相同则得分高、已采纳者优先。
 */
public final class RelatedQuestionPolicy {

    private RelatedQuestionPolicy() {
    }

    /** J(A,B) = |A∩B| / |A∪B|，其中 |A∪B| = |A| + |B| - |A∩B|。 */
    public static double jaccard(int shared, int sizeA, int sizeB) {
        if (shared <= 0) {
            return 0.0;
        }
        int union = sizeA + sizeB - shared;
        if (union <= 0) {
            return 0.0;
        }
        return (double) shared / (double) union;
    }

    /**
     * 排序规则：Jaccard 相似度降序 → 得分降序 → 已采纳优先 → 回答数降序；只保留至少共享一个标签的候选，
     * 并截断到 {@code limit} 条。
     */
    public static List<SearchIndex.RelatedCandidate> rank(List<SearchIndex.RelatedCandidate> candidates, int ownTagCount,
                                                          int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        Comparator<SearchIndex.RelatedCandidate> order =
                Comparator.comparingDouble(
                                (SearchIndex.RelatedCandidate c) -> jaccard(c.sharedTags(), ownTagCount, c.tagCount()))
                        .reversed()
                        .thenComparing(Comparator.comparingInt(SearchIndex.RelatedCandidate::score).reversed())
                        .thenComparing(Comparator.comparing(SearchIndex.RelatedCandidate::accepted).reversed())
                        .thenComparing(Comparator.comparingInt(SearchIndex.RelatedCandidate::answerCount).reversed());
        return candidates.stream()
                .filter(c -> c != null && c.sharedTags() > 0)
                .sorted(order)
                .limit(limit)
                .toList();
    }
}

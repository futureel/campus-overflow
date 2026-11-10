# 事件风暴工作坊记录（Event Storming）

> 日期：2026-09-21（周一，Sprint 1 第 4 次实验日）　时长：3 小时
> 主持/引导：陈洲（SM）　参与：张宇航（PO）、袁友为（Dev）、彭天赐（Dev/QA）
> 目标：用事件风暴统一全员对业务的理解，识别领域事件、命令、聚合与限界上下文，为 S2/S3 编码建立共同语言。
> 对应 Issue：#17。

## 1. 贴纸图例

| 颜色 | 含义 | 本项目内容 |
|---|---|---|
| 橙色 | 领域事件（过去时） | 见第 2 节 |
| 蓝色 | 命令（用户/系统发起的动作） | 见第 3 节 |
| 黄色 | 角色 / 聚合 | 见第 4 节 |
| 粉色 | 热点、待决议问题 | 见第 7 节 |
| 紫色 | 外部系统/策略 | 邮件、课程服务器、CI |

## 2. 领域事件主线（按业务时间线，橙色贴纸）

### 身份与课程（Identity）
1. 用户已注册 `UserRegisteredEvent`
2. 用户已登录（无领域事件，记审计日志 `AuthenticationAuditService`）
3. 登录失败已记录（`LoginThrottle`，连续失败触发锁定）
4. 课程已创建（`CourseService`）
5. 成员已加入课程 / 角色已分配（学生、教师、管理员）

### 问答核心（Q&A）
6. 问题已发布 `QuestionPostedEvent`
7. 问题已编辑 `QuestionEditedEvent`
8. 回答已提交 `AnswerSubmittedEvent`
9. 评论已发布 `CommentPostedEvent`
10. 投票已产生 `VoteCastEvent`（赞成/反对，不可投自己）
11. 回答已被教师背书 `AnswerEndorsedEvent`
12. 回答已被采纳 `AnswerAcceptedEvent`（仅一次）
13. 内容已删除 `ContentDeletedEvent`（软删除，ADR-006）

### 声誉与悬赏（Reputation）
14. 声誉已变更 `ReputationChangedEvent`（异步账本结算，ADR-004）
15. 徽章已授予 `BadgeAwardedEvent`
16. 悬赏已发起 `BountyOpenedEvent`
17. 悬赏已关闭（到期退回积分）`BountyClosedEvent`
18. 悬赏已发放 `BountyAwardedEvent`

### 发现与通知（Discovery，消费上述事件的投影）
19. 搜索索引已更新（`SearchIndexProjector` 消费事件投影）
2. 通知已生成（`NotificationProjector`，类型见 `NotificationType`：ANSWER_RECEIVED / ANSWER_ACCEPTED / ANSWER_ENDORSED / MENTIONED / BOUNTY_AWARDED / BADGE_AWARDED）
3. 通知已读（用户操作，无集成事件）

## 3. 命令 → 事件 → 聚合映射

| 命令（蓝） | 触发事件（橙） | 所属聚合 | 角色 |
|---|---|---|---|
| 注册用户 RegisterUserCommand | UserRegisteredEvent | User | 访客 |
| 创建课程 CreateCourseCommand | 课程已创建 | Course | 管理员 |
| 创建教职工 CreateStaffCommand | 角色已分配 | User | 管理员 |
| 发布问题 | QuestionPostedEvent | Question | 学生/教师 |
| 编辑问题 | QuestionEditedEvent | Question | 作者/管理员 |
| 提交回答 | AnswerSubmittedEvent | Answer | 学生/教师 |
| 发表评论 | CommentPostedEvent | Comment | 任意成员 |
| 投票 | VoteCastEvent | Vote | 非作者成员 |
| 背书回答 | AnswerEndorsedEvent | Answer | 教师 |
| 采纳回答 | AnswerAcceptedEvent | Question/Answer | 提问者/教师 |
| 删除内容 | ContentDeletedEvent | Question/Answer | 作者/管理员 |
| 发起悬赏 | BountyOpenedEvent | Bounty | 学生 |
| （定时）到期处理 | BountyClosedEvent | Bounty | 系统 ReputationJobs |
| （事件消费）结算声誉 | ReputationChangedEvent | ReputationAccount/Ledger | 系统 |
| （事件消费）授予徽章 | BadgeAwardedEvent | Badge | 系统 |
| 搜索 | 无事件（查询） | SearchQuery | 任意成员 |
| 查看通知/标记已读 | 无事件（查询） | Notification | 任意成员 |

## 4. 识别出的聚合与角色

**角色（黄色小人）**：访客、学生、教师、课程管理员、系统定时任务。

**聚合（黄色贴纸，聚合根加粗）**：

| 上下文 | 聚合根 | 关键值对象 |
|---|---|---|
| Identity | **User**、**Course** | Email、DisplayName、Role、CourseMembership |
| Q&A | **Question**、**Answer**、**Comment**、**Vote** | MarkdownBody、Tags、VoteValue |
| Reputation | **ReputationAccount**、**ReputationLedger**、**Bounty**、**Badge** | ReputationPoints、LedgerEntry、BountyAmount |
| Discovery | **SearchDocument**、**Notification** | SearchQuery、SearchSort、Mention |

## 5. 限界上下文与上下文映射

工作坊在白板上划出四条泳道，确认上下文间**只通过各 `*-api` 发布语言交互，禁止跨上下文直接访问对方的表**（与 ArchUnit 规则一致；QA 表不可 JOIN REP 表）。

```
Identity ──(UserRegisteredEvent, 发布语言)──> Reputation / Discovery
Q&A      ──(Question/Answer/Vote/Comment 事件)──> Reputation / Discovery
Reputation ──(声誉/徽章/悬赏事件)──> Discovery（通知）
Discovery ──(只读查询，不向上游发命令)
```

- 集成方式：事务性发件箱（ADR-002）+ OutboxRelay 中继，至少一次投递，消费端幂等。
- 上下文关系：Identity、Q&A、Reputation 是上游（U/D），Discovery 是下游（消费方）；上游不依赖下游。
- 共享内核 `co-shared-kernel`：领域异常、Actor、事件契约、分页，仅放真正共享的最小集合。

## 6. 统一术语表（Ubiquitous Language）

| 术语 | 英文/代码名 | 定义 |
|---|---|---|
| 问题 | Question | 课程范围内的提问，聚合根，软删除 |
| 回答 | Answer | 隶属于问题，可被背书/采纳 |
| 评论 | Comment | 同目标仅一级，不支持嵌套回复 |
| 投票 | Vote | 值只能为 +1/-1，禁止投给自己的内容 |
| 背书 | Endorse | 教师对回答的专业认可，不可重复 |
| 采纳 | Accept | 提问者/教师选定最佳回答，仅一次 |
| 声誉账户 | ReputationAccount | 异步事件结算的积分余额 |
| 声誉账本 | Ledger | 声誉变动流水，唯一键 (event_id,user_id,reason) 保证幂等 |
| 悬赏 | Bounty | 附在问题上的积分，到期未发放则退回 |
| 徽章 | Badge | 满足规则自动授予 |
| 搜索文档 | SearchDocument | 事件投影出的检索单元 |
| 通知 | Notification | 事件驱动生成，支持已读状态 |
| @提及 | Mention | 由 MentionParser 从正文解析，触发 MENTIONED 通知 |

## 7. 热点与待决议问题（粉色贴纸）

| 编号 | 热点 | 决议 | 负责人 | 状态 |
|---|---|---|---|---|
| H1 | 中文搜索分词不准怎么办 | 用 MySQL ngram（token_size=2），保留 LIKE 实现做对照（ADR-005） | 彭天赐 | 已决议 |
| H2 | 声誉事件重复会不会重复加分 | 消费端幂等：账本唯一键 + processed_event 表 | 袁友为 | 已决议 |
| H3 | 软删除的内容是否还能被搜索 | 已删除内容不进搜索索引，通知保留历史 | PO+陈洲 | 已决议 |
| H4 | 悬赏到期的时区与定时精度 | 服务端统一 UTC，定时任务 5 分钟扫描 | 陈洲 | 已决议 |
| H5 | 通知是否需要实时推送（SSE） | Sprint 1 先用 30 秒轮询，S4 再评估 SSE | 彭天赐 | 挂起至 S4 |

## 8. 结论与后续动作

- 18 个领域事件、17 条命令、4 个上下文、15 个聚合/值对象达成全员一致，术语表并入本文件作为后续编码命名依据。
- 后续动作：
  1. 袁友为按第 3 节映射从 Q&A 领域层开始 S2 红绿循环；
  2. 彭天赐依据 Discovery 投影关系准备搜索/通知测试；
  3. 陈洲把事件-命令映射回写到看板 Issue，确保每个事件有对应任务卡；
  4. H5 列入 Sprint 4 待办，Sprint 1 回顾会跟踪。

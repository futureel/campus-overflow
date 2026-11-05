# 上下文映射图（Context Map）

> 模拟版，Lab 3 事件风暴产出
> 风格：Shared Kernel（共享内核）+ Customer/Supplier（客户/供应商）

---

## 限界上下文列表

| 上下文 | 简称 | 职责 |
|---|---|---|
| Identity 身份 | ID | 用户注册、登录、认证、权限 |
| Q&A 问答 | QA | 提问、回答、投票、评论、标签 |
| Reputation 声誉 | REP | 声誉积分、排行榜、徽章、悬赏 |
| Discovery 发现 | DIS | 搜索、通知、@提及、相关推荐 |

---

## 上下文关系

```
┌─────────┐         ┌─────────┐
│  Identity │◄────────│   Q&A   │
│   (ID)    │  用户ID │  (QA)   │
└─────────┘         └────┬────┘
     ▲                   │
     │ 用户ID            │ 领域事件（投票/采纳/回答）
     │                   ▼
┌─────────┐         ┌─────────┐
│ Discovery │◄──────│ Reputation│
│  (DIS)   │ 通知    │  (REP)  │
└─────────┘         └─────────┘
     ▲
     │ 领域事件（问题创建/回答创建）
     │
┌─────────┐
│   Q&A   │
└─────────┘
```

---

## 详细关系说明

### Identity ↔ Q&A
- **关系**：Customer/Supplier
- **Q&A 依赖 Identity**：提问者/回答者必须是已注册用户
- **防腐层**：Q&A 只存 userId，不直接读 Identity 的 User 表

### Q&A ↔ Reputation
- **关系**：Publish/Subscribe（发布/订阅）
- **Q&A 发布领域事件**：AnswerSubmitted, VoteCast, AnswerAccepted
- **Reputation 订阅事件**，异步结算声誉，不阻塞 Q&A 主流程
- **实现方式**：Outbox 模式，Q&A 写 outbox_event 表，Reputation 轮询消费

### Q&A ↔ Discovery
- **关系**：Publish/Subscribe
- **Q&A 发布事件**：QuestionCreated, AnswerCreated
- **Discovery 订阅**，更新搜索索引、创建通知

### Reputation ↔ Discovery
- **关系**：Customer/Supplier
- **Reputation 发布事件**：ReputationChanged, BadgeAwarded
- **Discovery 订阅**，发通知给用户

---

## 聚合设计

### Question 聚合（Q&A 上下文）
- **根**：Question
- **实体**：Tag, Comment
- **值对象**：QuestionTitle, QuestionBody, VoteCount
- **不变式**：
  - 标题 5-150 字
  - 至少 1 个标签
  - 已采纳回答后，问题状态变"已解决"

### Answer 聚合（Q&A 上下文）
- **根**：Answer
- **实体**：Comment
- **值对象**：AnswerBody, VoteCount
- **不变式**：
  - 不能回答自己的问题
  - 只能被采纳一次

### ReputationAccount 聚合（Reputation 上下文）
- **根**：ReputationAccount
- **实体**：ReputationTransaction, Badge
- **不变式**：
  - 声誉不能为负（最低 0）
  - 积分变动必须有对应的交易记录

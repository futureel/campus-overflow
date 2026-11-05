# 事件风暴工作坊记录（模拟版）

> 日期：2026-11-XX | 参与人：张宇航、陈洲、袁有为、彭天赐
> 方法：事件风暴（Event Storming），按领域事件倒推命令和聚合

---

## 一、领域事件（橙色便签）

按时间顺序排列：

| # | 领域事件 | 发生时机 |
|---|---|---|
| 1 | 用户已注册 | 注册成功后 |
| 2 | 用户已验证邮箱 | 点击验证链接后 |
| 3 | 问题已提交 | 提问者发布问题后 |
| 4 | 问题已打标签 | 系统自动关联标签 |
| 5 | 回答已提交 | 回答者发布回答后 |
| 6 | 投票已 cast | 用户投赞成/反对票后 |
| 7 | 回答已采纳 | 提问者点采纳后 |
| 8 | 声誉已结算 | 投票/采纳事件触发异步结算后 |
| 9 | 排行榜已更新 | 声誉结算后定时刷新 |
| 10 | 搜索索引已更新 | 问题/回答创建后异步更新 |
| 11 | 通知已创建 | 回答/投票/@提及触发后 |
| 12 | 通知已读 | 用户点开通知后 |
| 13 | 悬赏已创建 | 提问者设置悬赏后 |
| 14 | 悬赏已发放 | 采纳回答后 |
| 15 | 徽章已授予 | 达到里程碑后 |

---

## 二、命令（蓝色便签）

每个事件前面对应的命令：

| 命令 | 触发的事件 |
|---|---|
| RegisterUser | 用户已注册 |
| VerifyEmail | 用户已验证邮箱 |
| SubmitQuestion | 问题已提交 |
| AddTagToQuestion | 问题已打标签 |
| SubmitAnswer | 回答已提交 |
| CastVote | 投票已 cast |
| AcceptAnswer | 回答已采纳 |
| SettleReputation | 声誉已结算 |
| RefreshLeaderboard | 排行榜已更新 |
| UpdateSearchIndex | 搜索索引已更新 |
| CreateNotification | 通知已创建 |
| MarkNotificationRead | 通知已读 |
| CreateBounty | 悬赏已创建 |
| GrantBounty | 悬赏已发放 |
| AwardBadge | 徽章已授予 |

---

## 三、聚合（黄色便签）

按聚合分组：

| 聚合 | 包含的实体 | 负责的事件 |
|---|---|---|
| **User** | User, Role | 用户已注册、邮箱已验证 |
| **Question** | Question, Tag, Comment | 问题已提交、问题已打标签 |
| **Answer** | Answer, Comment | 回答已提交、回答已采纳 |
| **Vote** | Vote | 投票已 cast |
| **ReputationAccount** | ReputationTransaction, Badge | 声誉已结算、徽章已授予 |
| **Bounty** | Bounty | 悬赏已创建、悬赏已发放 |
| **Notification** | Notification | 通知已创建、通知已读 |
| **SearchIndex** | SearchDocument | 搜索索引已更新 |

---

## 四、外部系统/角色（粉色便签）

| 角色 | 触发的命令 |
|---|---|
| 学生（提问者） | RegisterUser, SubmitQuestion, SubmitAnswer, CastVote, AcceptAnswer, CreateBounty |
| 学生（回答者） | SubmitAnswer, CastVote |
| 管理员 | AddTagToQuestion（管理标签） |
| 定时任务 | RefreshLeaderboard, SettleReputation |

---

## 五、识别的业务规则

1. 一个 Question 只能有一个 Answer 被采纳
2. 一个用户对一个内容只能投一次票，可改票
3. 不能给自己的内容投票
4. 悬赏从提问者声誉扣，采纳后给回答者
5. 声誉结算异步处理，不阻塞主流程（Outbox 模式）

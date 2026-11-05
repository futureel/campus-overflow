Feature: 采纳回答
  作为提问者
  我希望把某个回答标记为"已采纳"
  以便告诉其他人这是正确答案

  Background:
    Given 用户已登录
    And 该用户提了一个问题 "Spring Boot 启动报错"
    And 存在两个回答："检查 JDK 版本" 和 "重新装 Maven"

  Scenario: 正常采纳
    When 提问者对回答 "检查 JDK 版本" 点"采纳"
    Then 该回答显示绿色"已采纳"标记
    And 问题状态变为"已解决"
    And 回答者获得 15 积分

  Scenario: 只能采纳一个
    Given 提问者已采纳了回答 "检查 JDK 版本"
    When 提问者尝试采纳回答 "重新装 Maven"
    Then 返回错误"一个问题只能采纳一个回答"

  Scenario: 只有提问者能采纳
    Given 当前用户不是该问题的提问者
    When 该用户点"采纳"按钮
    Then 返回错误"只有提问者能采纳回答"

  Scenario: 取消采纳
    Given 提问者已采纳了回答 "检查 JDK 版本"
    When 提问者再次点该回答的"采纳"按钮
    Then 取消采纳标记
    And 回答者的 15 积分被扣除
    And 问题状态变回"未解决"

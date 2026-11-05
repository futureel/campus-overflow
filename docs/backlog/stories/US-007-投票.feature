Feature: 投票
  作为学生
  我希望对问题和回答投赞成票或反对票
  以便标记好坏内容

  Background:
    Given 用户已登录
    And 存在一个问题 "Spring Boot 启动报错"
    And 存在一个回答 "检查 JDK 版本..."

  Scenario: 给问题投赞成票
    When 用户对问题投赞成票
    Then 问题的赞成数 +1
    And 用户获得 2 积分

  Scenario: 给自己的问题投票
    Given 用户是该问题的提问者
    When 用户对自己的问题投票
    Then 返回错误"不能给自己的内容投票"

  Scenario: 重复投票
    Given 用户已对该问题投过赞成票
    When 用户再次投票
    Then 返回错误"每人只能投一次"

  Scenario: 改票（从赞成改反对）
    Given 用户已对该回答投过赞成票
    When 用户改成投反对票
    Then 赞成数 -1，反对数 +1
    And 回答者积分变化：-10（赞成）变 -2（反对）

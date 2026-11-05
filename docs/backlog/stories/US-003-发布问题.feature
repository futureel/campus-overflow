Feature: 发布问题
  作为学生
  我希望发布一个带标题、正文、标签的问题
  以便获得帮助

  Background:
    Given 用户已登录

  Scenario: 正常发布问题
    When 用户提交标题 "Spring Boot 启动报 ClassNotFound"、正文 "我运行 mvn spring-boot:run 报错..."、标签 "Java,Spring Boot"
    Then 系统创建问题，状态为"未回答"
    And 问题显示在列表最前面

  Scenario: 标题太短
    When 用户提交标题 "报错"（2个字）
    Then 返回错误"标题至少 5 个字"

  Scenario: 标题太长
    When 用户提交超过 150 字的标题
    Then 返回错误"标题最多 150 字"

  Scenario: 没有选标签
    When 用户提交问题但没选标签
    Then 返回错误"至少选择 1 个标签"

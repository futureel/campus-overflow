Feature: 用户注册
  作为一名新生
  我希望用学号邮箱注册账号
  以便我的身份和学校课程绑定

  Background:
    Given 系统已启动，数据库已连接

  Scenario: 正常注册
    When 用户输入邮箱 "student@hbu.edu.cn"、密码 "Password123"、昵称 "张同学"
    Then 系统创建账号，状态为"待验证"
    And 发送验证邮件到 "student@hbu.edu.cn"

  Scenario: 邮箱已被注册
    Given 数据库中已存在邮箱 "student@hbu.edu.cn" 的账号
    When 用户用同一邮箱注册
    Then 返回错误"邮箱已被注册"

  Scenario: 密码太短
    When 用户输入密码 "123"
    Then 返回错误"密码至少 8 位"

  Scenario: 昵称重复
    Given 数据库中已存在昵称 "张同学"
    When 用户用同一昵称注册
    Then 返回错误"昵称已被占用"

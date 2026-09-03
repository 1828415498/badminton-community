# 数据库设计

## 当前状态

**设计中。**

当前业务对象：

- User
- Activity
- Venue
- Court
- Reservation
- Registration
- Post
- Comment
- PostLike

## 当前关系草图

```text
User 1:N Activity
User 1:N Reservation
User 1:N Registration
User 1:N Post
User 1:N Comment
User 1:N PostLike

Venue 1:N Court
Court 1:N Reservation
Activity 1:N Reservation
Activity 1:N Registration
Post 1:N Comment
Post 1:N PostLike
```

## 待确认问题

- 各实体字段与类型
- Reservation 的时间字段与 Activity 时间字段如何建模
- Registration 的状态历史如何保存
- PostLike 的唯一约束
- 球场时间冲突约束如何落地
- 用户重叠时间最多两个预约如何落地
- 软删除策略
- 索引策略

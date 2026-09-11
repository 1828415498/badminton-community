package com.badmintoncommunity.module.activity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建活动请求（api.md §5.1）。
 *
 * <p>接口只收"办什么活动、几点到几点、占哪几块场、最多几个人"。
 * 时间规则（整点/不跨天/7 天窗口）、球场可用性、场地冲突全部由 Service 判定，
 * 前端不做任何限制性计算。</p>
 */
@Data
public class CreateActivityRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 100, message = "title 长度不能超过 100")
    private String title;

    @Size(max = 2000, message = "description 长度不能超过 2000")
    private String description;

    /** 开始时间（整点；须晚于当前时刻，且在未来 7 个自然日内） */
    @NotNull(message = "start_time 不能为空")
    private LocalDateTime startTime;

    /** 结束时间（整点；须晚于 start_time，且与 start 同一天） */
    @NotNull(message = "end_time 不能为空")
    private LocalDateTime endTime;

    /** 报名人数上限（≥1；创建者自动占用 1 个名额，故最小为 1） */
    @NotNull(message = "max_members 不能为空")
    @Min(value = 1, message = "max_members 不能小于 1")
    private Integer maxMembers;

    /** 球场 id 列表（至少 1 个；Service 会去重并按 id 升序加锁，顺序无关） */
    @NotEmpty(message = "court_ids 至少需要一个球场")
    private List<Long> courtIds;
}

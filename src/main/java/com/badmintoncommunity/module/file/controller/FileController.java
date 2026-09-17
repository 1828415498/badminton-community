package com.badmintoncommunity.module.file.controller;

import com.badmintoncommunity.common.Result;
import com.badmintoncommunity.module.file.dto.UploadedFileVO;
import com.badmintoncommunity.module.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口。
 *
 * <h2>两个路径，两种角色</h2>
 * <pre>
 * POST /api/upload          上传（需登录 → 被 WebConfig 的拦截器保护）
 * GET  /api/files/**        读取（匿名 → 必须放进白名单）
 * </pre>
 *
 * <h2>为什么读取必须匿名</h2>
 * 头像最终是写在 {@code <img src="/api/files/…">} 里的。浏览器加载 img 时
 * <b>不会</b>带上 {@code Authorization} 头 —— 如果这条路径也要认证，页面上所有
 * 头像都会变成裂图。所以「上传要身份，读取靠随机文件名兜底」是这类场景的标准取舍：
 * 文件名是 32 位随机串，猜不到就等于不公开。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传一张图片（POST /api/upload）。
     *
     * <p>注意 {@code @RequestParam(name = "file")} 必须显式写 name：
     * 全局的 snake_case 命名策略只作用于 Jackson（JSON 体），
     * 对表单/查询参数不生效 —— 这里参数名是单个单词，显式写出更不容易踩坑。</p>
     */
    @PostMapping("/upload")
    public Result<UploadedFileVO> upload(@RequestParam(name = "file") MultipartFile file) {
        return Result.ok(fileService.store(file));
    }
}

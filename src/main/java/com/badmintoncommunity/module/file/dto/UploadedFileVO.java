package com.badmintoncommunity.module.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传成功后返回的描述对象。
 *
 * <p>{@code url} 是<b>相对路径</b>（形如 {@code /api/files/2026/09/ab12….png}），
 * 前端直接塞进 {@code <img src>} 即可。用相对路径而不是完整域名，是为了让同一条
 * 数据在本地、测试、生产三套环境都能直接可用 —— 换域名时不用刷库。</p>
 */
@Data
@AllArgsConstructor
public class UploadedFileVO {

    /** 相对访问路径，存进 user.avatar_url 的就是它 */
    private String url;

    /** 原始文件名，仅用于前端提示，不参与存储 */
    private String name;

    /** 字节数 */
    private long size;
}

package com.badmintoncommunity.module.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图片上传配置（app.upload.*），默认值见 application.yml。
 *
 * <h2>为什么存储目录要可配置</h2>
 * 开发时文件落在项目目录下最省事；上线后应用目录往往不可写（或者容器重启就没了），
 * 那时只要把 <code>app.upload.dir</code> 指向一块独立磁盘即可，代码一行不用改。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 文件落盘根目录。相对路径以「应用启动目录」为基准（本地开发 = 项目根目录） */
    private String dir = "./uploads";

    /** 对外访问前缀。必须与 WebConfig 里的静态资源映射保持一致 */
    private String urlPrefix = "/api/files";

    /** 允许的图片扩展名（小写、不含点） */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp", "gif");
}

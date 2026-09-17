package com.badmintoncommunity.module.file.service;

import com.badmintoncommunity.common.BusinessException;
import com.badmintoncommunity.common.ResultCode;
import com.badmintoncommunity.module.file.config.UploadProperties;
import com.badmintoncommunity.module.file.dto.UploadedFileVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片存储（本地磁盘）。
 *
 * <h2>为什么存磁盘，不存数据库</h2>
 * 图片是二进制大对象。塞进数据库会让表体积暴涨、备份变慢，且每次查询都拖着一堆字节走。
 * 数据库只存「URL 字符串」（如 {@code user.avatar_url}），文件本体交给文件系统 ——
 * 最朴素，也最稳。将来要换对象存储，只需替换本类，接口和数据库都不用动。
 *
 * <h2>为什么丢弃原始文件名</h2>
 * 用户上传的文件名不可信，它可能：
 * <ul>
 *   <li>带 {@code ../../} 试图穿越目录写出到项目外；</li>
 *   <li>是中文 / emoji / 超长字符串，让编码和文件系统行为变得不可预期；</li>
 *   <li>与别人的文件重名而互相覆盖。</li>
 * </ul>
 * 统一改成 {@code UUID + 白名单扩展名}，三个问题一次解决。
 *
 * <h2>为什么按 yyyy/MM 分目录</h2>
 * 单目录堆到几十万个文件后，文件系统查找会明显变慢。按年月分桶是成本最低的分流，
 * 也便于将来按时间批量归档或清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 目录分桶格式，例如 2026/09 */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 读取文件头做类型嗅探的字节数（WebP 需要 12 字节） */
    private static final int HEAD_LEN = 12;

    private final UploadProperties props;

    /** 落盘根目录（绝对路径）。由 {@link #init()} 在 Bean 初始化后计算。 */
    private Path root;

    @PostConstruct
    void init() {
        this.root = Paths.get(props.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("upload dir ready: {}", root);
        } catch (IOException e) {
            // 目录都建不出来说明配置或权限有问题 —— 启动就失败，好过运行时才发现
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    /** 供 WebConfig 注册静态资源映射使用 */
    public Path getRoot() {
        return root;
    }

    /**
     * 保存一张上传的图片，返回它的访问 URL。
     *
     * @throws BusinessException 文件为空 / 类型不允许 / 写盘失败
     */
    public UploadedFileVO store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY);
        }

        String original = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "";

        String ext = extensionOf(original);
        if (!props.getAllowedExtensions().contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }

        // 纵深防御：扩展名是用户自己填的，把 a.exe 改名成 a.png 就能骗过上一道检查。
        // 于是再看一眼文件头（magic bytes），确认它真的是一张图。
        try (InputStream in = file.getInputStream()) {
            if (!looksLikeImage(in.readNBytes(HEAD_LEN))) {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
            }
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
        }

        String sub = LocalDate.now().format(MONTH_DIR);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        Path dir = root.resolve(sub).normalize();
        // 确认拼出来的目录没有被 ".." 带出根目录之外
        if (!dir.startsWith(root)) {
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
        }
        Path target = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("保存上传文件失败: {}", target, e);
            throw new BusinessException(ResultCode.FILE_SAVE_FAILED);
        }

        String url = props.getUrlPrefix() + "/" + sub + "/" + filename;
        log.debug("uploaded {} ({} bytes) -> {}", original, file.getSize(), url);
        return new UploadedFileVO(url, original, file.getSize());
    }

    /** 取小写扩展名（不含点）；没有扩展名时返回空串 */
    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 读文件头判断是不是常见图片格式。
     *
     * <p>这里只认四种最通用的格式，够覆盖头像场景；不做「解析整张图片」那么重的校验 ——
     * 那是杀毒软件和图片处理库的活，不是业务代码该干的。</p>
     */
    private static boolean looksLikeImage(byte[] head) {
        if (head.length < 4) {
            return false;
        }
        // JPEG: FF D8 FF
        if (head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return true;
        }
        // GIF: "GIF8"
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return true;
        }
        // WebP: "RIFF" … "WEBP"
        return head.length >= HEAD_LEN
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }
}

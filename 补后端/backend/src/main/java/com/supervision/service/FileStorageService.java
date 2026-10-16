package com.supervision.service;

import com.supervision.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** 通用上传和工程资料上传共用白名单、目录与文件登记。 */
@Service
public class FileStorageService {
    private final Path root;
    private final Set<String> extensions;
    private final AttachmentAccessService attachments;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String dir,
            @Value("${app.upload.allowed-extensions:jpg,jpeg,png,gif,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar}") String allowed,
            AttachmentAccessService attachments) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.extensions = Arrays.stream(allowed.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        this.attachments = attachments;
    }

    public Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("上传文件不能为空");
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) throw new BusinessException("文件名不能为空");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.contains("..")) throw new BusinessException("非法的文件名: " + original);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) throw new BusinessException("文件缺少扩展名，无法识别文件类型");
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(ext)) throw new BusinessException("不支持的文件类型: " + ext);
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(date).resolve(filename);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            String url = "/api/uploads/" + date + "/" + filename;
            attachments.register(url);
            return Map.of("url", url, "name", name, "size", file.getSize());
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(500, "文件上传失败，请重试"); }
    }
}

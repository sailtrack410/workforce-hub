package com.workforcehub.system.service;

import com.workforcehub.system.repository.DictRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 品牌化配置读取（登录页标语等），数据源为 sys_dict
 */
@Service
public class BrandingService {

    /** 登录页轮播标语字典类型 */
    public static final String DICT_TYPE_LOGIN_TAGLINE = "brand_login_tagline";

    private final DictRepository dictRepository;

    public BrandingService(DictRepository dictRepository) {
        this.dictRepository = dictRepository;
    }

    /**
     * 启用的登录页标语，按 sort_no 升序；无配置时返回空列表，由前端回退默认文案
     */
    public List<String> loginTaglines() {
        return dictRepository.listByType(DICT_TYPE_LOGIN_TAGLINE, 1).stream()
            .map((row) -> row.get("itemLabel"))
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .filter((label) -> !label.isBlank())
            .toList();
    }
}

package com.rick.frontendgen.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum CodeGenTypeEnum {

    HTML("原生 HTML 模式", "html"),
    MULTI_FILE("原生多文件模式", "multi_file"),
    VUE_PROJECT("Vue 工程模式", "vue_project");

    private final String text;
    private final String value;

    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * @param value
     * @return
     */
    public static CodeGenTypeEnum getEnumByValue(String value) {
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        // 兼容早期版本写入数据库的连字符值（例如 multi-file）。
        String normalizedValue = value.trim().replace('-', '_');
        for (CodeGenTypeEnum anEnum : CodeGenTypeEnum.values()) {
            if (anEnum.value.equals(normalizedValue)) {
                return anEnum;
            }
        }
        return null;
    }
}

package com.workforcehub.common.response;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 统一分页结果
 */
@Data
public class PageResult<T> implements Serializable {

    private long pageNo;
    private long pageSize;
    private long total;
    private List<T> list;

    public static <T> PageResult<T> of(long pageNo, long pageSize, long total, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setList(list);
        return result;
    }
}

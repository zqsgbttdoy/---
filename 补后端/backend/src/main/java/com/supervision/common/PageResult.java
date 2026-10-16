package com.supervision.common;

import lombok.Data;

@Data
public class PageResult<T> {

    private java.util.List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public PageResult(java.util.List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}

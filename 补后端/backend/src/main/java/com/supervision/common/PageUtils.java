package com.supervision.common;

/**
 * 分页参数统一钳制工具：pageNum ≥ 1，1 ≤ pageSize ≤ 200（默认 10）。
 * 各分页 Service 统一调用，防止恶意超大分页拖垮数据库。
 */
public final class PageUtils {

    /** pageSize 上限，防止单次查询拉取过多数据 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 统计类接口的 pageSize 上限（前端统计页需一次拉取全量数据做聚合） */
    public static final int MAX_STATS_PAGE_SIZE = 2000;

    /** pageSize 非法时的默认值 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private PageUtils() {
    }

    /** 钳制页码：小于 1 时归一为 1 */
    public static int normalizePageNum(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    /** 钳制每页条数：小于 1 时取默认值 10，大于 200 时钳制为 200 */
    public static int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 统计类接口专用的每页条数钳制：小于 1 时取默认值 10，大于 2000 时钳制为 2000。
     * <p>前端统计页（diary-statistics / log-statistics / safety-log-statistics）会以
     * pageSize=1000/2000 一次性拉取全量数据在浏览器端做聚合，普通 200 上限会导致数据截断，
     * 因此仅监理日记 / 监理日志 / 安全日志三个 Service 的 list 方法使用本方法放宽上限。
     */
    public static int normalizePageSizeForStats(int pageSize) {
        if (pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_STATS_PAGE_SIZE);
    }
}

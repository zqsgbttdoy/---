package com.supervision;

import com.supervision.common.PageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PageUtils 分页参数钳制工具单元测试：
 * 覆盖 normalizePageNum / normalizePageSize / normalizePageSizeForStats 的边界用例。
 */
@DisplayName("PageUtils 分页参数钳制测试")
class PageUtilsTest {

    // ---------------- normalizePageNum ----------------

    @Test
    @DisplayName("normalizePageNum - 小于1（0）应回退为1")
    void testNormalizePageNumZero() {
        assertEquals(1, PageUtils.normalizePageNum(0));
    }

    @Test
    @DisplayName("normalizePageNum - 负数应回退为1")
    void testNormalizePageNumNegative() {
        assertEquals(1, PageUtils.normalizePageNum(-5));
    }

    @Test
    @DisplayName("normalizePageNum - 正常值透传")
    void testNormalizePageNumNormal() {
        assertEquals(1, PageUtils.normalizePageNum(1));
        assertEquals(7, PageUtils.normalizePageNum(7));
        assertEquals(Integer.MAX_VALUE, PageUtils.normalizePageNum(Integer.MAX_VALUE));
    }

    // ---------------- normalizePageSize ----------------

    @Test
    @DisplayName("normalizePageSize - 小于1（0）应回退默认值10")
    void testNormalizePageSizeZero() {
        assertEquals(PageUtils.DEFAULT_PAGE_SIZE, PageUtils.normalizePageSize(0));
    }

    @Test
    @DisplayName("normalizePageSize - 负数应回退默认值10")
    void testNormalizePageSizeNegative() {
        assertEquals(PageUtils.DEFAULT_PAGE_SIZE, PageUtils.normalizePageSize(-1));
    }

    @Test
    @DisplayName("normalizePageSize - 超过200应钳制为200")
    void testNormalizePageSizeExceedingMax() {
        assertEquals(PageUtils.MAX_PAGE_SIZE, PageUtils.normalizePageSize(201));
        assertEquals(PageUtils.MAX_PAGE_SIZE, PageUtils.normalizePageSize(1000));
        assertEquals(PageUtils.MAX_PAGE_SIZE, PageUtils.normalizePageSize(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("normalizePageSize - 正常值透传（含边界1与200）")
    void testNormalizePageSizeNormal() {
        assertEquals(1, PageUtils.normalizePageSize(1));
        assertEquals(10, PageUtils.normalizePageSize(10));
        assertEquals(200, PageUtils.normalizePageSize(200));
    }

    // ---------------- normalizePageSizeForStats ----------------

    @Test
    @DisplayName("normalizePageSizeForStats - 小于1应回退默认值10")
    void testNormalizePageSizeForStatsInvalid() {
        assertEquals(PageUtils.DEFAULT_PAGE_SIZE, PageUtils.normalizePageSizeForStats(0));
        assertEquals(PageUtils.DEFAULT_PAGE_SIZE, PageUtils.normalizePageSizeForStats(-10));
    }

    @Test
    @DisplayName("normalizePageSizeForStats - 超过2000应钳制为2000")
    void testNormalizePageSizeForStatsExceedingMax() {
        assertEquals(PageUtils.MAX_STATS_PAGE_SIZE, PageUtils.normalizePageSizeForStats(2001));
        assertEquals(PageUtils.MAX_STATS_PAGE_SIZE, PageUtils.normalizePageSizeForStats(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("normalizePageSizeForStats - 正常值透传（含统计页常用1000/2000与边界1）")
    void testNormalizePageSizeForStatsNormal() {
        assertEquals(1, PageUtils.normalizePageSizeForStats(1));
        assertEquals(1000, PageUtils.normalizePageSizeForStats(1000));
        assertEquals(2000, PageUtils.normalizePageSizeForStats(2000));
        // 普通上限200不影响统计钳制：500 应原样透传
        assertEquals(500, PageUtils.normalizePageSizeForStats(500));
    }
}

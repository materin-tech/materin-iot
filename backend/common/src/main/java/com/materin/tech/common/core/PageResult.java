package com.materin.tech.common.core;

import java.util.List;

/** 分页信封：与前端约定 data = { items, total }。 */
public record PageResult<T>(List<T> items, long total) {
}

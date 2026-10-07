package com.materin.tech.component.ota.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** OTA 计划候选设备查询（NOT EXISTS 排除已添加，原生 SQL 分页，防大计划 IN 列表）。 */
@Mapper
public interface OtaCandidateMapper {

    String CONDITIONS = """
            FROM device d
            WHERE NOT EXISTS (SELECT 1 FROM ota_task_device otd
                              WHERE otd.task_id = #{taskId} AND otd.device_id = d.id)
            AND (#{productId} IS NULL OR d.product_id = #{productId})
            AND (#{keyword} IS NULL OR d.device_key LIKE CONCAT('%', #{keyword}, '%') OR d.name LIKE CONCAT('%', #{keyword}, '%'))
            """;

    @Select("SELECT d.id, d.device_key AS deviceKey, d.name, d.product_name AS productName, d.firmware "
            + CONDITIONS + " ORDER BY d.id LIMIT #{pageSize} OFFSET #{offset}")
    List<Map<String, Object>> selectCandidates(@Param("taskId") long taskId,
                                               @Param("productId") Long productId,
                                               @Param("keyword") String keyword,
                                               @Param("pageSize") int pageSize,
                                               @Param("offset") long offset);

    @Select("SELECT COUNT(*) " + CONDITIONS)
    long countCandidates(@Param("taskId") long taskId,
                         @Param("productId") Long productId,
                         @Param("keyword") String keyword);
}

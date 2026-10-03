package com.materin.tech.component.product.mapper;

import com.materin.tech.component.product.entity.Product;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @org.apache.ibatis.annotations.Update(
            "UPDATE product SET device_count = device_count + 1 WHERE id = #{id}")
    int incrementDeviceCount(@org.apache.ibatis.annotations.Param("id") Long id);

    @org.apache.ibatis.annotations.Update(
            "UPDATE product SET device_count = GREATEST(device_count - 1, 0) WHERE id = #{id}")
    int decrementDeviceCount(@org.apache.ibatis.annotations.Param("id") Long id);
}

package com.heecomou.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heecomou.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
package com.yhang.springbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yhang.springbootinit.model.entity.Question;

import java.util.Date;
import java.util.List;

/**
* @author MI
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2025-07-27 20:34:49
* @Entity generator.domain.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    List<Question> listPostWithDelete(Date fiveMinutesAgoDate);
}





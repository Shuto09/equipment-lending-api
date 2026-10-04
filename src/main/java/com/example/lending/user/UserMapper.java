package com.example.lending.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

/** SQL は mapper/user/UserMapper.xml。 */
@Mapper
public interface UserMapper {

	Optional<UserRow> findById(long id);

}

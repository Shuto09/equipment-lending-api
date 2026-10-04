package com.example.lending.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

/** SQL は mapper/user/UserMapper.xml。 */
@Mapper
public interface UserMapper {

	Optional<UserRow> findById(long id);

	/** {@code SELECT ... FOR UPDATE}。同じ利用者の貸出を直列化する（件数の上限 R6 の判定のため）。 */
	Optional<UserRow> lockById(long id);

}

package com.example.lending.equipment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は mapper/equipment/EquipmentMapper.xml（注釈では書かない）。 */
@Mapper
public interface EquipmentMapper {

	/** 生成された id を {@link NewEquipment#setId} で受ける。管理番号の重複は一意制約違反で知る。 */
	void insert(NewEquipment equipment);

	Optional<EquipmentRow> findById(long id);

	/** {@code SELECT ... FOR UPDATE}。廃棄と貸出の判定を、同じ備品について直列化する。 */
	Optional<EquipmentRow> lockById(long id);

	/** 備品＋貸出状態＋今の借り手。 */
	Optional<EquipmentView> findView(@Param("id") long id, @Param("today") LocalDate today);

	List<EquipmentView> search(EquipmentSearchQuery query);

	long count(EquipmentSearchQuery query);

	/** 楽観ロックの更新。0 件なら version が古い・廃棄済み・備品が無いのどれか。 */
	int updateWithVersion(EquipmentUpdate update);

	boolean hasOpenLoan(long equipmentId);

	int markDisposed(@Param("id") long id, @Param("disposedAt") OffsetDateTime disposedAt);

}

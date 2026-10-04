package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 登録する貸出。INSERT の後に MyBatis が生成された id を入れるので、ここだけ可変のクラスにする。 */
public class NewLoan {

	private Long id;

	private final long equipmentId;

	private final long borrowerId;

	private final OffsetDateTime lentAt;

	private final LocalDate dueDate;

	public NewLoan(long equipmentId, long borrowerId, OffsetDateTime lentAt, LocalDate dueDate) {
		this.equipmentId = equipmentId;
		this.borrowerId = borrowerId;
		this.lentAt = lentAt;
		this.dueDate = dueDate;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getEquipmentId() {
		return equipmentId;
	}

	public long getBorrowerId() {
		return borrowerId;
	}

	public OffsetDateTime getLentAt() {
		return lentAt;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

}

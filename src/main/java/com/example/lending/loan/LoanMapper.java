package com.example.lending.loan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は mapper/loan/LoanMapper.xml（注釈では書かない）。 */
@Mapper
public interface LoanMapper {

	/**
	 * 生成された id を {@link NewLoan#setId} で受ける。同じ備品の未返却が既にあれば、部分一意インデックス
	 * uq_loans_open_equipment の違反（DuplicateKeyException）になる。
	 */
	void insert(NewLoan loan);

	Optional<LoanView> findView(long id);

	/** 条件付きの返却（returned_at IS NULL の行だけ）。0 件なら返却済み。 */
	int markReturned(@Param("loanId") long loanId, @Param("returnedAt") OffsetDateTime returnedAt);

	/** 未返却の件数と、そのうち返却期限 < 今日の件数。 */
	BorrowerLoanStats statsByBorrower(@Param("borrowerId") long borrowerId, @Param("today") LocalDate today);

	List<LoanView> search(LoanSearchQuery query);

	long count(LoanSearchQuery query);

	/** 延滞（未返却かつ返却期限 < 今日）を、延滞日数の多い順（返却期限・id の順）で。 */
	List<OverdueLoanView> findOverdue(@Param("today") LocalDate today, @Param("limit") int limit,
			@Param("offset") long offset);

	long countOverdue(@Param("today") LocalDate today);

}

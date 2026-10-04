package com.example.lending.loan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import com.example.lending.equipment.EquipmentMapper;
import com.example.lending.equipment.EquipmentRow;
import com.example.lending.equipment.EquipmentStatus;
import com.example.lending.shared.config.LendingProperties;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.user.CurrentUser;
import com.example.lending.user.UserMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貸出の業務ルール（docs/要件と設計.md §4・§8）。{@code @Transactional} はこのクラスの公開メソッドだけに付ける。
 * 時刻は 1 操作で 1 回だけ Clock から読み、判定と保存の両方に同じ値を使う。
 */
@Service
public class LoanService {

	private final LoanMapper loanMapper;

	private final EquipmentMapper equipmentMapper;

	private final UserMapper userMapper;

	private final Clock clock;

	private final LendingProperties lending;

	public LoanService(LoanMapper loanMapper, EquipmentMapper equipmentMapper, UserMapper userMapper, Clock clock,
			LendingProperties lending) {
		this.loanMapper = loanMapper;
		this.equipmentMapper = equipmentMapper;
		this.userMapper = userMapper;
		this.clock = clock;
		this.lending = lending;
	}

	/**
	 * 貸出。安い判定から順に行い、入力だけで分かる期限の範囲はロックを取る前に弾く。
	 * ロックは「利用者 → 備品」の順に固定する（廃棄は備品だけ、返却は取らないので、逆順の組み合わせが無くデッドロックしない）。
	 * <ol>
	 * <li>期限の範囲（R4・422）</li>
	 * <li>利用者行をロック: 同じ人の貸出を直列化し、件数の上限（R6）を数え間違えない</li>
	 * <li>備品行をロック（無ければ 404）: 同じ備品への廃棄・ほかの貸出と直列化する</li>
	 * <li>廃棄済み（R2・409）→ 貸出中（R1・409）→ 延滞あり（R5・422）→ 件数の上限（R6・422）</li>
	 * </ol>
	 * 判定をすり抜けて INSERT が部分一意インデックスに当たった時も、制約名で 409 EQUIPMENT_ALREADY_LENT にする（二重の守り）。
	 */
	@Transactional
	public LoanResponse lend(CurrentUser user, LendRequest request) {
		Instant now = clock.instant();
		LocalDate today = lending.dateOf(now);
		LocalDate lastDueDate = today.plusDays(lending.maxLoanDays());
		if (request.dueDate().isBefore(today) || request.dueDate().isAfter(lastDueDate)) {
			throw new BusinessException(ErrorCode.DUE_DATE_OUT_OF_RANGE,
					"返却期限は " + today + " から " + lastDueDate + " までの日付で指定してください");
		}
		userMapper.lockById(user.id())
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "利用者 id=" + user.id() + " が見つかりません"));
		EquipmentRow equipment = equipmentMapper.lockById(request.equipmentId())
			.orElseThrow(() -> new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND,
					"備品 id=" + request.equipmentId() + " はありません"));
		if (equipment.status() == EquipmentStatus.DISPOSED) {
			throw new BusinessException(ErrorCode.EQUIPMENT_DISPOSED, "備品 " + equipment.assetCode() + " は廃棄済みです");
		}
		if (equipmentMapper.hasOpenLoan(equipment.id())) {
			throw alreadyLent(equipment);
		}
		BorrowerLoanStats stats = loanMapper.statsByBorrower(user.id(), today);
		if (stats.overdueCount() > 0) {
			throw new BusinessException(ErrorCode.BORROWER_HAS_OVERDUE,
					"延滞中の貸出が " + stats.overdueCount() + " 件あります。返却してから借りてください");
		}
		if (stats.openCount() >= lending.maxOpenLoansPerBorrower()) {
			throw new BusinessException(ErrorCode.LOAN_LIMIT_EXCEEDED, "未返却の貸出が上限（"
					+ lending.maxOpenLoansPerBorrower() + " 件）に達しています。返却してから借りてください");
		}
		NewLoan loan = new NewLoan(equipment.id(), user.id(), lending.timeOf(now), request.dueDate());
		try {
			loanMapper.insert(loan);
		}
		catch (DuplicateKeyException ex) {
			if (DbConstraints.isViolationOf(ex, DbConstraints.UQ_LOANS_OPEN_EQUIPMENT)) {
				throw alreadyLent(equipment);
			}
			throw ex; // 知らない制約は変換しない（500 にして原因を取り違えない）
		}
		return view(loan.getId(), today);
	}

	/**
	 * 返却（借りた本人か ADMIN）。返却済みかどうかは、条件付き UPDATE（returned_at IS NULL）の更新件数で判定する。
	 * 判定と書き込みが 1 文で終わるので、同じ貸出を同時に返却しても成功は 1 件だけ。
	 */
	@Transactional
	public LoanResponse returnLoan(CurrentUser user, long loanId) {
		Instant now = clock.instant();
		LoanView loan = loanMapper.findView(loanId).orElseThrow(() -> loanNotFound(loanId));
		requireBorrowerOrAdmin(user, loan, "返却できます");
		if (loanMapper.markReturned(loanId, lending.timeOf(now)) == 0) {
			throw new BusinessException(ErrorCode.LOAN_ALREADY_RETURNED, "貸出 id=" + loanId + " は返却済みです");
		}
		return view(loanId, lending.dateOf(now));
	}

	/** 詳細（借りた本人か ADMIN）。他人の貸出は 404 で隠さず 403 にする（§6）。 */
	@Transactional(readOnly = true)
	public LoanResponse get(CurrentUser user, long loanId) {
		LocalDate today = lending.dateOf(clock.instant());
		LoanView loan = loanMapper.findView(loanId).orElseThrow(() -> loanNotFound(loanId));
		requireBorrowerOrAdmin(user, loan, "見られます");
		return LoanResponse.from(loan, today, lending);
	}

	/**
	 * 一覧。MEMBER は自分の分だけ（borrowerId を省くと自分に固定、他人を指定すると 403）。ADMIN は全員分。
	 * 件数と中身の 2 文を REPEATABLE READ で同じスナップショットから読む（§9）。
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<LoanResponse> list(CurrentUser user, LoanSearchCondition condition, PageQuery page) {
		Long borrowerId = condition.borrowerId();
		if (!user.isAdmin()) {
			if (borrowerId != null && borrowerId != user.id()) {
				throw new BusinessException(ErrorCode.FORBIDDEN,
						"MEMBER は自分の貸出だけを一覧できます（borrowerId は省くか、自分の id にしてください）");
			}
			borrowerId = user.id();
		}
		LocalDate today = lending.dateOf(clock.instant());
		LoanSearchQuery query = LoanSearchQuery.of(condition, borrowerId, page);
		long total = loanMapper.count(query);
		return PageResult.of(loanMapper.search(query), view -> LoanResponse.from(view, today, lending), page, total);
	}

	/** 延滞一覧（ADMIN）。延滞日数の多い順。催促の連絡のため借り手のメールアドレスを含む。 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<OverdueLoanResponse> listOverdue(CurrentUser user, PageQuery page) {
		if (!user.isAdmin()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "延滞一覧は管理者（ADMIN）だけが見られます");
		}
		LocalDate today = lending.dateOf(clock.instant());
		long total = loanMapper.countOverdue(today);
		return PageResult.of(loanMapper.findOverdue(today, page.size(), page.offset()),
				view -> OverdueLoanResponse.from(view, lending), page, total);
	}

	private LoanResponse view(long loanId, LocalDate today) {
		return loanMapper.findView(loanId)
			.map(view -> LoanResponse.from(view, today, lending))
			.orElseThrow(() -> loanNotFound(loanId));
	}

	private static void requireBorrowerOrAdmin(CurrentUser user, LoanView loan, String action) {
		if (!user.isAdmin() && loan.borrowerId() != user.id()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "この貸出は、借りた本人と管理者（ADMIN）だけが" + action);
		}
	}

	private static BusinessException alreadyLent(EquipmentRow equipment) {
		return new BusinessException(ErrorCode.EQUIPMENT_ALREADY_LENT, "備品 " + equipment.assetCode() + " は貸出中です");
	}

	private static BusinessException loanNotFound(long loanId) {
		return new BusinessException(ErrorCode.LOAN_NOT_FOUND, "貸出 id=" + loanId + " はありません");
	}

}

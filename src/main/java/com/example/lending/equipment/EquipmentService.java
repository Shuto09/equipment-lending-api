package com.example.lending.equipment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import com.example.lending.shared.config.LendingProperties;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.user.CurrentUser;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 備品の業務ルール（docs/要件と設計.md §4）。{@code @Transactional} はこのクラスの公開メソッドだけに付ける。
 * 時刻は 1 操作で 1 回だけ Clock から読み、判定と保存の両方に同じ値を使う。
 */
@Service
public class EquipmentService {

	private final EquipmentMapper equipmentMapper;

	private final Clock clock;

	private final LendingProperties lending;

	public EquipmentService(EquipmentMapper equipmentMapper, Clock clock, LendingProperties lending) {
		this.equipmentMapper = equipmentMapper;
		this.clock = clock;
		this.lending = lending;
	}

	/** 登録（ADMIN）。管理番号の重複は一意制約違反を制約名で 409 にする。 */
	@Transactional
	public EquipmentResponse register(CurrentUser user, RegisterEquipmentRequest request) {
		requireAdmin(user);
		Instant now = clock.instant();
		NewEquipment equipment = new NewEquipment(request.assetCode(), request.name(), request.category(),
				emptyToNull(request.remarks()), lending.timeOf(now));
		try {
			equipmentMapper.insert(equipment);
		}
		catch (DuplicateKeyException ex) {
			if (DbConstraints.isViolationOf(ex, DbConstraints.UQ_EQUIPMENT_ASSET_CODE)) {
				throw new BusinessException(ErrorCode.ASSET_CODE_DUPLICATED,
						"管理番号 " + request.assetCode() + " は登録済みです");
			}
			throw ex; // 知らない制約は変換しない（500 にして原因を取り違えない）
		}
		return view(equipment.getId(), lending.dateOf(now));
	}

	/**
	 * 検索。件数と中身は別の 2 文なので、REPEATABLE READ で同じスナップショットを読み、
	 * 間に確定した貸出や廃棄で totalElements と items が食い違わないようにする（§9）。
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<EquipmentSummaryResponse> search(EquipmentSearchCondition condition, PageQuery page) {
		LocalDate today = lending.dateOf(clock.instant());
		EquipmentSearchQuery query = EquipmentSearchQuery.of(condition, page, today);
		long total = equipmentMapper.count(query);
		return PageResult.of(equipmentMapper.search(query), view -> EquipmentSummaryResponse.from(view, lending), page,
				total);
	}

	@Transactional(readOnly = true)
	public EquipmentResponse get(long equipmentId) {
		return view(equipmentId, lending.dateOf(clock.instant()));
	}

	/**
	 * 更新（ADMIN）。version を条件にした UPDATE（楽観ロック）が 0 件なら、読み直して
	 * 404・409 EQUIPMENT_DISPOSED・409 VERSION_CONFLICT を出し分ける。
	 */
	@Transactional
	public EquipmentResponse update(CurrentUser user, long equipmentId, UpdateEquipmentRequest request) {
		requireAdmin(user);
		LocalDate today = lending.dateOf(clock.instant());
		EquipmentUpdate update = new EquipmentUpdate(equipmentId, request.version(), request.name(),
				request.category(), request.remarks() != null, emptyToNull(request.remarks()));
		if (equipmentMapper.updateWithVersion(update) == 0) {
			EquipmentRow current = equipmentMapper.findById(equipmentId)
				.orElseThrow(() -> notFound(equipmentId));
			if (current.status() == EquipmentStatus.DISPOSED) {
				throw disposed(current);
			}
			throw new BusinessException(ErrorCode.VERSION_CONFLICT, "備品 " + current.assetCode()
					+ " はほかの更新で変わっています。読み直してから更新してください（今の version は " + current.version() + "）");
		}
		return view(equipmentId, today);
	}

	/**
	 * 廃棄（ADMIN）。備品行をロックしてから「廃棄済みか」「貸出中か」を判定する。
	 * 貸出も同じ行のロックを取るので、廃棄と貸出が同時でも「廃棄済みなのに未返却の貸出がある」状態は生まれない。
	 */
	@Transactional
	public EquipmentResponse dispose(CurrentUser user, long equipmentId) {
		requireAdmin(user);
		Instant now = clock.instant();
		EquipmentRow equipment = equipmentMapper.lockById(equipmentId).orElseThrow(() -> notFound(equipmentId));
		if (equipment.status() == EquipmentStatus.DISPOSED) {
			throw disposed(equipment);
		}
		if (equipmentMapper.hasOpenLoan(equipmentId)) {
			throw new BusinessException(ErrorCode.EQUIPMENT_IN_USE,
					"備品 " + equipment.assetCode() + " は貸出中なので廃棄できません。返却の後に廃棄してください");
		}
		equipmentMapper.markDisposed(equipmentId, lending.timeOf(now));
		return view(equipmentId, lending.dateOf(now));
	}

	private EquipmentResponse view(long equipmentId, LocalDate today) {
		return equipmentMapper.findView(equipmentId, today)
			.map(view -> EquipmentResponse.from(view, lending))
			.orElseThrow(() -> notFound(equipmentId));
	}

	private static void requireAdmin(CurrentUser user) {
		if (!user.isAdmin()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "この操作は管理者（ADMIN）だけが行えます");
		}
	}

	private static BusinessException notFound(long equipmentId) {
		return new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND, "備品 id=" + equipmentId + " はありません");
	}

	private static BusinessException disposed(EquipmentRow equipment) {
		return new BusinessException(ErrorCode.EQUIPMENT_DISPOSED, "備品 " + equipment.assetCode() + " は廃棄済みです");
	}

	/** 備考の「空」は NULL の 1 種類だけにする（DB の CHECK で空文字を禁止している）。 */
	private static String emptyToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}

}

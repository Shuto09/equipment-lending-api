package com.example.lending.equipment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.lending.shared.config.LendingProperties;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.user.CurrentUser;
import com.example.lending.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DuplicateKeyException;

import static com.example.lending.support.PostgresErrors.duplicateKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

	/** 2026-10-05 10:00 日本時間（UTC では 01:00）。 */
	private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");

	private static final OffsetDateTime NOW_TOKYO = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	private static final CurrentUser ADMIN = new CurrentUser(1, Role.ADMIN);

	private static final CurrentUser MEMBER = new CurrentUser(2, Role.MEMBER);

	private static final long ID = 10;

	@Mock
	private EquipmentMapper mapper;

	private EquipmentService service;

	@BeforeEach
	void setUp() {
		service = new EquipmentService(mapper, Clock.fixed(NOW, ZoneOffset.UTC), new LendingProperties(TOKYO, 14, 3));
	}

	@Test
	@DisplayName("AC-03 MEMBER は登録・更新・廃棄できない（403・マッパーを呼ばない）")
	void memberCannotRegisterUpdateOrDispose() {
		List<Executable> calls = List.of(
				() -> service.register(MEMBER, new RegisterEquipmentRequest("PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null)),
				() -> service.update(MEMBER, ID, new UpdateEquipmentRequest("新しい名前", null, null, 0)),
				() -> service.dispose(MEMBER, ID));
		for (Executable call : calls) {
			assertThat(catchThrowableOfType(BusinessException.class, call::execute).code()).isEqualTo(ErrorCode.FORBIDDEN);
		}
		verifyNoInteractions(mapper);
	}

	@Nested
	class Register {

		@Test
		@DisplayName("AC-01 登録日時は Clock の時刻。備考の空文字は NULL にそろえる")
		void register_usesClockAndNormalizesRemarks() {
			willAnswer(invocation -> {
				invocation.<NewEquipment>getArgument(0).setId(ID);
				return null;
			}).given(mapper).insert(any());
			given(mapper.findView(ID, TODAY)).willReturn(Optional.of(view(Availability.AVAILABLE)));

			EquipmentResponse response = service.register(ADMIN,
					new RegisterEquipmentRequest("PC-0001", "ノート PC", EquipmentCategory.LAPTOP, ""));

			ArgumentCaptor<NewEquipment> inserted = ArgumentCaptor.forClass(NewEquipment.class);
			verify(mapper).insert(inserted.capture());
			assertThat(inserted.getValue().getCreatedAt()).isEqualTo(NOW_TOKYO);
			assertThat(inserted.getValue().getRemarks()).isNull();
			assertThat(response.id()).isEqualTo(ID);
			assertThat(response.availability()).isEqualTo(Availability.AVAILABLE);
		}

		@Test
		@DisplayName("AC-02 制約名が uq_equipment_asset_code の一意制約違反は 409 ASSET_CODE_DUPLICATED")
		void register_duplicateAssetCode_conflict() {
			willThrow(duplicateKey(DbConstraints.UQ_EQUIPMENT_ASSET_CODE)).given(mapper).insert(any());

			assertThatThrownBy(() -> service.register(ADMIN,
					new RegisterEquipmentRequest("PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null)))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ASSET_CODE_DUPLICATED));
		}

		@Test
		@DisplayName("知らない制約の一意制約違反は変換しない（500 にして原因を取り違えない）")
		void register_otherConstraint_isNotConverted() {
			DuplicateKeyException other = duplicateKey("uq_something_else");
			willThrow(other).given(mapper).insert(any());

			assertThatThrownBy(() -> service.register(ADMIN,
					new RegisterEquipmentRequest("PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null)))
				.isSameAs(other);
		}

	}

	@Nested
	class Update {

		@Test
		@DisplayName("AC-23 送った項目だけを変える（null は変えない・備考の空文字は NULL にして書く）")
		void update_changesOnlySentFields() {
			given(mapper.updateWithVersion(any())).willReturn(1);
			given(mapper.findView(ID, TODAY)).willReturn(Optional.of(view(Availability.AVAILABLE)));

			service.update(ADMIN, ID, new UpdateEquipmentRequest("新しい名前", null, null, 3));
			service.update(ADMIN, ID, new UpdateEquipmentRequest(null, EquipmentCategory.OTHER, "", 4));

			ArgumentCaptor<EquipmentUpdate> updates = ArgumentCaptor.forClass(EquipmentUpdate.class);
			verify(mapper, times(2)).updateWithVersion(updates.capture());
			assertThat(updates.getAllValues()).containsExactly(
					new EquipmentUpdate(ID, 3, "新しい名前", null, false, null),
					new EquipmentUpdate(ID, 4, null, EquipmentCategory.OTHER, true, null));
		}

		@Test
		@DisplayName("AC-15 version が古いと 409 VERSION_CONFLICT（上書きしない）")
		void update_staleVersion_conflict() {
			given(mapper.updateWithVersion(any())).willReturn(0);
			given(mapper.findById(ID)).willReturn(Optional.of(row(EquipmentStatus.ACTIVE, 5)));

			assertCode(() -> service.update(ADMIN, ID, new UpdateEquipmentRequest("名前", null, null, 4)),
					ErrorCode.VERSION_CONFLICT);
		}

		@Test
		@DisplayName("更新が 0 件で、読み直すと廃棄済みなら 409 EQUIPMENT_DISPOSED")
		void update_disposed_conflict() {
			given(mapper.updateWithVersion(any())).willReturn(0);
			given(mapper.findById(ID)).willReturn(Optional.of(row(EquipmentStatus.DISPOSED, 5)));

			assertCode(() -> service.update(ADMIN, ID, new UpdateEquipmentRequest("名前", null, null, 5)),
					ErrorCode.EQUIPMENT_DISPOSED);
		}

		@Test
		@DisplayName("更新が 0 件で、読み直しても無ければ 404 EQUIPMENT_NOT_FOUND")
		void update_notFound() {
			given(mapper.updateWithVersion(any())).willReturn(0);
			given(mapper.findById(ID)).willReturn(Optional.empty());

			assertCode(() -> service.update(ADMIN, ID, new UpdateEquipmentRequest("名前", null, null, 0)),
					ErrorCode.EQUIPMENT_NOT_FOUND);
		}

	}

	@Nested
	class Dispose {

		@Test
		@DisplayName("AC-14 空いていれば廃棄できる。行ロックの後に判定し、廃棄日時は Clock の時刻")
		void dispose_available_succeeds() {
			given(mapper.lockById(ID)).willReturn(Optional.of(row(EquipmentStatus.ACTIVE, 0)));
			given(mapper.hasOpenLoan(ID)).willReturn(false);
			given(mapper.findView(ID, TODAY)).willReturn(Optional.of(view(Availability.DISPOSED)));

			EquipmentResponse response = service.dispose(ADMIN, ID);

			InOrder order = inOrder(mapper);
			order.verify(mapper).lockById(ID);
			order.verify(mapper).hasOpenLoan(ID);
			order.verify(mapper).markDisposed(ID, NOW_TOKYO);
			assertThat(response.availability()).isEqualTo(Availability.DISPOSED);
		}

		@Test
		@DisplayName("AC-14 貸出中は廃棄できない（409 EQUIPMENT_IN_USE）")
		void dispose_inUse_conflict() {
			given(mapper.lockById(ID)).willReturn(Optional.of(row(EquipmentStatus.ACTIVE, 0)));
			given(mapper.hasOpenLoan(ID)).willReturn(true);

			assertCode(() -> service.dispose(ADMIN, ID), ErrorCode.EQUIPMENT_IN_USE);
			verify(mapper, never()).markDisposed(anyLong(), any());
		}

		@Test
		@DisplayName("廃棄済みをもう一度廃棄すると 409 EQUIPMENT_DISPOSED")
		void dispose_alreadyDisposed_conflict() {
			given(mapper.lockById(ID)).willReturn(Optional.of(row(EquipmentStatus.DISPOSED, 1)));

			assertCode(() -> service.dispose(ADMIN, ID), ErrorCode.EQUIPMENT_DISPOSED);
			verify(mapper, never()).markDisposed(anyLong(), any());
		}

		@Test
		@DisplayName("無い備品は 404")
		void dispose_notFound() {
			given(mapper.lockById(ID)).willReturn(Optional.empty());

			assertCode(() -> service.dispose(ADMIN, ID), ErrorCode.EQUIPMENT_NOT_FOUND);
		}

	}

	@Nested
	class Read {

		@Test
		@DisplayName("詳細の日時は日本時間のオフセットで返す（DB からは UTC で読めても）")
		void get_convertsTimesToBusinessZone() {
			EquipmentView utc = new EquipmentView(ID, "PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null,
					Availability.LENT, 0, OffsetDateTime.parse("2026-10-01T00:00:00Z"), null, 3L, "山田 太郎", TODAY);
			given(mapper.findView(ID, TODAY)).willReturn(Optional.of(utc));

			EquipmentResponse response = service.get(ID);

			assertThat(response.createdAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T09:00:00+09:00"));
			assertThat(response.currentLoan().borrower().displayName()).isEqualTo("山田 太郎");
			assertThat(response.currentLoan().dueDate()).isEqualTo(TODAY);
		}

		@Test
		@DisplayName("無い備品の詳細は 404")
		void get_notFound() {
			given(mapper.findView(ID, TODAY)).willReturn(Optional.empty());

			assertCode(() -> service.get(ID), ErrorCode.EQUIPMENT_NOT_FOUND);
		}

		@Test
		@DisplayName("検索: キーワードはエスケープしてから渡し、今日の日付とページの位置も渡す")
		void search_buildsQuery() {
			given(mapper.count(any())).willReturn(41L);
			given(mapper.search(any())).willReturn(List.of(view(Availability.AVAILABLE)));
			EquipmentSearchCondition condition = new EquipmentSearchCondition("100%", List.of(EquipmentCategory.LAPTOP),
					null, SortOrder.desc(EquipmentSortKey.NAME));

			PageResult<EquipmentSummaryResponse> result = service.search(condition, new PageQuery(2, 20));

			ArgumentCaptor<EquipmentSearchQuery> query = ArgumentCaptor.forClass(EquipmentSearchQuery.class);
			verify(mapper).search(query.capture());
			assertThat(query.getValue()).isEqualTo(new EquipmentSearchQuery("%100\\%%",
					List.of(EquipmentCategory.LAPTOP), null, SortOrder.desc(EquipmentSortKey.NAME), 20, 40, TODAY));
			verify(mapper).count(query.getValue());
			assertThat(result.totalElements()).isEqualTo(41);
			assertThat(result.totalPages()).isEqualTo(3);
			assertThat(result.items()).hasSize(1);
		}

	}

	private static EquipmentView view(Availability availability) {
		return new EquipmentView(ID, "PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null, availability, 0,
				NOW_TOKYO, null, null, null, null);
	}

	private static EquipmentRow row(EquipmentStatus status, int version) {
		return new EquipmentRow(ID, "PC-0001", "ノート PC", EquipmentCategory.LAPTOP, null, status,
				status == EquipmentStatus.DISPOSED ? NOW_TOKYO : null, version, NOW_TOKYO);
	}

	private static void assertCode(Executable call, ErrorCode expected) {
		BusinessException ex = catchThrowableOfType(BusinessException.class, call::execute);
		assertThat(ex).as("BusinessException が投げられること").isNotNull();
		assertThat(ex.code()).isEqualTo(expected);
	}

}

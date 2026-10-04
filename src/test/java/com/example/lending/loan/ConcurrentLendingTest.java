package com.example.lending.loan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.equipment.EquipmentService;
import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.sql.DbConstraints;
import com.example.lending.support.AppDbTest;
import com.example.lending.support.TestData;
import com.example.lending.user.CurrentUser;
import com.example.lending.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.example.lending.support.TestData.BASE;
import static com.example.lending.support.TestData.TODAY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同時実行のテスト（docs/テスト計画.md §5）。本物の PostgreSQL に、実際に複数のスレッドから同時に書き込む。
 * <ul>
 * <li>各スレッドが別々のトランザクションで COMMIT しないと競合が起きないので、このクラスには {@code @Transactional} を付けない。</li>
 * <li>呼び出しはサービスの公開メソッド（HTTP を挟まず、競合の窓を広げる）。</li>
 * <li>競合は毎回同じ順で起きるとは限らないので、CT-1・CT-3・CT-4 は繰り返す。</li>
 * </ul>
 */
@AppDbTest
class ConcurrentLendingTest {

	/** 同時に走らせるスレッドの数。接続プールの上限（10）より小さくし、接続待ちで競合が薄まらないようにする。 */
	private static final int THREADS = 8;

	/** CT-1・CT-3・CT-4 の繰り返しの回数（docs/テスト計画.md §5 に所要時間の実測を書く）。 */
	private static final int REPETITIONS = 20;

	private static final String SUCCESS = "SUCCESS";

	@Autowired
	private LoanService loanService;

	@Autowired
	private EquipmentService equipmentService;

	@Autowired
	private LoanMapper loanMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TestData data;

	@BeforeEach
	void reset() {
		data.reset();
	}

	@RepeatedTest(REPETITIONS)
	@DisplayName("CT-1 AC-16 空いている備品 1 台を、別々の利用者 8 人が同時に借りる → 成功 1・EQUIPMENT_ALREADY_LENT 7・未返却 1 件")
	void ct1_sameEquipment_onlyOneLoanSucceeds() throws Exception {
		long equipment = data.equipment("PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP);
		List<Task> tasks = IntStream.rangeClosed(1, THREADS).mapToObj(i -> {
			CurrentUser member = new CurrentUser(data.member(i), Role.MEMBER);
			return (Task) () -> loanService.lend(member, lendRequest(equipment));
		}).toList();

		assertThat(countByOutcome(race(tasks))).isEqualTo(Map.of(SUCCESS, 1L, "EQUIPMENT_ALREADY_LENT", 7L));
		assertThat(count("SELECT count(*) FROM loans WHERE equipment_id = ? AND returned_at IS NULL", equipment))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("CT-2 R1 アプリの判定を通らずに、2 本のトランザクションで同じ備品の未返却を同時に INSERT → 1 本は制約（uq_loans_open_equipment）で止まる")
	void ct2_uniqueIndexAlone_stopsSecondOpenLoan() throws Exception {
		long equipment = data.equipment("PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		List<Task> tasks = Stream.of(data.yamada(), data.sato())
			.map(borrower -> (Task) () -> transaction.execute(status -> {
				loanMapper.insert(new NewLoan(equipment, borrower, BASE, TODAY.plusDays(7)));
				return null;
			}))
			.toList();

		assertThat(countByOutcome(race(tasks)))
			.isEqualTo(Map.of(SUCCESS, 1L, "DuplicateKeyException(" + DbConstraints.UQ_LOANS_OPEN_EQUIPMENT + ")", 1L));
		assertThat(count("SELECT count(*) FROM loans WHERE equipment_id = ? AND returned_at IS NULL", equipment))
			.isEqualTo(1);
	}

	@RepeatedTest(REPETITIONS)
	@DisplayName("CT-3 AC-17 未返却 0 件の利用者 1 人が、別々の備品 6 台を同時に借りる → 成功ちょうど 3・LOAN_LIMIT_EXCEEDED 3・未返却 3 件")
	void ct3_sameBorrower_loanLimitHolds() throws Exception {
		CurrentUser yamada = new CurrentUser(data.yamada(), Role.MEMBER);
		List<Task> tasks = IntStream.rangeClosed(1, 6).mapToObj(i -> {
			long equipment = data.equipment("PC-000" + i, "ノート PC " + i, EquipmentCategory.LAPTOP);
			return (Task) () -> loanService.lend(yamada, lendRequest(equipment));
		}).toList();

		assertThat(countByOutcome(race(tasks))).isEqualTo(Map.of(SUCCESS, 3L, "LOAN_LIMIT_EXCEEDED", 3L));
		assertThat(count("SELECT count(*) FROM loans WHERE borrower_id = ? AND returned_at IS NULL", yamada.id()))
			.isEqualTo(3);
	}

	@RepeatedTest(REPETITIONS)
	@DisplayName("CT-4 AC-18 空いている備品 1 台に、廃棄と貸出が同時 → どちらか一方だけが成功し、「廃棄済みなのに未返却の貸出がある」が生まれない")
	void ct4_disposeAndLend_neverBothSucceed() throws Exception {
		long equipment = data.equipment("PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP);
		CurrentUser yamada = new CurrentUser(data.yamada(), Role.MEMBER);
		CurrentUser admin = new CurrentUser(data.admin(), Role.ADMIN);

		List<String> outcomes = race(List.of(() -> loanService.lend(yamada, lendRequest(equipment)),
				() -> equipmentService.dispose(admin, equipment)));

		// [貸出の結果, 廃棄の結果]
		assertThat(outcomes).isIn(List.of(SUCCESS, "EQUIPMENT_IN_USE"), List.of("EQUIPMENT_DISPOSED", SUCCESS));
		assertThat(count("""
				SELECT count(*) FROM equipment e
				  JOIN loans l ON l.equipment_id = e.id AND l.returned_at IS NULL
				 WHERE e.status = 'DISPOSED' AND e.id = ?""", equipment)).isZero();
	}

	@Test
	@DisplayName("CT-5 R7 同じ貸出を 2 本同時に返却 → 成功 1・LOAN_ALREADY_RETURNED 1")
	void ct5_returnTwice_onlyOneSucceeds() throws Exception {
		long yamadaId = data.yamada();
		long loan = data.openLoan(data.equipment("PC-0001", "ノート PC 14 インチ", EquipmentCategory.LAPTOP), yamadaId,
				TODAY.plusDays(7));
		CurrentUser yamada = new CurrentUser(yamadaId, Role.MEMBER);

		List<String> outcomes = race(
				List.of(() -> loanService.returnLoan(yamada, loan), () -> loanService.returnLoan(yamada, loan)));

		assertThat(countByOutcome(outcomes)).isEqualTo(Map.of(SUCCESS, 1L, "LOAN_ALREADY_RETURNED", 1L));
	}

	private static LendRequest lendRequest(long equipmentId) {
		return new LendRequest(equipmentId, TODAY.plusDays(7));
	}

	/**
	 * タスクを別々のスレッドで一斉に始め、結果をタスクの順で返す。結果は SUCCESS、業務エラーの code、
	 * それ以外の例外の型（一意制約違反なら制約名つき）のどれか。
	 */
	private static List<String> race(List<Task> tasks) throws Exception {
		assertThat(tasks).hasSizeLessThanOrEqualTo(THREADS);
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> futures = tasks.stream().map(task -> pool.submit(() -> {
				ready.countDown();
				start.await(); // 全員がそろってから一斉に呼ぶ
				try {
					task.run();
					return SUCCESS;
				}
				catch (Exception ex) {
					return outcome(ex);
				}
			})).toList();
			assertThat(ready.await(30, SECONDS)).as("全スレッドがそろう").isTrue();
			start.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> future : futures) {
				outcomes.add(future.get(30, SECONDS)); // 固まった時にテストが終わらないのを防ぐ
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static String outcome(Exception ex) {
		if (ex instanceof BusinessException business) {
			return business.code().name();
		}
		return ex.getClass().getSimpleName()
				+ DbConstraints.violatedConstraint(ex).map(constraint -> "(" + constraint + ")").orElse("");
	}

	private static Map<String, Long> countByOutcome(List<String> outcomes) {
		return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	private long count(String sql, long id) {
		return data.jdbc().sql(sql).param(id).query(Long.class).single();
	}

	/** 競合させる 1 本の操作（戻り値は見ない。成功か、どの例外で失敗したかだけを見る）。 */
	@FunctionalInterface
	private interface Task {

		void run() throws Exception;

	}

}

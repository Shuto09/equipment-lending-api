package com.example.lending.loan;

import java.net.URI;

import com.example.lending.shared.paging.PageQuery;
import com.example.lending.shared.paging.PageResult;
import com.example.lending.shared.paging.SortOrder;
import com.example.lending.shared.paging.SortParser;
import com.example.lending.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 貸出の API。HTTP の入出力と入力検証だけを持ち、業務判定・権限・トランザクションはサービスに任せる。 */
@RestController
@RequestMapping("/api/loans")
@Tag(name = "貸出")
public class LoanController {

	private static final SortOrder<LoanSortKey> DEFAULT_SORT = SortOrder.desc(LoanSortKey.LENT_AT);

	private final LoanService loanService;

	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // OpenAPI に 201 と載せるため（実際の状態は ResponseEntity.created）
	@Operation(summary = "備品を借りる", description = "借り手は X-User-Id の本人。返却期限は今日から 14 日後まで（両端を含む・日本時間の日付）")
	public ResponseEntity<LoanResponse> lend(@Parameter(hidden = true) CurrentUser user,
			@Valid @RequestBody LendRequest request) {
		LoanResponse created = loanService.lend(user, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{id}")
			.buildAndExpand(created.id())
			.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	@Operation(summary = "貸出の一覧",
			description = "MEMBER は自分の分だけ（borrowerId を省くと自分、他人を指定すると 403）。ADMIN は全員分。並びが同じ行は id 順")
	public PageResult<LoanResponse> list(@Parameter(hidden = true) CurrentUser user,
			@Parameter(description = "OPEN（未返却・既定）・RETURNED（返却済み）・ALL") @RequestParam(
					defaultValue = "OPEN") LoanStateFilter state,
			@Parameter(description = "借り手の利用者 id") @RequestParam(required = false) Long borrowerId,
			@Parameter(description = "備品の id") @RequestParam(required = false) Long equipmentId,
			@Parameter(description = "並び替え。lentAt（既定は lentAt,desc）・dueDate。向きは ,asc か ,desc",
					example = "dueDate,asc") @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		LoanSearchCondition condition = new LoanSearchCondition(state, borrowerId, equipmentId,
				SortParser.parse(sort, LoanSortKey.class, DEFAULT_SORT));
		return loanService.list(user, condition, new PageQuery(page, size));
	}

	@GetMapping("/overdue")
	@Operation(summary = "延滞の一覧（ADMIN）", description = "未返却で返却期限を過ぎた貸出を、延滞日数の多い順に。借り手のメールアドレスを含む")
	public PageResult<OverdueLoanResponse> listOverdue(@Parameter(hidden = true) CurrentUser user,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return loanService.listOverdue(user, new PageQuery(page, size));
	}

	@GetMapping("/{id}")
	@Operation(summary = "貸出の詳細（借りた本人か ADMIN）")
	public LoanResponse get(@Parameter(hidden = true) CurrentUser user, @PathVariable long id) {
		return loanService.get(user, id);
	}

	@PostMapping("/{id}/return")
	@Operation(summary = "返却する（借りた本人か ADMIN）", description = "返却は 1 回だけ。返却日（日本時間）が期限より後なら returnedLate=true")
	public LoanResponse returnLoan(@Parameter(hidden = true) CurrentUser user, @PathVariable long id) {
		return loanService.returnLoan(user, id);
	}

}

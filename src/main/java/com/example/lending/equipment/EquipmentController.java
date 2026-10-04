package com.example.lending.equipment;

import java.net.URI;
import java.util.List;

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
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 備品の API。HTTP の入出力と入力検証だけを持ち、業務判定とトランザクションはサービスに任せる。
 * 認証は {@code AuthenticationInterceptor} で済んでいる（CurrentUser を引数に取らない検索・詳細も同じ）。
 */
@RestController
@RequestMapping("/api/equipment")
@Tag(name = "備品")
public class EquipmentController {

	private static final SortOrder<EquipmentSortKey> DEFAULT_SORT = SortOrder.asc(EquipmentSortKey.ASSET_CODE);

	private final EquipmentService equipmentService;

	public EquipmentController(EquipmentService equipmentService) {
		this.equipmentService = equipmentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // OpenAPI に 201 と載せるため（実際の状態は ResponseEntity.created）
	@Operation(summary = "備品を登録する（ADMIN）")
	public ResponseEntity<EquipmentResponse> register(@Parameter(hidden = true) CurrentUser user,
			@Valid @RequestBody RegisterEquipmentRequest request) {
		EquipmentResponse created = equipmentService.register(user, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
			.path("/{id}")
			.buildAndExpand(created.id())
			.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	@Operation(summary = "備品を探す", description = "既定では廃棄済みを除く。並びが同じ行は id 順")
	public PageResult<EquipmentSummaryResponse> search(
			@Parameter(description = "名前・管理番号の部分一致（大文字小文字を区別しない）") @RequestParam(
					required = false) @Size(max = 50) String keyword,
			@Parameter(description = "区分（複数可）") @RequestParam(required = false) List<EquipmentCategory> category,
			@Parameter(description = "貸出状態") @RequestParam(required = false) Availability availability,
			@Parameter(description = "並び替え。assetCode（既定）・name・category・createdAt・dueDate。向きは ,asc（既定）か ,desc",
					example = "name,desc") @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		EquipmentSearchCondition condition = new EquipmentSearchCondition(keyword, category, availability,
				SortParser.parse(sort, EquipmentSortKey.class, DEFAULT_SORT));
		return equipmentService.search(condition, new PageQuery(page, size));
	}

	@GetMapping("/{id}")
	@Operation(summary = "備品の詳細")
	public EquipmentResponse get(@PathVariable long id) {
		return equipmentService.get(id);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "備品の名称・区分・備考を直す（ADMIN）", description = "送った項目だけを変える。version は読んだときの値")
	public EquipmentResponse update(@Parameter(hidden = true) CurrentUser user, @PathVariable long id,
			@Valid @RequestBody UpdateEquipmentRequest request) {
		return equipmentService.update(user, id, request);
	}

	@PostMapping("/{id}/dispose")
	@Operation(summary = "備品を廃棄する（ADMIN）", description = "貸出中は廃棄できない。取り消しはできない")
	public EquipmentResponse dispose(@Parameter(hidden = true) CurrentUser user, @PathVariable long id) {
		return equipmentService.dispose(user, id);
	}

}

package com.example.lending.loan;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * 貸出の申請。借り手は要求を出した本人（X-User-Id）に固定する。
 * 返却期限の範囲（今日〜14 日後）は「今日」に依存するので、入力検証ではなくサービスで判定する（422）。
 */
public record LendRequest(@NotNull Long equipmentId, @NotNull LocalDate dueDate) {

}

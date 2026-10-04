package com.example.lending.loan;

/** 1 人の利用者の未返却の件数と、そのうち延滞している件数（貸出の判定 R5・R6 に使う）。 */
public record BorrowerLoanStats(long openCount, long overdueCount) {

}

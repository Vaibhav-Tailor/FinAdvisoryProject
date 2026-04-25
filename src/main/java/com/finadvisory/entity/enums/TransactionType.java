package com.finadvisory.entity.enums;

public enum TransactionType {
    PURCHASE,           // One-time lumpsum buy
    SIP,                // Systematic Investment Plan instalment
    REDEMPTION,         // Partial or full withdrawal
    SWITCH_IN,          // Switched into this scheme
    SWITCH_OUT,         // Switched out from this scheme
    DIVIDEND_PAYOUT,    // Dividend paid out to customer
    DIVIDEND_REINVEST,  // Dividend reinvested as units
    BONUS,              // Bonus units allotted
    OPENING_BALANCE     // CAS opening balance (synthetic/estimated entry)
}


// ─────────────────────────────────────────────────────────────────────────────
// FILE: enums/TransactionSource.java
// ─────────────────────────────────────────────────────────────────────────────

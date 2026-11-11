package com.expense.calculator.domain;

/** Which text a rule is matched against. */
public enum RuleScope {
    /** The whole bank narration (merchant, VPA, note ...). */
    NARRATION,
    /**
     * Only the note you typed in your UPI app ("taxi", "meds"), as extracted into
     * {@link EpnxTransaction#getRemark()}. Remark rules are checked before narration rules because a
     * note you wrote yourself is the strongest signal.
     */
    REMARK
}

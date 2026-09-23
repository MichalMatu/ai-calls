package pl.michalmatu.aicallbridge.localcall;

/**
 * Controls only where a deterministic CallPlan COMPLETE decision is committed.
 *
 * <p>The default keeps the historic workflow-owned completion behavior. Reviewed product
 * composition may defer the mutation so a separate application-owned boundary can require
 * stronger success evidence before calling the same workflow owner.</p>
 */
enum CallPlanCompletionMode {
    APPLY_TO_WORKFLOW,
    DEFER_TO_PRODUCT_OWNER
}

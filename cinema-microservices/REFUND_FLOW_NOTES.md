# Refund Flow Notes

## Current State
- `refund` exists as an internal payment-service flow, but it is not a finished core business flow.
- Current booking/payment behavior does not define a single authoritative refund trigger.

## Current Conflicts
- Customer booking cancel is blocked for `CONFIRMED + PAID` bookings.
- Operator/admin booking status updates do not define a refund completion path.
- Payment service exposes a refund endpoint, but the booking lifecycle does not clearly feed it.

## Unclear Refund Triggers
- It is unclear when a refund should start for a paid booking.
- It is unclear whether refund should be triggered by booking cancel, admin action, system action, or finance action.
- It is unclear whether a refunded booking must stay canceled, expired, or move to another terminal state.

## Data States Involved
- Booking states involved: `PENDING`, `RESERVED`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.
- Payment states involved: `UNPAID`, `PAID`, `FAILED`, `REFUND_PENDING`, `REFUNDED`.
- The transition path into `REFUND_PENDING` and then `REFUNDED` is not fully defined end to end.

## Integration Gaps
- The refund flow is not connected to a real payment-provider refund callback.
- Webhook handling currently focuses on paid/void events, not refund finalization.
- Reconciliation includes refund counters, but the external source of truth for refund completion is not defined.

## Authorization Questions
- It is unclear which actor owns refund initiation in the final business rule.
- It is unclear whether refund approval belongs to customer, admin, operator, or finance.

## Note
- This file documents issues only.
- No implementation decision is made here.

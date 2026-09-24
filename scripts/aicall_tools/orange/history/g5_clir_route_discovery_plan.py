#!/usr/bin/env python3
"""Pure G5 CLIR route-discovery contract; contains no dialing or device execution."""

from __future__ import annotations

from dataclasses import dataclass

from aicall_tools.calls.local_phone_llm_live_call import (
    ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
    ORANGE_REVIEWED_RESPONSES,
    ORANGE_SUPPORT_NUMBER,
    normalize_allowlisted_target,
)


@dataclass(frozen=True)
class G5ClirRouteDiscoveryPlan:
    target: str
    primary_action: str
    observe_next: bool
    live_call_readiness_required: bool
    external_effect_execution: bool
    commitment_permit_use: bool
    fresh_live_call_authorization_required: bool


def build_g5_clir_route_discovery_plan(
    target: str = ORANGE_SUPPORT_NUMBER,
) -> G5ClirRouteDiscoveryPlan:
    return G5ClirRouteDiscoveryPlan(
        target=normalize_allowlisted_target(target),
        primary_action=ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO,
        observe_next=True,
        live_call_readiness_required=True,
        external_effect_execution=False,
        commitment_permit_use=False,
        fresh_live_call_authorization_required=True,
    )


def validate_g5_clir_route_discovery_report(report: dict[str, str]) -> str:
    if report.get("orange_live_action") != ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO:
        raise ValueError("G5 route discovery action mismatch")
    expected_text = ORANGE_REVIEWED_RESPONSES[ORANGE_ACTION_CALLER_ID_RESTRICTION_INFO]
    if report.get("approved_text") != expected_text:
        raise ValueError("G5 route discovery did not use the reviewed read-only utterance")
    observation = report.get("orange_observation_text", "").strip()
    if not observation:
        raise ValueError("G5 route discovery observation is blank")
    return observation

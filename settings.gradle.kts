rootProject.name = "MineDrop-Network"

// =============================================================================
// MineDrop Network — Monorepo
// =============================================================================
// Phase 1 (Fully Implemented):
//   - mdn-api       : Shared library (packets, database, security, events)
//   - mdn-bridge    : Security foundation (plugin validation, handshake)
//   - mdn-core      : Network heartbeat (sessions, routing, sync, cache)
//
// Phase 2–4 (Skeletons for new developers to fill in):
//   - mdn-auth, mdn-security, mdn-economy, mdn-social,
//     mdn-communication, mdn-maintenance, mdn-moderation, mdn-sam
// =============================================================================

include(
    // Phase 1 — Fully implemented
    "mdn-api",
    "mdn-bridge",
    "mdn-core",

    // Phase 2 — Global Services (skeletons)
    "mdn-auth",
    "mdn-security",
    "mdn-economy",
    "mdn-social",
    "mdn-communication",
    "mdn-maintenance",

    // Phase 3 — Staff Systems (skeleton)
    "mdn-moderation",

    // Phase 4 — Gameplay (skeleton)
    "mdn-sam",
)

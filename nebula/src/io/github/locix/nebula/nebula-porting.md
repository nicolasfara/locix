The original Nebula is stronger as a real framework: deployment, sockets, reconnection, controller integration, dynamic roles, async services. The LociX port is stronger as a protocol specification: fewer moving parts, clearer causality, and much less incidental state.

**Mapping**

- `Engine` in [engine.py](/home/nicolas/Documents/repos/nebula/nebula/core/engine.py) maps mostly to [nebulaApp](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L214), [trainingProtocol](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L132), and the per-peer [NebulaState](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L61).
- `CommunicationsManager` in [communications.py](/home/nicolas/Documents/repos/nebula/nebula/core/network/communications.py) maps to LociX primitives:
  - choreography for round control and update collection in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L136)
  - multitier for dashboard reads in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L184)
  - collective for topology/eligibility in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L80)
- `RoleBehavior` in [noderole.py](/home/nicolas/Documents/repos/nebula/nebula/core/noderole.py) maps to static peer roles plus collective eligibility:
  - `Coordinator`, `Participant`, `Dashboard` at [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L40)
  - dynamic “can participate now” becomes `TopologyStatus.eligible` in [NebulaDomain.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/NebulaDomain.scala#L10)

**What Is Improved**

- Engine-wise, the round lifecycle is explicit in program order instead of being split across callbacks, locks, and helper objects. That makes [trainingProtocol](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L132) easier to reason about than the original `Engine` loop.
- Communication-wise, the protocol is typed and declarative. `broadcast`, `gather`, `asLocal`, and `asLocalAll` say what is happening directly, instead of manually managing message kinds and handlers.
- Role-wise, the port removes a lot of runtime indirection. In the original code, role logic is distributed across subclasses; here, topology-driven participation is a plain value computed by the collective layer.
- The collective part is arguably better aligned with the literature now: local sensing, gradient-to-seed, smoothing with `rep`, and membership gating are closer to aggregate-computing FL examples than Nebula’s policy-heavy infrastructure.

**What Is Not Improved**

- There is no real replacement for connection management, retries, forwarding, blacklisting, discovery, external services, or shutdown orchestration.
- Dynamic leadership transfer, malicious roles, proxy/server roles, and plugin-style extensibility are not preserved.
- The original `EventManager` is mostly collapsed into direct flow plus dashboard output; that is simpler, but much less general.

**Bottom Line**

- `Engine`: improved as a specification, weaker as an operational runtime.
- `Communications`: improved as a typed protocol layer, weaker as a network subsystem.
- `noderole`: improved as a simple semantic model, weaker as a flexible runtime role system.

| Original Nebula component | Original responsibility | LociX port mapping | Main change |
|---|---|---|---|
| [Engine](/home/nicolas/Documents/repos/nebula/nebula/core/engine.py) | Orchestrates startup, rounds, aggregation, lifecycle, shutdown | [nebulaApp](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L214), [trainingProtocol](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L132), [NebulaState](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L61) | From callback/lock-driven runtime engine to direct protocol specification |
| [CommunicationsManager](/home/nicolas/Documents/repos/nebula/nebula/core/network/communications.py) | Message dispatch, connection handling, forwarding, federation networking | `broadcast`/`gather` in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L137), `asLocal`/`asLocalAll` in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L184), `Collective` in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L80) | From explicit network subsystem to typed communication primitives |
| [RoleBehavior](/home/nicolas/Documents/repos/nebula/nebula/core/noderole.py) | Encodes trainer/aggregator/server/proxy/idle behavior and transitions | Static peers `Coordinator` / `Participant` / `Dashboard` in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L40), plus `eligible` in [NebulaDomain.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/NebulaDomain.scala#L10) | From OO runtime roles to simpler protocol roles plus collective participation state |
| [FedAvg aggregator](/home/nicolas/Documents/repos/nebula/nebula/core/aggregation/fedavg.py) | Weighted aggregation of real model tensors | [fedAvg](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/NebulaDomain.scala#L49) | Same idea, but mocked over numeric vectors |
| [Node startup](/home/nicolas/Documents/repos/nebula/nebula/core/node.py) | Config-driven model/dataset/trainer selection and deployment | Fixed datasets/models in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L53) and [NebulaDomain.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/NebulaDomain.scala#L35) | From configurable framework bootstrap to deterministic example setup |
| [Distance neighbor policy](/home/nicolas/Documents/repos/nebula/nebula/core/situationalawareness/awareness/sanetwork/neighborpolicies/distanceneighborpolicy.py) | Uses physical distance to drive neighbor decisions | [topologyAwareness](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L80) | Recast as aggregate-computing logic: bounded gradient to seed, smoothing, eligibility |
| [EventManager](/home/nicolas/Documents/repos/nebula/nebula/core/eventmanager.py) + [nebulaevents.py](/home/nicolas/Documents/repos/nebula/nebula/core/nebulaevents.py) | Internal event bus for rounds, updates, propagation, monitoring | Direct control flow plus final dashboard in [Nebula.scala](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L184) | Less extensible, but much easier to follow |
| Frontend/controller/reporting | Experiment setup, observability, control-plane integration | [dashboardReport](/home/nicolas/Documents/repos/locix/nebula/src/io/github/locix/nebula/Nebula.scala#L184) | Reduced to one in-program summary view |
| Security/reputation/attacks/add-ons | Runtime defenses and adversarial extensions | Not ported | Intentionally out of scope for a compact LociX example |

Short takeaway: the port improves semantic clarity and makes the distributed protocol easier to verify mentally, but it does so by dropping most of the original runtime infrastructure and configurability.

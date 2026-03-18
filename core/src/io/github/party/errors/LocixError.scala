package io.github.party.errors

import io.github.party.network.Identifier

enum LocixError:
  case ExpectLocalValue(key: Identifier, peerType: String)

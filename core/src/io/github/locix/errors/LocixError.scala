package io.github.locix.errors

import io.github.locix.network.Identifier

enum LocixError:
  case ExpectLocalValue(key: Identifier, peerType: String)

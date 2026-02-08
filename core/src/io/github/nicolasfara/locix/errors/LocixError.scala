package io.github.nicolasfara.locix.errors

import io.github.nicolasfara.locix.network.Identifier

enum LocixError:
  case ExpectLocalValue(key: Identifier, peerType: String)

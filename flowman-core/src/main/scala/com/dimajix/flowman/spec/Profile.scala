/*
 * Copyright 2018 Kaya Kupferschmidt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimajix.flowman.spec

import com.fasterxml.jackson.annotation.JsonProperty

import com.dimajix.flowman.util.splitSettings


class Profile {
    @JsonProperty(value="enabled") private var _enabled : Boolean = true
    @JsonProperty(value="environment") private var _environment: Seq[String] = Seq()
    @JsonProperty(value="config") private var _config: Seq[String] = Seq()
    @JsonProperty(value="connections") private var _databases: Map[String,Connection] = Map()

    /**
      * Returns true if the profile is enabled
      *
      * @return
      */
    def enabled : Boolean = _enabled

    /**
      * Returns a map of all configured databases
      *
      * @return
      */
    def connections : Map[String,Connection] = _databases

    /**
      * Returns all configuration variables as a key-value sequence
      *
      * @return
      */
    def config : Seq[(String,String)] = splitSettings(_config)

    /**
      * Returns the environment as a key-value-sequence
      *
      * @return
      */
    def environment : Seq[(String,String)] = splitSettings(_environment)
}

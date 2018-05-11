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

package com.dimajix.flowman.spec.schema

import scala.collection.mutable

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

import com.dimajix.flowman.execution.Context


object Schema {
    private val _schemas : mutable.Map[String,Class[_ <: Schema]] = mutable.Map()

    def register(name:String, clazz:Class[_ <: Schema]) : Unit = {
        _schemas.update(name, clazz)
    }

    def subtypes : Seq[(String,Class[_ <: Schema])] = _schemas.toSeq
}

/**
  * Interface class for declaring relations (for sources and sinks) as part of a model
  */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", defaultImpl = classOf[EmbeddedSchema])
@JsonSubTypes(value = Array(
    new JsonSubTypes.Type(name = "inline", value = classOf[EmbeddedSchema]),
    new JsonSubTypes.Type(name = "embedded", value = classOf[EmbeddedSchema]),
    new JsonSubTypes.Type(name = "avro", value = classOf[AvroSchema])
))
abstract class Schema {
    def description(implicit context: Context) : String
    def fields(implicit context: Context) : Seq[Field]
}

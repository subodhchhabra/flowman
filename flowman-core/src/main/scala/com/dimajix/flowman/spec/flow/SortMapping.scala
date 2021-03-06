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

package com.dimajix.flowman.spec.flow

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.col
import org.slf4j.LoggerFactory

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.execution.Executor
import com.dimajix.flowman.spec.MappingIdentifier


class SortMapping extends BaseMapping {
    private val logger = LoggerFactory.getLogger(classOf[SortMapping])

    @JsonProperty(value = "input", required = true) private var _input:String = _
    @JsonProperty(value = "columns", required = true) private var _columns:Seq[Map[String,String]] = Seq()

    def input(implicit context: Context) : MappingIdentifier = MappingIdentifier.parse(context.evaluate(_input))
    def columns(implicit context: Context) :Seq[(String,String)] = _columns.flatMap(_.mapValues(context.evaluate))

    /**
      * Executes this MappingType and returns a corresponding DataFrame
      *
      * @param executor
      * @param tables
      * @return
      */
    override def execute(executor:Executor, tables:Map[MappingIdentifier,DataFrame]) : DataFrame = {
        implicit val context = executor.context
        val input = this.input
        val columns = this.columns
        logger.info(s"Sorting mapping '$input' by columns ${columns.mkString(",")}")

        val df = tables(input)
        val cols = columns.map(nv =>
            if (nv._2.toLowerCase == "desc")
                col(nv._1).desc
            else
                col(nv._1).asc
        )
        df.sort(cols:_*)
    }

    /**
      * Returns the dependencies (i.e. names of tables in the Dataflow model)
      * @param context
      * @return
      */
    override def dependencies(implicit context: Context) : Array[MappingIdentifier] = {
        Array(input)
    }
}
